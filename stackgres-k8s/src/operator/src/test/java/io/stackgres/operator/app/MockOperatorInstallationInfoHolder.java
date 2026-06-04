/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.app;

import java.util.Map;
import java.util.Map.Entry;

import io.quarkus.test.Mock;
import jakarta.inject.Singleton;

@Mock
@Singleton
public class MockOperatorInstallationInfoHolder implements OperatorInstallationInfoHolder {

  @Override
  public String getInstallationId() {
    return "test";
  }

  @Override
  public Entry<String, String> getUserAgentHeaderEntry() {
    return Map.entry("User-Agent", "Test");
  }

}
