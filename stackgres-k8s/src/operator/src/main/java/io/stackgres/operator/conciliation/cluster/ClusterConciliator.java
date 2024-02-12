/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.cluster;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.stackgres.common.PatroniUtil;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgcluster.StackGresClusterConfigurations;
import io.stackgres.common.crd.sgcluster.StackGresClusterPatroni;
import io.stackgres.common.crd.sgcluster.StackGresClusterPatroniConfig;
import io.stackgres.common.labels.LabelFactoryForCluster;
import io.stackgres.common.patroni.PatroniCtl;
import io.stackgres.common.patroni.PatroniCtlMember;
import io.stackgres.operator.conciliation.AbstractConciliator;
import io.stackgres.operator.conciliation.AbstractDeployedResourcesScanner;
import io.stackgres.operator.conciliation.DeployedResource;
import io.stackgres.operator.conciliation.DeployedResourcesCache;
import io.stackgres.operator.conciliation.RequiredResourceGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ClusterConciliator extends AbstractConciliator<StackGresCluster> {

  private final LabelFactoryForCluster<StackGresCluster> labelFactory;
  private final PatroniCtl patroniCtl;

  @Inject
  public ClusterConciliator(
      KubernetesClient client,
      RequiredResourceGenerator<StackGresCluster> requiredResourceGenerator,
      AbstractDeployedResourcesScanner<StackGresCluster> deployedResourcesScanner,
      DeployedResourcesCache deployedResourcesCache,
      LabelFactoryForCluster<StackGresCluster> labelFactory,
      PatroniCtl patroniCtl) {
    super(client, requiredResourceGenerator, deployedResourcesScanner, deployedResourcesCache);
    this.labelFactory = labelFactory;
    this.patroniCtl = patroniCtl;
  }

  @Override
  protected boolean skipDeletion(HasMetadata foundDeployedResource, StackGresCluster config) {
    if (foundDeployedResource instanceof Pod foundDeployedResourcePod
        && foundDeployedResourcePod.getMetadata().getName().startsWith(
            config.getMetadata().getName() + "-")) {
      return true;
    }
    return false;
  }

  @Override
  @SuppressFBWarnings(value = "SA_LOCAL_SELF_COMPARISON",
      justification = "False positive")
  protected boolean forceChange(HasMetadata requiredResource, StackGresCluster config) {
    if (requiredResource instanceof StatefulSet requiredStatefulSet
        && requiredStatefulSet.getMetadata().getName().equals(
            config.getMetadata().getName())) {
      Map<String, String> primaryLabels =
          labelFactory.clusterPrimaryLabelsWithoutUidAndScope(config);
      final boolean noPrimaryPod = deployedResourcesCache
          .stream()
          .map(DeployedResource::foundDeployed)
          .noneMatch(foundDeployedResource -> isPrimaryPod(foundDeployedResource, primaryLabels));
      if (noPrimaryPod && LOGGER.isDebugEnabled()) {
        LOGGER.debug("Will force StatefulSet reconciliation since no primary pod with labels {} was"
            + " found for SGCluster {}.{}",
            primaryLabels,
            config.getMetadata().getNamespace(),
            config.getMetadata().getName());
      }
      final boolean anyPodWithWrongOrMissingRole;
      if (!Optional.ofNullable(config.getSpec().getConfigurations())
          .map(StackGresClusterConfigurations::getPatroni)
          .map(StackGresClusterPatroni::getInitialConfig)
          .map(StackGresClusterPatroniConfig::isPatroniOnKubernetes)
          .orElse(true)) {
        var patroniCtl = this.patroniCtl.instanceFor(config);
        var members = patroniCtl.list();
        anyPodWithWrongOrMissingRole = deployedResourcesCache
            .stream()
            .map(DeployedResource::foundDeployed)
            .anyMatch(foundDeployedResource -> isPodWithWrongOrMissingRole(foundDeployedResource, members));
      } else {
        anyPodWithWrongOrMissingRole = false;
      }
      if (anyPodWithWrongOrMissingRole && LOGGER.isDebugEnabled()) {
        LOGGER.debug("Will force StatefulSet reconciliation since some pod with wrong or missing role label was"
            + " found for SGCluster {}.{}",
            config.getMetadata().getNamespace(),
            config.getMetadata().getName());
      }
      return noPrimaryPod || anyPodWithWrongOrMissingRole;
    }
    return false;
  }

  @SuppressFBWarnings(value = "SA_LOCAL_SELF_COMPARISON",
      justification = "False positive")
  private boolean isPrimaryPod(
      HasMetadata foundDeployedResource,
      Map<String, String> primaryLabels) {
    return foundDeployedResource instanceof Pod foundDeployedPod
        && Optional.of(foundDeployedPod.getMetadata())
        .map(ObjectMeta::getLabels)
        .filter(labels -> primaryLabels.entrySet().stream()
            .allMatch(primaryLabel -> labels.entrySet().stream()
                .anyMatch(primaryLabel::equals)))
        .isPresent();
  }

  @SuppressFBWarnings(value = "SA_LOCAL_SELF_COMPARISON",
      justification = "False positive")
  private boolean isPodWithWrongOrMissingRole(
      HasMetadata foundDeployedResource,
      List<PatroniCtlMember> members) {
    return foundDeployedResource instanceof Pod foundDeployedPod
        && !Optional.of(foundDeployedPod.getMetadata())
        .map(ObjectMeta::getLabels)
        .filter(labels -> Objects.equals(
            members.stream()
            .filter(member -> foundDeployedPod.getMetadata().getName().equals(member.getMember())
                && member.getLabelRole() != null)
            .map(PatroniCtlMember::getLabelRole)
            .findFirst()
            .orElse(null),
            labels.get(PatroniUtil.ROLE_KEY)))
        .isPresent();
  }

}
