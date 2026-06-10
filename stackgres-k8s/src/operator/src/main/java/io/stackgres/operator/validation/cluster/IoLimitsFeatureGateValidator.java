/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.cluster;

import java.util.Optional;

import io.stackgres.common.ErrorType;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgcluster.StackGresClusterNonProduction;
import io.stackgres.common.crd.sgcluster.StackGresClusterPods;
import io.stackgres.common.crd.sgcluster.StackGresClusterPodsPersistentVolume;
import io.stackgres.common.crd.sgcluster.StackGresClusterPodsPersistentVolumeIoLimits;
import io.stackgres.common.crd.sgcluster.StackGresClusterSpec;
import io.stackgres.common.crd.sgcluster.StackGresFeatureGates;
import io.stackgres.operator.common.StackGresClusterReview;
import io.stackgres.operator.validation.ValidationType;
import io.stackgres.operatorframework.admissionwebhook.AdmissionRequest;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationFailed;
import jakarta.inject.Singleton;

@Singleton
@ValidationType(ErrorType.CONSTRAINT_VIOLATION)
public class IoLimitsFeatureGateValidator implements ClusterValidator {

  @Override
  public void validate(StackGresClusterReview review) throws ValidationFailed {
    boolean hasIoLimits = Optional.of(review.getRequest())
        .map(AdmissionRequest::getObject)
        .map(StackGresCluster::getSpec)
        .map(StackGresClusterSpec::getPods)
        .map(StackGresClusterPods::getPersistentVolume)
        .map(StackGresClusterPodsPersistentVolume::getIoLimits)
        .map(ioLimits -> ioLimits.getReadIops() != null
            || ioLimits.getWriteIops() != null
            || ioLimits.getReadMiBps() != null
            || ioLimits.getWriteMiBps() != null)
        .orElse(false);
    boolean hasIoLimitsFeatureGateEnabled = Optional.of(review.getRequest())
        .map(AdmissionRequest::getObject)
        .map(StackGresCluster::getSpec)
        .map(StackGresClusterSpec::getNonProductionOptions)
        .map(StackGresClusterNonProduction::getEnabledFeatureGates)
        .map(featureGates -> featureGates.contains(
            StackGresFeatureGates.IO_LIMITS.toString()))
        .orElse(false);
    if (hasIoLimits && !hasIoLimitsFeatureGateEnabled) {
      failWithFields("To enable per-volume I/O limits you must add \"io-limits\""
          + " feature gate under \".spec.nonProductionOptions.enabledFeatureGates\"."
          + " Be aware that setting I/O limits requires the Pods to run a privileged"
          + " init container as root and to mount the host cgroup filesystem.",
          ".spec.pods.persistentVolume.ioLimits");
    }
  }

}
