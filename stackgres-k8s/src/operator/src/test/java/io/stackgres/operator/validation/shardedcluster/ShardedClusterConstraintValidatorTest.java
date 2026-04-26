/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.shardedcluster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.common.collect.Lists;
import io.stackgres.common.crd.SecretKeySelector;
import io.stackgres.common.crd.Toleration;
import io.stackgres.common.crd.sgcluster.StackGresClusterNonProduction;
import io.stackgres.common.crd.sgcluster.StackGresClusterPodsPersistentVolume;
import io.stackgres.common.crd.sgcluster.StackGresClusterPodsScheduling;
import io.stackgres.common.crd.sgcluster.StackGresClusterPostgres;
import io.stackgres.common.crd.sgcluster.StackGresClusterReplication;
import io.stackgres.common.crd.sgcluster.StackGresClusterSpec;
import io.stackgres.common.crd.sgcluster.StackGresClusterSsl;
import io.stackgres.common.crd.sgcluster.StackGresFeatureGates;
import io.stackgres.common.crd.sgcluster.StackGresPostgresFlavor;
import io.stackgres.common.crd.sgcluster.StackGresReplicationMode;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterBackupConfiguration;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterConfigurations;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterCoordinator;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterPostgresServices;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterPostgresWorkersServices;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterReplication;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterShardPods;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterSpec;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterSpecAnnotations;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterSpecLabels;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterSpecMetadata;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterWorker;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterWorkers;
import io.stackgres.common.validation.ValidEnum;
import io.stackgres.common.validation.ValidEnumList;
import io.stackgres.operator.common.StackGresShardedClusterReview;
import io.stackgres.operator.common.fixture.AdmissionReviewFixtures;
import io.stackgres.operator.validation.AbstractConstraintValidator;
import io.stackgres.operator.validation.ConstraintValidationTest;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationFailed;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.Test;

