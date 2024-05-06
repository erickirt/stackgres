/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd.sgconfig;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.common.StackGresUtil;
import io.sundr.builder.annotations.Buildable;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown = true)
@Buildable(editableEnabled = false, validationEnabled = false, generateBuilderPackage = false,
    lazyCollectionInitEnabled = false, lazyMapInitEnabled = false,
    builderPackage = "io.fabric8.kubernetes.api.builder")
public class StackGresConfigSpec {

  private String containerRegistry;

  private String imagePullPolicy;

  private List<String> allowedNamespaces;

  private Map<String, String> allowedNamespaceLabelSelector;

  private Boolean disableClusterRole;

  private StackGresConfigOptionalServiceAccount serviceAccount;

  private StackGresConfigOperator operator;

  private StackGresConfigRestapi restapi;

  private StackGresConfigAdminui adminui;

  private StackGresConfigJobs jobs;

  private StackGresConfigDeploy deploy;

  private StackGresConfigCert cert;

  private StackGresConfigAuthentication authentication;

  private StackGresConfigPrometheus prometheus;

  private StackGresConfigGrafana grafana;

  private StackGresConfigExtensions extensions;

  private StackGresConfigDeveloper developer;

  private StackGresConfigShardingSphere shardingSphere;

  public String getContainerRegistry() {
    return containerRegistry;
  }

  public void setContainerRegistry(String containerRegistry) {
    this.containerRegistry = containerRegistry;
  }

  public String getImagePullPolicy() {
    return imagePullPolicy;
  }

  public void setImagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }

  public List<String> getAllowedNamespaces() {
    return allowedNamespaces;
  }

  public void setAllowedNamespaces(List<String> allowedNamespaces) {
    this.allowedNamespaces = allowedNamespaces;
  }

  public Map<String, String> getAllowedNamespaceLabelSelector() {
    return allowedNamespaceLabelSelector;
  }

  public void setAllowedNamespaceLabelSelector(Map<String, String> allowedNamespaceLabelSelector) {
    this.allowedNamespaceLabelSelector = allowedNamespaceLabelSelector;
  }

  public Boolean getDisableClusterRole() {
    return disableClusterRole;
  }

  public void setDisableClusterRole(Boolean disableClusterRole) {
    this.disableClusterRole = disableClusterRole;
  }

  public StackGresConfigOptionalServiceAccount getServiceAccount() {
    return serviceAccount;
  }

  public void setServiceAccount(StackGresConfigOptionalServiceAccount serviceAccount) {
    this.serviceAccount = serviceAccount;
  }

  public StackGresConfigOperator getOperator() {
    return operator;
  }

  public void setOperator(StackGresConfigOperator operator) {
    this.operator = operator;
  }

  public StackGresConfigRestapi getRestapi() {
    return restapi;
  }

  public void setRestapi(StackGresConfigRestapi restapi) {
    this.restapi = restapi;
  }

  public StackGresConfigAdminui getAdminui() {
    return adminui;
  }

  public void setAdminui(StackGresConfigAdminui adminui) {
    this.adminui = adminui;
  }

  public StackGresConfigJobs getJobs() {
    return jobs;
  }

  public void setJobs(StackGresConfigJobs jobs) {
    this.jobs = jobs;
  }

  public StackGresConfigDeploy getDeploy() {
    return deploy;
  }

  public void setDeploy(StackGresConfigDeploy deploy) {
    this.deploy = deploy;
  }

  public StackGresConfigCert getCert() {
    return cert;
  }

  public void setCert(StackGresConfigCert cert) {
    this.cert = cert;
  }

  public StackGresConfigAuthentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(StackGresConfigAuthentication authentication) {
    this.authentication = authentication;
  }

  public StackGresConfigPrometheus getPrometheus() {
    return prometheus;
  }

  public void setPrometheus(StackGresConfigPrometheus prometheus) {
    this.prometheus = prometheus;
  }

  public StackGresConfigGrafana getGrafana() {
    return grafana;
  }

  public void setGrafana(StackGresConfigGrafana grafana) {
    this.grafana = grafana;
  }

  public StackGresConfigExtensions getExtensions() {
    return extensions;
  }

  public void setExtensions(StackGresConfigExtensions extensions) {
    this.extensions = extensions;
  }

  public StackGresConfigDeveloper getDeveloper() {
    return developer;
  }

  public void setDeveloper(StackGresConfigDeveloper developer) {
    this.developer = developer;
  }

  public StackGresConfigShardingSphere getShardingSphere() {
    return shardingSphere;
  }

  public void setShardingSphere(StackGresConfigShardingSphere shardingSphere) {
    this.shardingSphere = shardingSphere;
  }

  @Override
  public int hashCode() {
    return Objects.hash(adminui, allowedNamespaceLabelSelector, allowedNamespaces, authentication,
        cert, containerRegistry, deploy, developer, disableClusterRole, extensions, grafana,
        imagePullPolicy, jobs, operator, prometheus, restapi, serviceAccount, shardingSphere);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof StackGresConfigSpec)) {
      return false;
    }
    StackGresConfigSpec other = (StackGresConfigSpec) obj;
    return Objects.equals(adminui, other.adminui)
        && Objects.equals(allowedNamespaceLabelSelector, other.allowedNamespaceLabelSelector)
        && Objects.equals(allowedNamespaces, other.allowedNamespaces)
        && Objects.equals(authentication, other.authentication) && Objects.equals(cert, other.cert)
        && Objects.equals(containerRegistry, other.containerRegistry)
        && Objects.equals(deploy, other.deploy) && Objects.equals(developer, other.developer)
        && Objects.equals(disableClusterRole, other.disableClusterRole)
        && Objects.equals(extensions, other.extensions) && Objects.equals(grafana, other.grafana)
        && Objects.equals(imagePullPolicy, other.imagePullPolicy)
        && Objects.equals(jobs, other.jobs) && Objects.equals(operator, other.operator)
        && Objects.equals(prometheus, other.prometheus) && Objects.equals(restapi, other.restapi)
        && Objects.equals(serviceAccount, other.serviceAccount)
        && Objects.equals(shardingSphere, other.shardingSphere);
  }

  @Override
  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }

}
