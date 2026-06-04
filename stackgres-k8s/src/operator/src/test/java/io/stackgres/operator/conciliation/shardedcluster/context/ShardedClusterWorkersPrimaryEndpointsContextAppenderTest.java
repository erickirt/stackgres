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
class ShardedClusterWorkersPrimaryEndpointsContextAppenderTest {

  private ShardedClusterWorkersPrimaryEndpointsContextAppender contextAppender;

  private StackGresCluster worker0;

  private StackGresCluster worker1;

  @Spy
  private StackGresShardedClusterContext.Builder contextBuilder;

  @Mock
  private ResourceFinder<Endpoints> endpointsFinder;

  @BeforeEach
  void setUp() {
    worker0 = Fixtures.cluster().loadDefault().get();
    worker0.getMetadata().setName(worker0.getMetadata().getName() + "0");
    worker1 = Fixtures.cluster().loadDefault().get();
    worker1.getMetadata().setName(worker0.getMetadata().getName() + "1");
    contextAppender = new ShardedClusterWorkersPrimaryEndpointsContextAppender(endpointsFinder);
  }

  @Test
  void givenClusterWithEndpoints_shouldPass() {
    Endpoints endpoints0 =
        new EndpointsBuilder()
        .withNewMetadata()
        .withName(PatroniUtil.readWriteName(worker0))
        .endMetadata()
        .build();
    Endpoints endpoints1 =
        new EndpointsBuilder()
        .withNewMetadata()
        .withName(PatroniUtil.readWriteName(worker1))
        .endMetadata()
        .build();
    when(endpointsFinder.findByNameAndNamespace(
        PatroniUtil.readWriteName(worker0),
        worker0.getMetadata().getNamespace()))
        .thenReturn(Optional.of(endpoints0));
    when(endpointsFinder.findByNameAndNamespace(
        PatroniUtil.readWriteName(worker1),
        worker0.getMetadata().getNamespace()))
        .thenReturn(Optional.of(endpoints1));
    contextAppender.appendContext(List.of(worker0, worker1), contextBuilder);
    verify(contextBuilder).workersPrimaryEndpoints(List.of(endpoints0, endpoints1));
  }

  @Test
  void givenClusterWithoutEndpoints_shouldPass() {
    when(endpointsFinder.findByNameAndNamespace(
        PatroniUtil.readWriteName(worker0),
        worker0.getMetadata().getNamespace()))
        .thenReturn(Optional.empty());
    when(endpointsFinder.findByNameAndNamespace(
        PatroniUtil.readWriteName(worker1),
        worker1.getMetadata().getNamespace()))
        .thenReturn(Optional.empty());
    contextAppender.appendContext(List.of(worker0, worker1), contextBuilder);
    verify(contextBuilder).workersPrimaryEndpoints(List.of());
  }

  @Test
  void givenClusterWithFirstEndpoints_shouldPass() {
    Endpoints endpoints0 =
        new EndpointsBuilder()
        .withNewMetadata()
        .withName(PatroniUtil.readWriteName(worker0))
        .endMetadata()
        .build();
    when(endpointsFinder.findByNameAndNamespace(
        PatroniUtil.readWriteName(worker0),
        worker0.getMetadata().getNamespace()))
        .thenReturn(Optional.of(endpoints0));
    when(endpointsFinder.findByNameAndNamespace(
        PatroniUtil.readWriteName(worker1),
        worker1.getMetadata().getNamespace()))
        .thenReturn(Optional.empty());
    contextAppender.appendContext(List.of(worker0, worker1), contextBuilder);
    verify(contextBuilder).workersPrimaryEndpoints(List.of(endpoints0));
  }

}
