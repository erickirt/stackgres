/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.cluster;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;

import org.jooq.lambda.Seq;

public enum ClusterStatefulSetPath {
  LOCAL_BIN_PATH("/usr/local/bin"),
  PG_BASE_PATH("/var/lib/postgresql"),
  PG_RUN_PATH("/var/run/postgresql"),
  PG_DATA_PATH(PG_BASE_PATH, "data"),
  BASE_ENV_PATH("/etc/env"),
  BASE_SECRET_PATH(BASE_ENV_PATH, ".secret"),
  PATRONI_ENV_PATH(BASE_ENV_PATH, ClusterStatefulSet.PATRONI_ENV),
  BACKUP_ENV_PATH(BASE_ENV_PATH, ClusterStatefulSet.BACKUP_ENV),
  BACKUP_SECRET_PATH(BASE_SECRET_PATH, ClusterStatefulSet.BACKUP_ENV),
  RESTORE_ENTRYPOINT_PATH("/etc/patroni/restore"),
  RESTORE_ENV_PATH(BASE_ENV_PATH, ClusterStatefulSet.RESTORE_ENV),
  RESTORE_SECRET_PATH(BASE_SECRET_PATH, ClusterStatefulSet.RESTORE_ENV);

  private final String path;
  private final EnvVar envVar;

  ClusterStatefulSetPath(String path) {
    this.path = path;
    this.envVar = new EnvVarBuilder()
        .withName(name())
        .withValue(path)
        .build();
  }

  ClusterStatefulSetPath(ClusterStatefulSetPath parent, String...paths) {
    this(Seq.of(parent.path).append(paths).toString("/"));
  }

  public String path() {
    return path;
  }

  public EnvVar envVar() {
    return envVar;
  }
}
