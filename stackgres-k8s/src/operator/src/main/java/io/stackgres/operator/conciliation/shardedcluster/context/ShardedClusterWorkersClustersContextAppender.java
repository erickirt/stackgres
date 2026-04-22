/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.shardedcluster.context;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardingType;
import io.stackgres.operator.conciliation.factory.shardedcluster.StackGresShardedClusterForCitusUtil;
import io.stackgres.operator.conciliation.factory.shardedcluster.StackGresShardedClusterForDdpUtil;
import io.stackgres.operator.conciliation.factory.shardedcluster.StackGresShardedClusterForShardingSphereUtil;
import io.stackgres.operator.conciliation.shardedcluster.StackGresShardedClusterContext.Builder;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShardedClusterWorkersClustersContextAppender {

  private final ShardedClusterWorkersPrimaryEndpointsContextAppender
      shardedClusterWorkersPrimaryEndpointsContextAppender;
  private final ObjectMapper objectMapper;

  public ShardedClusterWorkersClustersContextAppender(
      ShardedClusterWorkersPrimaryEndpointsContextAppender
          shardedClusterWorkersPrimaryEndpointsContextAppender,
      ObjectMapper objectMapper) {
    this.shardedClusterWorkersPrimaryEndpointsContextAppender =
        shardedClusterWorkersPrimaryEndpointsContextAppender;
    this.objectMapper = objectMapper;
  }

  public List<StackGresCluster> appendContext(
      StackGresShardedCluster cluster,
      Builder contextBuilder,
      Optional<StackGresShardedCluster> replicateCluster) {
    List<StackGresCluster> workers = getWorkersClusters(cluster, replicateCluster);
    contextBuilder.workers(workers);
    shardedClusterWorkersPrimaryEndpointsContextAppender.appendContext(workers, contextBuilder);
    return workers;
  }

  private List<StackGresCluster> getWorkersClusters(
      StackGresShardedCluster cluster,
      Optional<StackGresShardedCluster> replicateCluster) {
    return IntStream.range(0, cluster.getSpec().getWorkers().getClusters())
        .mapToObj(index -> getWorkersCluster(cluster, index, replicateCluster))
        .toList();
  }

  private StackGresCluster getWorkersCluster(
      StackGresShardedCluster original,
      int index,
      Optional<StackGresShardedCluster> replicateCluster) {
    StackGresShardedCluster cluster = objectMapper.convertValue(original, StackGresShardedCluster.class);
    switch (StackGresShardingType.fromString(cluster.getSpec().getType())) {
      case CITUS:
        return StackGresShardedClusterForCitusUtil.getWorkersCluster(cluster, index, replicateCluster);
      case DDP:
        return StackGresShardedClusterForDdpUtil.getWorkersCluster(cluster, index, replicateCluster);
      case SHARDING_SPHERE:
        return StackGresShardedClusterForShardingSphereUtil.getWorkersCluster(cluster, index, replicateCluster);
      default:
        throw new UnsupportedOperationException(
            "Sharding technology " + cluster.getSpec().getType() + " not implemented");
    }
  }

}
