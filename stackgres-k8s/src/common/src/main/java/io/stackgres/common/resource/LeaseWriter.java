/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.resource;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LeaseWriter extends AbstractResourceWriter<Lease> {

  @Inject
  public LeaseWriter(KubernetesClient client) {
    super(client);
  }

  public LeaseWriter() {
    super();
  }

  @Override
  protected MixedOperation<Lease, ?, ?> getResourceEndpoints(KubernetesClient client) {
    return client.leases();
  }

}
