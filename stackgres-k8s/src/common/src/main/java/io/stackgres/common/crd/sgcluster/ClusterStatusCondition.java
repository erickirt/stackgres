/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgcluster;

import java.util.Objects;

import io.stackgres.common.crd.Condition;

public enum ClusterStatusCondition {

  CLUSTER_BOOTSTRAPPED(Type.BOOTSTRAPPED, Status.TRUE, "ClusterBootstrapped"),
  CLUSTER_INITIAL_SCRIPTS_APPLIED(Type.INITIAL_SCRIPTS_APPLIED, Status.TRUE, "ClusterInitialScriptApplied"),
  POD_REQUIRES_RESTART(Type.PENDING_RESTART, Status.TRUE, "PodRequiresRestart"),
  FALSE_PENDING_RESTART(Type.PENDING_RESTART, Status.FALSE, "FalsePendingRestart"),
  CLUSTER_REQUIRES_UPGRADE(Type.PENDING_UPGRADE, Status.TRUE, "ClusterRequiresUpgrade"),
  FALSE_PENDING_UPGRADE(Type.PENDING_UPGRADE, Status.FALSE, "FalsePendingUpgrade"),
  CLUSTER_CONFIG_ERROR(Type.FAILED, Status.TRUE, "ClusterConfigFailed"),
  FALSE_FAILED(Type.FAILED, Status.FALSE, "FalseFailed"),
  // ComponentsUpdated: all the combinations of the latest minor (status), a newer major available
  // and extension upgrades available. The latest available versions and the number of extensions to
  // upgrade are advertised in the message. The minor in use determines the status; a newer major or
  // extension upgrades only append a suffix to the reason.
  COMPONENTS_UP_TO_DATE(Type.COMPONENTS_UPDATED, Status.TRUE, "UpToDate"),
  COMPONENTS_LATEST_MINOR_NOT_LATEST_MAJOR(
      Type.COMPONENTS_UPDATED, Status.TRUE, "NotLatestMajor"),
  COMPONENTS_LATEST_MINOR_AVAILABLE_EXTENSIONS_UPGRADE(
      Type.COMPONENTS_UPDATED, Status.TRUE, "AvailableExtensionsUpgrade"),
  COMPONENTS_LATEST_MINOR_NOT_LATEST_MAJOR_AVAILABLE_EXTENSIONS_UPGRADE(
      Type.COMPONENTS_UPDATED, Status.TRUE, "NotLatestMajorAnd-AvailableExtensionsUpgrade"),
  COMPONENTS_NOT_LATEST_MINOR(Type.COMPONENTS_UPDATED, Status.FALSE, "NotLatestMinor"),
  COMPONENTS_NOT_LATEST_MINOR_NOT_LATEST_MAJOR(
      Type.COMPONENTS_UPDATED, Status.FALSE, "NotLatestMinor-NotLatestMajor"),
  COMPONENTS_NOT_LATEST_MINOR_AVAILABLE_EXTENSIONS_UPGRADE(
      Type.COMPONENTS_UPDATED, Status.FALSE, "NotLatestMinor-AvailableExtensionsUpgrade"),
  COMPONENTS_NOT_LATEST_MINOR_NOT_LATEST_MAJOR_AVAILABLE_EXTENSIONS_UPGRADE(
      Type.COMPONENTS_UPDATED, Status.FALSE,
      "NotLatestMinor-NotLatestMajor-AvailableExtensionsUpgrade");

  private final String type;
  private final String status;
  private final String reason;

  ClusterStatusCondition(Type type, Status status, String reason) {
    this.type = type.getType();
    this.status = status.getStatus();
    this.reason = reason;
  }

  public Condition getCondition() {
    return new Condition(type, status, reason);
  }

  public boolean isCondition(Condition condition) {
    return Objects.equals(condition.getType(), type)
        && Objects.equals(condition.getStatus(), status)
        && Objects.equals(condition.getReason(), reason);
  }

  public enum Type {
    BOOTSTRAPPED("Bootstrapped"),
    INITIAL_SCRIPTS_APPLIED("InitialScriptsApplied"),
    PENDING_RESTART("PendingRestart"),
    PENDING_UPGRADE("PendingUpgrade"),
    COMPONENTS_UPDATED("ComponentsUpdated"),
    FAILED("Failed");

    private final String typeCondition;

    Type(String type) {
      this.typeCondition = type;
    }

    public String getType() {
      return typeCondition;
    }
  }

  public enum Status {
    TRUE("True"),
    FALSE("False"),
    UNKNOWN("Unknown");

    private final String statusCondition;

    Status(String statusCondition) {
      this.statusCondition = statusCondition;
    }

    public String getStatus() {
      return statusCondition;
    }
  }

}
