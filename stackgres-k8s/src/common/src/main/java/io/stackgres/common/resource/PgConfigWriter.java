/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.resource;

import io.stackgres.common.crd.sgpgconfig.StackGresPostgresConfig;
import io.stackgres.common.crd.sgpgconfig.StackGresPostgresConfigList;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PgConfigWriter
    extends
    AbstractCustomResourceWriter<StackGresPostgresConfig, StackGresPostgresConfigList> {

  public PgConfigWriter() {
    super(StackGresPostgresConfig.class, StackGresPostgresConfigList.class);
  }

}
