/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.factory.shardedcluster;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.io.Resources;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.stackgres.common.PatroniUtil;
import io.stackgres.common.StackGresShardedClusterUtil;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgcluster.StackGresClusterManagedScriptEntryBuilder;
import io.stackgres.common.crd.sgcluster.StackGresClusterManagedSql;
import io.stackgres.common.crd.sgcluster.StackGresClusterSpec;
import io.stackgres.common.crd.sgscript.StackGresScript;
import io.stackgres.common.crd.sgscript.StackGresScriptBuilder;
import io.stackgres.common.crd.sgscript.StackGresScriptEntry;
import io.stackgres.common.crd.sgscript.StackGresScriptEntryBuilder;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.operator.conciliation.shardedcluster.StackGresShardedClusterContext;
import io.stackgres.operatorframework.resource.ResourceUtil;
import org.jooq.impl.DSL;
import org.jooq.lambda.Seq;
import org.jooq.lambda.Unchecked;

public interface StackGresShardedClusterForDdpUtil extends StackGresShardedClusterUtil {

  Util UTIL = new Util();

  class Util extends StackGresShardedClusterForUtil {

    @Override
    void updateCoordinatorSpec(
        StackGresShardedCluster cluster,
        StackGresClusterSpec spec) {
      if (spec.getManagedSql() == null) {
        spec.setManagedSql(new StackGresClusterManagedSql());
      }
      spec.getManagedSql().setScripts(
          Seq.seq(Optional.ofNullable(spec.getManagedSql().getScripts())
              .stream()
              .flatMap(List::stream)
              .limit(1))
          .append(new StackGresClusterManagedScriptEntryBuilder()
              .withSgScript(StackGresShardedClusterUtil.coordinatorScriptName(cluster))
              .withId(1)
              .build())
          .append(Optional.ofNullable(spec.getManagedSql().getScripts())
              .stream()
              .flatMap(List::stream)
              .skip(1))
          .toList());
    }

    @Override
    void updateWorkersClusterSpec(
        StackGresShardedCluster cluster,
        StackGresClusterSpec spec,
        int index) {
      if (spec.getManagedSql() == null) {
        spec.setManagedSql(new StackGresClusterManagedSql());
      }
      spec.getManagedSql().setScripts(
          Seq.seq(Optional.ofNullable(spec.getManagedSql().getScripts())
              .stream()
              .flatMap(List::stream)
              .limit(1))
          .append(new StackGresClusterManagedScriptEntryBuilder()
              .withSgScript(StackGresShardedClusterUtil.workersScriptName(cluster))
              .withId(1)
              .build())
          .append(Optional.ofNullable(spec.getManagedSql().getScripts())
              .stream()
              .flatMap(List::stream)
              .skip(1))
          .toList());
    }
  }

  static StackGresCluster getCoordinatorCluster(
      StackGresShardedCluster cluster,
      Optional<StackGresShardedCluster> replicateCluster) {
    return UTIL.getCoordinatorCluster(cluster, replicateCluster);
  }

  static StackGresCluster getWorkersCluster(
      StackGresShardedCluster cluster,
      int index,
      Optional<StackGresShardedCluster> replicateCluster) {
    return UTIL.getWorkersCluster(cluster, index, replicateCluster);
  }
  
