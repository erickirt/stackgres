/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.shardedcluster.context;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.stackgres.common.PatroniUtil;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.resource.ResourceFinder;
import io.stackgres.operator.conciliation.shardedcluster.StackGresShardedClusterContext.Builder;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShardedClusterWorkersPrimaryEndpointsContextAppender {

  private final ResourceFinder<Endpoints> endpointsFinder;

  public ShardedClusterWorkersPrimaryEndpointsContextAppender(ResourceFinder<Endpoints> endpointsFinder) {
    this.endpointsFinder = endpointsFinder;
  }

  public void appendContext(List<StackGresCluster> workers, Builder contextBuilder) {
    List<Endpoints> workersPrimaryEndpoints = workers.stream()
        .map(shard -> endpointsFinder
            .findByNameAndNamespace(
                PatroniUtil.readWriteName(shard),
                shard.getMetadata().getNamespace()))
        .flatMap(Optional::stream)
        .toList();
    contextBuilder.workersPrimaryEndpoints(workersPrimaryEndpoints);
  }

}
