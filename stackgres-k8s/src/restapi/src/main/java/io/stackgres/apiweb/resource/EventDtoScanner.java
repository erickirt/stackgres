/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.resource;

import static io.stackgres.common.kubernetesclient.KubernetesClientUtil.listPaginated;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.stackgres.apiweb.dto.event.EventDto;
import io.stackgres.apiweb.transformer.EventMapper;
import io.stackgres.common.resource.ResourceScanner;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EventDtoScanner implements ResourceScanner<EventDto> {

  private final KubernetesClient client;

  @Inject
  public EventDtoScanner(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public List<EventDto> getResources() {
    return listPaginated(client.v1().events()).stream()
        .map(EventMapper::map)
        .toList();
  }

  @Override
  public List<EventDto> getResourcesWithLabels(Map<String, String> labels) {
    return listPaginated(client.v1().events().withLabels(labels)).stream()
        .map(EventMapper::map)
        .toList();
  }

  @Override
  public List<EventDto> getResourcesInNamespace(String namespace) {
    return listPaginated(client.v1().events().inNamespace(namespace)).stream()
        .map(EventMapper::map)
        .toList();
  }
}
