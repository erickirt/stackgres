/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.resource;

import io.stackgres.common.crd.sgshardeddbops.StackGresShardedDbOps;
import io.stackgres.common.crd.sgshardeddbops.StackGresShardedDbOpsList;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShardedDbOpsWriter
    extends AbstractCustomResourceWriter<StackGresShardedDbOps, StackGresShardedDbOpsList> {

  public ShardedDbOpsWriter() {
    super(StackGresShardedDbOps.class, StackGresShardedDbOpsList.class);
  }

}
