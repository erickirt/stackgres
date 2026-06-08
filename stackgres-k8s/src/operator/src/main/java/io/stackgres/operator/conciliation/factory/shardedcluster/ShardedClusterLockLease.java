/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.factory.shardedcluster;

import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import io.stackgres.common.LeaseLockUtil;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.labels.LabelFactoryForShardedCluster;
import io.stackgres.operator.conciliation.OperatorVersionBinder;
import io.stackgres.operator.conciliation.ResourceGenerator;
import io.stackgres.operator.conciliation.shardedcluster.StackGresShardedClusterContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@OperatorVersionBinder
public class ShardedClusterLockLease implements ResourceGenerator<StackGresShardedClusterContext> {

  private final LabelFactoryForShardedCluster labelFactory;

  @Inject
  public ShardedClusterLockLease(LabelFactoryForShardedCluster labelFactory) {
    this.labelFactory = labelFactory;
  }

  public static String name(StackGresShardedCluster shardedCluster) {
    return LeaseLockUtil.leaseNameForShardedCluster(shardedCluster.getMetadata().getUid());
  }

  @Override
  public Stream<HasMetadata> generateResource(StackGresShardedClusterContext context) {
    final StackGresShardedCluster shardedCluster = context.getSource();
    return Stream.of(new LeaseBuilder()
        .withNewMetadata()
        .withNamespace(shardedCluster.getMetadata().getNamespace())
        .withName(name(shardedCluster))
        .withLabels(labelFactory.genericLabels(shardedCluster))
        .endMetadata()
        .withNewSpec()
        .endSpec()
        .build());
  }

}
