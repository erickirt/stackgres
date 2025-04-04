/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.factory.cluster;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorRequirementBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodAffinityTermBuilder;
import io.fabric8.kubernetes.api.model.PodAntiAffinity;
import io.fabric8.kubernetes.api.model.PodAntiAffinityBuilder;
import io.fabric8.kubernetes.api.model.PodSecurityContext;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.TolerationBuilder;
import io.fabric8.kubernetes.api.model.TopologySpreadConstraintBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.stackgres.common.StackGresContainer;
import io.stackgres.common.StackGresContext;
import io.stackgres.common.StackGresInitContainer;
import io.stackgres.common.StackGresPort;
import io.stackgres.common.StackGresProperty;
import io.stackgres.common.StackGresVolume;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgcluster.StackGresClusterPods;
import io.stackgres.common.crd.sgcluster.StackGresClusterPodsScheduling;
import io.stackgres.common.crd.sgcluster.StackGresClusterSpec;
import io.stackgres.common.crd.sgconfig.StackGresConfigDeveloper;
import io.stackgres.common.crd.sgconfig.StackGresConfigDeveloperContainerPatches;
import io.stackgres.common.crd.sgconfig.StackGresConfigDeveloperPatches;
import io.stackgres.common.crd.sgconfig.StackGresConfigSpec;
import io.stackgres.common.labels.LabelFactoryForCluster;
import io.stackgres.operator.conciliation.InitContainerFactoryDiscoverer;
import io.stackgres.operator.conciliation.OperatorVersionBinder;
import io.stackgres.operator.conciliation.RunningContainerFactoryDiscoverer;
import io.stackgres.operator.conciliation.cluster.StackGresClusterContext;
import io.stackgres.operator.conciliation.factory.ContainerFactory;
import io.stackgres.operator.conciliation.factory.ImmutablePodTemplateResult;
import io.stackgres.operator.conciliation.factory.PodTemplateFactory;
import io.stackgres.operator.conciliation.factory.PodTemplateResult;
import io.stackgres.operator.conciliation.factory.ResourceFactory;
import io.stackgres.operator.conciliation.factory.cluster.patroni.PatroniRole;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

