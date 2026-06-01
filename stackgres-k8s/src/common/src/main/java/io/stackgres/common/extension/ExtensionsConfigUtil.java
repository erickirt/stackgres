/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.extension;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.stackgres.common.OperatorProperty;
import io.stackgres.common.crd.sgconfig.StackGresConfigExtensions;
import jakarta.ws.rs.core.UriBuilder;

/**
 * Builds the list of extensions repository URLs from the SGConfig extensions section, encoding the
 * metadata refresh settings as query
 * parameters interpreted by {@link ExtensionMetadataManager} and {@code WebClientFactory}.
 *
 * <p>When the {@code EXTENSIONS_REPOSITORY_URLS} environment variable (or the
 * {@code stackgres.extensionsRepositoryUrls} system property) is explicitly set it overrides the
 * repository URLs configured in SGConfig. The Helm chart leaves it unset (so SGConfig is used), but
 * other installation methods such as the OLM bundle Subscription may set it to keep control over
 * the extensions repository URLs.</p>
 */
public interface ExtensionsConfigUtil {

  String CACHE_TIMEOUT_PARAMETER = "cacheTimeout";
  String CACHE_REFRESH_DISABLED_PARAMETER = "cacheRefreshDisabled";
  String PROXY_URL_PARAMETER = "proxyUrl";
  String RETRY_PARAMETER = "retry";
  String EXTENSIONS_CACHE_SERVICE_SUFFIX = "-extensions-cache";

  static List<String> getExtensionsRepositoryUrls(
      StackGresConfigExtensions extensions) {
    return getRepositoryUrls(extensions)
        .stream()
        .map(url -> applyParameters(url, extensions))
        .toList();
  }

  static List<URI> getExtensionsRepositoryUris(
      StackGresConfigExtensions extensions) {
    return getExtensionsRepositoryUrls(extensions)
        .stream()
        .map(URI::create)
        .toList();
  }

  /**
   * Return the repository URLs taking the {@code EXTENSIONS_REPOSITORY_URLS} environment variable
   * (or {@code stackgres.extensionsRepositoryUrls} system property) override into account when it is
   * explicitly set, falling back to the SGConfig configured value otherwise. The application
   * properties default is deliberately ignored so that an unset override falls back to SGConfig.
   */
  private static List<String> getRepositoryUrls(StackGresConfigExtensions extensions) {
    return getRepositoryUrlsOverride()
        .orElseGet(() -> Optional.ofNullable(extensions)
            .map(StackGresConfigExtensions::getRepositoryUrls)
            .orElse(List.of()));
  }

  private static Optional<List<String>> getRepositoryUrlsOverride() {
    return Optional
        .ofNullable(System.getProperty(
            OperatorProperty.EXTENSIONS_REPOSITORY_URLS.getPropertyName()))
        .or(() -> Optional.ofNullable(System.getenv(
            OperatorProperty.EXTENSIONS_REPOSITORY_URLS.getEnvironmentVariableName())))
        .map(value -> Arrays.stream(value.split(","))
            .toList());
  }

  private static String applyParameters(
      String repositoryUrl, StackGresConfigExtensions extensions) {
    UriBuilder uriBuilder = UriBuilder.fromUri(repositoryUrl);
    final String refreshInterval = Optional.ofNullable(extensions)
        .map(StackGresConfigExtensions::getRefreshInterval)
        .orElse(null);
    if (refreshInterval != null && !refreshInterval.isBlank()) {
      uriBuilder.replaceQueryParam(CACHE_TIMEOUT_PARAMETER, refreshInterval);
    }
    if (extensions != null && Boolean.FALSE.equals(extensions.getRefreshEnabled())) {
      uriBuilder.replaceQueryParam(CACHE_REFRESH_DISABLED_PARAMETER, "true");
    }
    return uriBuilder.build().toString();
  }

}
