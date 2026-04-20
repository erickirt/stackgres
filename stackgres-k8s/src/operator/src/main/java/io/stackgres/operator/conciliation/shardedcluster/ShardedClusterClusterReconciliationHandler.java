/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.shardedcluster;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.operator.conciliation.FireAndForgetReconciliationHandler;
import io.stackgres.operator.conciliation.ReconciliationHandler;
import io.stackgres.operator.conciliation.ReconciliationScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ReconciliationScope(value = StackGresShardedCluster.class, kind = StackGresCluster.KIND)
@ApplicationScoped
public class ShardedClusterClusterReconciliationHandler
    implements ReconciliationHandler<StackGresShardedCluster> {

  protected static final Logger LOGGER =
      LoggerFactory.getLogger(FireAndForgetReconciliationHandler.class);

  private final ReconciliationHandler<StackGresShardedCluster> handler;

  @Inject
  public ShardedClusterClusterReconciliationHandler(
      ReconciliationHandler<StackGresShardedCluster> handler) {
    this.handler = handler;
  }

  @Override
  public final HasMetadata create(StackGresShardedCluster context, HasMetadata resource) {
    return doCreate(context, resource);
  }

  protected HasMetadata doCreate(StackGresShardedCluster context, HasMetadata resource) {
    return handler.create(context, resource);
  }

  @Override
  public final HasMetadata patch(StackGresShardedCluster context, HasMetadata newResource,
      HasMetadata oldResource) {
    return doPatch(context, newResource, oldResource);
  }

  protected HasMetadata doPatch(StackGresShardedCluster context, HasMetadata newResource, HasMetadata oldResource) {
    return handler.patch(context, newResource, oldResource);
  }

  @Override
  public final HasMetadata replace(StackGresShardedCluster context, HasMetadata resource) {
    return doReplace(context, resource);
  }

  protected HasMetadata doReplace(StackGresShardedCluster context, HasMetadata resource) {
    return handler.replace(context, resource);
  }

  @Override
  public final void delete(StackGresShardedCluster context, HasMetadata resource) {
    if (resource instanceof StackGresCluster cluster) {
      doDownscaleToZero(context, cluster);
    }
    throw new IllegalArgumentException("Resource must be a " + StackGresCluster.KIND + " instance");
  }

  @Override
  public final void deleteWithOrphans(StackGresShardedCluster context, HasMetadata resource) {
    if (resource instanceof StackGresCluster cluster) {
      doDownscaleToZero(context, cluster);
    }
    throw new IllegalArgumentException("Resource must be a " + StackGresCluster.KIND + " instance");
  }

  private void doDownscaleToZero(StackGresShardedCluster context, StackGresCluster cluster) {
    if (cluster.getSpec() != null) {
      cluster.getSpec().setInstances(0);
    }
    handler.patch(context, cluster, cluster);
  }

}
