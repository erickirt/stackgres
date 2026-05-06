/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.app;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.stackgres.apiweb.configuration.WebApiProperty;
import io.stackgres.apiweb.configuration.WebApiPropertyContext;
import io.stackgres.common.app.AbstractInstallationInfoHolder;
import jakarta.inject.Singleton;

@Singleton
public class WebApiInstallationInfoHolder extends AbstractInstallationInfoHolder {

  private final String installationId;

  public WebApiInstallationInfoHolder(
      WebApiPropertyContext context,
      KubernetesClient client) {
    super(client);
    this.installationId = context.getString(WebApiProperty.RESTAPI_INSTALATION_ID);
  }

  @Override
  protected String retrieveInstallationId() {
    return installationId;
  }

}
