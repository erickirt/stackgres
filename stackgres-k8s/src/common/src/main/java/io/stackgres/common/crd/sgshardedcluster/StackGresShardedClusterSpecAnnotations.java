/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgshardedcluster;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.common.StackGresUtil;
import io.stackgres.common.crd.sgcluster.StackGresClusterSpecAnnotations;
import io.sundr.builder.annotations.Buildable;
import jakarta.validation.constraints.Null;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown = true)
@Buildable(editableEnabled = false, generateBuilderPackage = false,
    lazyCollectionInitEnabled = false, lazyMapInitEnabled = false,
    builderPackage = "io.fabric8.kubernetes.api.builder")
public class StackGresShardedClusterSpecAnnotations extends StackGresClusterSpecAnnotations {

  private Map<String, String> coordinatorPrimaryService;

  private Map<String, String> coordinatorAnyService;

  private Map<String, String> queryRoutersPrimariesService;

  private Map<String, String> workersPrimariesService;

  @Null(message = "shardsPrimariesService is deprecated use workersPrimariesService instead")
  private Map<String, String> shardsPrimariesService;

  public Map<String, String> getCoordinatorPrimaryService() {
    return coordinatorPrimaryService;
  }

  public void setCoordinatorPrimaryService(Map<String, String> coordinatorPrimaryService) {
    this.coordinatorPrimaryService = coordinatorPrimaryService;
  }

  public Map<String, String> getCoordinatorAnyService() {
    return coordinatorAnyService;
  }

  public void setCoordinatorAnyService(Map<String, String> coordinatorAnyService) {
    this.coordinatorAnyService = coordinatorAnyService;
  }

  public Map<String, String> getQueryRoutersPrimariesService() {
    return queryRoutersPrimariesService;
  }

  public void setQueryRoutersPrimariesService(Map<String, String> queryRoutersPrimariesService) {
    this.queryRoutersPrimariesService = queryRoutersPrimariesService;
  }

  public Map<String, String> getWorkersPrimariesService() {
    return workersPrimariesService;
  }

  public void setWorkersPrimariesService(Map<String, String> workersPrimariesService) {
    this.workersPrimariesService = workersPrimariesService;
  }

  @Deprecated
  public Map<String, String> getShardsPrimariesService() {
    return shardsPrimariesService;
  }

  @Deprecated
  public void setShardsPrimariesService(Map<String, String> shardsPrimariesService) {
    this.shardsPrimariesService = shardsPrimariesService;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(coordinatorAnyService, coordinatorPrimaryService,
        queryRoutersPrimariesService, shardsPrimariesService, workersPrimariesService);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (!(obj instanceof StackGresShardedClusterSpecAnnotations)) {
      return false;
    }
    StackGresShardedClusterSpecAnnotations other = (StackGresShardedClusterSpecAnnotations) obj;
    return Objects.equals(coordinatorAnyService, other.coordinatorAnyService)
        && Objects.equals(coordinatorPrimaryService, other.coordinatorPrimaryService)
        && Objects.equals(queryRoutersPrimariesService, other.queryRoutersPrimariesService)
        && Objects.equals(shardsPrimariesService, other.shardsPrimariesService)
        && Objects.equals(workersPrimariesService, other.workersPrimariesService);
  }

  @Override
  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }
}
