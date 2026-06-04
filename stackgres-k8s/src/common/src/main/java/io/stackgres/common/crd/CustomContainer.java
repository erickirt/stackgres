/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.crd;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerResizePolicy;
import io.fabric8.kubernetes.api.model.ContainerRestartRule;
import io.fabric8.kubernetes.api.model.EnvFromSource;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Lifecycle;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.SecurityContext;
import io.fabric8.kubernetes.api.model.VolumeDevice;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.sundr.builder.annotations.Buildable;
import io.sundr.builder.annotations.BuildableReference;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
@Buildable(editableEnabled = false, generateBuilderPackage = false,
    lazyCollectionInitEnabled = false, lazyMapInitEnabled = false,
    builderPackage = "io.fabric8.kubernetes.api.builder",
    refs = {
        @BuildableReference(io.fabric8.kubernetes.api.model.Container.class),
        @BuildableReference(io.fabric8.kubernetes.api.model.EnvVar.class),
        @BuildableReference(io.fabric8.kubernetes.api.model.EnvFromSource.class),
        @BuildableReference(io.fabric8.kubernetes.api.model.Lifecycle.class),
        @BuildableReference(io.fabric8.kubernetes.api.model.Probe.class),
        @BuildableReference(io.fabric8.kubernetes.api.model.ContainerPort.class),
        @BuildableReference(io.fabric8.kubernetes.api.model.ResourceRequirements.class),
        @BuildableReference(io.fabric8.kubernetes.api.model.SecurityContext.class),
        @BuildableReference(io.fabric8.kubernetes.api.model.VolumeDevice.class),
        @BuildableReference(io.fabric8.kubernetes.api.model.VolumeMount.class),
        @BuildableReference(io.fabric8.kubernetes.api.model.ContainerResizePolicy.class),
        @BuildableReference(io.fabric8.kubernetes.api.model.ContainerRestartRule.class),
    })
public class CustomContainer extends io.fabric8.kubernetes.api.model.Container {

  private static final long serialVersionUID = 1L;

  public CustomContainer() {
    super();
  }

  public CustomContainer(List<String> args, List<String> command, List<EnvVar> env,
      List<EnvFromSource> envFrom, String image, String imagePullPolicy, Lifecycle lifecycle,
      Probe livenessProbe, String name, List<ContainerPort> ports, Probe readinessProbe,
      List<ContainerResizePolicy> resizePolicy, ResourceRequirements resources,
      String restartPolicy, List<ContainerRestartRule> restartPolicyRules,
      SecurityContext securityContext, Probe startupProbe, Boolean stdin, Boolean stdinOnce,
      String terminationMessagePath, String terminationMessagePolicy, Boolean tty,
      List<VolumeDevice> volumeDevices, List<VolumeMount> volumeMounts, String workingDir) {
    super(args, command, env, envFrom, image, imagePullPolicy, lifecycle, livenessProbe, name, ports,
        readinessProbe, resizePolicy, resources, restartPolicy, restartPolicyRules, securityContext,
        startupProbe, stdin, stdinOnce, terminationMessagePath, terminationMessagePolicy, tty,
        volumeDevices, volumeMounts, workingDir);
  }

  @Override
  public List<String> getArgs() {
    return super.getArgs();
  }

  @Override
  public List<String> getCommand() {
    return super.getCommand();
  }

  @Override
  public List<EnvVar> getEnv() {
    return super.getEnv();
  }

  @Override
  public List<EnvFromSource> getEnvFrom() {
    return super.getEnvFrom();
  }

  @Override
  public String getImage() {
    return super.getImage();
  }

  @Override
  public String getImagePullPolicy() {
    return super.getImagePullPolicy();
  }

  @Override
  public Lifecycle getLifecycle() {
    return super.getLifecycle();
  }

  @Override
  public Probe getLivenessProbe() {
    return super.getLivenessProbe();
  }

  @Override
  public String getName() {
    return super.getName();
  }

  @Override
  public List<ContainerPort> getPorts() {
    return super.getPorts();
  }

  @Override
  public Probe getReadinessProbe() {
    return super.getReadinessProbe();
  }

  @Override
  public List<ContainerResizePolicy> getResizePolicy() {
    return super.getResizePolicy();
  }

