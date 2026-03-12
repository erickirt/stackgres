/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.common;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.benmanes.caffeine.cache.Cache;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.stackgres.common.crd.sgbackup.StackGresBackup;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgconfig.StackGresConfig;
import io.stackgres.common.crd.sgdbops.StackGresDbOps;
import io.stackgres.common.crd.sgdistributedlogs.StackGresDistributedLogs;
import io.stackgres.common.crd.sgscript.StackGresScript;
import io.stackgres.common.crd.sgshardedbackup.StackGresShardedBackup;
import io.stackgres.common.crd.sgshardedcluster.StackGresShardedCluster;
import io.stackgres.common.crd.sgshardeddbops.StackGresShardedDbOps;
import io.stackgres.common.crd.sgstream.StackGresStream;
import io.stackgres.common.metrics.AbstractMetrics;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;

@Singleton
public class Metrics extends AbstractMetrics {

  private Map<Class<?>, Reconciliation> reconciliations;

  private Cache<?, ?> reconciliationCache;

  private Map<Class<?>, ResourceCache> watchCaches;

  private Map<URI, ExtensionMetadataCache> extensionMetadataCaches;

  static class Reconciliation {
    private long totalPerformed = 0;
    private long totalErrors = 0;
    private long lastDuration;
  }

  static class ResourceCache {
    private long size = 0;
  }

  static class ExtensionMetadataCache {
    private long indexSize = 0;
    private long indexSameMajorBuildsSize = 0;
    private long indexAnyVersionsSize = 0;
    private long publishersSize = 0;
  }

  @Inject
  public Metrics(
      MeterRegistry registry) {
    super(registry, "operator");
    reconciliations = Seq.<Class<?>>of(
        StackGresConfig.class,
        StackGresCluster.class,
        StackGresShardedCluster.class,
        StackGresDistributedLogs.class,
        StackGresBackup.class,
        StackGresDbOps.class,
        StackGresShardedBackup.class,
        StackGresShardedDbOps.class,
        StackGresScript.class,
        StackGresStream.class)
        .map(customResourceClass -> Tuple.tuple(customResourceClass, new Reconciliation()))
        .toMap(Tuple2::v1, Tuple2::v2);
    watchCaches = new HashMap<>();
    extensionMetadataCaches = new HashMap<>();
  }

  public void incrementReconciliationTotalPerformed(
      Class<?> customResourceClass) {
    String singular = HasMetadata.getSingular(customResourceClass);
    reconciliations.get(customResourceClass).totalPerformed++;
    registryGauge(
        "reconciliation_total_performed",
        List.of(new ImmutableTag("resource", singular)),
        this,
        metrics -> metrics.getReconciliationTotalPerformed(customResourceClass));
  }

  private long getReconciliationTotalPerformed(
      Class<?> customResourceClass) {
    return reconciliations.get(customResourceClass).totalPerformed;
  }

  public void incrementReconciliationTotalErrors(
      final Class<?> customResourceClass) {
    String singular = HasMetadata.getSingular(customResourceClass);
    reconciliations.get(customResourceClass).totalErrors++;
    registryGauge(
        "reconciliation_total_errors",
        List.of(new ImmutableTag("resource", singular)),
        this,
        metrics -> metrics.getReconciliationTotalErrors(customResourceClass));
  }

  private long getReconciliationTotalErrors(
      Class<?> customResourceClass) {
    return reconciliations.get(customResourceClass).totalErrors;
  }

  public void setReconciliationLastDuration(
      final Class<?> customResourceClass,
      final long lastDuration) {
    String singular = HasMetadata.getSingular(customResourceClass);
    reconciliations.get(customResourceClass).lastDuration = lastDuration;
    registryGauge(
        "reconciliation_last_duration",
        List.of(new ImmutableTag("resource", singular)),
        this,
        metrics -> metrics.getReconciliationLastDuration(customResourceClass));
  }

  private long getReconciliationLastDuration(
      Class<?> customResourceClass) {
    return reconciliations.get(customResourceClass).lastDuration;
  }

