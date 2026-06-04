/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.stackgres.common.crd.sgconfig.StackGresConfigExtensions;
import io.stackgres.common.crd.sgconfig.StackGresConfigExtensionsCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExtensionsConfigUtilTest {

  private static StackGresConfigExtensions extensions() {
    StackGresConfigExtensions extensions = new StackGresConfigExtensions();
    extensions.setRepositoryUrls(List.of("https://extensions.stackgres.io/postgres/repository"));
    return extensions;
  }

  private static StackGresConfigExtensions extensionsWithCache() {
    StackGresConfigExtensions extensions = extensions();
    StackGresConfigExtensionsCache cache = new StackGresConfigExtensionsCache();
    cache.setEnabled(true);
    extensions.setCache(cache);
    return extensions;
  }

  @BeforeEach
  @AfterEach
  void clearProperties() {
    System.clearProperty("stackgres.operatorName");
    System.clearProperty("stackgres.operatorNamespace");
    System.clearProperty("stackgres.extensionsRepositoryUrls");
  }

  @Test
  void nullExtensions_returnsEmptyList() {
    assertTrue(ExtensionsConfigUtil.getExtensionsRepositoryUrls(null).isEmpty());
  }

  @Test
  void refreshSettings_areEncodedAsQueryParameters() {
    StackGresConfigExtensions extensions = extensions();
    extensions.setRefreshInterval("P7D");
    extensions.setRefreshEnabled(false);
    List<String> urls = ExtensionsConfigUtil.getExtensionsRepositoryUrls(extensions);
    assertEquals(1, urls.size());
    String url = urls.get(0);
    assertTrue(url.contains("cacheTimeout=P7D"), url);
    assertTrue(url.contains("cacheRefreshDisabled=true"), url);
    assertFalse(url.contains("proxyUrl"), url);
  }

  @Test
  void enabledRefresh_doesNotAddDisabledParameter() {
    StackGresConfigExtensions extensions = extensions();
    extensions.setRefreshEnabled(true);
    String url = ExtensionsConfigUtil.getExtensionsRepositoryUrls(extensions).get(0);
    assertFalse(url.contains("cacheRefreshDisabled"), url);
  }

  @Test
  void proxy_notAppliedWhenCacheDisabled() {
    String url = ExtensionsConfigUtil.getExtensionsRepositoryUrls(extensions()).get(0);
    assertFalse(url.contains("proxyUrl"), url);
  }

  @Test
  void envOverride_replacesSgConfigRepositoryUrls() {
    System.setProperty("stackgres.extensionsRepositoryUrls",
        "https://override.example.com/a,https://override.example.com/b");
    List<String> urls = ExtensionsConfigUtil.getExtensionsRepositoryUrls(extensions());
    assertEquals(2, urls.size());
    assertTrue(urls.get(0).startsWith("https://override.example.com/a"), urls.toString());
    assertTrue(urls.get(1).startsWith("https://override.example.com/b"), urls.toString());
    assertFalse(urls.stream().anyMatch(url -> url.contains("extensions.stackgres.io")),
        urls.toString());
  }

  @Test
  void envOverride_stillAppliesRefreshSettingsFromSgConfig() {
    System.setProperty("stackgres.extensionsRepositoryUrls", "https://override.example.com/a");
    StackGresConfigExtensions extensions = extensions();
    extensions.setRefreshInterval("PT1H");
    extensions.setRefreshEnabled(false);
    String url = ExtensionsConfigUtil.getExtensionsRepositoryUrls(extensions).get(0);
    assertTrue(url.startsWith("https://override.example.com/a"), url);
    assertTrue(url.contains("cacheTimeout=PT1H"), url);
    assertTrue(url.contains("cacheRefreshDisabled=true"), url);
  }

  @Test
  void envOverride_usedEvenWhenExtensionsIsNull() {
    System.setProperty("stackgres.extensionsRepositoryUrls", "https://override.example.com/a");
    List<String> urls = ExtensionsConfigUtil.getExtensionsRepositoryUrls(null);
    assertEquals(1, urls.size());
    assertTrue(urls.get(0).startsWith("https://override.example.com/a"), urls.toString());
  }

}
