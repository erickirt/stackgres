/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.app;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.stackgres.common.StackGresProperty;
import jakarta.ws.rs.core.HttpHeaders;

public abstract class AbstractInstallationInfoHolder {

  private static final AtomicReference<InstallationInfo> INSTALLATION_INFO =
      new AtomicReference<>();

  private final KubernetesClient client;

  public AbstractInstallationInfoHolder(
      KubernetesClient client) {
    this.client = client;
  }

  private InstallationInfo getInstallationInfo() {
    if (INSTALLATION_INFO.get() == null) {
      String retrievedId = retrieveInstallationId();
      InstallationInfo retrievedInfo = new InstallationInfo(
          retrievedId, client.getKubernetesVersion().toString());
      InstallationInfo updatedInfo = INSTALLATION_INFO.updateAndGet(
          info -> info != null ? info : retrievedInfo);
      updateInstallationId(updatedInfo.id());
    }

    return INSTALLATION_INFO.get();
  }

  protected abstract String retrieveInstallationId();

  protected void updateInstallationId(String id) {
  }

  public String getInstallationId() {
    return getInstallationInfo().id();
  }

  public Map.Entry<String, String> getUserAgentHeaderEntry() {
    InstallationInfo installationInfo = getInstallationInfo();
    return Map.entry(
        HttpHeaders.USER_AGENT,
        String.format(
            Locale.ROOT,
            "StackGres/%s (Java %s; Platform %s-%s; K8s %s; %s)",
            StackGresProperty.OPERATOR_VERSION.getString(),
            Runtime.version().toString(),
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
            installationInfo.k8sVersion(),
            installationInfo.id()));
  }

  record InstallationInfo(String id, String k8sVersion) {};
}
