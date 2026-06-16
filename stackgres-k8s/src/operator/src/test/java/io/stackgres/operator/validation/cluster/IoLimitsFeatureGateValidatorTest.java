/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.cluster;

import static org.mockito.Mockito.when;

import io.stackgres.common.ErrorType;
import io.stackgres.common.crd.sgcluster.StackGresClusterPodsPersistentVolumeIoLimits;
import io.stackgres.operator.common.StackGresClusterReview;
import io.stackgres.operator.common.fixture.AdmissionReviewFixtures;
import io.stackgres.operator.utils.ValidationUtils;
import io.stackgres.operator.validation.IoLimitsFeatureGate;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationFailed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IoLimitsFeatureGateValidatorTest {

  @Mock
  private IoLimitsFeatureGate ioLimitsFeatureGate;

  private IoLimitsFeatureGateValidator validator;

  @BeforeEach
  void setUp() {
    validator = new IoLimitsFeatureGateValidator(ioLimitsFeatureGate);
  }

  @Test
  void givenNoIoLimits_shouldPass() throws ValidationFailed {
    final StackGresClusterReview review = getCreationReview();
    validator.validate(review);
  }

  @Test
  void givenIoLimitsWithGateEnabled_shouldPass() throws ValidationFailed {
    final StackGresClusterReview review = getCreationReview();
    setIoLimits(review);
    when(ioLimitsFeatureGate.isEnabled()).thenReturn(true);
    validator.validate(review);
  }

  @Test
  void givenIoLimitsWithGateDisabled_shouldFail() {
    final StackGresClusterReview review = getCreationReview();
    setIoLimits(review);
    when(ioLimitsFeatureGate.isEnabled()).thenReturn(false);

    ValidationUtils.assertValidationFailed(() -> validator.validate(review),
        ErrorType.CONSTRAINT_VIOLATION,
        "To enable per-volume I/O limits you must add the \"io-limits\" feature gate under"
            + " \".spec.featureGates\" of the SGConfig. This can only be done by the operator"
            + " administrator. Be aware that setting I/O limits requires the Pods to run a"
            + " privileged init container as root and to mount the host cgroup filesystem.",
        ".spec.pods.persistentVolume.ioLimits");
  }

  private void setIoLimits(StackGresClusterReview review) {
    StackGresClusterPodsPersistentVolumeIoLimits ioLimits =
        new StackGresClusterPodsPersistentVolumeIoLimits();
    ioLimits.setReadIops(1000);
    review.getRequest().getObject().getSpec().getPods().getPersistentVolume().setIoLimits(ioLimits);
  }

  private StackGresClusterReview getCreationReview() {
    return AdmissionReviewFixtures.cluster().loadCreate().get();
  }

}
