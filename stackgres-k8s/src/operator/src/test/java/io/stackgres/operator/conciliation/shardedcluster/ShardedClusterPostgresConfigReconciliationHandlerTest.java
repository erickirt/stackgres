/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.shardedcluster;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import io.stackgres.common.crd.sgpgconfig.StackGresPostgresConfig;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.fixture.Fixtures;
import io.stackgres.common.labels.LabelFactoryForShardedCluster;
import io.stackgres.operator.conciliation.ReconciliationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShardedClusterPostgresConfigReconciliationHandlerTest {

  @Mock
  private ReconciliationHandler<StackGresShardedCluster> delegateHandler;

  @Mock
  private LabelFactoryForShardedCluster labelFactory;

  private ShardedClusterPostgresConfigReconciliationHandler handler;

  private StackGresShardedCluster cluster;

  private StackGresPostgresConfig deployedConfig;

  private StackGresPostgresConfig requiredConfig;

  @BeforeEach
  void setUp() {
    handler = new ShardedClusterPostgresConfigReconciliationHandler(
        delegateHandler, labelFactory);

    cluster = Fixtures.shardedCluster().loadDefault().get();

    deployedConfig = Fixtures.postgresConfig().loadDefault().get();
    deployedConfig.getMetadata().setLabels(Map.of("coordinator", "true"));

    requiredConfig = Fixtures.postgresConfig().loadDefault().get();
    requiredConfig.getMetadata().setLabels(Map.of("coordinator", "true"));

    when(labelFactory.defaultConfigLabels(cluster))
        .thenReturn(Map.of("default-config", "true"));
  }

  @Test
  void givenGeneratedConfig_patchShouldDelegate() {
    handler.patch(cluster, requiredConfig, deployedConfig);

    verify(delegateHandler).patch(cluster, requiredConfig, deployedConfig);
  }

  @Test
  void givenDefaultConfig_patchShouldForget() {
    requiredConfig.getMetadata().setLabels(Map.of("default-config", "true"));

    handler.patch(cluster, requiredConfig, deployedConfig);

    verify(delegateHandler, never()).patch(any(), any(), any());
  }

  @Test
  void givenGeneratedConfig_deleteShouldBeSkipped() {
    handler.delete(cluster, deployedConfig);

    verify(delegateHandler, never()).delete(any(), any());
  }

  @Test
  void givenGeneratedConfig_deleteWithOrphansShouldBeSkipped() {
    handler.deleteWithOrphans(cluster, deployedConfig);

    verify(delegateHandler, never()).deleteWithOrphans(any(), any());
  }

}
