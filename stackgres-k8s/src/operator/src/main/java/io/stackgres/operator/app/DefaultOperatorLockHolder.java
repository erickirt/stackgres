/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.app;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfigBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElector;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import io.quarkus.runtime.Quarkus;
import io.stackgres.common.LeaseLockUtil;
import io.stackgres.common.OperatorProperty;
import io.stackgres.operator.conciliation.AbstractReconciliator;
import io.stackgres.operator.configuration.OperatorPropertyContext;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DefaultOperatorLockHolder implements OperatorLockHolder {

  protected static final Logger LOGGER = LoggerFactory.getLogger(
      DefaultOperatorLockHolder.class.getPackage().getName());

  static final String OPERATOR_LEASE_NAME = LeaseLockUtil.LEASE_NAME_PREFIX_OPERATOR + "-leader";

  private final KubernetesClient client;
  private final OperatorPropertyContext context;
  private final ExecutorService executorService;

  private final AtomicBoolean leader = new AtomicBoolean(false);
  private final AtomicBoolean doReconciliation = new AtomicBoolean(false);
  private final List<AbstractReconciliator<?, ?>> reconciliators = new ArrayList<>();

  private CompletableFuture<?> electionFuture;

  protected DefaultOperatorLockHolder(
      KubernetesClient client,
      OperatorPropertyContext context) {
    this.client = client;
    this.context = context;
    this.executorService = Executors.newSingleThreadExecutor(
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
    LOGGER.info("Starting Operator Lock Reconciliation using Lease {}/{}",
        operatorNamespace(), OPERATOR_LEASE_NAME);
    final int leaseDurationSeconds = context.getInt(OperatorProperty.LOCK_DURATION);
    final int retryPeriodSeconds = context.getInt(OperatorProperty.LOCK_POLL_INTERVAL);
    final Duration leaseDuration = Duration.ofSeconds(leaseDurationSeconds);
    final Duration retryPeriod = Duration.ofSeconds(retryPeriodSeconds);
    final Duration renewDeadline = computeRenewDeadline(leaseDuration, retryPeriod);

    final LeaseLock lock = new LeaseLock(
        operatorNamespace(),
        OPERATOR_LEASE_NAME,
        holderIdentity());

    final LeaderElector elector = new LeaderElector(
        client,
        new LeaderElectionConfigBuilder()
            .withName(OPERATOR_LEASE_NAME)
            .withLock(lock)
            .withLeaseDuration(leaseDuration)
            .withRenewDeadline(renewDeadline)
            .withRetryPeriod(retryPeriod)
            .withReleaseOnCancel(true)
            .withLeaderCallbacks(new LeaderCallbacks(
                this::onStartLeading,
                this::onStopLeading,
                this::onNewLeader))
            .build(),
        executorService);

    electionFuture = elector.start();
  }

  @Override
  public void stop() {
    if (electionFuture != null) {
      LOGGER.info("Stopping Operator Lock Reconciliation");
      electionFuture.cancel(true);
      electionFuture = null;
    }
    if (!executorService.isShutdown()) {
      executorService.shutdown();
    }
    try {
      executorService.awaitTermination(
          context.getInt(OperatorProperty.LOCK_POLL_INTERVAL),
          TimeUnit.SECONDS);
    } catch (Exception ex) {
      LOGGER.error("An error occurred during shutdown of operator lock reconciliator", ex);
    }
  }

  @Override
  public void forceUnlockOthers() {
    if (isLeader()) {
      return;
    }
    try {
      var deleted = client.leases()
          .inNamespace(operatorNamespace())
          .withName(OPERATOR_LEASE_NAME)
          .delete();
      if (deleted != null && !deleted.isEmpty()) {
        LOGGER.info("Lease {}/{} was forcibly deleted",
            operatorNamespace(), OPERATOR_LEASE_NAME);
      }
    } catch (KubernetesClientException ex) {
      LOGGER.warn("Could not forcibly delete operator Lease {}/{}: {}",
          operatorNamespace(), OPERATOR_LEASE_NAME, ex.getMessage());
    }
  }

  private void onStartLeading() {
    LOGGER.info("Lease on {}/{} was acquired. I am the leader!",
        operatorNamespace(), OPERATOR_LEASE_NAME);
    leader.set(true);
    if (doReconciliation.get()) {
      this.reconciliators.forEach(AbstractReconciliator::reconcileAll);
    }
  }

  private void onStopLeading() {
    LOGGER.warn("Lease on {}/{} was lost", operatorNamespace(), OPERATOR_LEASE_NAME);
    boolean wasLeader = leader.getAndSet(false);
    if (wasLeader && context.getBoolean(OperatorProperty.FORCE_UNLOCK_OPERATOR)) {
      LOGGER.error("Lease was lost while forcing unlock operator, exiting");
      Quarkus.asyncExit(1);
    }
  }

  private void onNewLeader(String newLeaderIdentity) {
    if (!holderIdentity().equals(newLeaderIdentity)) {
      LOGGER.info("Operator Lease is held by {}", newLeaderIdentity);
    }
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
   * fabric8's LeaderElectorBuilder requires renewDeadline > retryPeriod * (1 + JITTER_FACTOR)
   * and leaseDuration > renewDeadline. Pick a renew deadline that satisfies both bounds.
   */
  static Duration computeRenewDeadline(Duration leaseDuration, Duration retryPeriod) {
    long lease = leaseDuration.toMillis();
    long retry = retryPeriod.toMillis();
    long minRenew = retry + (long) Math.ceil(retry * 1.21d) + 1L;
    long preferred = Math.max(minRenew, lease * 2L / 3L);
    long maxRenew = lease - 1L;
    if (preferred >= maxRenew) {
      throw new IllegalArgumentException(
          "LOCK_DURATION (" + leaseDuration.toSeconds() + "s) must be larger than"
              + " LOCK_POLL_INTERVAL (" + retryPeriod.toSeconds() + "s)"
              + " to leave room for renew deadline (need at least " + (preferred + 1) + "ms)");
    }
    return Duration.ofMillis(preferred);
  }

}
