/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation.cluster;

import static io.stackgres.operatorframework.resource.ResourceUtil.getServiceAccountFromUsername;
import static io.stackgres.operatorframework.resource.ResourceUtil.isServiceAccountUsername;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.stackgres.common.ErrorType;
import io.stackgres.common.LeaseLockUtil;
import io.stackgres.common.OperatorProperty;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgdistributedlogs.StackGresDistributedLogs;
import io.stackgres.common.resource.LeaseFinder;
import io.stackgres.operator.common.StackGresClusterReview;
import io.stackgres.operator.configuration.OperatorPropertyContext;
import io.stackgres.operator.validation.ValidationType;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationFailed;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@ValidationType(ErrorType.FORBIDDEN_CLUSTER_UPDATE)
public class LockValidator implements ClusterValidator {

  final ObjectMapper objectMapper;
  final LeaseFinder leaseFinder;
  final int duration;
  final String operatorServiceAccount;

  @Inject
  public LockValidator(OperatorPropertyContext operatorPropertyContext,
      ObjectMapper objectMapper,
      LeaseFinder leaseFinder) {
    this.duration = operatorPropertyContext.getInt(OperatorProperty.LOCK_DURATION);
    this.operatorServiceAccount = operatorPropertyContext.getString(OperatorProperty.OPERATOR_NAMESPACE)
        + operatorPropertyContext.getString(OperatorProperty.OPERATOR_SERVICE_ACCOUNT);
    this.objectMapper = objectMapper;
    this.leaseFinder = leaseFinder;
  }

  @Override
  public void validate(StackGresClusterReview review) throws ValidationFailed {
    switch (review.getRequest().getOperation()) {
      case UPDATE: {
        StackGresCluster cluster = review.getRequest().getObject();
        StackGresCluster oldCluster = review.getRequest().getOldObject();
        if (Objects.equals(objectMapper.valueToTree(cluster.getSpec()),
            objectMapper.valueToTree(oldCluster.getSpec()))) {
          return;
        }
        Optional<Lease> lease = leaseFinder.findByNameAndNamespace(
            LeaseLockUtil.leaseNameForCluster(cluster.getMetadata().getUid()),
            cluster.getMetadata().getNamespace());
        if (lease.isPresent() && LeaseLockUtil.isHeld(lease.get())) {
          String username = review.getRequest().getUserInfo().getUsername();
          if ((
                username == null
                || !isServiceAccountUsername(username)
                || !Objects.equals(
                    LeaseLockUtil.getServiceAccount(lease.get()).orElse(null),
                    getServiceAccountFromUsername(username))
                )
              && ! (
                Objects.equals(username, operatorServiceAccount)
                && Optional.ofNullable(cluster.getMetadata().getOwnerReferences())
                .stream()
                .flatMap(List::stream)
                .anyMatch(ownerReference -> Objects.equals(
                    ownerReference.getApiVersion(),
                    HasMetadata.getApiVersion(StackGresDistributedLogs.class))
                    && Objects.equals(
                        ownerReference.getKind(),
                        HasMetadata.getKind(StackGresDistributedLogs.class)))
                  )
              ) {
            fail("SGCluster update is forbidden. It is locked by some SGBackup or SGDbOps"
                + " that is currently running. Please, wait for the operation to finish,"
                + " stop the operation by deleting it or wait for the lock duration of "
                + duration + " seconds to expire.");
          }
        }
        break;
      }
      default:
    }

  }

}
