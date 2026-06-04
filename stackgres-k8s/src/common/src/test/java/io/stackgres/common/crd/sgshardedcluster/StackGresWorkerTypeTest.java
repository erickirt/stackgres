/*
 * Copyright (C) 2026 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgshardedcluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StackGresWorkerTypeTest {

  @Test
  void toString_returnsExternalName() {
    assertEquals("Worker", StackGresWorkerType.WORKER.toString());
    assertEquals("QueryRouter", StackGresWorkerType.QUERY_ROUTER.toString());
  }

  @Test
  void fromString_returnsMatchingValue() {
    assertSame(StackGresWorkerType.WORKER, StackGresWorkerType.fromString("Worker"));
    assertSame(StackGresWorkerType.QUERY_ROUTER, StackGresWorkerType.fromString("QueryRouter"));
  }

  @Test
  void fromString_unknownValue_throws() {
    assertThrows(IllegalArgumentException.class, () -> StackGresWorkerType.fromString("Other"));
  }

  @Test
  void fromString_isCaseSensitive() {
    assertThrows(IllegalArgumentException.class, () -> StackGresWorkerType.fromString("worker"));
    assertThrows(IllegalArgumentException.class, () -> StackGresWorkerType.fromString("queryrouter"));
  }

}
