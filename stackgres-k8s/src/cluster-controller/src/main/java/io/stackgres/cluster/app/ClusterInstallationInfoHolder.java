/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.cluster.app;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.stackgres.cluster.configuration.ClusterControllerPropertyContext;
import io.stackgres.common.ClusterControllerProperty;
import io.stackgres.common.app.AbstractInstallationInfoHolder;
import jakarta.inject.Singleton;

@Singleton
public class ClusterInstallationInfoHolder extends AbstractInstallationInfoHolder {

  private final String installationId;

  public ClusterInstallationInfoHolder(
      ClusterControllerPropertyContext context,
      KubernetesClient client) {
    super(client);
    this.installationId = context.getString(ClusterControllerProperty.CLUSTER_CONTROLLER_INSTALLATION_ID);
  }

  @Override
  protected String retrieveInstallationId() {
    return installationId;
  }

}
