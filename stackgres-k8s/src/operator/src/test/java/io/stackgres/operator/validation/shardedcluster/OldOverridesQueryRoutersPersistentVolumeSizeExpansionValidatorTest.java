/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.shardedcluster;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterWorkerBuilder;
import io.stackgres.common.crd.sgshardedcluster.StackGresWorkerType;
import io.stackgres.common.labels.LabelFactoryForShardedCluster;
import io.stackgres.common.resource.CustomResourceScanner;
import io.stackgres.operator.common.StackGresShardedClusterReview;
import io.stackgres.operator.common.fixture.AdmissionReviewFixtures;
import io.stackgres.operator.conciliation.factory.shardedcluster.StackGresShardedClusterForCitusUtil;
import io.stackgres.operator.validation.PersistentVolumeSizeExpansionValidatorTest;
import io.stackgres.operatorframework.admissionwebhook.validating.Validator;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OldOverridesQueryRoutersPersistentVolumeSizeExpansionValidatorTest extends
    PersistentVolumeSizeExpansionValidatorTest<StackGresShardedClusterReview,
        StackGresShardedCluster, StackGresCluster> {

  @Mock
  protected CustomResourceScanner<StackGresCluster> clusterScanner;

  @Mock
  protected LabelFactoryForShardedCluster shardedClusterLabelFactory;

  private StackGresShardedClusterReview review;

  @Override
  protected StackGresShardedClusterReview getAdmissionReview() {
    review = AdmissionReviewFixtures.shardedCluster().loadUpdate().get();
    review.getRequest().getOldObject().getSpec().getWorkers().setOverrides(List.of(
        new StackGresShardedClusterWorkerBuilder()
        .withIndex(0)
        .withType(StackGresWorkerType.QUERY_ROUTER.toString())
        .withNewPodsForWorkers()
        .withNewPersistentVolume()
        .endPersistentVolume()
        .endPodsForWorkers()
        .build()));
    return review;
  }

  @Override
  protected Validator<StackGresShardedClusterReview> getValidator() {
    return new OverridesQueryRoutersPersistentVolumeSizeExpansionValidator(
        finder, clusterScanner, shardedClusterLabelFactory, pvcScanner, labelFactory);
  }

  @Override
  protected StackGresCluster getCluster(StackGresShardedCluster resource) {
    return StackGresShardedClusterForCitusUtil.getQueryRouterCluster(resource, 0, Optional.empty());
  }

  @Override
  protected void setVolumeSize(StackGresShardedCluster cluster, String size) {
    if (cluster == review.getRequest().getObject()) {
      cluster.getSpec().getCoordinator()
          .getPods().getPersistentVolume().setSize(size);
      return;
    }
    cluster.getSpec().getWorkers().getOverrides().get(0)
        .getPodsForWorkers().getPersistentVolume().setSize(size);
  }

  @Override
  protected void setStorageClassName(StackGresShardedCluster cluster, String storageClassName) {
    if (cluster == review.getRequest().getObject()) {
      cluster.getSpec().getCoordinator()
          .getPods().getPersistentVolume().setStorageClass(storageClassName);
      return;
    }
    cluster.getSpec().getWorkers().getOverrides().get(0)
        .getPodsForWorkers().getPersistentVolume().setStorageClass(storageClassName);
  }

  @Override
  protected String getStorageClassName(StackGresShardedCluster cluster) {
    if (cluster == review.getRequest().getObject()) {
      return cluster.getSpec().getCoordinator()
          .getPods().getPersistentVolume().getStorageClass();
    }
    return cluster.getSpec().getWorkers().getOverrides().get(0)
        .getPodsForWorkers().getPersistentVolume().getStorageClass();
  }

  @Override
  protected void configureEmptyPvcScanner(StackGresShardedCluster resource) {
    StackGresCluster cluster = getCluster(resource);
    when(clusterScanner.getResourcesWithLabels(any(), any()))
        .thenReturn(List.of(cluster));
    super.configureEmptyPvcScanner(resource);
  }

  @Override
  protected void configurePvcScanner(StackGresShardedCluster resource, String storageClassName,
      Map<String, String> clusterLabels, String clusterNamespace) {
    StackGresCluster cluster = getCluster(resource);
    when(clusterScanner.getResourcesWithLabels(any(), any()))
        .thenReturn(List.of(cluster));
    super.configurePvcScanner(resource, storageClassName, clusterLabels, clusterNamespace);
  }

  @Override
  protected void configureMixedPvcScanner(
      StackGresShardedCluster resource,
      Map<String, String> clusterLabels, String clusterNamespace,
      String expandableStorageClassName, String nonExpandableStorageClassName) {
    StackGresCluster cluster = getCluster(resource);
    when(clusterScanner.getResourcesWithLabels(any(), any()))
        .thenReturn(List.of(cluster));
    super.configureMixedPvcScanner(resource,
        clusterLabels, clusterNamespace, expandableStorageClassName,
        nonExpandableStorageClassName);
  }

}