  static StackGresScript getCoordinatorScript(
      StackGresShardedClusterContext context) {
    StackGresShardedCluster cluster = context.getShardedCluster();
    return new StackGresScriptBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withNamespace(cluster.getMetadata().getNamespace())
            .withName(StackGresShardedClusterUtil.coordinatorScriptName(cluster))
            .build())
        .editSpec()
        .withScripts(
            getDdpCreateDatabase(context, 0),
            getDdpInitScript(context, 1),
            getDdpUpdateWorkersScript(context, 2))
        .endSpec()
        .build();
  }

  static StackGresScript getWorkersScript(
      StackGresShardedClusterContext context) {
    StackGresShardedCluster cluster = context.getShardedCluster();
    return new StackGresScriptBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withNamespace(cluster.getMetadata().getNamespace())
            .withName(StackGresShardedClusterUtil.workersScriptName(cluster))
            .build())
        .editSpec()
        .withScripts(
            getDdpCreateDatabase(context, 0))
        .endSpec()
        .build();
  }

  private static StackGresScriptEntry getDdpCreateDatabase(
      StackGresShardedClusterContext context, int id) {
    StackGresShardedCluster cluster = context.getShardedCluster();
    final StackGresScriptEntry script = new StackGresScriptEntryBuilder()
        .withId(id)
        .withName("ddp-create-database")
        .withRetryOnError(true)
        .withScript(Unchecked.supplier(() -> Resources
            .asCharSource(StackGresShardedClusterForDdpUtil.class.getResource(
                "/ddp/ddp-create-database.sql"),
                StandardCharsets.UTF_8)
            .read()).get()
            .formatted(DSL.inline(cluster.getSpec().getDatabase())))
        .build();
    return script;
  }

  private static StackGresScriptEntry getDdpInitScript(
      StackGresShardedClusterContext context, int id) {
    StackGresShardedCluster cluster = context.getShardedCluster();
    final StackGresScriptEntry script = new StackGresScriptEntryBuilder()
        .withId(id)
        .withName("ddp-init")
        .withRetryOnError(true)
        .withDatabase(cluster.getSpec().getDatabase())
        .withScript(Unchecked.supplier(() -> Resources
            .asCharSource(StackGresShardedClusterForDdpUtil.class.getResource(
                "/ddp/ddp--0.1.0.sql"),
                StandardCharsets.UTF_8)
            .read()).get())
        .build();
    return script;
  }

  private static StackGresScriptEntry getDdpUpdateWorkersScript(
      StackGresShardedClusterContext context, int id) {
    StackGresShardedCluster cluster = context.getShardedCluster();
    final StackGresScriptEntry script = new StackGresScriptEntryBuilder()
        .withId(id)
        .withName("ddp-update-workers")
        .withRetryOnError(true)
        .withDatabase(cluster.getSpec().getDatabase())
        .withNewScriptFrom()
        .withNewSecretKeyRef()
        .withName(getUpdateWorkersSecretName(cluster))
        .withKey("ddp-update-workers.sql")
        .endSecretKeyRef()
        .endScriptFrom()
        .build();
    return script;
  }

  static Secret getUpdateWorkersSecret(
      StackGresShardedClusterContext context) {
    StackGresShardedCluster cluster = context.getShardedCluster();
    var superuserCredentials = ShardedClusterSecret.getSuperuserCredentials(context);
    final Secret secret = new SecretBuilder()
        .withNewMetadata()
        .withNamespace(cluster.getMetadata().getNamespace())
        .withName(getUpdateWorkersSecretName(cluster))
        .endMetadata()
        .withData(ResourceUtil.encodeSecret(Map.of("ddp-update-workers.sql",
            Unchecked.supplier(() -> Resources
                .asCharSource(StackGresShardedClusterForDdpUtil.class.getResource(
                    "/ddp/ddp-update-workers.sql"),
                    StandardCharsets.UTF_8)
                .read()).get().formatted(
                    cluster.getSpec().getWorkers().getClusters(),
                    DSL.inline("host '" + primaryShardServiceNamePlaceholder(cluster, "%1s") + "', "
                        + "port '" + PatroniUtil.REPLICATION_SERVICE_PORT + "', "
                        + "dbname '" + cluster.getSpec().getDatabase() + "'"),
                    DSL.inline("user '" + superuserCredentials.v1 + "', "
                        + "password '" + superuserCredentials.v2 + "'"),
                    DSL.inline(superuserCredentials.v1),
                    1000))))
        .build();
    return secret;
  }

  static String getUpdateWorkersSecretName(StackGresShardedCluster cluster) {
    return StackGresShardedClusterUtil.coordinatorScriptName(cluster) + "-update-workers";
  }

  private static String primaryShardServiceNamePlaceholder(
      StackGresShardedCluster cluster, String shardIndexPlaceholder) {
    return StackGresShardedClusterUtil.getWorkerClusterName(cluster, shardIndexPlaceholder);
  }
}
