/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.stackgres.common.crd.sgstream.StackGresStream;
import io.stackgres.operator.conciliation.FireAndForgetReconciliationHandler;
import io.stackgres.operator.conciliation.ReconciliationHandler;
import io.stackgres.operator.conciliation.ReconciliationScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Pre-creates the Lease used for distributed locking but never updates it once it exists.
 * The Stream worker pod owns the spec.
 */
@ReconciliationScope(value = StackGresStream.class, kind = "Lease")
@ApplicationScoped
public class StreamLeaseReconciliationHandler
    extends FireAndForgetReconciliationHandler<StackGresStream> {

  @Inject
  public StreamLeaseReconciliationHandler(
      @ReconciliationScope(value = StackGresStream.class, kind = "HasMetadata")
      ReconciliationHandler<StackGresStream> handler) {
    super(handler);
  }

  @Override
  protected boolean canForget(StackGresStream context, HasMetadata resource) {
    return true;
  }

}
