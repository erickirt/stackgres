/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.cluster;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.operator.conciliation.FireAndForgetReconciliationHandler;
import io.stackgres.operator.conciliation.ReconciliationHandler;
import io.stackgres.operator.conciliation.ReconciliationScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Pre-creates the Lease used for distributed locking but never updates it once it exists.
 * Workers (DbOps/Backup pods) own the spec and update it via CAS through kubectl/the
 * Java client; the operator must not clobber their renew timestamps.
 */
@ReconciliationScope(value = StackGresCluster.class, kind = "Lease")
@ApplicationScoped
public class ClusterLeaseReconciliationHandler
    extends FireAndForgetReconciliationHandler<StackGresCluster> {

  @Inject
  public ClusterLeaseReconciliationHandler(
      @ReconciliationScope(value = StackGresCluster.class, kind = "HasMetadata")
      ReconciliationHandler<StackGresCluster> handler) {
    super(handler);
  }

  @Override
  protected boolean canForget(StackGresCluster context, HasMetadata resource) {
    return true;
  }

}
