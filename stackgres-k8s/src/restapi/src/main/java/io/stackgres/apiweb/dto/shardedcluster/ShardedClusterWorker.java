/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.dto.shardedcluster;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.apiweb.dto.cluster.ClusterSpec;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@JsonIgnoreProperties(value = { "instances", "postgres", "postgresServices",
    "initialData", "replicateFrom", "distributedLogs", "toInstallPostgresExtensions",
    "prometheusAutobind", "nonProductionOptions" })
public class ShardedClusterWorker extends ClusterSpec {

  private Integer index;

  private List<IntOrString> indexes;

  private String type;

  private Integer instancesPerCluster;

  @JsonProperty("replication")
  private ShardedClusterReplication replicationForShards;

  @JsonProperty("configurations")
  private ShardedClusterInnerConfigurations configurationsForShards;

  public Integer getIndex() {
    return index;
  }

  public void setIndex(Integer index) {
    this.index = index;
  }

  public List<IntOrString> getIndexes() {
    return indexes;
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

  public ShardedClusterReplication getReplicationForShards() {
    return replicationForShards;
  }

  public void setReplicationForShards(ShardedClusterReplication replicationForShards) {
    this.replicationForShards = replicationForShards;
  }

  public ShardedClusterInnerConfigurations getConfigurationsForShards() {
    return configurationsForShards;
  }

  public void setConfigurationsForShards(
      ShardedClusterInnerConfigurations configurationsForShards) {
    this.configurationsForShards = configurationsForShards;
  }

}
