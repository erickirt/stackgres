/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation;

import java.util.Optional;

import io.stackgres.operator.customresource.sgcluster.StackGresCluster;
import io.stackgres.operator.customresource.sgcluster.StackGresClusterDefinition;
import io.stackgres.operator.customresource.sgcluster.StackGresClusterList;
import io.stackgres.operator.resource.KubernetesResourceScanner;
import io.stackgres.operatorframework.AdmissionReview;
import io.stackgres.operatorframework.Operation;
import io.stackgres.operatorframework.ValidationFailed;
import io.stackgres.operatorframework.Validator;

public abstract class DependenciesValidator<T extends AdmissionReview<?>> implements Validator<T> {

  private KubernetesResourceScanner<StackGresClusterList> clusterScanner;

  public DependenciesValidator() {
  }

  public DependenciesValidator(KubernetesResourceScanner<StackGresClusterList> clusterScanner) {
    this.clusterScanner = clusterScanner;
  }

  @Override
  public void validate(T review) throws ValidationFailed {
    if (review.getRequest().getOperation() == Operation.DELETE) {
      Optional<StackGresClusterList> clusters = clusterScanner
          .findResources(review.getRequest().getNamespace());

      if (clusters.isPresent()) {
        for (StackGresCluster i : clusters.get().getItems()) {
          validate(review, i);
        }
      }
    }
  }

  protected abstract void validate(T review, StackGresCluster i) throws ValidationFailed;

  protected void fail(T review, StackGresCluster i) throws ValidationFailed {
    throw new ValidationFailed("Can't delete "
        + review.getRequest().getResource().getResource()
        + "." + review.getRequest().getKind().getGroup()
        + " " + review.getRequest().getName() + " because the "
        + StackGresClusterDefinition.NAME + " " + i.getMetadata().getName() + " depends on it");
  }
}
