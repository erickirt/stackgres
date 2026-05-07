/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.factory.cluster;

import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import io.stackgres.common.LeaseLockUtil;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.labels.LabelFactoryForCluster;
import io.stackgres.operator.conciliation.OperatorVersionBinder;
import io.stackgres.operator.conciliation.ResourceGenerator;
import io.stackgres.operator.conciliation.cluster.StackGresClusterContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@OperatorVersionBinder
public class ClusterLockLease implements ResourceGenerator<StackGresClusterContext> {

  private final LabelFactoryForCluster labelFactory;

  @Inject
  public ClusterLockLease(LabelFactoryForCluster labelFactory) {
    this.labelFactory = labelFactory;
  }

  public static String name(StackGresCluster cluster) {
    return LeaseLockUtil.leaseNameForCluster(cluster.getMetadata().getUid());
  }

  @Override
  public Stream<HasMetadata> generateResource(StackGresClusterContext context) {
    final StackGresCluster cluster = context.getSource();
    return Stream.of(new LeaseBuilder()
        .withNewMetadata()
        .withNamespace(cluster.getMetadata().getNamespace())
        .withName(name(cluster))
        .withLabels(labelFactory.genericLabels(cluster))
        .endMetadata()
        .withNewSpec()
        .endSpec()
        .build());
  }

}
