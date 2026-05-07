/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.shardedcluster;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.operator.conciliation.FireAndForgetReconciliationHandler;
import io.stackgres.operator.conciliation.ReconciliationHandler;
import io.stackgres.operator.conciliation.ReconciliationScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Pre-creates the Lease used for distributed locking but never updates it once it exists.
 */
@ReconciliationScope(value = StackGresShardedCluster.class, kind = "Lease")
@ApplicationScoped
public class ShardedClusterLeaseReconciliationHandler
    extends FireAndForgetReconciliationHandler<StackGresShardedCluster> {

  @Inject
  public ShardedClusterLeaseReconciliationHandler(
      @ReconciliationScope(value = StackGresShardedCluster.class, kind = "HasMetadata")
      ReconciliationHandler<StackGresShardedCluster> handler) {
    super(handler);
  }

  @Override
  protected boolean canForget(StackGresShardedCluster context, HasMetadata resource) {
    return true;
  }

}
