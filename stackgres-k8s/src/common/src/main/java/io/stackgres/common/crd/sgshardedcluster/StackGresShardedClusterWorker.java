/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgshardedcluster;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.common.crd.sgcluster.StackGresClusterSpec;
import io.stackgres.common.validation.FieldReference;
import io.stackgres.common.validation.FieldReference.ReferencedField;
import io.sundr.builder.annotations.Buildable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown = true,
    value = { "instances", "postgres", "postgresServices",
        "initialData", "replicateFrom", "distributedLogs", "toInstallPostgresExtensions",
        "prometheusAutobind", "nonProductionOptions" })
@Buildable(editableEnabled = false, validationEnabled = false, generateBuilderPackage = false,
    lazyCollectionInitEnabled = false, lazyMapInitEnabled = false,
    builderPackage = "io.fabric8.kubernetes.api.builder")
public class StackGresShardedClusterWorker extends StackGresClusterSpec {

  @PositiveOrZero(message = "You need a shard index starting from zero")
  private Integer index;

  @PositiveOrZero(message = "instances can not be negative")
  private Integer instancesPerCluster;

  @JsonProperty("replication")
  @Valid
  private StackGresShardedClusterReplication replicationForWorkers;

  @JsonProperty("configurations")
  @Valid
  private StackGresShardedClusterWorkerConfigurations configurationsForWorkers;

  @JsonProperty("pods")
  @Valid
  private StackGresShardedClusterShardPods podsForWorkers;

  @ReferencedField("replication.syncInstances")
  interface SyncInstances extends FieldReference { }

  @Override
  public boolean isPosgresSectionPresent() {
    return true;
  }

  @Override
  public boolean isPostgresServicesPresent() {
    return true;
  }

  @Override
  public boolean isResourceProfilePresent() {
    return true;
  }

  @Override
  public boolean isConfigurationsSectionPresent() {
    return true;
  }

  @Override
  public boolean isPodsSectionPresent() {
    return true;
  }

  @Override
  public boolean isInstancesPositive() {
    return true;
  }

  @Override
  public boolean isReplicationSectionPresent() {
    return true;
  }

  @Override
  public boolean isSupportingInstancesForInstancesInReplicationGroups() {
    return true;
  }

  @Override
  public boolean isSupportingMinInstancesForMinInstancesInReplicationGroups() {
    return true;
  }

  @Override
  public boolean isSupportingRequiredSynchronousReplicas() {
    return true;
  }

  @JsonIgnore
  @AssertTrue(message = "The total number synchronous replicas must be less or equals than the"
      + " number of coordinator or any shard replicas",
      payload = { SyncInstances.class })
  public boolean isWorkersOverrideSupportingRequiredSynchronousReplicas() {
    return replicationForWorkers == null
        || !replicationForWorkers.isSynchronousMode()
        || replicationForWorkers.getSyncInstances() == null
        || getInstancesPerCluster() > replicationForWorkers.getSyncInstances();
  }

  public Integer getIndex() {
    return index;
  }

  public void setIndex(Integer index) {
    this.index = index;
  }

  public Integer getInstancesPerCluster() {
    return instancesPerCluster;
  }

  public void setInstancesPerCluster(Integer instancesPerCluster) {
    this.instancesPerCluster = instancesPerCluster;
  }

  public StackGresShardedClusterReplication getReplicationForWorkers() {
    return replicationForWorkers;
  }

  public void setReplicationForWorkers(StackGresShardedClusterReplication replicationForWorkers) {
    this.replicationForWorkers = replicationForWorkers;
  }

  public StackGresShardedClusterWorkerConfigurations getConfigurationsForWorkers() {
    return configurationsForWorkers;
  }

  public void setConfigurationsForWorkers(
      StackGresShardedClusterWorkerConfigurations configurationsForWorkers) {
    this.configurationsForWorkers = configurationsForWorkers;
  }

  public StackGresShardedClusterShardPods getPodsForWorkers() {
    return podsForWorkers;
  }

  public void setPodsForWorkers(StackGresShardedClusterShardPods podsForWorkers) {
    this.podsForWorkers = podsForWorkers;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result
        + Objects.hash(configurationsForWorkers, index, instancesPerCluster, podsForWorkers,
            replicationForWorkers);
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
    if (!(obj instanceof StackGresShardedClusterWorker)) {
      return false;
    }
    StackGresShardedClusterWorker other = (StackGresShardedClusterWorker) obj;
    return Objects.equals(configurationsForWorkers, other.configurationsForWorkers)
        && Objects.equals(index, other.index)
        && Objects.equals(instancesPerCluster, other.instancesPerCluster)
        && Objects.equals(podsForWorkers, other.podsForWorkers)
        && Objects.equals(replicationForWorkers, other.replicationForWorkers);
  }

}
