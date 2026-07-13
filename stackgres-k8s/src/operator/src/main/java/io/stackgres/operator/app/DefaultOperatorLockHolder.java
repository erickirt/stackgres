/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.app;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.quarkus.runtime.Quarkus;
import io.stackgres.common.LeaseLockUtil;
import io.stackgres.common.OperatorProperty;
import io.stackgres.common.kubernetesclient.KubernetesClientUtil;
import io.stackgres.operator.conciliation.AbstractReconciliator;
import io.stackgres.operator.configuration.OperatorPropertyContext;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the operator leader lock as a Kubernetes {@link Lease}, maintained through a
 * compare-and-swap loop that talks to the API server directly (no fabric8
 * {@code LeaderElector}/{@code LeaseLock} intermediates).
 *
 * <p>A single-threaded scheduler runs {@link #tryHoldLock()} every
 * {@link OperatorProperty#LOCK_POLL_INTERVAL} seconds. Each tick reads the Lease and, when it
 * is held by us or free/expired, renews or acquires it with an optimistic-locked write
 * (resourceVersion precondition) — the compare-and-swap. Because the loop polls forever, a
 * lock lost to a transient API-server outage is re-acquired as soon as it expires, instead of
 * leaving the operator stuck as a non-leader (the failure mode of the previous
 * {@code LeaderElector}-based implementation, whose election future completed on loss and was
 * never restarted).
 */
@Singleton
public class DefaultOperatorLockHolder implements OperatorLockHolder {

  protected static final Logger LOGGER = LoggerFactory.getLogger(
      DefaultOperatorLockHolder.class.getPackage().getName());

  static final String OPERATOR_LEASE_NAME = LeaseLockUtil.LEASE_NAME_PREFIX_OPERATOR + "-leader";

  private final KubernetesClient client;
  private final OperatorPropertyContext context;
  private final ScheduledExecutorService executorService;

  private final AtomicBoolean leader = new AtomicBoolean(false);
  private final AtomicBoolean doReconciliation = new AtomicBoolean(false);
  private final List<AbstractReconciliator<?, ?>> reconciliators = new ArrayList<>();

  protected DefaultOperatorLockHolder(
      KubernetesClient client,
      OperatorPropertyContext context) {
    this.client = client;
    this.context = context;
    this.executorService = Executors.newSingleThreadScheduledExecutor(
        r -> new Thread(r, "OperatorLockHolder"));
  }

  @Override
  public boolean isLeader() {
    return leader.get();
  }

  @Override
  public void register(AbstractReconciliator<?, ?> reconciliator) {
    this.reconciliators.add(reconciliator);
    if (leader.get() && doReconciliation.get()) {
      this.reconciliators.forEach(AbstractReconciliator::reconcileAll);
    }
  }

  @Override
  public void startReconciliation() {
    doReconciliation.set(true);
    if (leader.get()) {
      this.reconciliators.forEach(AbstractReconciliator::reconcileAll);
    }
  }

  @Override
  public void start() {
    final int leaseDurationSeconds = context.getInt(OperatorProperty.LOCK_DURATION);
    final int retryPeriodSeconds = context.getInt(OperatorProperty.LOCK_POLL_INTERVAL);
    validateLockTimings(leaseDurationSeconds, retryPeriodSeconds);
    LOGGER.info("Starting Operator Lock Reconciliation using Lease {}/{}",
        operatorNamespace(), OPERATOR_LEASE_NAME);
    executorService.scheduleWithFixedDelay(
        this::tryHoldLock,
        0,
        retryPeriodSeconds,
        TimeUnit.SECONDS);
  }

  @Override
  public void stop() {
    if (!executorService.isShutdown()) {
      LOGGER.info("Stopping Operator Lock Reconciliation");
      executorService.shutdown();
    }
    try {
      executorService.awaitTermination(
          context.getInt(OperatorProperty.LOCK_POLL_INTERVAL),
          TimeUnit.SECONDS);
    } catch (Exception ex) {
      LOGGER.error("An error occurred during shutdown of operator lock reconciliator", ex);
    }
    releaseLock();
  }

  @Override
  public void forceUnlockOthers() {
    if (isLeader()) {
      return;
    }
    try {
      final String holderIdentity = holderIdentity();
      Lease lease = getLease();
      Optional<String> currentHolder = LeaseLockUtil.getHolderIdentity(lease);
      if (currentHolder.isEmpty()
          || currentHolder.get().equals(holderIdentity)
          || !LeaseLockUtil.isHeld(lease)) {
        return;
      }
      clearHolder(lease);
      LOGGER.info("Lease {}/{} held by {} was forcibly released",
          operatorNamespace(), OPERATOR_LEASE_NAME, currentHolder.get());
    } catch (KubernetesClientException ex) {
      LOGGER.warn("Could not forcibly release operator Lease {}/{}: {}",
          operatorNamespace(), OPERATOR_LEASE_NAME, ex.getMessage());
    }
  }

  void tryHoldLock() {
    try {
      final String holderIdentity = holderIdentity();
      final Lease lease = getLease();
      final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
      switch (decideLeaseAction(lease, holderIdentity, now)) {
        case CREATE -> acquireByCreate(holderIdentity, now);
        case RENEW, ACQUIRE -> acquireByUpdate(lease, holderIdentity, now);
        case LOSE -> onLockLost(lease);
        default -> { /* nothing to do */ }
      }
    } catch (KubernetesClientException ex) {
      if (KubernetesClientUtil.isConflict(ex)) {
        LOGGER.debug("Conflict while holding operator Lease {}/{}, will retry on next poll",
            operatorNamespace(), OPERATOR_LEASE_NAME);
      } else {
        LOGGER.error("Reconciliation of operator lock failed", ex);
      }
    } catch (Exception ex) {
      LOGGER.error("Reconciliation of operator lock failed", ex);
    }
  }

  /**
   * The action this replica must take for the current Lease state, given who we are and the
   * current time. Kept side-effect free so the lock decision can be unit-tested without a live
   * API server: {@code CREATE} (Lease absent), {@code RENEW} (already ours), {@code ACQUIRE}
   * (free or expired — take it over) or {@code LOSE} (held by another valid holder).
   */
  enum LeaseAction {
    CREATE, RENEW, ACQUIRE, LOSE
  }

  static LeaseAction decideLeaseAction(Lease lease, String holderIdentity, ZonedDateTime now) {
    if (lease == null) {
      return LeaseAction.CREATE;
    }
    if (LeaseLockUtil.isHeldBy(lease, holderIdentity, now)) {
      return LeaseAction.RENEW;
    }
    if (!LeaseLockUtil.isHeld(lease, now)) {
      return LeaseAction.ACQUIRE;
    }
    return LeaseAction.LOSE;
  }

  private void acquireByCreate(String holderIdentity, ZonedDateTime now) {
    Lease lease = new LeaseBuilder()
        .withNewMetadata()
        .withNamespace(operatorNamespace())
        .withName(OPERATOR_LEASE_NAME)
        .endMetadata()
        .withNewSpec()
        .withHolderIdentity(holderIdentity)
        .withLeaseDurationSeconds(context.getInt(OperatorProperty.LOCK_DURATION))
        .withAcquireTime(now)
        .withRenewTime(now)
        .withLeaseTransitions(0)
        .endSpec()
        .build();
    client.leases()
        .inNamespace(operatorNamespace())
        .resource(lease)
        .create();
    onLockAcquired();
  }

  private void acquireByUpdate(Lease lease, String holderIdentity, ZonedDateTime now) {
    final LeaseSpec spec = Optional.ofNullable(lease.getSpec()).orElseGet(LeaseSpec::new);
    lease.setSpec(spec);
    if (!holderIdentity.equals(spec.getHolderIdentity())) {
      spec.setAcquireTime(now);
      spec.setLeaseTransitions(Optional.ofNullable(spec.getLeaseTransitions()).orElse(0) + 1);
    }
    spec.setHolderIdentity(holderIdentity);
    spec.setRenewTime(now);
    spec.setLeaseDurationSeconds(context.getInt(OperatorProperty.LOCK_DURATION));
    client.leases()
        .inNamespace(operatorNamespace())
        .resource(lease)
        .lockResourceVersion(lease.getMetadata().getResourceVersion())
        .update();
    onLockAcquired();
  }

  private void onLockAcquired() {
    if (leader.compareAndSet(false, true)) {
      LOGGER.info("Lease on {}/{} was acquired. I am the leader!",
          operatorNamespace(), OPERATOR_LEASE_NAME);
      if (doReconciliation.get()) {
        this.reconciliators.forEach(AbstractReconciliator::reconcileAll);
      }
    }
  }

  private void onLockLost(Lease lease) {
    if (leader.compareAndSet(true, false)) {
      LOGGER.warn("Lease on {}/{} was lost, now held by {}",
          operatorNamespace(), OPERATOR_LEASE_NAME,
          LeaseLockUtil.getHolderIdentity(lease).orElse("<unknown>"));
      if (context.getBoolean(OperatorProperty.FORCE_UNLOCK_OPERATOR)) {
        LOGGER.error("Lease was lost while forcing unlock operator, exiting");
        Quarkus.asyncExit(1);
      }
    }
  }

  private void releaseLock() {
    if (!leader.get()) {
      return;
    }
    try {
      final String holderIdentity = holderIdentity();
      Lease lease = getLease();
      if (LeaseLockUtil.isHeldBy(lease, holderIdentity)) {
        clearHolder(lease);
        LOGGER.info("Lease on {}/{} was released", operatorNamespace(), OPERATOR_LEASE_NAME);
      }
    } catch (KubernetesClientException ex) {
      LOGGER.warn("Could not release operator Lease {}/{}: {}",
          operatorNamespace(), OPERATOR_LEASE_NAME, ex.getMessage());
    } finally {
      leader.set(false);
    }
  }

  private void clearHolder(Lease lease) {
    lease.getSpec().setHolderIdentity(null);
    lease.getSpec().setRenewTime(null);
    client.leases()
        .inNamespace(operatorNamespace())
        .resource(lease)
        .lockResourceVersion(lease.getMetadata().getResourceVersion())
        .update();
  }

  private Lease getLease() {
    return client.leases()
        .inNamespace(operatorNamespace())
        .withName(OPERATOR_LEASE_NAME)
        .get();
  }

  private String holderIdentity() {
    return LeaseLockUtil.holderIdentity(
        context.getString(OperatorProperty.OPERATOR_SERVICE_ACCOUNT),
        context.getString(OperatorProperty.OPERATOR_POD_NAME));
  }

  private String operatorNamespace() {
    return context.getString(OperatorProperty.OPERATOR_NAMESPACE);
  }

  /**
   * The lease duration must exceed the poll interval so the holder gets at least one renewal
   * opportunity within each lease window; otherwise the lock could expire between polls.
   */
  static void validateLockTimings(int leaseDurationSeconds, int retryPeriodSeconds) {
    if (leaseDurationSeconds <= retryPeriodSeconds) {
      throw new IllegalArgumentException(
          "LOCK_DURATION (" + leaseDurationSeconds + "s) must be larger than"
              + " LOCK_POLL_INTERVAL (" + retryPeriodSeconds + "s)");
    }
  }

}
