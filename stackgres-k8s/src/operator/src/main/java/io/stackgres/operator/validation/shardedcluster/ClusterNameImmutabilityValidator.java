/*
 * Copyright (C) 2026 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.shardedcluster;

import java.util.Objects;
import java.util.Optional;

import io.stackgres.common.ErrorType;
import io.stackgres.common.StackGresVersion;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterCoordinator;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterSpec;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterWorkers;
import io.stackgres.operator.common.StackGresShardedClusterReview;
import io.stackgres.operator.validation.ValidationType;
import io.stackgres.operatorframework.admissionwebhook.Operation;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationFailed;
import jakarta.inject.Singleton;

@Singleton
@ValidationType(ErrorType.FORBIDDEN_CR_UPDATE)
public class ClusterNameImmutabilityValidator implements ShardedClusterValidator {

  @Override
  public void validate(StackGresShardedClusterReview review) throws ValidationFailed {
    if (review.getRequest().getOperation() != Operation.UPDATE) {
      return;
    }
    StackGresShardedCluster cluster = review.getRequest().getObject();
    StackGresShardedCluster oldCluster = review.getRequest().getOldObject();

    String coordinatorClusterName = coordinatorClusterName(cluster);
    String oldCoordinatorClusterName = coordinatorClusterName(oldCluster);
    if (!Objects.equals(coordinatorClusterName, oldCoordinatorClusterName)) {
      fail("spec.coordinator.clusterName can only be set on creation");
    }

    String workersClusterNameTemplate = workersClusterNameTemplate(cluster);
    String oldWorkersClusterNameTemplate = workersClusterNameTemplate(oldCluster);
    if (!Objects.equals(workersClusterNameTemplate, oldWorkersClusterNameTemplate)
        && !(StackGresVersion.getStackGresVersion(oldCluster).getVersionAsNumber()
            <= StackGresVersion.V_1_18.getVersionAsNumber()
          && oldCluster.getSpec().getShards() != null
          && oldCluster.getSpec().getWorkers() == null
          && workersClusterNameTemplate.equals(cluster.getMetadata().getName() + "-shard"))) {
      fail("spec.workers.clusterNameTemplate can only be set on creation");
    }
  }

  private static String coordinatorClusterName(StackGresShardedCluster cluster) {
    return Optional.ofNullable(cluster)
        .map(StackGresShardedCluster::getSpec)
        .map(StackGresShardedClusterSpec::getCoordinator)
        .map(StackGresShardedClusterCoordinator::getClusterName)
        .orElse(null);
  }

  private static String workersClusterNameTemplate(StackGresShardedCluster cluster) {
    return Optional.ofNullable(cluster)
        .map(StackGresShardedCluster::getSpec)
        .map(StackGresShardedClusterSpec::getWorkers)
        .map(StackGresShardedClusterWorkers::getClusterNameTemplate)
        .orElse(null);
  }

}
