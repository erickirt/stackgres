/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.shardedcluster.context;

import java.util.Optional;

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
public class ShardedClusterCoordinatorClusterContextAppender {

  private final ShardedClusterCoordinatorPrimaryEndpointsContextAppender
      shardedClusterCoordinatorPrimaryEndpointsContextAppender;
  private final ObjectMapper objectMapper;

  public ShardedClusterCoordinatorClusterContextAppender(
      ShardedClusterCoordinatorPrimaryEndpointsContextAppender
          shardedClusterCoordinatorPrimaryEndpointsContextAppender,
      ObjectMapper objectMapper) {
    this.shardedClusterCoordinatorPrimaryEndpointsContextAppender =
        shardedClusterCoordinatorPrimaryEndpointsContextAppender;
    this.objectMapper = objectMapper;
  }

  public StackGresCluster appendContext(
      StackGresShardedCluster cluster,
      Builder contextBuilder,
      Optional<StackGresShardedCluster> replicateCluster) {
    StackGresCluster coordinator = getCoordinatorCluster(cluster, replicateCluster);
    contextBuilder.coordinator(coordinator);
    shardedClusterCoordinatorPrimaryEndpointsContextAppender.appendContext(coordinator, contextBuilder);
    return coordinator;
  }

  private StackGresCluster getCoordinatorCluster(
      StackGresShardedCluster original,
      Optional<StackGresShardedCluster> replicateCluster) {
    StackGresShardedCluster cluster = objectMapper.convertValue(original, StackGresShardedCluster.class);
    switch (StackGresShardingType.fromString(cluster.getSpec().getType())) {
      case CITUS:
        return StackGresShardedClusterForCitusUtil.getCoordinatorCluster(cluster, replicateCluster);
      case DDP:
        return StackGresShardedClusterForDdpUtil.getCoordinatorCluster(cluster, replicateCluster);
      case SHARDING_SPHERE:
        return StackGresShardedClusterForShardingSphereUtil.getCoordinatorCluster(cluster, replicateCluster);
      default:
        throw new UnsupportedOperationException(
            "Sharding technology " + cluster.getSpec().getType() + " not implemented");
    }
  }

}
