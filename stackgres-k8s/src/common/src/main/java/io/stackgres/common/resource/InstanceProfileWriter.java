/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.resource;

import io.stackgres.common.crd.sgprofile.StackGresInstanceProfile;
import io.stackgres.common.crd.sgprofile.StackGresInstanceProfileList;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InstanceProfileWriter
    extends AbstractCustomResourceWriter<StackGresInstanceProfile, StackGresInstanceProfileList> {

  public InstanceProfileWriter() {
    super(StackGresInstanceProfile.class, StackGresInstanceProfileList.class);
  }

}
