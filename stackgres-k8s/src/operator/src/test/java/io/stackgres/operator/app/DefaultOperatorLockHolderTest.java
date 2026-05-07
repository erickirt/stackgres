/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.stackgres.operator.conciliation.AbstractReconciliator;
import io.stackgres.operator.configuration.OperatorPropertyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Lightweight unit tests for the operator lock holder bookkeeping. The full
 * leader-election lifecycle (start/stop/onStartLeading/onStopLeading) is
 * covered by integration / e2e tests since it depends on a live or mocked
 * Kubernetes API server and the fabric8 LeaderElector loop.
 */
@ExtendWith(MockitoExtension.class)
class DefaultOperatorLockHolderTest {

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
  void computeRenewDeadlineFitsWithinLeaseDuration() {
    Duration lease = Duration.ofSeconds(30);
    Duration retry = Duration.ofSeconds(5);
    Duration renew = DefaultOperatorLockHolder.computeRenewDeadline(lease, retry);
    // fabric8 requires lease > renew > retry * (1 + JITTER_FACTOR)
    assertTrue(renew.compareTo(lease) < 0, "renew must be < lease");
    long minRenewMillis = retry.toMillis() + (long) Math.ceil(retry.toMillis() * 1.21d) + 1L;
    assertTrue(renew.toMillis() >= minRenewMillis,
        "renew must clear retry + jitter (min " + minRenewMillis + "ms)");
  }

  @Test
  void computeRenewDeadlineDefaultsToTwoThirdsOfLease() {
    // For LOCK_DURATION=60, LOCK_POLL_INTERVAL=2 the two-thirds heuristic should win
    // over the (retry + jitter) lower bound.
    Duration renew = DefaultOperatorLockHolder.computeRenewDeadline(
        Duration.ofSeconds(60), Duration.ofSeconds(2));
    assertEquals(40_000L, renew.toMillis());
  }

  @Test
  void computeRenewDeadlineRejectsTooSmallLease() {
    // retry + jitter would push renew past the lease duration.
    assertThrows(IllegalArgumentException.class,
        () -> DefaultOperatorLockHolder.computeRenewDeadline(
            Duration.ofSeconds(2), Duration.ofSeconds(2)));
  }

}
