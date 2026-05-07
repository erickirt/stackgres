/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.shardeddbops;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.stackgres.common.crd.sgshardeddbops.ShardedDbOpsEventReason;
import io.stackgres.common.crd.sgshardeddbops.StackGresShardedDbOps;
import io.stackgres.common.event.EventEmitter;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.common.resource.CustomResourceScanner;
import io.stackgres.common.resource.CustomResourceWriter;
import io.stackgres.operator.app.OperatorLockHolder;
import io.stackgres.operator.common.Metrics;
import io.stackgres.operator.common.PatchResumer;
import io.stackgres.operator.common.StackGresShardedDbOpsReview;
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
public class ShardedDbOpsReconciliator
    extends AbstractReconciliator<StackGresShardedDbOps, StackGresShardedDbOpsReview> {

  @Dependent
  static class Parameters {
    @Inject OperatorPropertyContext operatorPropertyContext;
    @Inject CustomResourceScanner<StackGresShardedDbOps> scanner;
    @Inject CustomResourceFinder<StackGresShardedDbOps> finder;
    @Inject MutationPipeline<StackGresShardedDbOps, StackGresShardedDbOpsReview> mutationPipeline;
    @Inject ValidationPipeline<StackGresShardedDbOpsReview> validationPipeline;
    @Inject CustomResourceWriter<StackGresShardedDbOps> writer;
    @Inject AbstractConciliator<StackGresShardedDbOps> conciliator;
    @Inject DeployedResourcesCache deployedResourcesCache;
    @Inject HandlerDelegator<StackGresShardedDbOps> handlerDelegator;
    @Inject KubernetesClient client;
    @Inject EventEmitter<StackGresShardedDbOps> eventController;
    @Inject ShardedDbOpsStatusManager statusManager;
    @Inject CustomResourceWriter<StackGresShardedDbOps> dbOpsWriter;
    @Inject ObjectMapper objectMapper;
    @Inject OperatorLockHolder operatorLockReconciliator;
    @Inject ReconciliatorWorkerThreadPool reconciliatorWorkerThreadPool;
    @Inject Metrics metrics;
  }

  private final EventEmitter<StackGresShardedDbOps> eventController;
  private final PatchResumer<StackGresShardedDbOps> patchResumer;
  private final ShardedDbOpsStatusManager statusManager;
  private final CustomResourceWriter<StackGresShardedDbOps> dbOpsWriter;

  @Inject
  public ShardedDbOpsReconciliator(Parameters parameters) {
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
        StackGresShardedDbOps.KIND);
    this.eventController = parameters.eventController;
    this.patchResumer = new PatchResumer<>(parameters.objectMapper);
    this.statusManager = parameters.statusManager;
    this.dbOpsWriter = parameters.dbOpsWriter;
  }

  @Override
  protected void setSpecAndStatus(StackGresShardedDbOps currentConfig,
      StackGresShardedDbOps mutatedAndValidatedConfig) {
    currentConfig.setSpec(mutatedAndValidatedConfig.getSpec());
    currentConfig.setStatus(mutatedAndValidatedConfig.getStatus());
  }

  @Override
  protected StackGresShardedDbOpsReview createAdmissionReview() {
    return new StackGresShardedDbOpsReview();
  }

  @Override
  protected Class<StackGresShardedDbOps> getResourceClass() {
    return StackGresShardedDbOps.class;
  }

  void onStart(@Observes StartupEvent ev) {
    start();
  }

  void onStop(@Observes ShutdownEvent ev) {
    stop();
  }

  @Override
  protected void reconciliationCycle(StackGresShardedDbOps configKey, int retry, boolean load) {
    super.reconciliationCycle(configKey, retry, load);
  }

  @Override
  protected void onPreReconciliation(StackGresShardedDbOps config) {
  }

  @Override
  protected void onPostReconciliation(StackGresShardedDbOps config) {
    dbOpsWriter.update(config, statusManager::refreshCondition);
  }

  @Override
  protected void onConfigCreated(StackGresShardedDbOps dbOps, ReconciliationResult result) {
    final String resourceChanged = patchResumer.resourceChanged(dbOps, result);
    eventController.sendEvent(ShardedDbOpsEventReason.SHARDED_DBOPS_CREATED,
        "SGShardedDbOps " + dbOps.getMetadata().getNamespace() + "."
            + dbOps.getMetadata().getName() + " created: " + resourceChanged, dbOps);
  }

  @Override
  protected void onConfigUpdated(StackGresShardedDbOps dbOps, ReconciliationResult result) {
    final String resourceChanged = patchResumer.resourceChanged(dbOps, result);
    eventController.sendEvent(ShardedDbOpsEventReason.SHARDED_DBOPS_UPDATED,
        "SGShardedDbOps " + dbOps.getMetadata().getNamespace() + "."
            + dbOps.getMetadata().getName() + " updated: " + resourceChanged, dbOps);
  }

  @Override
  protected void onError(Exception ex, StackGresShardedDbOps dbOps) {
    String message = MessageFormatter.arrayFormat(
        "SGShardedDbOps reconciliation cycle failed",
        new String[]{
        }).getMessage();
    eventController.sendEvent(ShardedDbOpsEventReason.SHARDED_DBOPS_CONFIG_ERROR,
        message + ": " + ex.getMessage(), dbOps);
  }

}
