/*
 * Copyright (C) 2026 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgshardedcluster;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import io.fabric8.kubernetes.api.model.IntOrString;
import org.junit.jupiter.api.Test;

class StackGresShardedClusterWorkerTest {

  private static StackGresShardedClusterSpec spec(Integer queryRouterClusters) {
    var spec = new StackGresShardedClusterSpec();
    var coordinator = new StackGresShardedClusterCoordinator();
    coordinator.setQueryRouterClusters(queryRouterClusters);
    spec.setCoordinator(coordinator);
    return spec;
  }

  @Test
  void getPlainIndexes_index_returnsSingletonList() {
    var worker = new StackGresShardedClusterWorker();
    worker.setIndex(3);

    assertEquals(List.of(3), worker.getPlainIndexes(spec(null)));
  }

  @Test
  void getPlainIndexes_neitherIndexNorIndexes_returnsEmptyList() {
    var worker = new StackGresShardedClusterWorker();

    assertEquals(List.of(), worker.getPlainIndexes(spec(null)));
  }

  @Test
  void getPlainIndexes_indexesIntegerEntries_returnsThem() {
    var worker = new StackGresShardedClusterWorker();
    worker.setIndexes(List.of(new IntOrString(0), new IntOrString(2), new IntOrString(5)));

    assertEquals(List.of(0, 2, 5), worker.getPlainIndexes(spec(null)));
  }

  @Test
  void getPlainIndexes_indexesRange_isExpandedInclusively() {
    var worker = new StackGresShardedClusterWorker();
    worker.setIndexes(List.of(new IntOrString("1-3")));

    assertEquals(List.of(1, 2, 3), worker.getPlainIndexes(spec(null)));
  }

  @Test
  void getPlainIndexes_indexesAll_expandsToQueryRouterClustersRange() {
    var worker = new StackGresShardedClusterWorker();
    worker.setIndexes(List.of(new IntOrString("all")));

    assertEquals(List.of(0, 1, 2), worker.getPlainIndexes(spec(3)));
  }

  @Test
  void getPlainIndexes_indexesAllWithoutQueryRouterClusters_returnsEmpty() {
    var worker = new StackGresShardedClusterWorker();
    worker.setIndexes(List.of(new IntOrString("all")));

    assertEquals(List.of(), worker.getPlainIndexes(spec(0)));
    assertEquals(List.of(), worker.getPlainIndexes(spec(null)));
  }

  @Test
  void getPlainIndexes_mixedEntries_combinesAll() {
    var worker = new StackGresShardedClusterWorker();
    worker.setIndexes(List.of(
        new IntOrString(0),
        new IntOrString("2-4"),
        new IntOrString(7)));

    assertEquals(List.of(0, 2, 3, 4, 7), worker.getPlainIndexes(spec(null)));
  }

  @Test
  void getPlainIndexes_indexTakesPrecedenceOverIndexes() {
    var worker = new StackGresShardedClusterWorker();
    worker.setIndex(9);
    worker.setIndexes(List.of(new IntOrString(0), new IntOrString(1)));

    assertEquals(List.of(9), worker.getPlainIndexes(spec(null)));
  }

  @Test
  void getPlainIndexes_unknownStringEntry_isIgnored() {
    var worker = new StackGresShardedClusterWorker();
    worker.setIndexes(List.of(new IntOrString("bogus"), new IntOrString(5)));

    assertEquals(List.of(5), worker.getPlainIndexes(spec(null)));
  }

}
