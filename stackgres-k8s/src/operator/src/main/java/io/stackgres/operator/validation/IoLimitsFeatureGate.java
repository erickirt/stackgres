/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation;

import io.stackgres.common.OperatorProperty;
import io.stackgres.common.crd.sgconfig.StackGresConfig;
import io.stackgres.common.crd.sgconfig.StackGresConfigFeatureGates;
import io.stackgres.common.crd.sgconfig.StackGresConfigSpec;
import io.stackgres.common.resource.CustomResourceFinder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Reads the global {@code io-limits} feature gate from the operator's SGConfig.
 *
 * <p>Per-volume I/O limits force the cluster Pods to run a privileged {@code setup-io-limits}
 * init container as root and to mount the host cgroup filesystem read-write, so the capability
 * can only be enabled globally by the operator administrator under {@code .spec.featureGates} of
 * the SGConfig instead of by the authors of each SGCluster or SGShardedCluster.</p>
 */
@ApplicationScoped
public class IoLimitsFeatureGate {

  private final String operatorName = OperatorProperty.OPERATOR_NAME.getString();
  private final String sgConfigNamespace = OperatorProperty.SGCONFIG_NAMESPACE.get()
      .orElseGet(OperatorProperty.OPERATOR_NAMESPACE::getString);

  private final CustomResourceFinder<StackGresConfig> configFinder;

  @Inject
  public IoLimitsFeatureGate(CustomResourceFinder<StackGresConfig> configFinder) {
    this.configFinder = configFinder;
  }

  public boolean isEnabled() {
    return configFinder.findByNameAndNamespace(operatorName, sgConfigNamespace)
        .map(StackGresConfig::getSpec)
        .map(StackGresConfigSpec::getFeatureGates)
        .map(featureGates -> featureGates.contains(
            StackGresConfigFeatureGates.IO_LIMITS.toString()))
        .orElse(false);
  }

}
