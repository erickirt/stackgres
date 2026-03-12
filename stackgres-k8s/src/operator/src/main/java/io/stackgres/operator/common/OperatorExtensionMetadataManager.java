/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.common;

import java.net.URI;

import io.stackgres.common.OperatorProperty;
import io.stackgres.common.WebClientFactory;
import io.stackgres.common.extension.ExtensionMetadataManager;
import io.stackgres.operator.configuration.OperatorPropertyContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.lambda.Seq;

@Singleton
public class OperatorExtensionMetadataManager extends ExtensionMetadataManager {

  private final Metrics metrics;

  @Inject
  public OperatorExtensionMetadataManager(
      OperatorPropertyContext propertyContext,
      WebClientFactory webClientFactory,
      Metrics metrics) {
    super(
        webClientFactory,
        Seq.of(propertyContext.getStringArray(
            OperatorProperty.EXTENSIONS_REPOSITORY_URLS))
            .map(URI::create)
            .toList());
    this.metrics = metrics;
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
