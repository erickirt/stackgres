/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.distributedlogs.context;

import java.util.Optional;

import io.stackgres.common.crd.sgdistributedlogs.StackGresDistributedLogs;
import io.stackgres.common.crd.sgprofile.StackGresInstanceProfile;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.operator.conciliation.ContextAppender;
import io.stackgres.operator.conciliation.distributedlogs.StackGresDistributedLogsContext.Builder;
import io.stackgres.operator.initialization.DefaultProfileFactory;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DistributedLogsInstanceProfileContextAppender
    extends ContextAppender<StackGresDistributedLogs, Builder> {

  private final CustomResourceFinder<StackGresInstanceProfile> profileFinder;
  private final DefaultProfileFactory defaultProfileFactory;

  public DistributedLogsInstanceProfileContextAppender(
      CustomResourceFinder<StackGresInstanceProfile> profileFinder,
      DefaultProfileFactory defaultProfileFactory) {
    this.profileFinder = profileFinder;
    this.defaultProfileFactory = defaultProfileFactory;
  }

  @Override
  public void appendContext(StackGresDistributedLogs cluster, Builder contextBuilder) {
    final Optional<StackGresInstanceProfile> profile = profileFinder
        .findByNameAndNamespace(
            cluster.getSpec().getSgInstanceProfile(),
            cluster.getMetadata().getNamespace());
    if (!cluster.getSpec().getSgInstanceProfile().equals(defaultProfileFactory.getDefaultResourceName(cluster))
        && profile.isEmpty()) {
      throw new IllegalArgumentException(
          StackGresInstanceProfile.KIND + " " + cluster.getSpec().getSgInstanceProfile() + " was not found");
    }
    contextBuilder.profile(profile);
  }

}
