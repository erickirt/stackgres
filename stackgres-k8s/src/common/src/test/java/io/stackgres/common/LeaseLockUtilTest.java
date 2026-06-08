/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import org.junit.jupiter.api.Test;

class LeaseLockUtilTest {

  @Test
  void holderIdentityRoundTrips() {
    String holder = LeaseLockUtil.holderIdentity("sa", "pod");
    assertEquals("sa/pod", holder);
    assertEquals(Optional.of("sa"), LeaseLockUtil.getServiceAccountFromHolderIdentity(holder));
    assertEquals(Optional.of("pod"), LeaseLockUtil.getPodFromHolderIdentity(holder));
  }

  @Test
  void getServiceAccountReturnsEmptyForMalformedIdentity() {
    assertEquals(Optional.empty(), LeaseLockUtil.getServiceAccountFromHolderIdentity(null));
    assertEquals(Optional.empty(), LeaseLockUtil.getServiceAccountFromHolderIdentity("noslash"));
  }

  @Test
  void leaseNamesUseExpectedPrefixes() {
    String uid = "12345678-1234-1234-1234-123456789012";
    assertEquals("sgcluster-" + uid, LeaseLockUtil.leaseNameForCluster(uid));
    assertEquals("sgshardedcluster-" + uid, LeaseLockUtil.leaseNameForShardedCluster(uid));
    assertEquals("sgstream-" + uid, LeaseLockUtil.leaseNameForStream(uid));
    // All names must fit in the 63-char DNS label limit
    assertTrue(LeaseLockUtil.leaseNameForCluster(uid).length() <= 63);
    assertTrue(LeaseLockUtil.leaseNameForShardedCluster(uid).length() <= 63);
    assertTrue(LeaseLockUtil.leaseNameForStream(uid).length() <= 63);
  }

  @Test
  void heldDetectsNonExpiredLease() {
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    Lease lease = lease("sa/pod", now, 30);
    assertTrue(LeaseLockUtil.isHeld(lease, now.plusSeconds(5)));
    assertTrue(LeaseLockUtil.isHeldBy(lease, "sa/pod", now.plusSeconds(5)));
    assertFalse(LeaseLockUtil.isHeldBy(lease, "other/pod", now.plusSeconds(5)));
  }

  @Test
  void heldReturnsFalseAfterExpiry() {
    ZonedDateTime renew = ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(60);
    Lease lease = lease("sa/pod", renew, 30);
    assertFalse(LeaseLockUtil.isHeld(lease));
    assertFalse(LeaseLockUtil.isHeldBy(lease, "sa/pod"));
  }

  @Test
  void heldReturnsFalseForEmptyLease() {
    Lease lease = new LeaseBuilder().withNewMetadata().endMetadata().withNewSpec().endSpec().build();
    assertFalse(LeaseLockUtil.isHeld(lease));
    assertFalse(LeaseLockUtil.isHeldBy(lease, "sa/pod"));
  }

  @Test
  void getServiceAccountReadsHolder() {
    Lease lease = lease("sa/pod", ZonedDateTime.now(ZoneOffset.UTC), 30);
    assertEquals(Optional.of("sa"), LeaseLockUtil.getServiceAccount(lease));
  }

  private static Lease lease(String holder, ZonedDateTime renewTime, int durationSeconds) {
    return new LeaseBuilder()
        .withNewMetadata().withName("test").withNamespace("test").endMetadata()
        .withNewSpec()
        .withHolderIdentity(holder)
        .withRenewTime(renewTime)
        .withAcquireTime(renewTime)
        .withLeaseDurationSeconds(durationSeconds)
        .withLeaseTransitions(0)
        .endSpec()
        .build();
  }

}
