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
        <= StackGresVersion.V_1_18.getVersionAsNumber()) {
      if (resource.getSpec().getShards() != null
          && resource.getSpec().getWorkers() == null) {
        resource.getSpec().setWorkers(resource.getSpec().getShards());
        resource.getSpec().getWorkers().setClusterNameTemplate(resource.getMetadata().getName() + "-shard");
        resource.getSpec().setShards(null);
      }
      if (resource.getSpec().getPostgresServices() != null
          && resource.getSpec().getPostgresServices().getShards() != null
          && resource.getSpec().getPostgresServices().getWorkers() == null) {
        resource.getSpec().getPostgresServices().setWorkers(
            resource.getSpec().getPostgresServices().getShards());
        resource.getSpec().getPostgresServices().setShards(null);
      }
      if (resource.getSpec().getMetadata() != null
          && resource.getSpec().getMetadata().getLabels() != null
          && resource.getSpec().getMetadata().getLabels().getShardsPrimariesService() != null
          && resource.getSpec().getMetadata().getLabels().getWorkersPrimariesService() == null) {
        resource.getSpec().getMetadata().getLabels().setWorkersPrimariesService(
            resource.getSpec().getMetadata().getLabels().getShardsPrimariesService());
        resource.getSpec().getMetadata().getLabels().setShardsPrimariesService(null);
      }
      if (resource.getSpec().getMetadata() != null
          && resource.getSpec().getMetadata().getAnnotations() != null
          && resource.getSpec().getMetadata().getAnnotations().getShardsPrimariesService() != null
          && resource.getSpec().getMetadata().getAnnotations().getWorkersPrimariesService() == null) {
        resource.getSpec().getMetadata().getAnnotations().setWorkersPrimariesService(
            resource.getSpec().getMetadata().getAnnotations().getShardsPrimariesService());
        resource.getSpec().getMetadata().getAnnotations().setShardsPrimariesService(null);
      }
    }
    return resource;
  }

}
