/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.resource;

import javax.enterprise.context.ApplicationScoped;

import io.fabric8.kubernetes.api.model.DoneableSecret;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Namespaceable;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

@ApplicationScoped
public class SecretScheduler extends AbstractResourceScheduler<Secret, SecretList, DoneableSecret> {

  @Override
  protected Namespaceable<NonNamespaceOperation<Secret, SecretList,
        DoneableSecret, Resource<Secret, DoneableSecret>>> getResourceOperator(
      KubernetesClient client) {
    return client.secrets();
  }

}
