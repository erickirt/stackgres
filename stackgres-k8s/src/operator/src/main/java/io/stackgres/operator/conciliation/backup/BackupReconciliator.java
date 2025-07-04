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
import io.stackgres.common.resource.CustomResourceScheduler;
import io.stackgres.operator.app.OperatorLockHolder;
import io.stackgres.operator.common.Metrics;
import io.stackgres.operator.common.PatchResumer;
import io.stackgres.operator.conciliation.AbstractConciliator;
import io.stackgres.operator.conciliation.AbstractReconciliator;
import io.stackgres.operator.conciliation.DeployedResourcesCache;
import io.stackgres.operator.conciliation.HandlerDelegator;
import io.stackgres.operator.conciliation.ReconciliationResult;
import io.stackgres.operator.conciliation.ReconciliatorWorkerThreadPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.helpers.MessageFormatter;

@ApplicationScoped
public class BackupReconciliator
    extends AbstractReconciliator<StackGresBackup> {

  @Dependent
  static class Parameters {
    @Inject CustomResourceScanner<StackGresBackup> scanner;
    @Inject CustomResourceFinder<StackGresBackup> finder;
    @Inject AbstractConciliator<StackGresBackup> conciliator;
    @Inject DeployedResourcesCache deployedResourcesCache;
    @Inject HandlerDelegator<StackGresBackup> handlerDelegator;
    @Inject KubernetesClient client;
    @Inject EventEmitter<StackGresBackup> eventController;
    @Inject CustomResourceScheduler<StackGresBackup> backupScheduler;
    @Inject BackupStatusManager statusManager;
    @Inject ObjectMapper objectMapper;
    @Inject OperatorLockHolder operatorLockReconciliator;
    @Inject ReconciliatorWorkerThreadPool reconciliatorWorkerThreadPool;
    @Inject Metrics metrics;
  }

  private final EventEmitter<StackGresBackup> eventController;
  private final CustomResourceScheduler<StackGresBackup> backupScheduler;
  private final BackupStatusManager statusManager;
  private final PatchResumer<StackGresBackup> patchResumer;

  @Inject
  public BackupReconciliator(Parameters parameters) {
    super(parameters.scanner, parameters.finder,
        parameters.conciliator, parameters.deployedResourcesCache,
        parameters.handlerDelegator, parameters.client,
        parameters.operatorLockReconciliator,
        parameters.reconciliatorWorkerThreadPool,
        parameters.metrics,
        StackGresBackup.KIND);
    this.eventController = parameters.eventController;
    this.backupScheduler = parameters.backupScheduler;
    this.statusManager = parameters.statusManager;
    this.patchResumer = new PatchResumer<>(parameters.objectMapper);
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
    backupScheduler.update(config, statusManager::refreshCondition);
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
