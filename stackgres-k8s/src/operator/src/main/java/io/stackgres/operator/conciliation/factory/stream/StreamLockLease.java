/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.factory.stream;

import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import io.stackgres.common.LeaseLockUtil;
import io.stackgres.common.crd.sgstream.StackGresStream;
import io.stackgres.common.labels.LabelFactoryForStream;
import io.stackgres.operator.conciliation.OperatorVersionBinder;
import io.stackgres.operator.conciliation.ResourceGenerator;
import io.stackgres.operator.conciliation.stream.StackGresStreamContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@OperatorVersionBinder
public class StreamLockLease implements ResourceGenerator<StackGresStreamContext> {

  private final LabelFactoryForStream labelFactory;

  @Inject
  public StreamLockLease(LabelFactoryForStream labelFactory) {
    this.labelFactory = labelFactory;
  }

  public static String name(StackGresStream stream) {
    return LeaseLockUtil.leaseNameForStream(stream.getMetadata().getUid());
  }

  @Override
  public Stream<HasMetadata> generateResource(StackGresStreamContext context) {
    final StackGresStream stream = context.getSource();
    return Stream.of(new LeaseBuilder()
        .withNewMetadata()
        .withNamespace(stream.getMetadata().getNamespace())
        .withName(name(stream))
        .withLabels(labelFactory.genericLabels(stream))
        .endMetadata()
        .withNewSpec()
        .endSpec()
        .build());
  }

}
