/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgcluster;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.common.StackGresUtil;
import io.stackgres.common.StackGresVersion;
import io.stackgres.common.StackGresVersion.DeprecatedVersionPlaceholder;
import io.sundr.builder.annotations.Buildable;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown = true)
@Buildable(editableEnabled = false, validationEnabled = false, generateBuilderPackage = false,
    lazyCollectionInitEnabled = false, lazyMapInitEnabled = false,
    builderPackage = "io.fabric8.kubernetes.api.builder")
public class StackGresClusterSpecAnnotations {

  private Map<String, String> allResources;

  private Map<String, String> clusterPods;

  @DeprecatedVersionPlaceholder(StackGresVersion.V_1_15)
  private Map<String, String> pods;

  private Map<String, String> services;

  private Map<String, String> primaryService;

  private Map<String, String> replicasService;

  public Map<String, String> getAllResources() {
    return allResources;
  }

  public void setAllResources(Map<String, String> allResources) {
    this.allResources = allResources;
  }

  public Map<String, String> getClusterPods() {
    return clusterPods;
  }

  public void setClusterPods(Map<String, String> clusterPods) {
    this.clusterPods = clusterPods;
  }

  public Map<String, String> getPods() {
    return pods;
  }

  public void setPods(Map<String, String> pods) {
    this.pods = pods;
  }

  public Map<String, String> getServices() {
    return services;
  }

  public void setServices(Map<String, String> services) {
    this.services = services;
  }

  public Map<String, String> getPrimaryService() {
    return primaryService;
  }

  public void setPrimaryService(Map<String, String> primaryService) {
    this.primaryService = primaryService;
  }

  public Map<String, String> getReplicasService() {
    return replicasService;
  }

  public void setReplicasService(Map<String, String> replicasService) {
    this.replicasService = replicasService;
  }

  @Override
  public int hashCode() {
    return Objects.hash(allResources, clusterPods, pods, primaryService, replicasService, services);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof StackGresClusterSpecAnnotations)) {
      return false;
    }
    StackGresClusterSpecAnnotations other = (StackGresClusterSpecAnnotations) obj;
    return Objects.equals(allResources, other.allResources)
        && Objects.equals(clusterPods, other.clusterPods) && Objects.equals(pods, other.pods)
        && Objects.equals(primaryService, other.primaryService)
        && Objects.equals(replicasService, other.replicasService)
        && Objects.equals(services, other.services);
  }

  @Override
  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }
}
