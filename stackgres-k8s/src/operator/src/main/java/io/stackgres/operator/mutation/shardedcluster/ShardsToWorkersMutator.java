/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.mutation.shardedcluster;

import io.stackgres.common.StackGresVersion;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.operator.common.StackGresShardedClusterReview;
import io.stackgres.operatorframework.admissionwebhook.Operation;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShardsToWorkersMutator implements ShardedClusterMutator {

  @Override
  public StackGresShardedCluster mutate(
      StackGresShardedClusterReview review, StackGresShardedCluster resource) {
    if (review.getRequest().getOperation() != Operation.UPDATE) {
      return resource;
    }
    if (StackGresVersion.getStackGresVersion(resource).getVersionAsNumber()
        <= StackGresVersion.V_1_18.getVersionAsNumber()
        && resource.getSpec().getShards() != null
        && resource.getSpec().getWorkers() == null) {
      resource.getSpec().setWorkers(resource.getSpec().getShards());
      resource.getSpec().getWorkers().setClusterNameTemplate(resource.getMetadata().getName() + "-shard");
    }
    return resource;
  }

}
