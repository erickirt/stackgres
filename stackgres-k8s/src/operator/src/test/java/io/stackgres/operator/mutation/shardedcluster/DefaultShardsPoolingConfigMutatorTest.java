/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.mutation.shardedcluster;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.stackgres.common.StackGresComponent;
import io.stackgres.common.crd.sgpooling.StackGresPoolingConfig;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.operator.common.StackGresShardedClusterReview;
import io.stackgres.operator.common.fixture.AdmissionReviewFixtures;
import io.stackgres.operator.initialization.DefaultPoolingConfigFactory;
import io.stackgres.operator.mutation.AbstractDefaultResourceMutatorTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultShardsPoolingConfigMutatorTest
    extends AbstractDefaultResourceMutatorTest<StackGresPoolingConfig, HasMetadata, StackGresShardedCluster,
        StackGresShardedClusterReview, DefaultShardsPoolingConfigMutator> {

  private static final String POSTGRES_VERSION =
      StackGresComponent.POSTGRESQL.getLatest().streamOrderedVersions().findFirst().get();

  @Override
  protected StackGresShardedClusterReview getAdmissionReview() {
    return AdmissionReviewFixtures.shardedCluster().loadCreate().get();
  }

  @Override
  protected DefaultShardsPoolingConfigMutator getDefaultConfigMutator() {
    var resourceFactory = new DefaultPoolingConfigFactory();
    var mutator = new DefaultShardsPoolingConfigMutator(
        resourceFactory);
    return mutator;
  }

  @Override
  protected Class<StackGresShardedCluster> getResourceClass() {
    return StackGresShardedCluster.class;
  }

  @Override
  protected void checkConfigurationIsSet(StackGresShardedCluster newResource) {
    assertNotNull(newResource.getSpec().getShards());
    assertNotNull(newResource.getSpec().getShards().getConfigurations());
    assertNotNull(newResource.getSpec().getShards().getConfigurations()
        .getSgPoolingConfig());
  }

  @Override
  protected void setUpExistingConfiguration() {
    review.getRequest().getObject().getSpec().getPostgres().setVersion(POSTGRES_VERSION);
  }

  @Override
  protected void setUpMissingConfiguration() {
    review.getRequest().getObject().getSpec().getPostgres().setVersion(POSTGRES_VERSION);
    review.getRequest().getObject().getSpec().getShards().getConfigurations()
        .setSgPoolingConfig(null);
  }

  @Override
  protected void setUpMissingConfigurationSection() {
    review.getRequest().getObject().getSpec().getPostgres().setVersion(POSTGRES_VERSION);
    review.getRequest().getObject().getSpec().setShards(null);
  }

}