  @Override
  public ResourceRequirements getResources() {
    return super.getResources();
  }

  @Override
  public String getRestartPolicy() {
    return super.getRestartPolicy();
  }

  @Override
  public List<ContainerRestartRule> getRestartPolicyRules() {
    return super.getRestartPolicyRules();
  }

  @Override
  public SecurityContext getSecurityContext() {
    return super.getSecurityContext();
  }

  @Override
  public Probe getStartupProbe() {
    return super.getStartupProbe();
  }

  @Override
  public Boolean getStdin() {
    return super.getStdin();
  }

  @Override
  public Boolean getStdinOnce() {
    return super.getStdinOnce();
  }

  @Override
  public String getTerminationMessagePath() {
    return super.getTerminationMessagePath();
  }

  @Override
  public String getTerminationMessagePolicy() {
    return super.getTerminationMessagePolicy();
  }

  @Override
  public Boolean getTty() {
    return super.getTty();
  }

  @Override
  public List<VolumeDevice> getVolumeDevices() {
    return super.getVolumeDevices();
  }

  @Override
  public List<VolumeMount> getVolumeMounts() {
    return super.getVolumeMounts();
  }

  @Override
  public String getWorkingDir() {
    return super.getWorkingDir();
  }

  @Override
  public void setArgs(List<String> args) {
    super.setArgs(args);
  }

  @Override
  public void setCommand(List<String> command) {
    super.setCommand(command);
  }

  @Override
  public void setEnv(List<EnvVar> env) {
    super.setEnv(env);
  }

  @Override
  public void setEnvFrom(List<EnvFromSource> envFrom) {
    super.setEnvFrom(envFrom);
  }

  @Override
  public void setImage(String image) {
    super.setImage(image);
  }

  @Override
  public void setImagePullPolicy(String imagePullPolicy) {
    super.setImagePullPolicy(imagePullPolicy);
  }

  @Override
  public void setLifecycle(Lifecycle lifecycle) {
    super.setLifecycle(lifecycle);
  }

  @Override
  public void setLivenessProbe(Probe livenessProbe) {
    super.setLivenessProbe(livenessProbe);
  }

  @Override
  public void setName(String name) {
    super.setName(name);
  }

  @Override
  public void setPorts(List<ContainerPort> ports) {
    super.setPorts(ports);
  }

  @Override
  public void setReadinessProbe(Probe readinessProbe) {
    super.setReadinessProbe(readinessProbe);
  }

  @Override
  public void setResizePolicy(List<ContainerResizePolicy> resizePolicy) {
    super.setResizePolicy(resizePolicy);
  }

  @Override
  public void setResources(ResourceRequirements resources) {
    super.setResources(resources);
  }

  @Override
  public void setRestartPolicy(String restartPolicy) {
    super.setRestartPolicy(restartPolicy);
  }

  @Override
  public void setRestartPolicyRules(List<ContainerRestartRule> restartPolicyRules) {
    super.setRestartPolicyRules(restartPolicyRules);
  }

  @Override
  public void setSecurityContext(SecurityContext securityContext) {
    super.setSecurityContext(securityContext);
  }

  @Override
  public void setStartupProbe(Probe startupProbe) {
    super.setStartupProbe(startupProbe);
  }

  @Override
  public void setStdin(Boolean stdin) {
    super.setStdin(stdin);
  }

  @Override
  public void setStdinOnce(Boolean stdinOnce) {
    super.setStdinOnce(stdinOnce);
  }

  @Override
  public void setTerminationMessagePath(String terminationMessagePath) {
    super.setTerminationMessagePath(terminationMessagePath);
  }

  @Override
  public void setTerminationMessagePolicy(String terminationMessagePolicy) {
    super.setTerminationMessagePolicy(terminationMessagePolicy);
  }

  @Override
  public void setTty(Boolean tty) {
    super.setTty(tty);
  }

  @Override
  public void setVolumeDevices(List<VolumeDevice> volumeDevices) {
    super.setVolumeDevices(volumeDevices);
  }

  @Override
  public void setVolumeMounts(List<VolumeMount> volumeMounts) {
    super.setVolumeMounts(volumeMounts);
  }

  @Override
  public void setWorkingDir(String workingDir) {
    super.setWorkingDir(workingDir);
  }

}
