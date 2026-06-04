/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.mutation.shardedcluster;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.stackgres.common.StackGresShardedClusterUtil;
import io.stackgres.common.crd.sgcluster.StackGresClusterManagedScriptEntry;
import io.stackgres.common.crd.sgcluster.StackGresClusterManagedSql;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterSpec;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterWorker;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterWorkers;
import io.stackgres.operator.common.StackGresShardedClusterReview;
import io.stackgres.operatorframework.admissionwebhook.Operation;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class WorkersOverridesScriptsConfigMutator implements ShardedClusterMutator {

  @Override
  public StackGresShardedCluster mutate(
      StackGresShardedClusterReview review, StackGresShardedCluster resource) {
    if (review.getRequest().getOperation() != Operation.CREATE
        && review.getRequest().getOperation() != Operation.UPDATE) {
      return resource;
    }
    setDefaultScriptIds(resource);
    fillRequiredFields(resource);
    return resource;
  }

  private void setDefaultScriptIds(StackGresShardedCluster resource) {
    Optional.of(resource)
        .map(StackGresShardedCluster::getSpec)
        .map(StackGresShardedClusterSpec::getWorkersOrShards)
        .map(StackGresShardedClusterWorkers::getOverrides)
        .stream()
        .flatMap(List::stream)
        .flatMap(override -> Optional.of(override)
            .map(StackGresShardedClusterWorker::getManagedSql)
            .map(StackGresClusterManagedSql::getScripts)
            .stream())
        .flatMap(List::stream)
        .filter(scriptEntry -> Objects.equals(scriptEntry.getId(), -1))
        .forEach(scriptEntry -> scriptEntry.setId(null));
  }

  private void fillRequiredFields(StackGresShardedCluster resource) {
    for (var override : Optional.of(resource)
        .map(StackGresShardedCluster::getSpec)
        .map(StackGresShardedClusterSpec::getWorkersOrShards)
        .map(StackGresShardedClusterWorkers::getOverrides)
        .stream()
        .flatMap(List::stream)
        .toList()) {
      int lastId = Optional.of(override)
          .map(StackGresShardedClusterWorker::getManagedSql)
          .map(StackGresClusterManagedSql::getScripts)
          .stream()
          .flatMap(List::stream)
          .map(StackGresClusterManagedScriptEntry::getId)
          .reduce(StackGresShardedClusterUtil.LAST_RESERVER_SCRIPT_ID,
              (last, id) -> id == null || last >= id ? last : id, (u, v) -> v);
      for (StackGresClusterManagedScriptEntry scriptEntry : Optional.of(override)
          .map(StackGresShardedClusterWorker::getManagedSql)
          .map(StackGresClusterManagedSql::getScripts)
          .orElse(List.of())) {
        if (scriptEntry.getId() == null) {
          lastId++;
          scriptEntry.setId(lastId);
        }
      }
    }
  }

}
