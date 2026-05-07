/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.fixture.upgrade;

import io.stackgres.common.crd.sgprofile.StackGresInstanceProfile;
import io.stackgres.common.crd.sgprofile.StackGresInstanceProfileBuilder;
import io.stackgres.testutil.fixture.Fixture;

public class InstanceProfileFixture extends Fixture<StackGresInstanceProfile> {

  public InstanceProfileFixture loadDefault() {
    fixture = readFromJson(UPGRADE_SGINSTANCEPROFILE_JSON);
    return this;
  }

  public StackGresInstanceProfileBuilder getBuilder() {
    return new StackGresInstanceProfileBuilder(fixture);
  }

}
