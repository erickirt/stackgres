/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.stackgres.common.crd.sgbackup.BackupEventReason;
import io.stackgres.common.crd.sgbackup.StackGresBackup;
import io.stackgres.common.event.EventEmitter;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.common.resource.CustomResourceScanner;
import io.stackgres.common.resource.CustomResourceWriter;
import io.stackgres.operator.app.OperatorLockHolder;
import io.stackgres.operator.common.Metrics;
import io.stackgres.operator.common.PatchResumer;
import io.stackgres.operator.common.StackGresBackupReview;
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
public class BackupReconciliator
    extends AbstractReconciliator<StackGresBackup, StackGresBackupReview> {

  @Dependent
  static class Parameters {
    @Inject OperatorPropertyContext operatorPropertyContext;
    @Inject CustomResourceScanner<StackGresBackup> scanner;
    @Inject CustomResourceFinder<StackGresBackup> finder;
    @Inject MutationPipeline<StackGresBackup, StackGresBackupReview> mutationPipeline;
    @Inject ValidationPipeline<StackGresBackupReview> validationPipeline;
    @Inject CustomResourceWriter<StackGresBackup> writer;
    @Inject AbstractConciliator<StackGresBackup> conciliator;
    @Inject DeployedResourcesCache deployedResourcesCache;
    @Inject HandlerDelegator<StackGresBackup> handlerDelegator;
    @Inject KubernetesClient client;
    @Inject EventEmitter<StackGresBackup> eventController;
    @Inject CustomResourceWriter<StackGresBackup> backupWriter;
    @Inject BackupStatusManager statusManager;
    @Inject ObjectMapper objectMapper;
    @Inject OperatorLockHolder operatorLockReconciliator;
    @Inject ReconciliatorWorkerThreadPool reconciliatorWorkerThreadPool;
    @Inject Metrics metrics;
  }

  private final EventEmitter<StackGresBackup> eventController;
  private final CustomResourceWriter<StackGresBackup> backupWriter;
  private final BackupStatusManager statusManager;
  private final PatchResumer<StackGresBackup> patchResumer;

  @Inject
  public BackupReconciliator(Parameters parameters) {
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
        StackGresBackup.KIND);
    this.eventController = parameters.eventController;
    this.backupWriter = parameters.backupWriter;
    this.statusManager = parameters.statusManager;
    this.patchResumer = new PatchResumer<>(parameters.objectMapper);
  }

  @Override
  protected void setSpecAndStatus(StackGresBackup currentConfig, StackGresBackup mutatedAndValidatedConfig) {
    currentConfig.setSpec(mutatedAndValidatedConfig.getSpec());
    currentConfig.setStatus(mutatedAndValidatedConfig.getStatus());
  }

  @Override
  protected StackGresBackupReview createAdmissionReview() {
    return new StackGresBackupReview();
  }

  @Override
  protected Class<StackGresBackup> getResourceClass() {
    return StackGresBackup.class;
  }

  void onStart(@Observes StartupEvent ev) {
    start();
  }

  void onStop(@Observes ShutdownEvent ev) {
    stop();
  }

  @Override
  protected void reconciliationCycle(StackGresBackup configKey, int retry, boolean load) {
    super.reconciliationCycle(configKey, retry, load);
  }

  @Override
  protected void onPreReconciliation(StackGresBackup config) {
    backupWriter.update(config, statusManager::refreshCondition);
  }

  @Override
  protected void onPostReconciliation(StackGresBackup config) {
  }

  @Override
  protected void onConfigCreated(StackGresBackup backup, ReconciliationResult result) {
    final String resourceChanged = patchResumer.resourceChanged(backup, result);
    eventController.sendEvent(BackupEventReason.BACKUP_CREATED,
        "SGBackup " + backup.getMetadata().getNamespace() + "."
            + backup.getMetadata().getName() + " created: " + resourceChanged, backup);
  }

  @Override
  protected void onConfigUpdated(StackGresBackup backup, ReconciliationResult result) {
    final String resourceChanged = patchResumer.resourceChanged(backup, result);
    eventController.sendEvent(BackupEventReason.BACKUP_UPDATED,
        "SGBackup " + backup.getMetadata().getNamespace() + "."
            + backup.getMetadata().getName() + " updated: " + resourceChanged, backup);
  }

  @Override
  protected void onError(Exception ex, StackGresBackup backup) {
    String message = MessageFormatter.arrayFormat(
        "SGBackup reconciliation cycle failed",
        new String[]{
        }).getMessage();
    eventController.sendEvent(BackupEventReason.BACKUP_CONFIG_ERROR,
        message + ": " + ex.getMessage(), backup);
  }

}
