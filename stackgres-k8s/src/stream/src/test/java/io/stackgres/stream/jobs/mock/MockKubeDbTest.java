/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.stream.jobs.mock;

import java.util.function.Consumer;

import io.quarkus.test.InjectMock;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgstream.StackGresStream;
import io.stackgres.common.resource.ClusterFinder;
import io.stackgres.common.resource.ClusterWriter;
import io.stackgres.common.resource.StreamFinder;
import io.stackgres.common.resource.StreamWriter;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

public abstract class MockKubeDbTest {

  protected MockKubeDb kubeDb;

  @InjectMock
  protected ClusterFinder clusterFinder;
  @InjectMock
  protected ClusterWriter clusterWriter;
  @InjectMock
  protected StreamFinder streamFinder;
  @InjectMock
  protected StreamWriter streamWriter;

  @BeforeEach
  public void steupKubeDbMocks() {
    kubeDb = new MockKubeDb();
    var mockClusterFinder = new MockClusterFinder(kubeDb);
    Mockito.lenient()
        .when(clusterFinder.findByNameAndNamespace(Mockito.any(), Mockito.any()))
        .then(invocation -> mockClusterFinder.findByNameAndNamespace(
            invocation.getArgument(0),
            invocation.getArgument(1)));
    var mockClusterWriter = new MockClusterWriter(kubeDb);
    Mockito.lenient()
        .when(clusterWriter.create(Mockito.any(), Mockito.anyBoolean()))
        .then(invocation -> mockClusterWriter.create(
            invocation.getArgument(0),
            invocation.getArgument(1)));
    Mockito
        .doAnswer(invocation -> {
          mockClusterWriter.delete(invocation.getArgument(0));
          return null;
        })
        .when(clusterWriter).delete(Mockito.any());
    Mockito.lenient()
        .when(clusterWriter.update(Mockito.any()))
        .then(invocation -> mockClusterWriter.update(
            invocation.getArgument(0)));
    Mockito.lenient()
        .when(clusterWriter.update(Mockito.any(), Mockito.anyBoolean()))
        .then(invocation -> mockClusterWriter.update(
            invocation.getArgument(0),
            invocation.<Boolean>getArgument(1)));
    Mockito.lenient()
        .when(clusterWriter.update(Mockito.any(), Mockito.<Consumer<StackGresCluster>>any()))
        .then(invocation -> mockClusterWriter.update(
            invocation.getArgument(0),
            invocation.<Consumer<StackGresCluster>>getArgument(1)));
    Mockito.lenient()
        .when(clusterWriter.updateStatus(Mockito.any(), Mockito.<Consumer<StackGresCluster>>any()))
        .then(invocation -> mockClusterWriter.updateStatus(
            invocation.getArgument(0),
            invocation.<Consumer<StackGresCluster>>getArgument(1)));
    var mockStreamFinder = new MockStreamFinder(kubeDb);
    Mockito.lenient()
        .when(streamFinder.findByNameAndNamespace(Mockito.any(), Mockito.any()))
        .then(invocation -> mockStreamFinder.findByNameAndNamespace(
            invocation.getArgument(0),
            invocation.getArgument(1)));
    var mockStreamWriter = new MockStreamWriter(kubeDb);
    Mockito.lenient()
        .when(streamWriter.create(Mockito.any(), Mockito.anyBoolean()))
        .then(invocation -> mockStreamWriter.create(
            invocation.getArgument(0),
            invocation.getArgument(1)));
    Mockito
        .doAnswer(invocation -> {
          mockStreamWriter.delete(invocation.getArgument(0));
          return null;
        })
        .when(streamWriter).delete(Mockito.any());
    Mockito.lenient()
        .when(streamWriter.update(Mockito.any()))
        .then(invocation -> mockStreamWriter.update(
            invocation.getArgument(0)));
    Mockito.lenient()
        .when(streamWriter.update(Mockito.any(), Mockito.anyBoolean()))
        .then(invocation -> mockStreamWriter.update(
            invocation.getArgument(0),
            invocation.<Boolean>getArgument(1)));
    Mockito.lenient()
        .when(streamWriter.update(Mockito.any(), Mockito.<Consumer<StackGresStream>>any()))
        .then(invocation -> mockStreamWriter.update(
            invocation.getArgument(0),
            invocation.<Consumer<StackGresStream>>getArgument(1)));
    Mockito.lenient()
        .when(streamWriter.updateStatus(Mockito.any(), Mockito.<Consumer<StackGresStream>>any()))
        .then(invocation -> mockStreamWriter.updateStatus(
            invocation.getArgument(0),
            invocation.<Consumer<StackGresStream>>getArgument(1)));
  }

}
