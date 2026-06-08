/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.stream.jobs.lock;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseSpec;
import io.smallrye.mutiny.Uni;
import io.stackgres.common.LeaseLockUtil;
import io.stackgres.common.resource.LeaseFinder;
import io.stackgres.common.resource.LeaseWriter;
import io.stackgres.stream.jobs.MutinyUtil;
import io.stackgres.stream.jobs.StreamExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class LockAcquirer {

  private static final Logger LOGGER = LoggerFactory.getLogger(LockAcquirer.class);

  @Inject
  LeaseFinder leaseFinder;

  @Inject
  LeaseWriter leaseWriter;

  @Inject
  StreamExecutorService executorService;

  public Uni<?> lockRun(LockRequest lockRequest, Uni<?> task) {
    final String leaseName = LeaseLockUtil.leaseNameForStream(lockRequest.getLockResourceUid());
    final String holderIdentity = LeaseLockUtil.holderIdentity(
        lockRequest.getServiceAccount(), lockRequest.getPodName());
    return executorService.itemAsync(() -> getLease(lockRequest.getNamespace(), leaseName))
        .invoke(lease -> LOGGER.info("Acquiring Lease {}/{} for stream {}",
            lockRequest.getNamespace(), leaseName, lockRequest.getLockResourceName()))
        .invoke(lease -> acquireLock(lockRequest, leaseName, holderIdentity))
        .onFailure(RetryLockException.class)
        .retry()
        .withBackOff(
            Duration.ofSeconds(lockRequest.getPollInterval()),
            Duration.ofSeconds(lockRequest.getPollInterval()))
        .indefinitely()
        .invoke(lease -> LOGGER.info("Lease {}/{} acquired for stream {}",
            lockRequest.getNamespace(), leaseName, lockRequest.getLockResourceName()))
        .invoke(() -> LOGGER.info("Executing locked task"))
        .chain(lease -> Uni.combine().any().of(
            task
                .onFailure()
                .invoke(ex -> LOGGER.error("Locked task failed", ex))
                .chain(() -> Uni.createFrom().voidItem()),
            Uni.createFrom().voidItem()
                .chain(() -> executorService.invokeAsync(
                    () -> refreshLock(lockRequest, leaseName, holderIdentity)))
                .onItem()
                .delayIt()
                .by(Duration.ofSeconds(lockRequest.getPollInterval()))
                .repeat()
                .indefinitely()
                .skip()
                .where(ignored -> true)
                .toUni()
                .onFailure()
                .transform(MutinyUtil.logOnFailureToRetry("updating the lock")))
            .onItemOrFailure()
            .call((result, ex) -> Uni.createFrom().voidItem()
                .chain(() -> executorService.invokeAsync(
                    () -> releaseLock(lockRequest, leaseName, holderIdentity)))
                .invoke(() -> LOGGER.info("Lease {}/{} released for stream {}",
                    lockRequest.getNamespace(), leaseName, lockRequest.getLockResourceName()))
                .onFailure()
                .transform(MutinyUtil.logOnFailureToRetry("releasing the lock"))
                .onFailure()
                .retry()
                .withBackOff(Duration.ofMillis(5), Duration.ofSeconds(5))
                .atMost(10)
                .invoke(() -> {
                  if (ex != null) {
                    throw new RetryLockException(ex);
                  }
                })
                .onFailure(RetryLockException.class)
                .transform(Throwable::getCause)));
  }

  private Lease getLease(String namespace, String leaseName) {
    return leaseFinder.findByNameAndNamespace(leaseName, namespace)
        .orElseThrow(() -> new IllegalStateException(
            "Lease " + namespace + "/" + leaseName + " does not exist."
                + " The operator must pre-create it."));
  }

  private void acquireLock(LockRequest lockRequest, String leaseName, String holderIdentity) {
    final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    Lease lease = getLease(lockRequest.getNamespace(), leaseName);
    if (LeaseLockUtil.isHeld(lease, now)
        && !LeaseLockUtil.isHeldBy(lease, holderIdentity, now)) {
      LOGGER.info("Lease {}/{} is held by {}, waiting for release",
          lockRequest.getNamespace(), leaseName,
          LeaseLockUtil.getHolderIdentity(lease).orElse("?"));
      throw new RetryLockException();
    }
    leaseWriter.update(lease, foundLease -> setHolder(foundLease, holderIdentity,
        lockRequest.getDuration(), now,
        !LeaseLockUtil.isHeldBy(foundLease, holderIdentity, now)));
  }

  private void refreshLock(LockRequest lockRequest, String leaseName, String holderIdentity) {
    final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    Lease lease = getLease(lockRequest.getNamespace(), leaseName);
    if (!LeaseLockUtil.isHeldBy(lease, holderIdentity, now)) {
      LOGGER.error("Lock lost for stream {}", lockRequest.getLockResourceName());
      throw new RuntimeException(
          "Lock lost for stream " + lockRequest.getLockResourceName());
    }
    leaseWriter.update(lease, foundLease -> setHolder(foundLease, holderIdentity,
        lockRequest.getDuration(), now, false));
  }

  private void releaseLock(LockRequest lockRequest, String leaseName, String holderIdentity) {
    Lease lease = leaseFinder.findByNameAndNamespace(leaseName, lockRequest.getNamespace())
        .orElse(null);
    if (lease == null
        || !LeaseLockUtil.isHeldBy(lease, holderIdentity, ZonedDateTime.now(ZoneOffset.UTC))) {
      return;
    }
    leaseWriter.update(lease, foundLease -> {
      if (foundLease.getSpec() == null) {
        return;
      }
      foundLease.getSpec().setHolderIdentity(null);
      foundLease.getSpec().setLeaseDurationSeconds(null);
      foundLease.getSpec().setRenewTime(null);
    });
  }

  private static void setHolder(Lease lease, String holderIdentity, int durationSeconds,
      ZonedDateTime now, boolean acquireNew) {
    if (lease.getSpec() == null) {
      lease.setSpec(new LeaseSpec());
    }
    lease.getSpec().setHolderIdentity(holderIdentity);
    lease.getSpec().setLeaseDurationSeconds(durationSeconds);
    lease.getSpec().setRenewTime(now);
    if (acquireNew) {
      lease.getSpec().setAcquireTime(now);
      lease.getSpec().setLeaseTransitions(
          (lease.getSpec().getLeaseTransitions() == null
              ? 0 : lease.getSpec().getLeaseTransitions()) + 1);
    }
  }

}
