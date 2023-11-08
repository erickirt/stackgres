/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.factory.distributedlogs;

import static io.stackgres.common.StackGresUtil.getDefaultPullPolicy;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.stackgres.common.ClusterContext;
import io.stackgres.common.ClusterPath;
import io.stackgres.common.KubectlUtil;
import io.stackgres.common.StackGresDistributedLogsUtil;
import io.stackgres.common.StackGresInitContainer;
import io.stackgres.common.StackGresVolume;
import io.stackgres.operator.conciliation.OperatorVersionBinder;
import io.stackgres.operator.conciliation.factory.ContainerFactory;
import io.stackgres.operator.conciliation.factory.ContainerUserOverrideMounts;
import io.stackgres.operator.conciliation.factory.InitContainer;

@Singleton
@OperatorVersionBinder
@InitContainer(StackGresInitContainer.SETUP_SCRIPTS)
public class ScriptsSetUp implements ContainerFactory<DistributedLogsContainerContext> {

  private final ContainerUserOverrideMounts containerUserOverrideMounts;

  @Inject
  KubectlUtil kubectl;

  @Inject
  public ScriptsSetUp(
      ContainerUserOverrideMounts containerUserOverrideMounts) {
    this.containerUserOverrideMounts = containerUserOverrideMounts;
  }

  @Override
  public Container getContainer(DistributedLogsContainerContext context) {
    final ClusterContext clusterContext = () -> StackGresDistributedLogsUtil
        .getStackGresClusterForDistributedLogs(context.getDistributedLogsContext().getSource());

    return new ContainerBuilder()
        .withName(StackGresInitContainer.SETUP_SCRIPTS.getName())
        .withImage(kubectl
            .getImageName(context.getDistributedLogsContext().getSource()))
        .withImagePullPolicy(getDefaultPullPolicy())
        .withCommand("/bin/sh", "-ex",
            ClusterPath.TEMPLATES_PATH.path()
                + "/" + ClusterPath.LOCAL_BIN_SETUP_SCRIPTS_SH_PATH.filename())
        .withEnv(ClusterPath.envVars(clusterContext))
        .addToEnv(new EnvVarBuilder().withName("HOME").withValue("/tmp").build())
        .withVolumeMounts(
            new VolumeMountBuilder()
                .withName(StackGresVolume.SCRIPT_TEMPLATES.getName())
                .withMountPath(ClusterPath.TEMPLATES_PATH.path())
                .build())
        .addAllToVolumeMounts(containerUserOverrideMounts.getVolumeMounts(context))
        .addToVolumeMounts(
            new VolumeMountBuilder()
                .withName(StackGresVolume.LOCAL_BIN.getName())
                .withMountPath(ClusterPath.LOCAL_BIN_PATH.path())
                .build())
        .build();
  }

}