  public void registerReconciliationCache(Cache<?, ?> reconciliationCache) {
    this.reconciliationCache = reconciliationCache;
    registryGauge(
        "reconciliation_cache_estimated_size",
        this,
        metrics -> metrics.reconciliationCache.estimatedSize());
    registryGauge(
        "reconciliation_cache_average_load_penalty",
        this,
        metrics -> metrics.reconciliationCache.stats().averageLoadPenalty());
    registryGauge(
        "reconciliation_cache_hit_rate",
        this,
        metrics -> metrics.reconciliationCache.stats().hitRate());
    registryGauge(
        "reconciliation_cache_load_failure_rate",
        this,
        metrics -> metrics.reconciliationCache.stats().loadFailureRate());
    registryGauge(
        "reconciliation_cache_miss_rate",
        this,
        metrics -> metrics.reconciliationCache.stats().missRate());
    registryGauge(
        "reconciliation_cache_eviction_weight",
        this,
        metrics -> metrics.reconciliationCache.stats().evictionCount());
    registryGauge(
        "reconciliation_cache_eviction_count",
        this,
        metrics -> metrics.reconciliationCache.stats().evictionWeight());
    registryGauge(
        "reconciliation_cache_hit_count",
        this,
        metrics -> metrics.reconciliationCache.stats().hitCount());
    registryGauge(
        "reconciliation_cache_load_count",
        this,
        metrics -> metrics.reconciliationCache.stats().loadCount());
    registryGauge(
        "reconciliation_cache_load_failure_count",
        this,
        metrics -> metrics.reconciliationCache.stats().loadFailureCount());
    registryGauge(
        "reconciliation_cache_load_success_count",
        this,
        metrics -> metrics.reconciliationCache.stats().loadSuccessCount());
    registryGauge(
        "reconciliation_cache_miss_count",
        this,
        metrics -> metrics.reconciliationCache.stats().missCount());
    registryGauge(
        "reconciliation_cache_request_count",
        this,
        metrics -> metrics.reconciliationCache.stats().requestCount());
    registryGauge(
        "reconciliation_cache_total_load_time",
        this,
        metrics -> metrics.reconciliationCache.stats().totalLoadTime());
  }

  public void setWatchCacheSize(
      Class<?> customResourceClass, long size) {
    String singular = HasMetadata.getSingular(customResourceClass);
    watchCaches.get(customResourceClass).size = size;
    registryGauge(
        "watch_cache_size",
        List.of(new ImmutableTag("resource", singular)),
        this,
        metrics -> metrics.getWatchCacheSize(customResourceClass));
  }

  private long getWatchCacheSize(
      Class<?> customResourceClass) {
    return watchCaches.get(customResourceClass).size;
  }

  public void setExtensionMetadataCacheValues(
      URI uri,
      long indexSize,
      long indexSameMajorBuildsSize,
      long indexAnyVersionsSize,
      long publishersSize) {
    String uriName = uri.toASCIIString();
    var extensionMetadataCache = extensionMetadataCaches.get(uri);
    extensionMetadataCache.indexSize = indexSize;
    extensionMetadataCache.indexSameMajorBuildsSize = indexSameMajorBuildsSize;
    extensionMetadataCache.indexAnyVersionsSize = indexAnyVersionsSize;
    extensionMetadataCache.publishersSize = publishersSize;
    registryGauge(
        "extension_metadata_cache_index_size",
        List.of(new ImmutableTag("uri", uriName)),
        this,
        metrics -> metrics.getExtensionMetadataCacheIndexSize(uri));
    registryGauge(
        "extension_metadata_cache_index_same_major_builds_size",
        List.of(new ImmutableTag("uri", uriName)),
        this,
        metrics -> metrics.getExtensionMetadataCacheIndexSameMajorBuildsSize(uri));
    registryGauge(
        "extension_metadata_cache_index_any_versions_size_size",
        List.of(new ImmutableTag("uri", uriName)),
        this,
        metrics -> metrics.getExtensionMetadataCacheIndexAnyVersionsSize(uri));
    registryGauge(
        "extension_metadata_cache_publishers_size",
        List.of(new ImmutableTag("uri", uriName)),
        this,
        metrics -> metrics.getExtensionMetadataCachePublishersSize(uri));
  }

  private long getExtensionMetadataCacheIndexSize(
      URI uri) {
    return extensionMetadataCaches.get(uri).indexSize;
  }

  private long getExtensionMetadataCacheIndexSameMajorBuildsSize(
      URI uri) {
    return extensionMetadataCaches.get(uri).indexSameMajorBuildsSize;
  }

  private long getExtensionMetadataCacheIndexAnyVersionsSize(
      URI uri) {
    return extensionMetadataCaches.get(uri).indexAnyVersionsSize;
  }

  private long getExtensionMetadataCachePublishersSize(
      URI uri) {
    return extensionMetadataCaches.get(uri).publishersSize;
  }

}
