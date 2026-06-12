/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.shardedcluster.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgpooling.StackGresPoolingConfig;
import io.stackgres.common.crd.sgpooling.StackGresPoolingConfigBuilder;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterWorker;
import io.stackgres.common.fixture.Fixtures;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.operator.conciliation.factory.shardedcluster.StackGresShardedClusterForCitusUtil;
import io.stackgres.operator.conciliation.shardedcluster.StackGresShardedClusterContext;
import io.stackgres.operator.initialization.DefaultPoolingConfigFactory;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShardedClusterWorkersPoolingConfigContextAppenderTest {

  private ShardedClusterWorkersPoolingConfigContextAppender contextAppender;

  private StackGresShardedCluster cluster;

  private List<Tuple3<Integer, Optional<StackGresShardedClusterWorker>, StackGresCluster>> workers;

  private List<Tuple3<Integer, Optional<StackGresShardedClusterWorker>, StackGresCluster>> queryRouters;

  private DefaultPoolingConfigFactory defaultPoolingConfigFactory = new DefaultPoolingConfigFactory();

  @Spy
  private StackGresShardedClusterContext.Builder contextBuilder;

  @Mock
  private CustomResourceFinder<StackGresPoolingConfig> poolingConfigFinder;

  @BeforeEach
  void setUp() {
    cluster = Fixtures.shardedCluster().loadDefault().get();
    workers = List.of(
        Tuple.tuple(0, Optional.empty(), StackGresShardedClusterForCitusUtil
            .getWorkerCluster(cluster, 0, Optional.empty())),
        Tuple.tuple(1, Optional.empty(), StackGresShardedClusterForCitusUtil
            .getWorkerCluster(cluster, 1, Optional.empty())));
    queryRouters = List.of(
        Tuple.tuple(1024, Optional.empty(), StackGresShardedClusterForCitusUtil
            .getQueryRouterCluster(cluster, 1024, Optional.empty())));
    contextAppender = new ShardedClusterWorkersPoolingConfigContextAppender(
        poolingConfigFinder,
        new DefaultPoolingConfigFactory());
  }

  @Test
  void givenClusterWithPoolingConfig_shouldPass() {
    final Optional<StackGresPoolingConfig> poolingConfig = Optional.of(
        new StackGresPoolingConfigBuilder()
        .build());
    when(poolingConfigFinder.findByNameAndNamespace(any(), any()))
        .thenReturn(poolingConfig);
    contextAppender.appendContext(cluster, contextBuilder, workers, queryRouters);
    verify(contextBuilder).workersPoolingConfigs(List.of(
        Tuple.tuple(0, poolingConfig),
        Tuple.tuple(1, poolingConfig)));
    verify(contextBuilder).queryRoutersPoolingConfigs(List.of(
        Tuple.tuple(1024, poolingConfig)));
  }

  @Test
  void givenClusterWithoutPoolingConfig_shouldFail() {
    when(poolingConfigFinder.findByNameAndNamespace(any(), any()))
        .thenReturn(Optional.empty());
    var ex =
        assertThrows(IllegalArgumentException.class, () -> contextAppender.appendContext(
            cluster, contextBuilder, workers, queryRouters));
    assertEquals("SGPoolingConfig pgbouncerconf was not found", ex.getMessage());
  }

  @Test
  void givenClusterWithoutPoolingConfigAndPoolingDisabled_shouldPass() {
    cluster.getSpec().getWorkers().getPods().setDisableConnectionPooling(true);
    when(poolingConfigFinder.findByNameAndNamespace(any(), any()))
        .thenReturn(Optional.empty());
    contextAppender.appendContext(cluster, contextBuilder, workers, queryRouters);
    verify(contextBuilder).workersPoolingConfigs(List.of(
        Tuple.tuple(0, Optional.empty()),
        Tuple.tuple(1, Optional.empty())));
    verify(contextBuilder).queryRoutersPoolingConfigs(List.of(
        Tuple.tuple(1024, Optional.empty())));
  }

  @Test
  void givenClusterWithoutDefaultPoolingConfig_shouldPass() {
    cluster.getSpec().getWorkers().getConfigurations().setSgPoolingConfig(
        defaultPoolingConfigFactory.getDefaultResourceName(cluster));
    when(poolingConfigFinder.findByNameAndNamespace(any(), any()))
        .thenReturn(Optional.empty());
    contextAppender.appendContext(cluster, contextBuilder, workers, queryRouters);
    verify(contextBuilder).workersPoolingConfigs(List.of(
        Tuple.tuple(0, Optional.empty()),
        Tuple.tuple(1, Optional.empty())));
    verify(contextBuilder).queryRoutersPoolingConfigs(List.of(
        Tuple.tuple(1024, Optional.empty())));
  }

}
