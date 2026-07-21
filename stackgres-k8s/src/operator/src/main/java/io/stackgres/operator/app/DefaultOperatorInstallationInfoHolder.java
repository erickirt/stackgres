/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.app;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map.Entry;
import java.util.Objects;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.stackgres.common.OperatorProperty;
import io.stackgres.common.app.AbstractInstallationInfoHolder;
import io.stackgres.common.crd.sgconfig.StackGresConfig;
import io.stackgres.common.crd.sgconfig.StackGresConfigStatus;
import io.stackgres.common.kubernetesclient.KubernetesClientUtil;
import io.stackgres.operator.configuration.OperatorPropertyContext;
import jakarta.inject.Singleton;

@Singleton
public class DefaultOperatorInstallationInfoHolder
    extends AbstractInstallationInfoHolder
    implements OperatorInstallationInfoHolder {

  private final String operatorNamespace;
  private final String operatorName;
  private final KubernetesClient client;

  public DefaultOperatorInstallationInfoHolder(
      OperatorPropertyContext context,
      KubernetesClient client) {
    super(client);
    this.operatorNamespace = context.getString(OperatorProperty.OPERATOR_NAMESPACE);
    this.operatorName = context.getString(OperatorProperty.OPERATOR_NAME);
    this.client = client;
  }

  @Override
  protected String retrieveInstallationId() {
    StackGresConfig config = client
        .resources(StackGresConfig.class)
        .inNamespace(operatorNamespace)
        .withName(operatorName)
        .get();
    if (config == null) {
      throw new RuntimeException("Config not found");
    }
    if (config.getStatus() != null
        && config.getStatus().getInstallationId() != null) {
      return config.getStatus().getInstallationId();
    }
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[4];

    // Random 4 bytes identifier
    bytes[0] = (byte) random.nextInt();
    bytes[1] = (byte) random.nextInt();
    bytes[2] = (byte) random.nextInt();
    bytes[3] = (byte) random.nextInt();

    String generatedId = Base64.getEncoder().encodeToString(bytes).substring(0, 6);

    return generatedId;
  }

  @Override
  protected void updateInstallationId(String id) {
    KubernetesClientUtil.retryOnConflict(() -> {
      StackGresConfig currentConfig = client
          .resources(StackGresConfig.class)
          .inNamespace(operatorNamespace)
          .withName(operatorName)
          .get();
      if (currentConfig.getStatus() == null) {
        currentConfig.setStatus(new StackGresConfigStatus());
      }
      if (Objects.equals(
          currentConfig.getStatus().getInstallationId(),
          id)) {
        return;
      }
      currentConfig.getStatus().setInstallationId(id);
      client
          .resources(StackGresConfig.class)
          .inNamespace(operatorNamespace)
          .resource(currentConfig)
          .lockResourceVersion(currentConfig.getMetadata().getResourceVersion())
          .updateStatus();
    });
  }

  @Override
  public String getInstallationId() {
    return super.getInstallationId();
  }

  @Override
  public Entry<String, String> getUserAgentHeaderEntry() {
    return super.getUserAgentHeaderEntry();
  }

}
