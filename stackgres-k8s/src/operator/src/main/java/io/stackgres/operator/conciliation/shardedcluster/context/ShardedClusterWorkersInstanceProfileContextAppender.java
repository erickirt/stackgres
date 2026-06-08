/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.shardedcluster.context;

import java.util.Optional;

import io.stackgres.common.crd.sgprofile.StackGresInstanceProfile;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.operator.conciliation.ContextAppender;
import io.stackgres.operator.conciliation.shardedcluster.StackGresShardedClusterContext.Builder;
import io.stackgres.operator.initialization.DefaultProfileFactory;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShardedClusterWorkersInstanceProfileContextAppender
    extends ContextAppender<StackGresShardedCluster, Builder> {

  private final CustomResourceFinder<StackGresInstanceProfile> profileFinder;
  private final DefaultProfileFactory defaultProfileFactory;

  public ShardedClusterWorkersInstanceProfileContextAppender(
      CustomResourceFinder<StackGresInstanceProfile> profileFinder,
      DefaultProfileFactory defaultProfileFactory) {
    this.profileFinder = profileFinder;
    this.defaultProfileFactory = defaultProfileFactory;
  }

  @Override
  public void appendContext(StackGresShardedCluster cluster, Builder contextBuilder) {
    final Optional<StackGresInstanceProfile> workersProfile = profileFinder
        .findByNameAndNamespace(
            cluster.getSpec().getWorkers().getSgInstanceProfile(),
            cluster.getMetadata().getNamespace());
    if (!cluster.getSpec().getWorkers().getSgInstanceProfile()
        .equals(defaultProfileFactory.getDefaultResourceName(cluster))
        && workersProfile.isEmpty()) {
      throw new IllegalArgumentException(
          StackGresInstanceProfile.KIND
            + " " + cluster.getSpec().getWorkers().getSgInstanceProfile() + " was not found");
    }
    contextBuilder.workersProfile(workersProfile);
  }

}
