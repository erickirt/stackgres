/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.shardedcluster.context;

import java.util.Optional;

import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterReplicateFrom;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterReplicateFromInstance;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterSpec;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.operator.conciliation.shardedcluster.StackGresShardedClusterContext.Builder;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShardedClusterReplicateFromContextAppender {

  private final CustomResourceFinder<StackGresShardedCluster> clusterFinder;

  public ShardedClusterReplicateFromContextAppender(
      CustomResourceFinder<StackGresShardedCluster> clusterFinder) {
    this.clusterFinder = clusterFinder;
  }

  public Optional<StackGresShardedCluster> appendContext(StackGresShardedCluster cluster, Builder contextBuilder) {
    final Optional<StackGresShardedCluster> replicateCluster =
        Optional.of(cluster)
        .map(StackGresShardedCluster::getSpec)
        .map(StackGresShardedClusterSpec::getReplicateFrom)
        .map(StackGresShardedClusterReplicateFrom::getInstance)
        .map(StackGresShardedClusterReplicateFromInstance::getSgShardedCluster)
        .flatMap(sgShardedCluster -> Optional.of(
            clusterFinder.findByNameAndNamespace(
                sgShardedCluster,
                cluster.getMetadata().getNamespace())
            .orElseThrow(() -> new IllegalArgumentException("Can not find SGShardedCluster "
                + sgShardedCluster + " to replicate from"))));

    contextBuilder
        .replicateCluster(replicateCluster);

    return replicateCluster;
  }

}
