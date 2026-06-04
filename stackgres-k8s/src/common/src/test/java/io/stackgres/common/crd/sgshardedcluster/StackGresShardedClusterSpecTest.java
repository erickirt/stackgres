/*
 * Copyright (C) 2026 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgshardedcluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.IntOrString;
import org.junit.jupiter.api.Test;

class StackGresShardedClusterSpecTest {

  private static StackGresShardedClusterSpec specWith(
      Integer queryRouterClusters,
      Integer queryRouterIndexOffset,
      List<StackGresShardedClusterWorker> overrides) {
    var spec = new StackGresShardedClusterSpec();
    var coordinator = new StackGresShardedClusterCoordinator();
    coordinator.setQueryRouterClusters(queryRouterClusters);
    coordinator.setQueryRouterIndexOffset(queryRouterIndexOffset);
    spec.setCoordinator(coordinator);
    var workers = new StackGresShardedClusterWorkers();
    workers.setOverrides(overrides);
    spec.setWorkers(workers);
    return spec;
  }

  private static StackGresShardedClusterWorker worker(Integer index, String type) {
    var worker = new StackGresShardedClusterWorker();
    worker.setIndex(index);
    worker.setType(type);
    return worker;
  }

  private static StackGresShardedClusterWorker workerWithIndexes(
      List<IntOrString> indexes, String type) {
    var worker = new StackGresShardedClusterWorker();
    worker.setIndexes(indexes);
    worker.setType(type);
    return worker;
  }

  @Test
  void getPlainOverrides_nullWorkers_returnsEmpty() {
    var spec = new StackGresShardedClusterSpec();

    assertEquals(List.of(), spec.getPlainOverrides());
  }

  @Test
  void getPlainOverrides_nullOverrides_returnsEmpty() {
    var spec = specWith(null, null, null);

    assertEquals(List.of(), spec.getPlainOverrides());
  }

  @Test
  void getPlainOverrides_singleIndex_returnsOneEntry() {
    var spec = specWith(null, null, List.of(worker(2, null)));

    var result = spec.getPlainOverrides();

    assertEquals(1, result.size());
    assertEquals(2, result.get(0).getIndex());
    assertNull(result.get(0).getIndexes());
  }

  @Test
  void getPlainOverrides_indexesRange_isExpanded() {
    var spec = specWith(null, null, List.of(
        workerWithIndexes(List.of(new IntOrString("0-2")), null)));

    var result = spec.getPlainOverrides();

    assertEquals(List.of(0, 1, 2),
        result.stream().map(StackGresShardedClusterWorker::getIndex).toList());
    result.forEach(w -> assertNull(w.getIndexes()));
  }

  @Test
  void getPlainOverrides_indexesAll_expandsToQueryRouterClusters() {
    var spec = specWith(2, null, List.of(
        workerWithIndexes(List.of(new IntOrString("all")), null)));

    var result = spec.getPlainOverrides();

    assertEquals(List.of(0, 1),
        result.stream().map(StackGresShardedClusterWorker::getIndex).toList());
  }

  @Test
  void getWorkersOverrides_includesEntriesWithoutTypeAndWithWorkerType() {
    var spec = specWith(null, null, List.of(
        worker(0, null),
        worker(1, "Worker"),
        worker(5, "QueryRouter")));

    var result = spec.getWorkersOverrides();

    assertEquals(List.of(0, 1),
        result.stream().map(StackGresShardedClusterWorker::getIndex).toList());
  }

  @Test
  void getQueryRouterOverrides_filtersByQueryRouterTypeAndAppliesOffset() {
    var spec = specWith(null, null, List.of(
        worker(0, "Worker"),
        worker(1, "QueryRouter"),
        worker(2, "QueryRouter")));

    var result = spec.getQueryRoutersOverrides();

    assertEquals(List.of(1024 + 1, 1024 + 2),
        result.stream().map(StackGresShardedClusterWorker::getIndex).toList());
  }

  @Test
  void getQueryRouterOverrides_appliesCustomQueryRouterIndexOffset() {
    var spec = specWith(null, 4096, List.of(
        worker(0, "QueryRouter"),
        worker(3, "QueryRouter")));

    var result = spec.getQueryRoutersOverrides();

    assertEquals(List.of(4096, 4099),
        result.stream().map(StackGresShardedClusterWorker::getIndex).toList());
  }

  @Test
  void getQueryRouterOverrides_indexesAll_expandsAndOffsets() {
    var spec = specWith(2, null, List.of(
        workerWithIndexes(List.of(new IntOrString("all")), "QueryRouter")));

    var result = spec.getQueryRoutersOverrides();

    assertEquals(List.of(1024, 1025),
        result.stream().map(StackGresShardedClusterWorker::getIndex).toList());
  }

  @Test
  void getWorkersOverrides_isUnaffectedByQueryRouterIndexOffset() {
    var spec = specWith(null, 4096, List.of(worker(0, null), worker(3, "Worker")));

    var result = spec.getWorkersOverrides();

    assertEquals(List.of(0, 3),
        result.stream().map(StackGresShardedClusterWorker::getIndex).toList());
  }

  @Test
  void getPlainOverrides_doesNotMutateOriginalOverrides() {
    var override = workerWithIndexes(
        new ArrayList<>(List.of(new IntOrString(1), new IntOrString(2))), null);
    var spec = specWith(null, null, List.of(override));

    spec.getPlainOverrides();

    assertEquals(2, override.getIndexes().size());
    assertNull(override.getIndex());
  }

}
