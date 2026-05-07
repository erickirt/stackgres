/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.resource;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LeaseFinder extends AbstractResourceFinderAndScanner<Lease> {

  @Inject
  public LeaseFinder(KubernetesClient client) {
    super(client);
  }

  public LeaseFinder() {
    super();
  }

  @Override
  protected MixedOperation<Lease, ? extends KubernetesResourceList<Lease>, ? extends Resource<Lease>>
      getOperation(KubernetesClient client) {
    return client.leases();
  }

}
