/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.shardeddbops.context;

import java.util.Optional;

import io.stackgres.common.crd.sgprofile.StackGresInstanceProfile;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.operator.conciliation.shardeddbops.StackGresShardedDbOpsContext.Builder;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShardedDbOpsClusterInstanceProfileContextAppender {

  private final CustomResourceFinder<StackGresInstanceProfile> profileFinder;

  public ShardedDbOpsClusterInstanceProfileContextAppender(
      CustomResourceFinder<StackGresInstanceProfile> profileFinder) {
    this.profileFinder = profileFinder;
  }

  public void appendContext(StackGresShardedCluster cluster, Builder contextBuilder) {
    final Optional<StackGresInstanceProfile> foundProfile = profileFinder
        .findByNameAndNamespace(
            cluster.getSpec().getCoordinator().getSgInstanceProfile(),
            cluster.getMetadata().getNamespace());
    if (foundProfile.isEmpty()) {
      throw new IllegalArgumentException(
          StackGresInstanceProfile.KIND + " "
              + cluster.getSpec().getCoordinator().getSgInstanceProfile() + " was not found");
    }
    contextBuilder.foundProfile(foundProfile);
  }

}
