/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.shardedcluster.context;

import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.fixture.Fixtures;
import io.stackgres.operator.conciliation.shardedcluster.StackGresShardedClusterContext;
import io.stackgres.testutil.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShardedClusterWorkersClustersContextAppenderTest {

  private ShardedClusterWorkersClustersContextAppender contextAppender;

  private StackGresShardedCluster cluster;

  @Spy
  private StackGresShardedClusterContext.Builder contextBuilder;

  @Mock
  private ShardedClusterWorkersPrimaryEndpointsContextAppender
      shardedClusterWorkersPrimaryEndpointsContextAppender;

  @Mock
  private ShardedClusterQueryRoutersPrimaryEndpointsContextAppender
      shardedClusterQueryRoutersPrimaryEndpointsContextAppender;

  @BeforeEach
  void setUp() {
    cluster = Fixtures.shardedCluster().loadDefault().get();
    contextAppender = new ShardedClusterWorkersClustersContextAppender(
        shardedClusterWorkersPrimaryEndpointsContextAppender,
        shardedClusterQueryRoutersPrimaryEndpointsContextAppender,
        JsonUtil.jsonMapper());
  }

  @Test
  void givenCluster_shouldPass() {
    contextAppender.appendContext(cluster, contextBuilder, Optional.empty());
    ArgumentCaptor<List<StackGresCluster>> workers = ArgumentCaptor.captor();
    ArgumentCaptor<List<StackGresCluster>> queryRouters = ArgumentCaptor.captor();
    verify(contextBuilder).workers(workers.capture());
    verify(contextBuilder).queryRouters(queryRouters.capture());
    verify(shardedClusterWorkersPrimaryEndpointsContextAppender).appendContext(workers.getValue(), contextBuilder);
    verify(shardedClusterQueryRoutersPrimaryEndpointsContextAppender)
        .appendContext(queryRouters.getValue(), contextBuilder);
  }

}
