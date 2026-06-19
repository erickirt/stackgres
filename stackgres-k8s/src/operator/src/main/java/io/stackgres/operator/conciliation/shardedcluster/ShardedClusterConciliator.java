/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.shardedcluster;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgcluster.StackGresClusterSpec;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterDbOpsStatus;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterStatus;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.operator.conciliation.AbstractConciliator;
import io.stackgres.operator.conciliation.AbstractDeployedResourcesScanner;
import io.stackgres.operator.conciliation.DeployedResourcesCache;
import io.stackgres.operator.conciliation.RequiredResourceGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ShardedClusterConciliator extends AbstractConciliator<StackGresShardedCluster> {

  @Inject
  public ShardedClusterConciliator(
      KubernetesClient client,
      CustomResourceFinder<StackGresShardedCluster> finder,
      RequiredResourceGenerator<StackGresShardedCluster> requiredResourceGenerator,
      AbstractDeployedResourcesScanner<StackGresShardedCluster> deployedResourcesScanner,
      DeployedResourcesCache deployedResourcesCache) {
    super(client, finder, requiredResourceGenerator, deployedResourcesScanner, deployedResourcesCache);
  }

  @Override
  protected boolean skipDeletion(HasMetadata foundDeployedResource, StackGresShardedCluster config) {
    if (foundDeployedResource instanceof StackGresCluster foundDeployedCluster) {
      if (isMajorVersionUpgradeInProgress(config)) {
        return true;
      }
      return Optional.of(foundDeployedCluster)
          .map(StackGresCluster::getSpec)
          .map(StackGresClusterSpec::getInstances)
          .orElse(0) == 0;
    }
    return super.skipDeletion(foundDeployedResource, config);
  }

  @Override
  protected boolean skipUpdate(HasMetadata requiredResource, StackGresShardedCluster config) {
    // While a major version upgrade SGShardedDbOps is in progress the per-component child SGDbOps
    // are in full control of each child SGCluster. Stop reconciling (patching) child SGClusters so
    // that the SGShardedCluster does not revert the changes performed by the child SGDbOps.
    if (requiredResource instanceof StackGresCluster
        && isMajorVersionUpgradeInProgress(config)) {
      return true;
    }
    return super.skipUpdate(requiredResource, config);
  }

  private static boolean isMajorVersionUpgradeInProgress(StackGresShardedCluster config) {
    return Optional.ofNullable(config.getStatus())
        .map(StackGresShardedClusterStatus::getDbOps)
        .map(StackGresShardedClusterDbOpsStatus::getMajorVersionUpgrade)
        .isPresent();
  }

}
