/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgshardedcluster;

import org.jetbrains.annotations.NotNull;

public enum StackGresWorkerType {

  WORKER("Worker"),
  QUERY_ROUTER("QueryRouter");

  private final @NotNull String type;

  StackGresWorkerType(@NotNull String type) {
    this.type = type;
  }

  @Override
  public @NotNull String toString() {
    return type;
  }

  public static @NotNull StackGresWorkerType fromString(@NotNull String from) {
    for (StackGresWorkerType value : StackGresWorkerType.values()) {
      if (value.toString().equals(from)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Unknwon worker type " + from);
  }
}
