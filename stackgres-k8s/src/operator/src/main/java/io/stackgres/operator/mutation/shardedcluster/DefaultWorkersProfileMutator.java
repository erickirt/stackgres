/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.mutation.shardedcluster;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.stackgres.common.crd.sgprofile.StackGresProfile;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterWorkers;
import io.stackgres.operator.common.StackGresShardedClusterReview;
import io.stackgres.operator.initialization.DefaultCustomResourceFactory;
import io.stackgres.operator.mutation.AbstractDefaultResourceMutator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DefaultWorkersProfileMutator
    extends AbstractDefaultResourceMutator<
        StackGresProfile, HasMetadata, StackGresShardedCluster, StackGresShardedClusterReview>
    implements ShardedClusterMutator {

  @Inject
  public DefaultWorkersProfileMutator(
      DefaultCustomResourceFactory<StackGresProfile, HasMetadata> resourceFactory) {
    super(resourceFactory);
  }

  @Override
  protected void setValueSection(StackGresShardedCluster resource) {
    if (resource.getSpec().getWorkersOrShards() == null) {
      resource.getSpec().setWorkers(
          new StackGresShardedClusterWorkers());
    }
  }

  @Override
  protected String getTargetPropertyValue(StackGresShardedCluster resource) {
    return resource.getSpec().getWorkersOrShards().getSgInstanceProfile();
  }

  @Override
  protected void setTargetProperty(StackGresShardedCluster resource, String defaultResourceName) {
    resource.getSpec().getWorkersOrShards().setSgInstanceProfile(defaultResourceName);
  }

}
