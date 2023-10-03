/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.app;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionVersion;
import io.stackgres.common.CrdLoader;
import io.stackgres.common.YamlMapperProvider;
import io.stackgres.common.resource.ResourceFinder;
import io.stackgres.common.resource.ResourceWriter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class CrdInstaller {

  private static final Logger LOGGER = LoggerFactory.getLogger(CrdInstaller.class);

  private final ResourceFinder<CustomResourceDefinition> crdResourceFinder;
  private final ResourceWriter<CustomResourceDefinition> crdResourceWriter;
  private final CrdLoader crdLoader;

  @Inject
  public CrdInstaller(
      ResourceFinder<CustomResourceDefinition> crdResourceFinder,
      ResourceWriter<CustomResourceDefinition> crdResourceWriter,
      YamlMapperProvider yamlMapperProvider) {
    this.crdResourceFinder = crdResourceFinder;
    this.crdResourceWriter = crdResourceWriter;
    this.crdLoader = new CrdLoader(yamlMapperProvider.get());
  }

  public void installCustomResourceDefinitions() {
    LOGGER.info("Installing CRDs");
    crdLoader.scanCrds()
        .forEach(this::installCrd);
  }

  protected void installCrd(@NotNull CustomResourceDefinition currentCrd) {
    String name = currentCrd.getMetadata().getName();
    LOGGER.info("Installing CRD " + name);
    Optional<CustomResourceDefinition> installedCrdOpt = crdResourceFinder
        .findByName(name);

    if (installedCrdOpt.isPresent()) {
      LOGGER.debug("CRD {} is present, patching it", name);
      CustomResourceDefinition installedCrd = installedCrdOpt.get();
      if (!isCurrentCrdInstalled(currentCrd, installedCrd)) {
        upgradeCrd(currentCrd, installedCrd);
      }
      updateAlreadyInstalledVersions(currentCrd, installedCrd);
      crdResourceWriter.update(installedCrd);
    } else {
      LOGGER.info("CRD {} is not present, installing it", name);
      crdResourceWriter.create(currentCrd);
    }
  }

  private void updateAlreadyInstalledVersions(CustomResourceDefinition currentCrd,
      CustomResourceDefinition installedCrd) {
    installedCrd.getSpec().getVersions().forEach(installedVersion -> {
      currentCrd.getSpec()
          .getVersions()
          .stream()
          .filter(v -> v.getName().equals(installedVersion.getName()))
          .forEach(currentVersion -> updateAlreadyDeployedVersion(
              installedVersion, currentVersion));
    });
  }

  private void updateAlreadyDeployedVersion(CustomResourceDefinitionVersion installedVersion,
      CustomResourceDefinitionVersion currentVersion) {
    installedVersion.setSchema(currentVersion.getSchema());
    installedVersion.setSubresources(currentVersion.getSubresources());
    installedVersion.setAdditionalPrinterColumns(currentVersion.getAdditionalPrinterColumns());
  }

  private void upgradeCrd(
      CustomResourceDefinition currentCrd,
      CustomResourceDefinition installedCrd) {
    disableStorageVersions(installedCrd);
    addNewSchemaVersions(currentCrd, installedCrd);
    crdResourceWriter.update(installedCrd);
  }

  private void disableStorageVersions(CustomResourceDefinition installedCrd) {
    installedCrd.getSpec().getVersions()
        .forEach(versionDefinition -> versionDefinition.setStorage(false));
  }

  private void addNewSchemaVersions(
      CustomResourceDefinition currentCrd,
      CustomResourceDefinition installedCrd) {
    List<String> installedVersions = installedCrd.getSpec().getVersions()
        .stream()
        .map(CustomResourceDefinitionVersion::getName)
        .toList();

    List<String> versionsToInstall = currentCrd.getSpec().getVersions()
        .stream()
        .map(CustomResourceDefinitionVersion::getName)
        .filter(Predicate.not(installedVersions::contains))
        .toList();

    currentCrd.getSpec().getVersions().stream()
        .filter(version -> versionsToInstall.contains(version.getName()))
        .forEach(installedCrd.getSpec().getVersions()::add);
  }

  private boolean isCurrentCrdInstalled(
      CustomResourceDefinition currentCrd,
      CustomResourceDefinition installedCrd) {
    final String currentVersion = currentCrd.getSpec().getVersions()
        .stream()
        .filter(CustomResourceDefinitionVersion::getStorage).findFirst()
        .orElseThrow(() -> new RuntimeException("At least one CRD version must be stored"))
        .getName();
    return installedCrd.getSpec().getVersions().stream()
        .map(CustomResourceDefinitionVersion::getName)
        .anyMatch(installedVersion -> installedVersion.equals(currentVersion));
  }

  public void checkCustomResourceDefinitions() {
    crdLoader.scanCrds()
        .forEach(this::checkCrd);
  }

  protected void checkCrd(@NotNull CustomResourceDefinition currentCrd) {
    String name = currentCrd.getMetadata().getName();
    Optional<CustomResourceDefinition> installedCrdOpt = crdResourceFinder
        .findByName(name);

    if (installedCrdOpt.isEmpty()) {
      throw new RuntimeException("CRD " + name + " was not found.");
    }
  }

}
