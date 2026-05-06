/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.app;

import java.util.Map;

public interface OperatorInstallationInfoHolder {

  String getInstallationId();

  Map.Entry<String, String> getUserAgentHeaderEntry();

}
