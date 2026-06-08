/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.stream.jobs.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import io.smallrye.mutiny.Uni;
import io.stackgres.common.LeaseLockUtil;
import io.stackgres.common.crd.sgstream.StackGresStream;
import io.stackgres.common.fixture.Fixtures;
import io.stackgres.stream.jobs.mock.MockKubeDbTest;
import io.stackgres.testutil.StringUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@WithKubernetesTestServer
@QuarkusTest
class LockAcquirerTest extends MockKubeDbTest {

  private final AtomicInteger streamNr = new AtomicInteger(0);
  @Inject
  LockAcquirer lockAcquirer;
  @Inject
  KubernetesClient client;
  private StackGresStream stream;
  private String streamName;
  private String streamNamespace;
  private String leaseName;
  private LockRequest lockRequest;
  private ExecutorService executorService;

  private static LockRequest buildLockRequest(StackGresStream stream) {
    return LockRequest.builder()
        .serviceAccount(StringUtils.getRandomString())
        .podName(StringUtils.getRandomString())
        .namespace(stream.getMetadata().getNamespace())
        .lockResourceName(stream.getMetadata().getName())
        .lockResourceUid(stream.getMetadata().getUid())
        .duration(30)
        .pollInterval(1)
        .build();
  }

  @BeforeEach
  void setUp() {
    stream = Fixtures.stream().loadSgClusterToCloudEvent().get();
    stream.getMetadata().setName("test-" + streamNr.incrementAndGet());
    if (stream.getMetadata().getUid() == null) {
      stream.getMetadata().setUid(java.util.UUID.randomUUID().toString());
    }
    streamName = stream.getMetadata().getName();
    streamNamespace = stream.getMetadata().getNamespace();
    leaseName = LeaseLockUtil.leaseNameForStream(stream.getMetadata().getUid());
    lockRequest = buildLockRequest(stream);
    executorService = Executors.newSingleThreadExecutor();
  }

  @AfterEach
  void tearDown() {
    executorService.shutdownNow();
    client.leases().inNamespace(streamNamespace).withName(leaseName).delete();
  }

  @Test
  void givenAnUnlockedStream_itShouldAcquireTheLockBeforeRunningTheTask() {
    prepareUnlockedLease();

    AtomicBoolean taskRunned = new AtomicBoolean(false);
    lockAcquirer.lockRun(lockRequest, Uni.createFrom().voidItem().invoke(item -> {
      Lease lease = getLease();
      assertNotNull(lease);
      assertNotNull(lease.getSpec().getHolderIdentity());
      assertEquals(holderIdentity(lockRequest), lease.getSpec().getHolderIdentity());
      assertNotNull(lease.getSpec().getRenewTime());
      taskRunned.set(true);
    })).await().indefinitely();

    assertTrue(taskRunned.get());
  }

  @Test
  void givenAnUnlockedStream_itShouldReleaseTheLockIfTheTaskExitsSuccessfully() {
    prepareUnlockedLease();

    runTaskSuccessfully();

    Lease lease = getLease();
    assertNull(lease.getSpec().getHolderIdentity());
    assertNull(lease.getSpec().getRenewTime());
  }

  @Test
  void givenALockedStreamByMe_itShouldUpdateTheLockTimestampBeforeRunningTheTask() {
    final ZonedDateTime renewTime = ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(1);
    prepareLockedLease(holderIdentity(lockRequest), renewTime, 30);

    AtomicBoolean taskRunned = new AtomicBoolean(false);

    lockAcquirer.lockRun(lockRequest, Uni.createFrom().voidItem().invoke(item -> {
      taskRunned.set(true);
      Lease lease = getLease();
      assertEquals(holderIdentity(lockRequest), lease.getSpec().getHolderIdentity());
      assertNotNull(lease.getSpec().getRenewTime());
      assertTrue(lease.getSpec().getRenewTime().isAfter(renewTime));
    })).await().indefinitely();
  }

  @Test
  void givenALockedStream_itShouldWaitUntilTheLockIsReleasedBeforeRunningTheTask() {
    final ZonedDateTime renewTime = ZonedDateTime.now(ZoneOffset.UTC);
    prepareLockedLease(StringUtils.getRandomString(), renewTime, lockRequest.getPollInterval() + 1);

    AtomicBoolean taskRan = asycRunTaskSuccessfully();

    sleep(lockRequest.getPollInterval() + 1);

    assertFalse(taskRan.get());

    removeLockHolder();

    sleep(lockRequest.getPollInterval() + 2);

    assertTrue(taskRan.get());
  }

  @Test
  void givenATimedoutLockedStream_itShouldOverrideTheLock() {
    // Renew time well in the past so lease is expired
    final ZonedDateTime renewTime =
        ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(lockRequest.getDuration() + 5);
    prepareLockedLease("some-other-holder", renewTime, lockRequest.getDuration());

    AtomicBoolean taskRan = asycRunTaskSuccessfully();

    sleep(lockRequest.getPollInterval() + 1);

    assertTrue(taskRan.get());
  }

