/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgshardedcluster;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.common.crd.sgcluster.StackGresClusterSpec;
import io.stackgres.common.validation.FieldReference;
import io.stackgres.common.validation.FieldReference.ReferencedField;
import io.stackgres.common.validation.ValidEnum;
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

  private List<IntOrString> indexes;

  @ValidEnum(enumClass = StackGresWorkerType.class, allowNulls = true,
      message = "type can be Worker or QueryRouter")
  private String type;

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

  @ReferencedField("index")
  interface Index extends FieldReference { }

  @ReferencedField("indexes")
  interface Indexes extends FieldReference { }

  @ReferencedField("replication.syncInstances")
  interface SyncInstances extends FieldReference { }

  @JsonIgnore
  @AssertTrue(message = "Fields index and indexes are mutually exclusive",
      payload = { Index.class, Indexes.class })
  public boolean areIndexAndIndexesMutuallyExclusive() {
    return index == null || indexes == null;
  }

  @JsonIgnore
  @AssertTrue(message = "Fields index or indexes are required",
      payload = { Index.class, Indexes.class })
  public boolean areIndexAndIndexesRequired() {
    return index != null || indexes != null;
  }

  @JsonIgnore
  @AssertTrue(message = "Elements of indexes must be integer, a string with"
      + " format `[0-9]+-[0-9]+` or the string `all`",
      payload = { Indexes.class })
  public boolean areIndexesElementValid() {
    return indexes == null
        || indexes.stream().allMatch(element -> element.getStrVal() == null
            || element.getStrVal().matches("[0-9]+-[0-9]+")
            || element.getStrVal().equals("all"));
  }

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

  public List<IntOrString> getIndexes() {
    return indexes;
  }

  @JsonIgnore
  public List<Integer> getPlainIndexes(StackGresShardedClusterSpec spec) {
    if (index != null) {
      return List.of(index);
    }
    if (indexes != null) {
      return indexes.stream()
          .flatMap(entry -> {
            Integer index = entry.getIntVal();
            if (index != null) {
              return Stream.of(index);
            }
            String indexRange = entry.getStrVal();
            if (indexRange != null
                && indexRange.matches("[0-9]+-[0-9]+")) {
              int dashIndex = indexRange.indexOf("-");
              return IntStream
                  .rangeClosed(
                      Integer.parseInt(indexRange.substring(0, dashIndex)),
                      Integer.parseInt(indexRange.substring(dashIndex + 1)))
                  .mapToObj(i -> i);
            }
            if (indexRange != null
                && indexRange.equals("all")
                && Optional.of(spec)
                .map(StackGresShardedClusterSpec::getCoordinator)
                .map(StackGresShardedClusterCoordinator::getQueryRouterClusters)
                .orElse(0) > 0) {
              return IntStream
                  .rangeClosed(
                      Integer.valueOf(0),
                      Integer.valueOf(spec.getCoordinator().getQueryRouterClusters() - 1))
                  .mapToObj(i -> i);
            }
            return Stream.of();
          })
          .toList();
    }
    return List.of();
  }

  public void setIndexes(List<IntOrString> indexes) {
    this.indexes = indexes;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
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
    result = prime * result + Objects.hash(configurationsForWorkers, index, indexes,
        instancesPerCluster, podsForWorkers, replicationForWorkers, type);
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
        && Objects.equals(index, other.index) && Objects.equals(indexes, other.indexes)
        && Objects.equals(instancesPerCluster, other.instancesPerCluster)
        && Objects.equals(podsForWorkers, other.podsForWorkers)
        && Objects.equals(replicationForWorkers, other.replicationForWorkers)
        && Objects.equals(type, other.type);
  }

}
