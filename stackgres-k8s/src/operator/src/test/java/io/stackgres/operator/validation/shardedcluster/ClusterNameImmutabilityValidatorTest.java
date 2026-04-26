/*
 * Copyright (C) 2026 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.shardedcluster;

import io.stackgres.common.ErrorType;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterWorkers;
import io.stackgres.operator.common.StackGresShardedClusterReview;
import io.stackgres.operator.common.fixture.AdmissionReviewFixtures;
import io.stackgres.operator.utils.ValidationUtils;
import io.stackgres.operatorframework.admissionwebhook.Operation;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationFailed;
import io.stackgres.testutil.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClusterNameImmutabilityValidatorTest {

  private ClusterNameImmutabilityValidator validator;

  @BeforeEach
  void setUp() {
    validator = new ClusterNameImmutabilityValidator();
  }

  private StackGresShardedClusterReview updateReview() {
    var review = AdmissionReviewFixtures.shardedCluster().loadCreate().get();
    review.getRequest().setOperation(Operation.UPDATE);
    review.getRequest().setOldObject(JsonUtil.copy(review.getRequest().getObject()));
    return review;
  }

  @Test
  void create_shouldPass() throws ValidationFailed {
    var review = AdmissionReviewFixtures.shardedCluster().loadCreate().get();
    review.getRequest().getObject().getSpec().getCoordinator().setClusterName("custom-coord");
    review.getRequest().getObject().getSpec().getWorkers().setClusterNameTemplate("custom-w-");

    validator.validate(review);
  }

  @Test
  void update_withoutChanges_shouldPass() throws ValidationFailed {
    validator.validate(updateReview());
  }

  @Test
  void update_withSameCoordinatorClusterName_shouldPass() throws ValidationFailed {
    var review = updateReview();
    review.getRequest().getObject().getSpec().getCoordinator().setClusterName("custom-coord");
    review.getRequest().getOldObject().getSpec().getCoordinator().setClusterName("custom-coord");

    validator.validate(review);
  }

  @Test
  void update_changingCoordinatorClusterName_shouldFail() {
    var review = updateReview();
    review.getRequest().getOldObject().getSpec().getCoordinator().setClusterName("old-coord");
    review.getRequest().getObject().getSpec().getCoordinator().setClusterName("new-coord");

    ValidationUtils.assertValidationFailed(() -> validator.validate(review),
        ErrorType.FORBIDDEN_CR_UPDATE,
        "spec.coordinator.clusterName can only be set on creation");
  }

  @Test
  void update_settingCoordinatorClusterNameFromNull_shouldFail() {
    var review = updateReview();
    review.getRequest().getObject().getSpec().getCoordinator().setClusterName("new-coord");

    ValidationUtils.assertValidationFailed(() -> validator.validate(review),
        ErrorType.FORBIDDEN_CR_UPDATE,
        "spec.coordinator.clusterName can only be set on creation");
  }

  @Test
  void update_clearingCoordinatorClusterName_shouldFail() {
    var review = updateReview();
    review.getRequest().getOldObject().getSpec().getCoordinator().setClusterName("old-coord");

    ValidationUtils.assertValidationFailed(() -> validator.validate(review),
        ErrorType.FORBIDDEN_CR_UPDATE,
        "spec.coordinator.clusterName can only be set on creation");
  }

  @Test
  void update_withSameWorkersClusterNameTemplate_shouldPass() throws ValidationFailed {
    var review = updateReview();
    review.getRequest().getObject().getSpec().getWorkers().setClusterNameTemplate("custom-w-");
    review.getRequest().getOldObject().getSpec().getWorkers().setClusterNameTemplate("custom-w-");

    validator.validate(review);
  }

  @Test
  void update_changingWorkersClusterNameTemplate_shouldFail() {
    var review = updateReview();
    review.getRequest().getOldObject().getSpec().getWorkers().setClusterNameTemplate("old-");
    review.getRequest().getObject().getSpec().getWorkers().setClusterNameTemplate("new-");

    ValidationUtils.assertValidationFailed(() -> validator.validate(review),
        ErrorType.FORBIDDEN_CR_UPDATE,
        "spec.workers.clusterNameTemplate can only be set on creation");
  }

  @Test
  void update_settingWorkersClusterNameTemplateFromNull_shouldFail() {
    var review = updateReview();
    review.getRequest().getObject().getSpec().getWorkers().setClusterNameTemplate("new-");

    ValidationUtils.assertValidationFailed(() -> validator.validate(review),
        ErrorType.FORBIDDEN_CR_UPDATE,
        "spec.workers.clusterNameTemplate can only be set on creation");
  }

  @Test
  void update_migratingFromDeprecatedShards_shouldAllowClusterNameTemplate() throws ValidationFailed {
    var review = updateReview();
    StackGresShardedClusterWorkers workers = review.getRequest().getOldObject().getSpec().getWorkers();
    review.getRequest().getOldObject().getSpec().setWorkers(null);
    review.getRequest().getOldObject().getSpec().setShards(workers);
    // Simulates what ShardsToWorkersMutator did on the new object
    review.getRequest().getObject().getSpec().getWorkers().setClusterNameTemplate(
        review.getRequest().getObject().getMetadata().getName() + "-shard");

    validator.validate(review);
  }

  @Test
  void delete_shouldPass() throws ValidationFailed {
    var review = updateReview();
    review.getRequest().setOperation(Operation.DELETE);
    review.getRequest().getObject().getSpec().getCoordinator().setClusterName("different");

    validator.validate(review);
  }

}
