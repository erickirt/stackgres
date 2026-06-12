/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.factory.shardedcluster.citus;

import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardingType;
import io.stackgres.common.labels.LabelFactoryForShardedCluster;
import io.stackgres.operator.conciliation.OperatorVersionBinder;
import io.stackgres.operator.conciliation.ResourceGenerator;
import io.stackgres.operator.conciliation.factory.shardedcluster.StackGresShardedClusterForCitusUtil;
import io.stackgres.operator.conciliation.shardedcluster.StackGresShardedClusterContext;
import io.stackgres.operator.initialization.DefaultShardedClusterPostgresConfigFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.lambda.Seq;

@Singleton
@OperatorVersionBinder
public class CitusShardedClusterWorkerPostgresConfig implements ResourceGenerator<StackGresShardedClusterContext> {

  final LabelFactoryForShardedCluster labelFactory;

  final DefaultShardedClusterPostgresConfigFactory defaultPostgresConfigFactory;

  @Inject
  public CitusShardedClusterWorkerPostgresConfig(
      LabelFactoryForShardedCluster labelFactory,
      DefaultShardedClusterPostgresConfigFactory defaultPostgresConfigFactory) {
    this.labelFactory = labelFactory;
    this.defaultPostgresConfigFactory = defaultPostgresConfigFactory;
  }

  @Override
  public Stream<HasMetadata> generateResource(StackGresShardedClusterContext context) {
    return Seq.of(Boolean.TRUE)
        .filter(ignore -> StackGresShardingType.CITUS.equals(
            StackGresShardingType.fromString(context.getShardedCluster().getSpec().getType())))
        .flatMap(ignore -> context.getWorkersPostgresConfigs().stream())
        .<HasMetadata>map(workersPostgresConfig -> StackGresShardedClusterForCitusUtil
            .getWorkerPostgresConfig(
                context.getSource(),
                workersPostgresConfig.v1,
                workersPostgresConfig.v2
                .orElse(defaultPostgresConfigFactory.buildResource(context.getResource()))))
        .map(config -> {
          config.getMetadata().setLabels(labelFactory.workersLabels(context.getSource()));
          return config;
        });
  }

}
