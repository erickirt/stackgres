/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.common;

import java.net.URI;
import java.util.List;
import java.util.Map;

import io.stackgres.common.OperatorProperty;
import io.stackgres.common.WebClientFactory;
import io.stackgres.common.crd.sgconfig.StackGresConfig;
import io.stackgres.common.crd.sgconfig.StackGresConfigSpec;
import io.stackgres.common.extension.ExtensionMetadataManager;
import io.stackgres.common.extension.ExtensionsConfigUtil;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.operator.app.OperatorInstallationInfoHolder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class OperatorExtensionMetadataManager extends ExtensionMetadataManager {

  private final Metrics metrics;

  @Inject
  public OperatorExtensionMetadataManager(
      CustomResourceFinder<StackGresConfig> configFinder,
      WebClientFactory webClientFactory,
      OperatorInstallationInfoHolder installationInfoHolder,
      Metrics metrics) {
    super(
        webClientFactory,
        () -> getExtensionsRepositoryUris(configFinder),
        () -> Map.ofEntries(installationInfoHolder.getUserAgentHeaderEntry()));
    this.metrics = metrics;
  }

  private static List<URI> getExtensionsRepositoryUris(
      CustomResourceFinder<StackGresConfig> configFinder) {
    return ExtensionsConfigUtil.getExtensionsRepositoryUris(
        configFinder.findByNameAndNamespace(
            OperatorProperty.OPERATOR_NAME.getString(),
            OperatorProperty.OPERATOR_NAMESPACE.getString())
            .map(StackGresConfig::getSpec)
            .map(StackGresConfigSpec::getExtensions)
            .orElse(null));
  }

  @Override
  protected void putExtensionMetadataCache(
      URI uri,
      ExtensionMetadataCache cache) {
    super.putExtensionMetadataCache(uri, cache);
    metrics.setExtensionMetadataCacheValues(
        uri,
        cache.getIndex().size(),
        cache.getIndexSameMajorBuilds().size(),
        cache.getIndexAnyVersions().size(),
        cache.getPublishers().size());
  }

}
