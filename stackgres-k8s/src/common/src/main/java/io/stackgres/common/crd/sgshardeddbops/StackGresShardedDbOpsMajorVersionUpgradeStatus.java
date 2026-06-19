/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgshardeddbops;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.common.StackGresUtil;
import io.sundr.builder.annotations.Buildable;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown = true)
@Buildable(editableEnabled = false, generateBuilderPackage = false,
    lazyCollectionInitEnabled = false, lazyMapInitEnabled = false,
    builderPackage = "io.fabric8.kubernetes.api.builder")
public class StackGresShardedDbOpsMajorVersionUpgradeStatus extends ShardedDbOpsRestartStatus {

  private String sourcePostgresVersion;

  private String targetPostgresVersion;

  private String status;

  private Boolean rollback;

  public String getSourcePostgresVersion() {
    return sourcePostgresVersion;
  }

  public void setSourcePostgresVersion(String sourcePostgresVersion) {
    this.sourcePostgresVersion = sourcePostgresVersion;
  }

  public String getTargetPostgresVersion() {
    return targetPostgresVersion;
  }

  public void setTargetPostgresVersion(String targetPostgresVersion) {
    this.targetPostgresVersion = targetPostgresVersion;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Boolean getRollback() {
    return rollback;
  }

  public void setRollback(Boolean rollback) {
    this.rollback = rollback;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result
        + Objects.hash(rollback, sourcePostgresVersion, status, targetPostgresVersion);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (!(obj instanceof StackGresShardedDbOpsMajorVersionUpgradeStatus)) {
      return false;
    }
    StackGresShardedDbOpsMajorVersionUpgradeStatus other =
        (StackGresShardedDbOpsMajorVersionUpgradeStatus) obj;
    return Objects.equals(rollback, other.rollback)
        && Objects.equals(sourcePostgresVersion, other.sourcePostgresVersion)
        && Objects.equals(status, other.status)
        && Objects.equals(targetPostgresVersion, other.targetPostgresVersion);
  }

  @Override
  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }

}
