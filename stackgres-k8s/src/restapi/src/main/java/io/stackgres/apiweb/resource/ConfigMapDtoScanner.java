/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.resource;

import static io.stackgres.common.kubernetesclient.KubernetesClientUtil.listPaginated;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.stackgres.apiweb.dto.configmap.ConfigMapDto;
import io.stackgres.apiweb.transformer.ConfigMapMapper;
import io.stackgres.common.resource.ResourceScanner;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ConfigMapDtoScanner implements ResourceScanner<ConfigMapDto> {

  private final KubernetesClient client;

  @Inject
  public ConfigMapDtoScanner(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public List<ConfigMapDto> getResources() {
    return listPaginated(client.configMaps()).stream()
        .map(ConfigMapMapper::map)
        .toList();
  }

  @Override
  public List<ConfigMapDto> getResourcesWithLabels(Map<String, String> labels) {
    return listPaginated(client.configMaps().withLabels(labels)).stream()
        .map(ConfigMapMapper::map)
        .toList();
  }

  @Override
  public List<ConfigMapDto> getResourcesInNamespace(String namespace) {
    return listPaginated(client.configMaps().inNamespace(namespace)).stream()
        .map(ConfigMapMapper::map)
        .toList();
  }
}
