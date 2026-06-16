/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgconfig;

import org.jetbrains.annotations.NotNull;

public enum StackGresConfigFeatureGates {

  IO_LIMITS("io-limits");

  private final @NotNull String type;

  StackGresConfigFeatureGates(@NotNull String type) {
    this.type = type;
  }

  @Override
  public @NotNull String toString() {
    return type;
  }
}
