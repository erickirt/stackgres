/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.shardedcluster;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.stackgres.common.ErrorType;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgcluster.StackGresClusterPods;
import io.stackgres.common.crd.sgcluster.StackGresClusterPodsPersistentVolume;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterShardPods;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterSpec;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedClusterWorker;
import io.stackgres.common.labels.LabelFactoryForCluster;
import io.stackgres.common.labels.LabelFactoryForShardedCluster;
import io.stackgres.common.resource.CustomResourceScanner;
import io.stackgres.common.resource.ResourceFinder;
import io.stackgres.common.resource.ResourceScanner;
import io.stackgres.operator.common.StackGresShardedClusterReview;
import io.stackgres.operator.validation.PersistentVolumeSizeExpansionValidator;
import io.stackgres.operator.validation.ValidationType;
import io.stackgres.operatorframework.admissionwebhook.Operation;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationFailed;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

@Singleton
@ValidationType(ErrorType.FORBIDDEN_CLUSTER_UPDATE)
public class OverridesQueryRoutersPersistentVolumeSizeExpansionValidator
    implements ShardedClusterValidator {

  private final ResourceFinder<StorageClass> finder;

  private final CustomResourceScanner<StackGresCluster> clusterScanner;

  private final ResourceScanner<PersistentVolumeClaim> pvcScanner;

  private final LabelFactoryForShardedCluster labelFactory;

  private final LabelFactoryForCluster clusterLabelFactory;

  @Inject
  public OverridesQueryRoutersPersistentVolumeSizeExpansionValidator(
      ResourceFinder<StorageClass> finder,
      CustomResourceScanner<StackGresCluster> clusterScanner,
      LabelFactoryForShardedCluster labelFactory,
      ResourceScanner<PersistentVolumeClaim> pvcScanner,
      LabelFactoryForCluster clusterLabelFactory) {
    this.finder = finder;
    this.clusterScanner = clusterScanner;
    this.labelFactory = labelFactory;
    this.pvcScanner = pvcScanner;
    this.clusterLabelFactory = clusterLabelFactory;
  }

  @Override
  public void validate(StackGresShardedClusterReview review) throws ValidationFailed {
    if (review.getRequest().getOperation() != Operation.UPDATE) {
      return;
    }

    for (var overrideShardIndex : Seq.seq(
        review.getRequest().getObject().getSpec().getQueryRoutersOverrides())
        .append(review.getRequest().getOldObject().getSpec().getQueryRoutersOverrides())
        .filter(overrideShard -> overrideShard.getPodsForWorkers() != null
            && overrideShard.getPodsForWorkers().getPersistentVolume() != null
            && overrideShard.getPodsForWorkers().getPersistentVolume().getSize() != null)
        .grouped(StackGresShardedClusterWorker::getIndex)
        .map(Tuple2::v1)
        .toList()) {
      new OverrideWorkerPersistentVolumeSizeExpansionValidator(overrideShardIndex)
          .validate(review);
    }
  }

  @ValidationType(ErrorType.FORBIDDEN_CLUSTER_UPDATE)
  class OverrideWorkerPersistentVolumeSizeExpansionValidator
      extends PersistentVolumeSizeExpansionValidator<StackGresShardedClusterReview, StackGresShardedCluster>
      implements ShardedClusterValidator {
    final Integer index;

    public OverrideWorkerPersistentVolumeSizeExpansionValidator(Integer index) {
      this.index = index;
    }

    @Override
    protected @NotNull String getVolumeSize(StackGresShardedCluster cluster) {
      return Optional.of(cluster)
          .map(StackGresShardedCluster::getSpec)
          .map(StackGresShardedClusterSpec::getQueryRoutersOverrides)
          .stream()
          .flatMap(List::stream)
          .filter(overrideShard -> Objects.equals(
              overrideShard.getIndex(),
              index))
          .findFirst()
          .map(StackGresShardedClusterWorker::getPodsForWorkers)
          .map(StackGresShardedClusterShardPods::getPersistentVolume)
          .map(StackGresClusterPodsPersistentVolume::getSize)
          .orElse(cluster.getSpec().getCoordinator().getPods().getPersistentVolume().getSize());
    }

    @Override
    protected Optional<String> getStorageClass(StackGresShardedCluster cluster) {
      return Optional.of(cluster)
          .map(StackGresShardedCluster::getSpec)
          .map(StackGresShardedClusterSpec::getQueryRoutersOverrides)
          .stream()
          .flatMap(List::stream)
          .filter(overrideShard -> Objects.equals(
              overrideShard.getIndex(),
              index))
          .flatMap(override -> Optional.of(override)
              .map(StackGresShardedClusterWorker::getPodsForWorkers)
              .map(StackGresClusterPods::getPersistentVolume)
              .stream())
          .findFirst()
          .or(() -> Optional.of(cluster.getSpec().getCoordinator().getPods().getPersistentVolume()))
          .map(StackGresClusterPodsPersistentVolume::getStorageClass);
    }

    @Override
    protected void throwValidationError(String message) throws ValidationFailed {
      fail(message);
    }

    @Override
    protected ResourceFinder<StorageClass> getStorageClassFinder() {
      return finder;
    }

    @Override
    protected LabelFactoryForCluster getLabelFactory() {
      return clusterLabelFactory;
    }

    @Override
    protected List<StackGresCluster> getClusters(StackGresShardedCluster resource) {
      return clusterScanner.getResourcesWithLabels(
          resource.getMetadata().getNamespace(), labelFactory.queryRoutersLabels(resource));
    }

    @Override
    protected ResourceScanner<PersistentVolumeClaim> getPvcScanner() {
      return pvcScanner;
    }
  }
}
