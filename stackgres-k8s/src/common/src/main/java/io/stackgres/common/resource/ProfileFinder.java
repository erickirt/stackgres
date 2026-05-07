/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.resource;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.stackgres.common.crd.sgprofile.StackGresInstanceProfile;
import io.stackgres.common.crd.sgprofile.StackGresInstanceProfileList;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ProfileFinder extends AbstractCustomResourceFinder<StackGresInstanceProfile> {

  /**
   * Create a {@code ProfileConfigFinder} instance.
   */
  @Inject
  public ProfileFinder(KubernetesClient client) {
    super(client, StackGresInstanceProfile.class, StackGresInstanceProfileList.class);
  }

}