@Singleton
@OperatorVersionBinder
public class ClusterPodTemplateSpecFactory
    implements PodTemplateFactory<ClusterContainerContext> {

  private final ResourceFactory<StackGresClusterContext, PodSecurityContext> podSecurityContext;

  private final LabelFactoryForCluster labelFactory;

  private final RunningContainerFactoryDiscoverer<ClusterContainerContext>
      runningContainerFactoryDiscoverer;

  private final InitContainerFactoryDiscoverer<ClusterContainerContext>
      initContainerFactoryDiscoverer;

  @Inject
  public ClusterPodTemplateSpecFactory(
      ResourceFactory<StackGresClusterContext, PodSecurityContext> podSecurityContext,
      LabelFactoryForCluster labelFactory,
      RunningContainerFactoryDiscoverer<ClusterContainerContext>
          runningContainerFactoryDiscoverer,
      InitContainerFactoryDiscoverer<ClusterContainerContext>
          initContainerFactoryDiscoverer) {
    this.podSecurityContext = podSecurityContext;
    this.labelFactory = labelFactory;
    this.runningContainerFactoryDiscoverer = runningContainerFactoryDiscoverer;
    this.initContainerFactoryDiscoverer = initContainerFactoryDiscoverer;
  }

  @Override
  public PodTemplateResult getPodTemplateSpec(ClusterContainerContext context) {

    final List<ContainerFactory<ClusterContainerContext>> containerFactories =
        runningContainerFactoryDiscoverer.discoverContainers(context);

    final List<Container> containers = containerFactories.stream()
        .map(f -> f.getContainer(context)).toList();

    Map<String, String> componentVersions = containerFactories.stream()
        .map(f -> f.getComponentVersions(context))
        .map(Map::entrySet)
        .flatMap(Set::stream)
        .distinct()
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

    final List<ContainerFactory<ClusterContainerContext>> initContainerFactories =
        initContainerFactoryDiscoverer.discoverContainers(context);

    List<Container> initContainers = initContainerFactories
        .stream().map(f -> f.getContainer(context))
        .toList();

    final List<String> claimedVolumes = Stream.concat(containers.stream(), initContainers.stream())
        .flatMap(container -> container.getVolumeMounts().stream())
        .map(VolumeMount::getName)
        .filter(volumeName -> Seq.seq(
            Optional.of(context.getClusterContext().getConfig().getSpec())
            .map(StackGresConfigSpec::getDeveloper)
            .map(StackGresConfigDeveloper::getPatches)
            .map(StackGresConfigDeveloperPatches::getClusterController)
            .map(StackGresConfigDeveloperContainerPatches::getVolumes)
            .stream()
            .flatMap(List::stream)
            .map(Volume.class::cast))
            .grouped(volume -> volume.getName())
            .map(Tuple2::v1)
            .noneMatch(volumeName::equals))
        .distinct()
        .toList();

    claimedVolumes.forEach(rv -> {
      if (!context.availableVolumes().containsKey(rv) && !context.getDataVolumeName().equals(rv)) {
        throw new IllegalArgumentException("Volume " + rv + " is required but not available");
      }
    });

    List<Volume> volumes = claimedVolumes.stream()
        .map(volumeName -> context.availableVolumes().get(volumeName))
        .filter(Objects::nonNull)
        .toList();

    StackGresCluster cluster = context.getClusterContext().getSource();
    final Map<String, String> podLabels = labelFactory.statefulSetPodLabels(cluster);
    final Map<String, String> customPodLabels = context.getClusterContext()
        .clusterPodsCustomLabels();

    final boolean isEnabledClusterPodAntiAffinity =
        !context.getClusterContext().calculateDisableClusterPodAntiAffinity();

    var podTemplate = new PodTemplateSpecBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .addToLabels(customPodLabels)
            .addToLabels(podLabels)
            .addToAnnotations(StackGresContext.VERSION_KEY,
                StackGresProperty.OPERATOR_VERSION.getString())
            .addToAnnotations(componentVersions)
            .build())
        .withNewSpec()
        .withShareProcessNamespace(Boolean.TRUE)
        .withServiceAccountName(PatroniRole.roleName(context.getClusterContext()))
        .withSecurityContext(podSecurityContext.createResource(context.getClusterContext()))
        .withVolumes(volumes)
        .withContainers(containers)
        .withInitContainers(initContainers)
        .withTerminationGracePeriodSeconds(
            Optional.ofNullable(cluster.getSpec().getPods().getTerminationGracePeriodSeconds())
            .orElse(60L))
        .withShareProcessNamespace(Boolean.TRUE)
        .withServiceAccountName(PatroniRole.roleName(context.getClusterContext()))
        .withSecurityContext(podSecurityContext.createResource(context.getClusterContext()))
        .withNodeSelector(Optional.ofNullable(cluster.getSpec())
            .map(StackGresClusterSpec::getPods)
            .map(StackGresClusterPods::getScheduling)
            .map(StackGresClusterPodsScheduling::getNodeSelector)
            .orElse(null))
        .withTolerations(Optional.ofNullable(cluster.getSpec())
            .map(StackGresClusterSpec::getPods)
            .map(StackGresClusterPods::getScheduling)
            .map(StackGresClusterPodsScheduling::getTolerations)
            .map(tolerations -> Seq.seq(tolerations)
                .map(TolerationBuilder::new)
                .map(TolerationBuilder::build)
                .toList())
            .orElse(null))
        .withAffinity(new AffinityBuilder()
            .withNodeAffinity(Optional.ofNullable(cluster.getSpec())
                .map(StackGresClusterSpec::getPods)
                .map(StackGresClusterPods::getScheduling)
                .map(StackGresClusterPodsScheduling::getNodeAffinity)
                .orElse(null))
            .withPodAffinity(Optional.ofNullable(cluster.getSpec())
                .map(StackGresClusterSpec::getPods)
                .map(StackGresClusterPods::getScheduling)
                .map(StackGresClusterPodsScheduling::getPodAffinity)
                .orElse(null))
            .withPodAntiAffinity(Optional.ofNullable(cluster.getSpec())
                .map(StackGresClusterSpec::getPods)
                .map(StackGresClusterPods::getScheduling)
                .map(StackGresClusterPodsScheduling::getPodAntiAffinity)
                .map(PodAntiAffinityBuilder::new)
                .orElseGet(PodAntiAffinityBuilder::new)
                .addAllToRequiredDuringSchedulingIgnoredDuringExecution(Seq.of(
                    new PodAffinityTermBuilder()
                        .withLabelSelector(new LabelSelectorBuilder()
                            .withMatchExpressions(new LabelSelectorRequirementBuilder()
                                .withKey(labelFactory.labelMapper().appKey())
                                .withOperator("In")
                                .withValues(labelFactory.labelMapper().appName())
                                .build(),
                                new LabelSelectorRequirementBuilder()
                                    .withKey(labelFactory.labelMapper().clusterKey(cluster))
                                    .withOperator("In")
                                    .withValues(StackGresContext.RIGHT_VALUE)
                                    .build())
                            .build())
                        .withTopologyKey("kubernetes.io/hostname")
                        .build())
                    .filter(affinity -> isEnabledClusterPodAntiAffinity)
                    .append(Optional.ofNullable(cluster.getSpec())
                        .map(StackGresClusterSpec::getPods)
                        .map(StackGresClusterPods::getScheduling)
                        .map(StackGresClusterPodsScheduling::getPodAntiAffinity)
                        .map(PodAntiAffinity::getRequiredDuringSchedulingIgnoredDuringExecution)
                        .stream()
                        .flatMap(List::stream))
                    .toList())
                .build())
            .build())
        .withPriorityClassName(Optional.ofNullable(cluster.getSpec())
                        .map(StackGresClusterSpec::getPods)
                        .map(StackGresClusterPods::getScheduling)
                        .map(StackGresClusterPodsScheduling::getPriorityClassName)
                        .orElse(null))
        .withTopologySpreadConstraints(Optional.ofNullable(cluster.getSpec())
            .map(StackGresClusterSpec::getPods)
            .map(StackGresClusterPods::getScheduling)
            .map(StackGresClusterPodsScheduling::getTopologySpreadConstraints)
            .map(topologySpreadConstraints -> Seq.seq(topologySpreadConstraints)
                .map(TopologySpreadConstraintBuilder::new)
                .map(TopologySpreadConstraintBuilder::build)
                .toList())
            .orElse(null))
        .withContainers(containers)
        .addAllToContainers(Optional.of(cluster)
            .map(StackGresCluster::getSpec)
            .map(StackGresClusterSpec::getPods)
            .map(StackGresClusterPods::getCustomContainers)
            .stream()
            .flatMap(List::stream)
            .map(ContainerBuilder::new)
            .map(builder -> builder.withName(
                StackGresContainer.CUSTOM.formatted(builder.getName())))
            .map(builder -> builder.withPorts(
                Optional.ofNullable(builder.buildPorts())
                .stream()
                .flatMap(List::stream)
                .map(ContainerPortBuilder::new)
                .map(containerPortBuilder -> containerPortBuilder.withName(
                    StackGresPort.CUSTOM.getName(containerPortBuilder.getName())))
                .map(containerPortBuilder -> containerPortBuilder.getProtocol() == null
                    ? containerPortBuilder.withProtocol("TCP") : containerPortBuilder)
                .map(ContainerPortBuilder::build)
                .toList()))
            .map(ContainerBuilder::build)
            .toList())
        .withInitContainers(initContainers)
        .addAllToInitContainers(Optional.of(cluster)
            .map(StackGresCluster::getSpec)
            .map(StackGresClusterSpec::getPods)
            .map(StackGresClusterPods::getCustomInitContainers)
            .stream()
            .flatMap(List::stream)
            .map(ContainerBuilder::new)
            .map(builder -> builder.withName(
                StackGresInitContainer.CUSTOM.formatted(builder.getName())))
            .map(builder -> builder.withPorts(
                Optional.ofNullable(builder.buildPorts())
                .stream()
                .flatMap(List::stream)
                .map(ContainerPortBuilder::new)
                .map(containerPortBuilder -> containerPortBuilder.withName(
                    StackGresPort.CUSTOM.getName(containerPortBuilder.getName())))
                .map(ContainerPortBuilder::build)
                .toList()))
            .map(ContainerBuilder::build)
            .toList())
        .withVolumes(volumes)
        .addAllToVolumes(Optional.of(cluster)
            .map(StackGresCluster::getSpec)
            .map(StackGresClusterSpec::getPods)
            .map(StackGresClusterPods::getCustomVolumes)
            .stream()
            .flatMap(List::stream)
            .map(VolumeBuilder::new)
            .map(builder -> builder.withName(StackGresVolume.CUSTOM.getName(builder.getName())))
            .map(VolumeBuilder::build)
            .toList())
        .addAllToVolumes(Seq.seq(
            Optional.of(context.getClusterContext().getConfig().getSpec())
            .map(StackGresConfigSpec::getDeveloper)
            .map(StackGresConfigDeveloper::getPatches)
            .map(StackGresConfigDeveloperPatches::getClusterController)
            .map(StackGresConfigDeveloperContainerPatches::getVolumes)
            .stream()
            .flatMap(List::stream)
            .map(Volume.class::cast))
            .grouped(volume -> volume.getName())
            .flatMap(t -> t.v2.limit(1))
            .toList())
        .endSpec()
        .build();

    return ImmutablePodTemplateResult.builder()
        .spec(podTemplate)
        .claimedVolumes(claimedVolumes)
        .build();
  }

}
