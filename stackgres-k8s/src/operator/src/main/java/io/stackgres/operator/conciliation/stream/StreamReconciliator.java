/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.stackgres.common.crd.sgstream.StackGresStream;
import io.stackgres.common.crd.sgstream.StreamEventReason;
import io.stackgres.common.event.EventEmitter;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.common.resource.CustomResourceScanner;
import io.stackgres.common.resource.CustomResourceWriter;
import io.stackgres.operator.app.OperatorLockHolder;
import io.stackgres.operator.common.Metrics;
import io.stackgres.operator.common.PatchResumer;
import io.stackgres.operator.common.StackGresStreamReview;
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
public class StreamReconciliator
    extends AbstractReconciliator<StackGresStream, StackGresStreamReview> {

  @Dependent
  static class Parameters {
    @Inject OperatorPropertyContext operatorPropertyContext;
    @Inject CustomResourceScanner<StackGresStream> scanner;
    @Inject CustomResourceFinder<StackGresStream> finder;
    @Inject MutationPipeline<StackGresStream, StackGresStreamReview> mutationPipeline;
    @Inject ValidationPipeline<StackGresStreamReview> validationPipeline;
    @Inject CustomResourceWriter<StackGresStream> writer;
    @Inject AbstractConciliator<StackGresStream> conciliator;
    @Inject DeployedResourcesCache deployedResourcesCache;
    @Inject HandlerDelegator<StackGresStream> handlerDelegator;
    @Inject KubernetesClient client;
    @Inject EventEmitter<StackGresStream> eventController;
    @Inject StreamStatusManager statusManager;
    @Inject CustomResourceWriter<StackGresStream> streamWriter;
    @Inject ObjectMapper objectMapper;
    @Inject OperatorLockHolder operatorLockReconciliator;
    @Inject ReconciliatorWorkerThreadPool reconciliatorWorkerThreadPool;
    @Inject Metrics metrics;
  }

  private final EventEmitter<StackGresStream> eventController;
  private final PatchResumer<StackGresStream> patchResumer;
  private final StreamStatusManager statusManager;
  private final CustomResourceWriter<StackGresStream> streamWriter;

  @Inject
  public StreamReconciliator(Parameters parameters) {
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
        StackGresStream.KIND);
    this.eventController = parameters.eventController;
    this.patchResumer = new PatchResumer<>(parameters.objectMapper);
    this.statusManager = parameters.statusManager;
    this.streamWriter = parameters.streamWriter;
  }

  @Override
  protected void setSpecAndStatus(StackGresStream currentConfig, StackGresStream mutatedAndValidatedConfig) {
    currentConfig.setSpec(mutatedAndValidatedConfig.getSpec());
    currentConfig.setStatus(mutatedAndValidatedConfig.getStatus());
  }

  @Override
  protected StackGresStreamReview createAdmissionReview() {
    return new StackGresStreamReview();
  }

  @Override
  protected Class<StackGresStream> getResourceClass() {
    return StackGresStream.class;
  }

  void onStart(@Observes StartupEvent ev) {
    start();
  }

  void onStop(@Observes ShutdownEvent ev) {
    stop();
  }

  @Override
  protected void reconciliationCycle(StackGresStream configKey, int retry, boolean load) {
    super.reconciliationCycle(configKey, retry, load);
  }

  @Override
  protected void onPreReconciliation(StackGresStream config) {
  }

  @Override
  protected void onPostReconciliation(StackGresStream config) {
    streamWriter.update(config, statusManager::refreshCondition);
  }

  @Override
  protected void onConfigCreated(StackGresStream stream, ReconciliationResult result) {
    final String resourceChanged = patchResumer.resourceChanged(stream, result);
    eventController.sendEvent(StreamEventReason.STREAM_CREATED,
        "SGStream " + stream.getMetadata().getNamespace() + "."
            + stream.getMetadata().getName() + " created: " + resourceChanged, stream);
  }

  @Override
  protected void onConfigUpdated(StackGresStream stream, ReconciliationResult result) {
    final String resourceChanged = patchResumer.resourceChanged(stream, result);
    eventController.sendEvent(StreamEventReason.STREAM_UPDATED,
        "SGStream " + stream.getMetadata().getNamespace() + "."
            + stream.getMetadata().getName() + " updated: " + resourceChanged, stream);
  }

  @Override
  protected void onError(Exception ex, StackGresStream stream) {
    String message = MessageFormatter.arrayFormat(
        "SGStream reconciliation cycle failed",
        new String[]{
        }).getMessage();
    eventController.sendEvent(StreamEventReason.STREAM_CONFIG_ERROR,
        message + ": " + ex.getMessage(), stream);
  }

}
