/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.factory.shardedcluster.backup;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.stackgres.common.ClusterPath;
import io.stackgres.common.StackGresVolume;
import io.stackgres.operator.conciliation.factory.VolumeMountsProvider;
import io.stackgres.operator.conciliation.shardedcluster.StackGresShardedClusterContext;

@ApplicationScoped
public class ShardedBackupScriptTemplatesVolumeMounts
    implements VolumeMountsProvider<StackGresShardedClusterContext> {

  @Override
  public List<VolumeMount> getVolumeMounts(StackGresShardedClusterContext context) {
    return List.of(
        new VolumeMountBuilder()
            .withName(StackGresVolume.SCRIPT_TEMPLATES.getName())
            .withSubPath(ClusterPath.LOCAL_BIN_CREATE_SHARDED_BACKUP_SH_PATH.filename())
            .withMountPath(ClusterPath.LOCAL_BIN_CREATE_SHARDED_BACKUP_SH_PATH.path())
            .withReadOnly(true)
            .build(),
        new VolumeMountBuilder()
            .withName(StackGresVolume.SCRIPT_TEMPLATES.getName())
            .withSubPath(ClusterPath.LOCAL_BIN_SHELL_UTILS_PATH.filename())
            .withMountPath(ClusterPath.LOCAL_BIN_SHELL_UTILS_PATH.path())
            .withReadOnly(true)
            .build()
    );
  }

  @Override
  public List<EnvVar> getDerivedEnvVars(StackGresShardedClusterContext context) {
    return List.of(
        ClusterPath.TEMPLATES_PATH.envVar()
    );
  }
}
