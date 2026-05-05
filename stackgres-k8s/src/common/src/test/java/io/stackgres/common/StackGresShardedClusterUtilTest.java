/*
 * Copyright (C) 2026 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterCoordinator;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterSpec;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterWorkers;
import org.junit.jupiter.api.Test;

class StackGresShardedClusterUtilTest {

  private static StackGresShardedCluster newCluster() {
    var cluster = new StackGresShardedCluster();
    cluster.setMetadata(new ObjectMetaBuilder().withName("stackgres").build());
    cluster.setSpec(new StackGresShardedClusterSpec());
    cluster.getSpec().setCoordinator(new StackGresShardedClusterCoordinator());
    cluster.getSpec().setWorkers(new StackGresShardedClusterWorkers());
    return cluster;
  }

  @Test
  void getClusterName_index0_returnsCoordinatorName() {
    assertEquals(
        "stackgres-coord",
        StackGresShardedClusterUtil.getClusterName(newCluster(), 0));
  }

  @Test
  void getClusterName_index1_returnsFirstWorkerName() {
    assertEquals(
        "stackgres-worker0",
        StackGresShardedClusterUtil.getClusterName(newCluster(), 1));
  }

  @Test
  void getClusterName_index3_returnsThirdWorkerName() {
    assertEquals(
        "stackgres-worker2",
        StackGresShardedClusterUtil.getClusterName(newCluster(), 3));
  }

  @Test
  void getCoordinatorClusterName_default_returnsNameDashCoord() {
    assertEquals(
        "stackgres-coord",
        StackGresShardedClusterUtil.getCoordinatorClusterName(newCluster()));
  }

  @Test
  void getCoordinatorClusterName_whenCoordinatorClusterNameSet_usesIt() {
    var cluster = newCluster();
    cluster.getSpec().getCoordinator().setClusterName("custom-coordinator");

    assertEquals(
        "custom-coordinator",
        StackGresShardedClusterUtil.getCoordinatorClusterName(cluster));
  }

  @Test
  void getCoordinatorClusterName_fromName_returnsNameDashCoord() {
    assertEquals(
        "mycluster-coord",
        StackGresShardedClusterUtil.getCoordinatorClusterName("mycluster"));
  }

  @Test
  void getWorkerClusterName_default_returnsNameDashWorkerIndex() {
    assertEquals(
        "stackgres-worker0",
        StackGresShardedClusterUtil.getWorkerClusterName(newCluster(), 0));
    assertEquals(
        "stackgres-worker3",
        StackGresShardedClusterUtil.getWorkerClusterName(newCluster(), 3));
  }

  @Test
  void getWorkerClusterName_whenClusterNameTemplateSet_appendsIndex() {
    var cluster = newCluster();
    cluster.getSpec().getWorkers().setClusterNameTemplate("legacy-shard");

    assertEquals(
        "legacy-shard0",
        StackGresShardedClusterUtil.getWorkerClusterName(cluster, 0));
    assertEquals(
        "legacy-shard2",
        StackGresShardedClusterUtil.getWorkerClusterName(cluster, 2));
  }

  @Test
  void getWorkerClusterName_stringIndex_supportsArbitrarySuffix() {
    var cluster = newCluster();
    cluster.getSpec().getWorkers().setClusterNameTemplate("w-");

    assertEquals(
        "w-abc",
        StackGresShardedClusterUtil.getWorkerClusterName(cluster, "abc"));
  }

  @Test
  void getQueryRouterClusterName_default_returnsNameDashRouterIndex() {
    assertEquals(
        "stackgres-router0",
        StackGresShardedClusterUtil.getQueryRouterClusterName(newCluster(), 1024));
    assertEquals(
        "stackgres-router3",
        StackGresShardedClusterUtil.getQueryRouterClusterName(newCluster(), 1027));
  }

  @Test
  void getQueryRouterClusterName_whenTemplateSet_appendsZeroBasedIndex() {
    var cluster = newCluster();
    cluster.getSpec().getCoordinator().setQueryRouterClusterNameTemplate("custom-router");

    assertEquals(
        "custom-router0",
        StackGresShardedClusterUtil.getQueryRouterClusterName(cluster, 1024));
    assertEquals(
        "custom-router2",
        StackGresShardedClusterUtil.getQueryRouterClusterName(cluster, 1026));
  }

  @Test
  void getQueryRouterClusterName_whenIndexOffsetSet_appliesIt() {
    var cluster = newCluster();
    cluster.getSpec().getCoordinator().setQueryRouterIndexOffset(2048);

    assertEquals(
        "stackgres-router0",
        StackGresShardedClusterUtil.getQueryRouterClusterName(cluster, 2048));
    assertEquals(
        "stackgres-router5",
        StackGresShardedClusterUtil.getQueryRouterClusterName(cluster, 2053));
  }

  @Test
  void getQueryRouterClusterName_stringIndex_supportsArbitrarySuffix() {
    var cluster = newCluster();
    cluster.getSpec().getCoordinator().setQueryRouterClusterNameTemplate("r-");

    assertEquals(
        "r-abc",
        StackGresShardedClusterUtil.getQueryRouterClusterName(cluster, "abc"));
  }

  @Test
  void coordinatorConfigName_default_returnsCoordinatorClusterName() {
    assertEquals(
        "stackgres-coord",
        StackGresShardedClusterUtil.coordinatorConfigName(newCluster()));
  }

  @Test
  void coordinatorConfigName_whenCoordinatorClusterNameSet_usesIt() {
    var cluster = newCluster();
    cluster.getSpec().getCoordinator().setClusterName("custom-coordinator");

    assertEquals(
        "custom-coordinator",
        StackGresShardedClusterUtil.coordinatorConfigName(cluster));
  }

  @Test
  void coordinatorScriptName_returnsNameDashCoord() {
    assertEquals(
        "stackgres-coord",
        StackGresShardedClusterUtil.coordinatorScriptName(newCluster()));
  }

  @Test
  void workersScriptName_returnsNameDashWorkers() {
    assertEquals(
        "stackgres-workers",
        StackGresShardedClusterUtil.workersScriptName(newCluster()));
  }

  @Test
  void postgresSslSecretName_returnsNameDashSsl() {
    assertEquals(
        "stackgres-ssl",
        StackGresShardedClusterUtil.postgresSslSecretName(newCluster()));
  }

  @Test
  void primaryCoordinatorServiceName_returnsClusterName() {
    assertEquals(
        "stackgres",
        StackGresShardedClusterUtil.primaryCoordinatorServiceName(newCluster()));
  }

  @Test
  void anyCoordinatorServiceName_returnsNameDashReads() {
    assertEquals(
        "stackgres-reads",
        StackGresShardedClusterUtil.anyCoordinatorServiceName(newCluster()));
  }

  @Test
  void primariesWorkersServiceName_returnsNameDashWorkers() {
    assertEquals(
        "stackgres-workers",
        StackGresShardedClusterUtil.primariesWorkersServiceName(newCluster()));
  }

  @Test
  void primariesShardsServiceName_returnsNameDashShards() {
    assertEquals(
        "stackgres-shards",
        StackGresShardedClusterUtil.primariesShardsServiceName(newCluster()));
  }

}
