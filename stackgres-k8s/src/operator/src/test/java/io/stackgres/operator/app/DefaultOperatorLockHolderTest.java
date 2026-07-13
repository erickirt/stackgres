/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.stackgres.operator.app.DefaultOperatorLockHolder.LeaseAction;
import io.stackgres.operator.conciliation.AbstractReconciliator;
import io.stackgres.operator.configuration.OperatorPropertyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the operator lock holder. These cover the in-memory bookkeeping
 * (register / startReconciliation / stop), the timing-guard, and the side-effect-free
 * compare-and-swap decision ({@link DefaultOperatorLockHolder#decideLeaseAction}). The
 * decision function is where the recovery behaviour lives, so it is exercised here directly;
 * the surrounding API-server I/O and the multi-replica election lifecycle remain covered by
 * integration / e2e tests.
 */
@ExtendWith(MockitoExtension.class)
class DefaultOperatorLockHolderTest {

  private static final String ME = "stackgres-operator/stackgres-operator-0";
  private static final String OTHER = "stackgres-operator/stackgres-operator-1";
  private static final int LEASE_DURATION = 30;

  @Mock
  private KubernetesClient client;

  @Mock
  private OperatorPropertyContext context;

  @Mock
  private AbstractReconciliator<?, ?> reconciliator;

  private DefaultOperatorLockHolder operatorLockHolder;

  @BeforeEach
  void setUp() {
    operatorLockHolder = new DefaultOperatorLockHolder(client, context);
  }

  private Lease leaseHeldBy(String holder, ZonedDateTime renewTime) {
    return new LeaseBuilder()
        .withNewSpec()
        .withHolderIdentity(holder)
        .withLeaseDurationSeconds(LEASE_DURATION)
        .withRenewTime(renewTime)
        .withLeaseTransitions(0)
        .endSpec()
        .build();
  }

  @Test
  void registeringBeforeLeaderElectionDoesNotTriggerReconcile() {
    operatorLockHolder.register(reconciliator);
    verify(reconciliator, never()).reconcileAll();
    assertFalse(operatorLockHolder.isLeader());
  }

  @Test
  void startReconciliationBeforeBecomingLeaderDoesNotTriggerReconcile() {
    operatorLockHolder.startReconciliation();
    operatorLockHolder.register(reconciliator);
    verify(reconciliator, never()).reconcileAll();
    assertFalse(operatorLockHolder.isLeader());
  }

  @Test
  void registerAfterStopIsSafe() {
    operatorLockHolder.stop();
    operatorLockHolder.register(reconciliator);
    verify(reconciliator, never()).reconcileAll();
  }

  @Test
  void validateLockTimingsAcceptsDurationLargerThanPollInterval() {
    DefaultOperatorLockHolder.validateLockTimings(30, 2);
  }

  @Test
  void validateLockTimingsRejectsDurationNotLargerThanPollInterval() {
    assertThrows(IllegalArgumentException.class,
        () -> DefaultOperatorLockHolder.validateLockTimings(2, 2));
    assertThrows(IllegalArgumentException.class,
        () -> DefaultOperatorLockHolder.validateLockTimings(2, 5));
  }

  @Test
  void decidesToCreateWhenLeaseIsAbsent() {
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    assertEquals(LeaseAction.CREATE,
        DefaultOperatorLockHolder.decideLeaseAction(null, ME, now));
  }

  @Test
  void decidesToRenewWhenLeaseIsAlreadyHeldByMe() {
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    assertEquals(LeaseAction.RENEW,
        DefaultOperatorLockHolder.decideLeaseAction(leaseHeldBy(ME, now), ME, now));
  }

  @Test
  void decidesToLoseWhenLeaseIsHeldByAnotherValidHolder() {
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    assertEquals(LeaseAction.LOSE,
        DefaultOperatorLockHolder.decideLeaseAction(leaseHeldBy(OTHER, now), ME, now));
  }

  @Test
  void decidesToAcquireWhenAnotherHoldersLeaseHasExpired() {
    // The recovery path the previous LeaderElector-based implementation never took: once a
    // foreign holder's lease expires, the polling loop takes it over instead of staying idle.
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    Lease expired = leaseHeldBy(OTHER, now.minusSeconds(LEASE_DURATION + 5));
    assertEquals(LeaseAction.ACQUIRE,
        DefaultOperatorLockHolder.decideLeaseAction(expired, ME, now));
  }

  @Test
  void decidesToAcquireWhenMyOwnLeaseHasExpired() {
    // A lease that was ours but lapsed (e.g. after a connectivity gap) is re-acquired rather
    // than treated as still held.
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    Lease expired = leaseHeldBy(ME, now.minusSeconds(LEASE_DURATION + 5));
    assertEquals(LeaseAction.ACQUIRE,
        DefaultOperatorLockHolder.decideLeaseAction(expired, ME, now));
  }

}
