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
  private final ShardedClusterQueryRoutersPrimaryEndpointsContextAppender
      shardedClusterQueryRoutersPrimaryEndpointsContextAppender;
  private final ObjectMapper objectMapper;

  public ShardedClusterWorkersClustersContextAppender(
      ShardedClusterWorkersPrimaryEndpointsContextAppender
          shardedClusterWorkersPrimaryEndpointsContextAppender,
      ShardedClusterQueryRoutersPrimaryEndpointsContextAppender
          shardedClusterQueryRoutersPrimaryEndpointsContextAppender,
      ObjectMapper objectMapper) {
    this.shardedClusterWorkersPrimaryEndpointsContextAppender =
        shardedClusterWorkersPrimaryEndpointsContextAppender;
    this.shardedClusterQueryRoutersPrimaryEndpointsContextAppender =
        shardedClusterQueryRoutersPrimaryEndpointsContextAppender;
    this.objectMapper = objectMapper;
  }

  public void appendContext(
      StackGresShardedCluster cluster,
      Builder contextBuilder,
      Optional<StackGresShardedCluster> replicateCluster) {
    List<StackGresCluster> workers = getWorkersClusters(cluster, replicateCluster);
    contextBuilder.workers(workers);
    shardedClusterWorkersPrimaryEndpointsContextAppender.appendContext(workers, contextBuilder);
    List<StackGresCluster> queryRouters = getQueryRoutersClusters(cluster, replicateCluster);
    contextBuilder.queryRouters(queryRouters);
    shardedClusterQueryRoutersPrimaryEndpointsContextAppender.appendContext(queryRouters, contextBuilder);
  }

  private List<StackGresCluster> getWorkersClusters(
      StackGresShardedCluster cluster,
      Optional<StackGresShardedCluster> replicateCluster) {
    return IntStream.range(0, cluster.getSpec().getWorkers().getClusters())
        .mapToObj(index -> getWorkerCluster(cluster, index, replicateCluster))
        .toList();
  }

  private StackGresCluster getWorkerCluster(
      StackGresShardedCluster original,
      int index,
      Optional<StackGresShardedCluster> replicateCluster) {
    StackGresShardedCluster cluster = objectMapper.convertValue(original, StackGresShardedCluster.class);
    switch (StackGresShardingType.fromString(cluster.getSpec().getType())) {
      case CITUS:
        return StackGresShardedClusterForCitusUtil.getWorkerCluster(cluster, index, replicateCluster);
      case DDP:
        return StackGresShardedClusterForDdpUtil.getWorkerCluster(cluster, index, replicateCluster);
      case SHARDING_SPHERE:
        return StackGresShardedClusterForShardingSphereUtil.getWorkerCluster(cluster, index, replicateCluster);
      default:
        throw new UnsupportedOperationException(
            "Sharding technology " + cluster.getSpec().getType() + " not implemented");
    }
  }

  private List<StackGresCluster> getQueryRoutersClusters(
      StackGresShardedCluster cluster,
      Optional<StackGresShardedCluster> replicateCluster) {
    final int queryRouterIndexOffset =
        Optional.ofNullable(cluster.getSpec().getCoordinator().getQueryRouterIndexOffset())
        .orElse(1024);
    final int queryRouterClusters = Optional.ofNullable(cluster.getSpec().getCoordinator().getQueryRouterClusters())
        .orElse(0);
    return IntStream.range(
            queryRouterIndexOffset,
            queryRouterIndexOffset + queryRouterClusters)
        .mapToObj(index -> getQueryRouterCluster(cluster, index, replicateCluster))
        .toList();
  }

  private StackGresCluster getQueryRouterCluster(
      StackGresShardedCluster original,
      int index,
      Optional<StackGresShardedCluster> replicateCluster) {
    StackGresShardedCluster cluster = objectMapper.convertValue(original, StackGresShardedCluster.class);
    switch (StackGresShardingType.fromString(cluster.getSpec().getType())) {
      case CITUS:
        return StackGresShardedClusterForCitusUtil.getQueryRouterCluster(cluster, index, replicateCluster);
      default:
        throw new UnsupportedOperationException(
            "Sharding technology " + cluster.getSpec().getType() + " not implemented for query routers");
    }
  }

}