class ShardedClusterConstraintValidatorTest
    extends ConstraintValidationTest<StackGresShardedClusterReview> {

  @Override
  protected AbstractConstraintValidator<StackGresShardedClusterReview> buildValidator() {
    return new ShardedClusterConstraintValidator();
  }

  @Override
  protected StackGresShardedClusterReview getValidReview() {
    return AdmissionReviewFixtures.shardedCluster().loadCreate().get();
  }

  @Override
  protected StackGresShardedClusterReview getInvalidReview() {
    final StackGresShardedClusterReview review =
        AdmissionReviewFixtures.shardedCluster().loadCreate().get();

    review.getRequest().getObject().setSpec(null);
    return review;
  }

  @Test
  void nullSpec_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().setSpec(null);

    checkNotNullErrorCause(StackGresShardedCluster.class, "spec", review);
  }

  @Test
  void sslCertificateSecretWithEmptyName_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getPostgres().setSsl(new StackGresClusterSsl());
    review.getRequest().getObject().getSpec().getPostgres().getSsl().setEnabled(true);
    review.getRequest().getObject().getSpec().getPostgres().getSsl()
        .setCertificateSecretKeySelector(
            new SecretKeySelector("test", null));
    review.getRequest().getObject().getSpec().getPostgres().getSsl().setPrivateKeySecretKeySelector(
        new SecretKeySelector("test", "test"));

    checkErrorCause(SecretKeySelector.class,
        "spec.postgres.ssl.certificateSecretKeySelector.name",
        "isNameNotEmpty", review, AssertTrue.class);
  }

  @Test
  void sslPrivateKeySecretWithEmptyName_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getPostgres().setSsl(new StackGresClusterSsl());
    review.getRequest().getObject().getSpec().getPostgres().getSsl().setEnabled(true);
    review.getRequest().getObject().getSpec().getPostgres().getSsl()
        .setCertificateSecretKeySelector(
            new SecretKeySelector("test", "test"));
    review.getRequest().getObject().getSpec().getPostgres().getSsl().setPrivateKeySecretKeySelector(
        new SecretKeySelector("test", null));

    checkErrorCause(SecretKeySelector.class,
        "spec.postgres.ssl.privateKeySecretKeySelector.name",
        "isNameNotEmpty", review, AssertTrue.class);
  }

  @Test
  void givenNullSyncInstances_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator().setInstances(2);
    review.getRequest().getObject().getSpec().getReplication().setMode(
        StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getReplication().setSyncInstances(null);

    checkErrorCause(StackGresShardedClusterReplication.class,
        "spec.replication.syncInstances",
        "isSyncInstancesSetForSyncMode",
        review, AssertTrue.class);
  }

  @Test
  void givenSyncInstancesLessThanOne_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator().setInstances(2);
    review.getRequest().getObject().getSpec().getReplication().setMode(
        StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getReplication().setSyncInstances(0);

    checkErrorCause(StackGresClusterReplication.class,
        "spec.replication.syncInstances",
        review, Min.class, "must be greater than or equal to 1");
  }

  @Test
  void givenNullObjectStorageOnBackups_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec()
        .setConfigurations(new StackGresShardedClusterConfigurations());
    review.getRequest().getObject().getSpec().getConfigurations().setBackups(new ArrayList<>());
    review.getRequest().getObject().getSpec().getConfigurations().getBackups()
        .add(new StackGresShardedClusterBackupConfiguration());
    review.getRequest().getObject().getSpec().getConfigurations().getBackups().get(0)
        .setPaths(List.of("test-0", "test-1", "test-2"));

    checkErrorCause(StackGresShardedClusterBackupConfiguration.class,
        "spec.configurations.backups[0].sgObjectStorage",
        review, NotNull.class, "must not be null");
  }

  @Test
  void invalidBackupsLowMaxRetries_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec()
        .setConfigurations(new StackGresShardedClusterConfigurations());
    review.getRequest().getObject().getSpec().getConfigurations().setBackups(new ArrayList<>());
    review.getRequest().getObject().getSpec().getConfigurations().getBackups()
        .add(new StackGresShardedClusterBackupConfiguration());
    review.getRequest().getObject().getSpec().getConfigurations().getBackups().get(0)
        .setPaths(List.of("test-0", "test-1", "test-2"));
    review.getRequest().getObject().getSpec().getConfigurations().getBackups().get(0)
        .setSgObjectStorage("test");
    review.getRequest().getObject().getSpec().getConfigurations().getBackups().get(0)
        .setMaxRetries(-1);

    checkErrorCause(StackGresShardedClusterBackupConfiguration.class, "spec.configurations.backups[0].maxRetries",
        review, Min.class);

  }

  @Test
  void givenValidFlavor_shouldPass() throws ValidationFailed {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getPostgres().setFlavor(
        StackGresPostgresFlavor.BABELFISH.toString());

    validator.validate(review);
  }

  @Test
  void givenInvalidFlavor_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getPostgres().setFlavor(
        "glassfish");

    checkErrorCause(StackGresClusterPostgres.class, "spec.postgres.flavor",
        review, ValidEnum.class);
  }

  @Test
  void givenValidFeatureGate_shouldPass() throws ValidationFailed {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().setNonProductionOptions(
        new StackGresClusterNonProduction());
    review.getRequest().getObject().getSpec().getNonProductionOptions().setEnabledFeatureGates(
        Lists.newArrayList(StackGresFeatureGates.BABELFISH_FLAVOR.toString()));

    validator.validate(review);
  }

  @Test
  void givenInvalidFeatureGate_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().setNonProductionOptions(
        new StackGresClusterNonProduction());
    review.getRequest().getObject().getSpec().getNonProductionOptions().setEnabledFeatureGates(
        Lists.newArrayList("glassfish-flavor"));

    checkErrorCause(StackGresClusterNonProduction.class,
        "spec.nonProductionOptions.enabledFeatureGates",
        review, ValidEnumList.class);
  }

  @Test
  void givenMissingReplicationMode_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getReplication().setMode(null);

    checkErrorCause(StackGresClusterReplication.class,
        "spec.replication.mode",
        review, ValidEnum.class);
  }

  @Test
  void givenMissingReplicationRole_shouldPass() throws ValidationFailed {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getReplication().setRole(null);

    validator.validate(review);
  }

  @Test
  void givenInstancesGreatherThanSyncInstances_shouldPass() throws ValidationFailed {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator().setInstances(2);
    review.getRequest().getObject().getSpec().getWorkers().setInstancesPerCluster(2);
    review.getRequest().getObject().getSpec().getReplication().setMode(
        StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getReplication().setSyncInstances(1);

    validator.validate(review);
  }

  @Test
  void nullCoordinatorResourceProfile_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator().setSgInstanceProfile(null);

    checkErrorCause(StackGresClusterSpec.class,
        "spec.coordinator.sgInstanceProfile",
        "isResourceProfilePresent", review,
        AssertTrue.class);
  }

  @Test
  void nullCoordinatorVolumeSize_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator()
        .getPods().getPersistentVolume().setSize(null);

    checkNotNullErrorCause(StackGresClusterPodsPersistentVolume.class,
        "spec.coordinator.pods.persistentVolume.size",
        review);
  }

  @Test
  void invalidCoordinatorVolumeSize_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator()
        .getPods().getPersistentVolume().setSize("512");

    checkErrorCause(StackGresClusterPodsPersistentVolume.class,
        "spec.coordinator.pods.persistentVolume.size",
        review, Pattern.class);
  }

  @Test
  void validCoordinatorNodeSelector_shouldPass() throws ValidationFailed {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator().getPods()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .setNodeSelector(new HashMap<>());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getNodeSelector()
        .put("test", "true");

    validator.validate(review);
  }

  @Test
  void validCoordinatorToleration_shouldPass() throws ValidationFailed {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator().getPods()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations().get(0)
        .setKey("test");

    validator.validate(review);
  }

  @Test
  void validCoordinatorTolerationKeyEmpty_shouldPass() throws ValidationFailed {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator().getPods()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations().get(0)
        .setKey("");
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations().get(0)
        .setOperator("Exists");

    validator.validate(review);
  }

  @Test
  void invalidCoordinatorTolerationKeyEmpty_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator().getPods()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations().get(0)
        .setKey("");

    checkErrorCause(Toleration.class,
        new String[] {"spec.coordinator.pods.scheduling.tolerations[0].key",
            "spec.coordinator.pods.scheduling.tolerations[0].operator"},
        "isOperatorExistsWhenKeyIsEmpty", review,
        AssertTrue.class);
  }

  @Test
  void invalidCoordinatorTolerationOperator_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator().getPods()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations().get(0)
        .setKey("test");
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations().get(0)
        .setOperator("NotExists");

    checkErrorCause(Toleration.class, "spec.coordinator.pods.scheduling.tolerations[0].operator",
        "isOperatorValid", review, AssertTrue.class);
  }

  @Test
  void invalidCoordinatorTolerationEffect_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator().getPods()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations().get(0)
        .setKey("test");
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations().get(0)
        .setEffect("NeverSchedule");

    checkErrorCause(Toleration.class, "spec.coordinator.pods.scheduling.tolerations[0].effect",
        "isEffectValid", review, AssertTrue.class);
  }

  @Test
  void givenCoordinatorTolerationsSetAndEffectNoExecute_shouldPass() throws ValidationFailed {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator().getPods()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations().get(0)
        .setKey("test");
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations().get(0)
        .setTolerationSeconds(100L);
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations().get(0)
        .setEffect("NoExecute");

    validator.validate(review);
  }

  @Test
  void givenCoordinatorTolerationsSetAndEffectOtherThanNoExecute_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator().getPods()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations().get(0)
        .setKey("test");
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations().get(0)
        .setTolerationSeconds(100L);
    review.getRequest().getObject().getSpec().getCoordinator().getPods().getScheduling()
        .getTolerations().get(0)
        .setEffect(new Random().nextBoolean() ? "NoSchedule" : "PreferNoSchedule");

    checkErrorCause(Toleration.class, "spec.coordinator.pods.scheduling.tolerations[0].effect",
        "isEffectNoExecuteIfTolerationIsSet", review, AssertTrue.class);
  }

  @Test
  void givenCoordinatorInstancesEqualsToSyncInstances_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator().setInstances(1);
    review.getRequest().getObject().getSpec().getReplication().setMode(
        StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getReplication().setSyncInstances(1);

    checkErrorCause(StackGresShardedClusterSpec.class,
        "spec.replication.syncInstances",
        "isSupportingRequiredSynchronousReplicas",
        review, AssertTrue.class);
  }

  @Test
  void givenCoordinatorInstancesLessThanSyncInstances_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator().setInstances(1);
    review.getRequest().getObject().getSpec().getReplication().setMode(
        StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getReplication().setSyncInstances(2);

    checkErrorCause(StackGresShardedClusterSpec.class,
        "spec.replication.syncInstances",
        "isSupportingRequiredSynchronousReplicas",
        review, AssertTrue.class);
  }

  @Test
  void givenCoordinatorInstancesEqualsToCoordinatorSyncInstances_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator().setInstances(1);
    review.getRequest().getObject().getSpec().getCoordinator()
        .setReplicationForCoordinator(new StackGresShardedClusterReplication());
    review.getRequest().getObject().getSpec().getCoordinator().getReplicationForCoordinator()
        .setMode(StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getCoordinator().getReplicationForCoordinator()
        .setSyncInstances(1);

    checkErrorCause(StackGresShardedClusterCoordinator.class,
        "spec.coordinator.replication.syncInstances",
        "isCoordinatorSupportingRequiredSynchronousReplicas",
        review, AssertTrue.class);
  }

  @Test
  void givenCoordinatorInstancesLessThanCoordinatorSyncInstances_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator().setInstances(1);
    review.getRequest().getObject().getSpec().getCoordinator()
        .setReplicationForCoordinator(new StackGresShardedClusterReplication());
    review.getRequest().getObject().getSpec().getCoordinator().getReplicationForCoordinator()
        .setMode(StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getCoordinator().getReplicationForCoordinator()
        .setSyncInstances(2);

    checkErrorCause(StackGresShardedClusterCoordinator.class,
        "spec.coordinator.replication.syncInstances",
        "isCoordinatorSupportingRequiredSynchronousReplicas",
        review, AssertTrue.class);
  }

  @Test
  void givenCoordinatorNullSyncInstances_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator().setInstances(2);
    review.getRequest().getObject().getSpec().getReplication().setMode(
        StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getReplication().setSyncInstances(null);

    checkErrorCause(StackGresShardedClusterReplication.class,
        "spec.replication.syncInstances",
        "isSyncInstancesSetForSyncMode",
        review, AssertTrue.class);
  }

  @Test
  void givenCoordinatorSyncInstancesLessThanOne_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getCoordinator().setInstances(2);
    review.getRequest().getObject().getSpec().getReplication().setMode(
        StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getReplication().setSyncInstances(0);

    checkErrorCause(StackGresClusterReplication.class,
        "spec.replication.syncInstances",
        review, Min.class, "must be greater than or equal to 1");
  }

  @Test
  void nullWorkersResourceProfile_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers().setSgInstanceProfile(null);

    checkErrorCause(StackGresClusterSpec.class,
        "spec.workers.sgInstanceProfile",
        "isResourceProfilePresent", review,
        AssertTrue.class);
  }

  @Test
  void nullWorkersVolumeSize_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .getPods().getPersistentVolume().setSize(null);

    checkNotNullErrorCause(StackGresClusterPodsPersistentVolume.class,
        "spec.workers.pods.persistentVolume.size",
        review);
  }

  @Test
  void invalidWorkersVolumeSize_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .getPods().getPersistentVolume().setSize("512");

    checkErrorCause(StackGresClusterPodsPersistentVolume.class,
        "spec.workers.pods.persistentVolume.size",
        review, Pattern.class);
  }

  @Test
  void validWorkersNodeSelector_shouldPass() throws ValidationFailed {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers().getPods()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .setNodeSelector(new HashMap<>());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getNodeSelector()
        .put("test", "true");

    validator.validate(review);
  }

  @Test
  void validWorkersToleration_shouldPass() throws ValidationFailed {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers().getPods()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations().get(0)
        .setKey("test");

    validator.validate(review);
  }

  @Test
  void validWorkersTolerationKeyEmpty_shouldPass() throws ValidationFailed {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers().getPods()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations().get(0)
        .setKey("");
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations().get(0)
        .setOperator("Exists");

    validator.validate(review);
  }

  @Test
  void invalidWorkersTolerationKeyEmpty_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers().getPods()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations().get(0)
        .setKey("");

    checkErrorCause(Toleration.class,
        new String[] {"spec.workers.pods.scheduling.tolerations[0].key",
            "spec.workers.pods.scheduling.tolerations[0].operator"},
        "isOperatorExistsWhenKeyIsEmpty", review,
        AssertTrue.class);
  }

  @Test
  void invalidWorkersTolerationOperator_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers().getPods()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations().get(0)
        .setKey("test");
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations().get(0)
        .setOperator("NotExists");

    checkErrorCause(Toleration.class, "spec.workers.pods.scheduling.tolerations[0].operator",
        "isOperatorValid", review, AssertTrue.class);
  }

  @Test
  void invalidWorkersTolerationEffect_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers().getPods()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations().get(0)
        .setKey("test");
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations().get(0)
        .setEffect("NeverSchedule");

    checkErrorCause(Toleration.class, "spec.workers.pods.scheduling.tolerations[0].effect",
        "isEffectValid", review, AssertTrue.class);
  }

  @Test
  void givenWorkersTolerationsSetAndEffectNoExecute_shouldPass() throws ValidationFailed {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers().getPods()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations().get(0)
        .setKey("test");
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations().get(0)
        .setTolerationSeconds(100L);
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations().get(0)
        .setEffect("NoExecute");

    validator.validate(review);
  }

  @Test
  void givenWorkersTolerationsSetAndEffectOtherThanNoExecute_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers().getPods()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations().get(0)
        .setKey("test");
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations().get(0)
        .setTolerationSeconds(100L);
    review.getRequest().getObject().getSpec().getWorkers().getPods().getScheduling()
        .getTolerations().get(0)
        .setEffect(new Random().nextBoolean() ? "NoSchedule" : "PreferNoSchedule");

    checkErrorCause(Toleration.class, "spec.workers.pods.scheduling.tolerations[0].effect",
        "isEffectNoExecuteIfTolerationIsSet", review, AssertTrue.class);
  }

  @Test
  void givenWorkersInstancesPerClusterEqualsToSyncInstances_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers().setInstancesPerCluster(1);
    review.getRequest().getObject().getSpec().getReplication().setMode(
        StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getReplication().setSyncInstances(1);

    checkErrorCause(StackGresShardedClusterSpec.class,
        "spec.replication.syncInstances",
        "isSupportingRequiredSynchronousReplicas",
        review, AssertTrue.class);
  }

  @Test
  void givenWorkersInstancesPerClusterLessThanSyncInstances_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers().setInstancesPerCluster(1);
    review.getRequest().getObject().getSpec().getReplication().setMode(
        StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getReplication().setSyncInstances(2);

    checkErrorCause(StackGresShardedClusterSpec.class,
        "spec.replication.syncInstances",
        "isSupportingRequiredSynchronousReplicas",
        review, AssertTrue.class);
  }

  @Test
  void givenWorkersInstancesPerClusterEqualsToWorkersSyncInstances_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers().setInstancesPerCluster(1);
    review.getRequest().getObject().getSpec().getWorkers()
        .setReplicationForWorkers(new StackGresShardedClusterReplication());
    review.getRequest().getObject().getSpec().getWorkers().getReplicationForWorkers()
        .setMode(StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getWorkers().getReplicationForWorkers()
        .setSyncInstances(1);

    checkErrorCause(StackGresShardedClusterWorkers.class,
        "spec.workers.replication.syncInstances",
        "isWorkersSupportingRequiredSynchronousReplicas",
        review, AssertTrue.class);
  }

  @Test
  void givenWorkersInstancesPerClusterLessThanWorkersSyncInstances_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers().setInstancesPerCluster(1);
    review.getRequest().getObject().getSpec().getWorkers()
        .setReplicationForWorkers(new StackGresShardedClusterReplication());
    review.getRequest().getObject().getSpec().getWorkers().getReplicationForWorkers()
        .setMode(StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getWorkers().getReplicationForWorkers()
        .setSyncInstances(2);

    checkErrorCause(StackGresShardedClusterWorkers.class,
        "spec.workers.replication.syncInstances",
        "isWorkersSupportingRequiredSynchronousReplicas",
        review, AssertTrue.class);
  }

  @Test
  void givenWorkersNullSyncInstances_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers().setInstancesPerCluster(2);
    review.getRequest().getObject().getSpec().getReplication().setMode(
        StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getReplication().setSyncInstances(null);

    checkErrorCause(StackGresShardedClusterReplication.class,
        "spec.replication.syncInstances",
        "isSyncInstancesSetForSyncMode",
        review, AssertTrue.class);
  }

  @Test
  void givenWorkersSyncInstancesLessThanOne_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers().setInstancesPerCluster(2);
    review.getRequest().getObject().getSpec().getReplication().setMode(
        StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getReplication().setSyncInstances(0);

    checkErrorCause(StackGresClusterReplication.class,
        "spec.replication.syncInstances",
        review, Min.class, "must be greater than or equal to 1");
  }

  @Test
  void nullOverridesWorkersResourceProfile_shouldPass() throws ValidationFailed {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setSgInstanceProfile(null);

    validator.validate(review);
  }

  @Test
  void nullOverridesWorkersVolumeSize_shouldPass() throws ValidationFailed {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setPodsForWorkers(new StackGresShardedClusterShardPods());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0)
        .getPodsForWorkers().setPersistentVolume(null);

    validator.validate(review);
  }

  @Test
  void nullOverridesWorkersVolumeSize_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setPodsForWorkers(new StackGresShardedClusterShardPods());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .setPersistentVolume(new StackGresClusterPodsPersistentVolume());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0)
        .getPodsForWorkers().getPersistentVolume().setSize(null);

    checkNotNullErrorCause(StackGresClusterPodsPersistentVolume.class,
        "spec.workers.overrides[0].pods.persistentVolume.size",
        review);
  }

  @Test
  void invalidOverridesWorkersVolumeSize_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setPodsForWorkers(new StackGresShardedClusterShardPods());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .setPersistentVolume(new StackGresClusterPodsPersistentVolume());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0)
        .getPodsForWorkers().getPersistentVolume().setSize("512");

    checkErrorCause(StackGresClusterPodsPersistentVolume.class,
        "spec.workers.overrides[0].pods.persistentVolume.size",
        review, Pattern.class);
  }

  @Test
  void validOverridesWorkersNodeSelector_shouldPass() throws ValidationFailed {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setPodsForWorkers(new StackGresShardedClusterShardPods());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().setNodeSelector(new HashMap<>());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getNodeSelector()
        .put("test", "true");

    validator.validate(review);
  }

  @Test
  void validOverridesWorkersToleration_shouldPass() throws ValidationFailed {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setPodsForWorkers(new StackGresShardedClusterShardPods());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling()
        .getTolerations().get(0)
        .setKey("test");

    validator.validate(review);
  }

  @Test
  void validOverridesWorkersTolerationKeyEmpty_shouldPass() throws ValidationFailed {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setPodsForWorkers(new StackGresShardedClusterShardPods());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations().get(0)
        .setKey("");
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations().get(0)
        .setOperator("Exists");

    validator.validate(review);
  }

  @Test
  void invalidOverridesWorkersTolerationKeyEmpty_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setPodsForWorkers(new StackGresShardedClusterShardPods());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations().get(0)
        .setKey("");

    checkErrorCause(Toleration.class,
        new String[] {
            "spec.workers.overrides[0].pods.scheduling.tolerations[0].key",
            "spec.workers.overrides[0].pods.scheduling.tolerations[0].operator"},
        "isOperatorExistsWhenKeyIsEmpty", review,
        AssertTrue.class);
  }

  @Test
  void invalidOverridesWorkersTolerationOperator_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setPodsForWorkers(new StackGresShardedClusterShardPods());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations().get(0)
        .setKey("test");
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations().get(0)
        .setOperator("NotExists");

    checkErrorCause(Toleration.class,
        "spec.workers.overrides[0].pods.scheduling.tolerations[0].operator",
        "isOperatorValid", review, AssertTrue.class);
  }

  @Test
  void invalidOverridesWorkersTolerationEffect_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setPodsForWorkers(new StackGresShardedClusterShardPods());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations().get(0)
        .setKey("test");
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations().get(0)
        .setEffect("NeverSchedule");

    checkErrorCause(Toleration.class,
        "spec.workers.overrides[0].pods.scheduling.tolerations[0].effect",
        "isEffectValid", review, AssertTrue.class);
  }

  @Test
  void givenOverridesWorkersTolerationsSetAndEffectNoExecute_shouldPass() throws ValidationFailed {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setPodsForWorkers(new StackGresShardedClusterShardPods());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations().get(0)
        .setKey("test");
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations().get(0)
        .setTolerationSeconds(100L);
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations().get(0)
        .setEffect("NoExecute");

    validator.validate(review);
  }

  @Test
  void givenOverridesWorkersTolerationsSetAndEffectOtherThanNoExecute_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setPodsForWorkers(new StackGresShardedClusterShardPods());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .setScheduling(new StackGresClusterPodsScheduling());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().setTolerations(new ArrayList<>());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations()
        .add(new Toleration());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations().get(0)
        .setKey("test");
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations().get(0)
        .setTolerationSeconds(100L);
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getPodsForWorkers()
        .getScheduling().getTolerations().get(0)
        .setEffect(new Random().nextBoolean() ? "NoSchedule" : "PreferNoSchedule");

    checkErrorCause(Toleration.class,
        "spec.workers.overrides[0].pods.scheduling.tolerations[0].effect",
        "isEffectNoExecuteIfTolerationIsSet", review, AssertTrue.class);
  }

  @Test
  void givenOverridesWorkersInstancesPerClusterEqualsToSyncInstances_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .setInstancesPerCluster(3);
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setInstancesPerCluster(1);
    review.getRequest().getObject().getSpec().getReplication().setMode(
        StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getReplication().setSyncInstances(1);

    checkErrorCause(StackGresShardedClusterSpec.class,
        "spec.replication.syncInstances",
        "isSupportingRequiredSynchronousReplicas",
        review, AssertTrue.class);
  }

  @Test
  void givenOverridesWorkersInstancesPerClusterLessThanSyncInstances_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .setInstancesPerCluster(3);
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setInstancesPerCluster(1);
    review.getRequest().getObject().getSpec().getReplication().setMode(
        StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getReplication().setSyncInstances(2);

    checkErrorCause(StackGresShardedClusterSpec.class,
        "spec.replication.syncInstances",
        "isSupportingRequiredSynchronousReplicas",
        review, AssertTrue.class);
  }

  @Test
  void givenOverridesWorkersInstancesPerClusterEqualsToWorkersSyncInstances_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setInstancesPerCluster(1);
    review.getRequest().getObject().getSpec().getWorkers()
        .setReplicationForWorkers(new StackGresShardedClusterReplication());
    review.getRequest().getObject().getSpec().getWorkers()
        .getReplicationForWorkers()
        .setMode(StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getWorkers()
        .getReplicationForWorkers()
        .setSyncInstances(1);

    checkErrorCause(StackGresShardedClusterWorker.class,
        "spec.workers.replication.syncInstances",
        "isWorkersOverrideSupportingRequiredSynchronousReplicas",
        review, AssertTrue.class);
  }

  @Test
  void givenOverridesWorkersInstancesPerClusterEqualsToOverridesWorkersSyncInstances_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setInstancesPerCluster(1);
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0)
        .setReplicationForWorkers(new StackGresShardedClusterReplication());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getReplicationForWorkers()
        .setMode(StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getReplicationForWorkers()
        .setSyncInstances(1);

    checkErrorCause(StackGresShardedClusterWorker.class,
        "spec.workers.overrides[0].replication.syncInstances",
        "isWorkersOverrideSupportingRequiredSynchronousReplicas",
        review, AssertTrue.class);
  }

  @Test
  void givenOverridesWorkersInstancesPerClusterLessThanWorkersSyncInstances_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setInstancesPerCluster(1);
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0)
        .setReplicationForWorkers(new StackGresShardedClusterReplication());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getReplicationForWorkers()
        .setMode(StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).getReplicationForWorkers()
        .setSyncInstances(2);

    checkErrorCause(StackGresShardedClusterWorker.class,
        "spec.workers.overrides[0].replication.syncInstances",
        "isWorkersOverrideSupportingRequiredSynchronousReplicas",
        review, AssertTrue.class);
  }

  @Test
  void givenOverridesWorkersNullSyncInstances_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setInstancesPerCluster(2);
    review.getRequest().getObject().getSpec().getReplication().setMode(
        StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getReplication().setSyncInstances(null);

    checkErrorCause(StackGresShardedClusterReplication.class,
        "spec.replication.syncInstances",
        "isSyncInstancesSetForSyncMode",
        review, AssertTrue.class);
  }

  @Test
  void givenOverridesWorkersSyncInstancesLessThanOne_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getWorkers()
        .setOverrides(List.of(new StackGresShardedClusterWorker()));
    review.getRequest().getObject().getSpec().getWorkers()
        .getOverrides().get(0).setInstancesPerCluster(2);
    review.getRequest().getObject().getSpec().getReplication().setMode(
        StackGresReplicationMode.SYNC.toString());
    review.getRequest().getObject().getSpec().getReplication().setSyncInstances(0);

    checkErrorCause(StackGresClusterReplication.class,
        "spec.replication.syncInstances",
        review, Min.class, "must be greater than or equal to 1");
  }

  @Test
  void deprecatedSpecShards_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().setShards(
        review.getRequest().getObject().getSpec().getWorkers());

    checkErrorCause(StackGresShardedClusterSpec.class,
        "spec.shards",
        review, Null.class, "shards is deprecated use workers instead");
  }

  @Test
  void deprecatedPostgresServicesShards_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().getPostgresServices().setShards(
        new StackGresShardedClusterPostgresWorkersServices());

    checkErrorCause(StackGresShardedClusterPostgresServices.class,
        "spec.postgresServices.shards",
        review, Null.class, "shards is deprecated use workers instead");
  }

  @Test
  void deprecatedLabelsShardsPrimariesService_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().setMetadata(
        new StackGresShardedClusterSpecMetadata());
    review.getRequest().getObject().getSpec().getMetadata().setLabels(
        new StackGresShardedClusterSpecLabels());
    review.getRequest().getObject().getSpec().getMetadata().getLabels()
        .setShardsPrimariesService(Map.of("key", "value"));

    checkErrorCause(StackGresShardedClusterSpecLabels.class,
        "spec.metadata.labels.shardsPrimariesService",
        review, Null.class,
        "shardsPrimariesService is deprecated use workersPrimariesService instead");
  }

  @Test
  void deprecatedAnnotationsShardsPrimariesService_shouldFail() {
    StackGresShardedClusterReview review = getValidReview();
    review.getRequest().getObject().getSpec().setMetadata(
        new StackGresShardedClusterSpecMetadata());
    review.getRequest().getObject().getSpec().getMetadata().setAnnotations(
        new StackGresShardedClusterSpecAnnotations());
    review.getRequest().getObject().getSpec().getMetadata().getAnnotations()
        .setShardsPrimariesService(Map.of("key", "value"));

    checkErrorCause(StackGresShardedClusterSpecAnnotations.class,
        "spec.metadata.annotations.shardsPrimariesService",
        review, Null.class,
        "shardsPrimariesService is deprecated use workersPrimariesService instead");
  }

}
