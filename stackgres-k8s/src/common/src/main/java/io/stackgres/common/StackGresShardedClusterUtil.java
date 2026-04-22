/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common;

import java.util.Optional;

import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterSpec;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterWorkers;
import io.stackgres.operatorframework.resource.ResourceUtil;

public interface StackGresShardedClusterUtil {

  String CERTIFICATE_KEY = "tls.crt";
  String PRIVATE_KEY_KEY = "tls.key";
  int LAST_RESERVER_SCRIPT_ID = 9;

  static String getClusterName(StackGresShardedCluster cluster, int index) {
    if (index == 0) {
      return getCoordinatorClusterName(cluster);
    }
    return getWorkerClusterName(cluster, index - 1);
  }

  static String getCoordinatorClusterName(StackGresShardedCluster cluster) {
    return getCoordinatorClusterName(cluster.getMetadata().getName());
  }

  static String getCoordinatorClusterName(String name) {
    return name + "-coord";
  }

  static String getWorkerClusterName(StackGresShardedCluster cluster, int workerIndex) {
    return getWorkerClusterName(cluster, String.valueOf(workerIndex));
  }

  static String getWorkerClusterName(StackGresShardedCluster cluster, String workerIndex) {
    return Optional.of(cluster.getSpec())
        .map(StackGresShardedClusterSpec::getWorkers)
        .map(StackGresShardedClusterWorkers::getClusterNameTemplate)
        .orElseGet(() -> cluster.getMetadata().getName() + "-worker") + workerIndex;
  }

  static String coordinatorConfigName(StackGresShardedCluster cluster) {
    return Optional.of(cluster.getSpec())
        .map(StackGresShardedClusterSpec::getWorkers)
        .map(StackGresShardedClusterWorkers::getClusterNameTemplate)
        .orElseGet(() -> cluster.getMetadata().getName() + "-coord");
  }

  static String coordinatorScriptName(StackGresShardedCluster cluster) {
    return cluster.getMetadata().getName() + "-coord";
  }

  static String workersScriptName(StackGresShardedCluster cluster) {
    return cluster.getMetadata().getName() + "-workers";
  }

  static String postgresSslSecretName(StackGresShardedCluster cluster) {
    return ResourceUtil.nameIsValidDnsSubdomain(cluster.getMetadata().getName() + "-ssl");
  }

  static String primaryCoordinatorServiceName(StackGresShardedCluster cluster) {
    return primaryCoordinatorServiceName(cluster.getMetadata().getName());
  }

  static String primaryCoordinatorServiceName(String clusterName) {
    return ResourceUtil.nameIsValidService(clusterName);
  }

  static String anyCoordinatorServiceName(StackGresShardedCluster cluster) {
    return ResourceUtil.nameIsValidService(cluster.getMetadata().getName() + "-reads");
  }

  static String primariesWorkersServiceName(StackGresShardedCluster cluster) {
    return ResourceUtil.nameIsValidService(cluster.getMetadata().getName() + "-workers");
  }

  static String primariesShardsServiceName(StackGresShardedCluster cluster) {
    return ResourceUtil.nameIsValidService(cluster.getMetadata().getName() + "-shards");
  }

}
