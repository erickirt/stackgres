/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.cluster;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;

import com.google.common.collect.ImmutableList;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.stackgres.operator.common.StackGresClusterContext;
import io.stackgres.operator.common.StackGresClusterResourceStreamFactory;
import io.stackgres.operator.common.StackGresGeneratorContext;
import io.stackgres.operator.common.StackGresUtil;
import io.stackgres.operatorframework.resource.ResourceUtil;

import org.jooq.lambda.Seq;

@ApplicationScoped
public class RestoreSecret extends AbstractBackupSecret
    implements StackGresClusterResourceStreamFactory {

  private static final String RESTORE_SECRET_SUFFIX = "-restore";

  public static String name(StackGresClusterContext context) {
    return ResourceUtil.resourceName(
        context.getCluster().getMetadata().getName() + RESTORE_SECRET_SUFFIX);
  }

  @Override
  public Stream<HasMetadata> streamResources(StackGresGeneratorContext context) {
    Map<String, String> data = new HashMap<String, String>();

    context.getClusterContext().getRestoreContext().ifPresent(restoreContext -> {
      data.put("BACKUP_RESOURCE_VERSION",
          restoreContext.getBackup().getMetadata().getResourceVersion());

      data.putAll(getBackupSecrets(restoreContext.getBackup().getStatus().getBackupConfig(),
          restoreContext.getSecrets()));
    });

    return Seq.of(new SecretBuilder()
        .withNewMetadata()
        .withNamespace(context.getClusterContext().getCluster().getMetadata().getNamespace())
        .withName(name(context.getClusterContext()))
        .withLabels(StackGresUtil.clusterLabels(context.getClusterContext().getCluster()))
        .withOwnerReferences(ImmutableList.of(
            ResourceUtil.getOwnerReference(context.getClusterContext().getCluster())))
        .endMetadata()
        .withType("Opaque")
        .withStringData(StackGresUtil.addMd5Sum(data))
        .build());
  }

}
