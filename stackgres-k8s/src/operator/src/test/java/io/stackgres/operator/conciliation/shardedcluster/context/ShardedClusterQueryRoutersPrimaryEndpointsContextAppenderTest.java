/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.shardedcluster.context;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.stackgres.common.PatroniUtil;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.fixture.Fixtures;
import io.stackgres.common.resource.ResourceFinder;
import io.stackgres.operator.conciliation.shardedcluster.StackGresShardedClusterContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShardedClusterQueryRoutersPrimaryEndpointsContextAppenderTest {

  private ShardedClusterQueryRoutersPrimaryEndpointsContextAppender contextAppender;

  private StackGresCluster queryRouter0;

  private StackGresCluster queryRouter1;

  @Spy
  private StackGresShardedClusterContext.Builder contextBuilder;

  @Mock
  private ResourceFinder<Endpoints> endpointsFinder;

  @BeforeEach
  void setUp() {
    queryRouter0 = Fixtures.cluster().loadDefault().get();
    queryRouter0.getMetadata().setName(queryRouter0.getMetadata().getName() + "-qr0");
    queryRouter1 = Fixtures.cluster().loadDefault().get();
    queryRouter1.getMetadata().setName(queryRouter0.getMetadata().getName() + "-qr1");
    contextAppender = new ShardedClusterQueryRoutersPrimaryEndpointsContextAppender(endpointsFinder);
  }

  @Test
  void givenClusterWithEndpoints_shouldPass() {
    Endpoints endpoints0 =
        new EndpointsBuilder()
        .withNewMetadata()
        .withName(PatroniUtil.readWriteName(queryRouter0))
        .endMetadata()
        .build();
    Endpoints endpoints1 =
        new EndpointsBuilder()
        .withNewMetadata()
        .withName(PatroniUtil.readWriteName(queryRouter1))
        .endMetadata()
        .build();
    when(endpointsFinder.findByNameAndNamespace(
        PatroniUtil.readWriteName(queryRouter0),
        queryRouter0.getMetadata().getNamespace()))
        .thenReturn(Optional.of(endpoints0));
    when(endpointsFinder.findByNameAndNamespace(
        PatroniUtil.readWriteName(queryRouter1),
        queryRouter0.getMetadata().getNamespace()))
        .thenReturn(Optional.of(endpoints1));
    contextAppender.appendContext(List.of(queryRouter0, queryRouter1), contextBuilder);
    verify(contextBuilder).queryRoutersPrimaryEndpoints(List.of(endpoints0, endpoints1));
  }

  @Test
  void givenClusterWithoutEndpoints_shouldPass() {
    when(endpointsFinder.findByNameAndNamespace(
        PatroniUtil.readWriteName(queryRouter0),
        queryRouter0.getMetadata().getNamespace()))
        .thenReturn(Optional.empty());
    when(endpointsFinder.findByNameAndNamespace(
        PatroniUtil.readWriteName(queryRouter1),
        queryRouter1.getMetadata().getNamespace()))
        .thenReturn(Optional.empty());
    contextAppender.appendContext(List.of(queryRouter0, queryRouter1), contextBuilder);
    verify(contextBuilder).queryRoutersPrimaryEndpoints(List.of());
  }

  @Test
  void givenClusterWithFirstEndpoints_shouldPass() {
    Endpoints endpoints0 =
        new EndpointsBuilder()
        .withNewMetadata()
        .withName(PatroniUtil.readWriteName(queryRouter0))
        .endMetadata()
        .build();
    when(endpointsFinder.findByNameAndNamespace(
        PatroniUtil.readWriteName(queryRouter0),
        queryRouter0.getMetadata().getNamespace()))
        .thenReturn(Optional.of(endpoints0));
    when(endpointsFinder.findByNameAndNamespace(
        PatroniUtil.readWriteName(queryRouter1),
        queryRouter1.getMetadata().getNamespace()))
        .thenReturn(Optional.empty());
    contextAppender.appendContext(List.of(queryRouter0, queryRouter1), contextBuilder);
    verify(contextBuilder).queryRoutersPrimaryEndpoints(List.of(endpoints0));
  }

  @Test
  void givenNoQueryRouters_shouldPassEmpty() {
    contextAppender.appendContext(List.of(), contextBuilder);
    verify(contextBuilder).queryRoutersPrimaryEndpoints(List.of());
  }

}
