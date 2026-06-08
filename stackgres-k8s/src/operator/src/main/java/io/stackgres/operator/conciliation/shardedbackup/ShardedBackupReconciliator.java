/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.shardedbackup;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.stackgres.common.crd.sgshardedbackup.ShardedBackupEventReason;
import io.stackgres.common.crd.sgshardedbackup.StackGresShardedBackup;
import io.stackgres.common.event.EventEmitter;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.common.resource.CustomResourceScanner;
import io.stackgres.common.resource.CustomResourceWriter;
import io.stackgres.operator.app.OperatorLockHolder;
import io.stackgres.operator.common.Metrics;
import io.stackgres.operator.common.PatchResumer;
import io.stackgres.operator.common.StackGresShardedBackupReview;
import io.stackgres.operator.conciliation.AbstractConciliator;
import io.stackgres.operator.conciliation.AbstractReconciliator;
import io.stackgres.operator.conciliation.DeployedResourcesCache;
import io.stackgres.operator.conciliation.HandlerDelegator;
import io.stackgres.operator.conciliation.ReconciliationResult;
import io.stackgres.operator.conciliation.ReconciliatorWorkerThreadPool;
import io.stackgres.operator.configuration.OperatorPropertyContext;
import io.stackgres.operatorframework.admissionwebhook.mutating.MutationPipeline;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationPipeline;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.helpers.MessageFormatter;

@ApplicationScoped
public class ShardedBackupReconciliator
    extends AbstractReconciliator<StackGresShardedBackup, StackGresShardedBackupReview> {

  @Dependent
  static class Parameters {
    @Inject OperatorPropertyContext operatorPropertyContext;
    @Inject CustomResourceScanner<StackGresShardedBackup> scanner;
    @Inject CustomResourceFinder<StackGresShardedBackup> finder;
    @Inject MutationPipeline<StackGresShardedBackup, StackGresShardedBackupReview> mutationPipeline;
    @Inject ValidationPipeline<StackGresShardedBackupReview> validationPipeline;
    @Inject CustomResourceWriter<StackGresShardedBackup> writer;
    @Inject AbstractConciliator<StackGresShardedBackup> conciliator;
    @Inject DeployedResourcesCache deployedResourcesCache;
    @Inject HandlerDelegator<StackGresShardedBackup> handlerDelegator;
    @Inject KubernetesClient client;
    @Inject EventEmitter<StackGresShardedBackup> eventController;
    @Inject CustomResourceWriter<StackGresShardedBackup> backupWriter;
    @Inject ShardedBackupStatusManager statusManager;
    @Inject ObjectMapper objectMapper;
    @Inject OperatorLockHolder operatorLockReconciliator;
    @Inject ReconciliatorWorkerThreadPool reconciliatorWorkerThreadPool;
    @Inject Metrics metrics;
  }

  private final EventEmitter<StackGresShardedBackup> eventController;
  private final CustomResourceWriter<StackGresShardedBackup> backupWriter;
  private final ShardedBackupStatusManager statusManager;
  private final PatchResumer<StackGresShardedBackup> patchResumer;

  @Inject
  public ShardedBackupReconciliator(Parameters parameters) {
    super(
        parameters.operatorPropertyContext,
        parameters.scanner,
        parameters.finder,
        parameters.objectMapper,
        parameters.mutationPipeline,
        parameters.validationPipeline,
        parameters.writer,
        parameters.conciliator,
        parameters.deployedResourcesCache,
        parameters.handlerDelegator,
        parameters.client,
        parameters.operatorLockReconciliator,
        parameters.reconciliatorWorkerThreadPool,
        parameters.metrics,
        StackGresShardedBackup.KIND);
    this.eventController = parameters.eventController;
    this.backupWriter = parameters.backupWriter;
    this.statusManager = parameters.statusManager;
    this.patchResumer = new PatchResumer<>(parameters.objectMapper);
  }

  @Override
  protected void setSpecAndStatus(StackGresShardedBackup currentConfig,
      StackGresShardedBackup mutatedAndValidatedConfig) {
    currentConfig.setSpec(mutatedAndValidatedConfig.getSpec());
    currentConfig.setStatus(mutatedAndValidatedConfig.getStatus());
  }

  @Override
  protected StackGresShardedBackupReview createAdmissionReview() {
    return new StackGresShardedBackupReview();
  }

  @Override
  protected Class<StackGresShardedBackup> getResourceClass() {
    return StackGresShardedBackup.class;
  }

  void onStart(@Observes StartupEvent ev) {
    start();
  }

  void onStop(@Observes ShutdownEvent ev) {
    stop();
  }

  @Override
  protected void reconciliationCycle(StackGresShardedBackup configKey, int retry, boolean load) {
    super.reconciliationCycle(configKey, retry, load);
  }

  @Override
  protected void onPreReconciliation(StackGresShardedBackup config) {
    backupWriter.update(config, statusManager::refreshCondition);
  }

  @Override
  protected void onPostReconciliation(StackGresShardedBackup config) {
  }

  @Override
  protected void onConfigCreated(StackGresShardedBackup backup, ReconciliationResult result) {
    final String resourceChanged = patchResumer.resourceChanged(backup, result);
    eventController.sendEvent(ShardedBackupEventReason.BACKUP_CREATED,
        "SGShardedBackup " + backup.getMetadata().getNamespace() + "."
            + backup.getMetadata().getName() + " created: " + resourceChanged, backup);
  }

  @Override
  protected void onConfigUpdated(StackGresShardedBackup backup, ReconciliationResult result) {
    final String resourceChanged = patchResumer.resourceChanged(backup, result);
    eventController.sendEvent(ShardedBackupEventReason.BACKUP_UPDATED,
        "SGShardedBackup " + backup.getMetadata().getNamespace() + "."
            + backup.getMetadata().getName() + " updated: " + resourceChanged, backup);
  }

  @Override
  protected void onError(Exception ex, StackGresShardedBackup backup) {
    String message = MessageFormatter.arrayFormat(
        "SGShardedBackup reconciliation cycle failed",
        new String[]{
        }).getMessage();
    eventController.sendEvent(ShardedBackupEventReason.BACKUP_CONFIG_ERROR,
        message + ": " + ex.getMessage(), backup);
  }

}
