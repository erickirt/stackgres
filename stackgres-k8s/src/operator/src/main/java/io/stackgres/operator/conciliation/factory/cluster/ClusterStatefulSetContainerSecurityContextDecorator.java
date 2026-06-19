/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.factory.cluster;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import io.stackgres.operator.conciliation.OperatorVersionBinder;
import io.stackgres.operator.conciliation.cluster.StackGresClusterContext;
import io.stackgres.operator.conciliation.factory.Decorator;
import jakarta.inject.Singleton;

/**
 * Set a hardened container security context (no privilege escalation, all
 * capabilities dropped and the runtime default seccomp profile) on every
 * container and init container of the cluster StatefulSet that does not
 * declare its own security context (e.g. the setup-io-limits init container
 * that requires to run as root with specific capabilities).
 */
@Singleton
@OperatorVersionBinder
public class ClusterStatefulSetContainerSecurityContextDecorator
    implements Decorator<StackGresClusterContext> {

  @Override
  public HasMetadata decorate(StackGresClusterContext context, HasMetadata resource) {
    if (resource instanceof StatefulSet statefulSet) {
      Optional.of(statefulSet)
          .map(StatefulSet::getSpec)
          .map(StatefulSetSpec::getTemplate)
          .map(PodTemplateSpec::getSpec)
          .ifPresent(this::setContainersSecurityContext);
    }
    return resource;
  }

  private void setContainersSecurityContext(PodSpec podSpec) {
    Optional.ofNullable(podSpec.getContainers())
        .stream()
        .flatMap(List::stream)
        .forEach(this::setContainerSecurityContext);
    Optional.ofNullable(podSpec.getInitContainers())
        .stream()
        .flatMap(List::stream)
        .forEach(this::setContainerSecurityContext);
  }

  private void setContainerSecurityContext(Container container) {
    if (container.getSecurityContext() != null) {
      return;
    }
    container.setSecurityContext(new SecurityContextBuilder()
        .withAllowPrivilegeEscalation(false)
        .withNewCapabilities()
        .withDrop("ALL")
        .endCapabilities()
        .withNewSeccompProfile()
        .withType("RuntimeDefault")
        .endSeccompProfile()
        .build());
  }

}