  @Test
  void givenALongRunningTask_itShouldUpdateTheLockTimestampPeriodically() {
    prepareUnlockedLease();

    AtomicBoolean taskRan = asycRunTaskSuccessfully(3);

    assertFalse(taskRan.get());

    awaitLockAcquired();

    Lease lease = getLease();
    assertNotNull(lease.getSpec().getRenewTime());
    final ZonedDateTime expiry = lease.getSpec().getRenewTime()
        .plus(Duration.ofSeconds(lease.getSpec().getLeaseDurationSeconds()));
    assertTrue(expiry.isAfter(ZonedDateTime.now(ZoneOffset.UTC)));

    sleep(lockRequest.getPollInterval() + 3);

    assertTrue(taskRan.get());
  }

  /** Polls the test K8s server for a Lease whose holder is us, up to ~10s. */
  private void awaitLockAcquired() {
    final long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline) {
      Lease lease = getLease();
      if (lease != null
          && lease.getSpec() != null
          && holderIdentity(lockRequest).equals(lease.getSpec().getHolderIdentity())
          && lease.getSpec().getRenewTime() != null) {
        return;
      }
      sleepMillis(50);
    }
    throw new AssertionError("Lock was not acquired within 10s");
  }

  private void sleepMillis(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  private String holderIdentity(LockRequest lockRequest) {
    return LeaseLockUtil.holderIdentity(
        lockRequest.getServiceAccount(), lockRequest.getPodName());
  }

  private Lease getLease() {
    return client.leases().inNamespace(streamNamespace).withName(leaseName).get();
  }

  private void removeLockHolder() {
    Lease lease = getLease();
    lease.getSpec().setHolderIdentity(null);
    lease.getSpec().setRenewTime(null);
    lease.getSpec().setLeaseDurationSeconds(null);
    client.leases().inNamespace(streamNamespace).resource(lease).update();
  }

  private void runTaskSuccessfully() {
    AtomicBoolean taskRan = new AtomicBoolean(false);

    lockAcquirer.lockRun(lockRequest, Uni.createFrom().voidItem().invoke(item -> {
      Lease lease = getLease();
      assertEquals(holderIdentity(lockRequest), lease.getSpec().getHolderIdentity());
      taskRan.set(true);
    })).await().indefinitely();

    assertTrue(taskRan.get());
  }

  private AtomicBoolean asycRunTaskSuccessfully() {
    return asycRunTaskSuccessfully(0);
  }

  private AtomicBoolean asycRunTaskSuccessfully(int delay) {
    AtomicBoolean taskRan = new AtomicBoolean(false);

    executorService.execute(
        () -> lockAcquirer.lockRun(lockRequest,
            Uni.createFrom().voidItem().invoke(item -> {
              if (delay > 0) {
                sleep(delay);
              }
              Lease lease = getLease();
              assertEquals(holderIdentity(lockRequest), lease.getSpec().getHolderIdentity(),
                  "Task ran without Lock!!");
              assertNotNull(lease.getSpec().getRenewTime());
              taskRan.set(true);
            })).await().indefinitely());

    return taskRan;
  }

  private void prepareUnlockedLease() {
    upsertLease(new LeaseBuilder()
        .withNewMetadata()
        .withNamespace(streamNamespace)
        .withName(leaseName)
        .endMetadata()
        .withNewSpec()
        .endSpec()
        .build());
  }

  private void prepareLockedLease(String holderIdentity, ZonedDateTime renewTime,
      int durationSeconds) {
    upsertLease(new LeaseBuilder()
        .withNewMetadata()
        .withNamespace(streamNamespace)
        .withName(leaseName)
        .endMetadata()
        .withNewSpec()
        .withHolderIdentity(holderIdentity)
        .withRenewTime(renewTime)
        .withAcquireTime(renewTime)
        .withLeaseDurationSeconds(durationSeconds)
        .withLeaseTransitions(0)
        .endSpec()
        .build());
  }

  private void upsertLease(Lease lease) {
    Lease existing = client.leases()
        .inNamespace(lease.getMetadata().getNamespace())
        .withName(lease.getMetadata().getName())
        .get();
    if (existing == null) {
      client.leases().inNamespace(lease.getMetadata().getNamespace()).resource(lease).create();
    } else {
      lease.getMetadata().setResourceVersion(existing.getMetadata().getResourceVersion());
      client.leases().inNamespace(lease.getMetadata().getNamespace()).resource(lease).update();
    }
  }

  private void sleep(int seconds) {
    try {
      Thread.sleep(seconds * 1000L);
    } catch (InterruptedException ignored) {
      // ignored
    }
  }

}
