/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.resource;

import io.stackgres.common.crd.sgdistributedlogs.StackGresDistributedLogs;
import io.stackgres.common.crd.sgdistributedlogs.StackGresDistributedLogsList;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DistributedLogsWriter
    extends
    AbstractCustomResourceWriter<StackGresDistributedLogs, StackGresDistributedLogsList> {

  public DistributedLogsWriter() {
    super(StackGresDistributedLogs.class, StackGresDistributedLogsList.class);
  }

}
