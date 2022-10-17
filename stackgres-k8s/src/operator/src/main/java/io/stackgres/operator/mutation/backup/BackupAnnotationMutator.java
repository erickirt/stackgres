/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.mutation.backup;

import java.util.Map;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;

import io.stackgres.common.StackGresContext;
import io.stackgres.common.StackGresVersion;
import io.stackgres.common.crd.sgbackup.StackGresBackup;
import io.stackgres.operator.common.BackupReview;
import io.stackgres.operator.mutation.AbstractAnnotationMutator;

@ApplicationScoped
public class BackupAnnotationMutator
    extends AbstractAnnotationMutator<StackGresBackup, BackupReview>
    implements BackupMutator {

  private static final long VERSION_1_2 = StackGresVersion.V_1_2.getVersionAsNumber();

  @Override
  public Optional<Map<String, String>> getAnnotationsToOverwrite(StackGresBackup resource) {
    final long version = StackGresVersion.getStackGresVersionAsNumber(resource);
    if (VERSION_1_2 > version) {
      return Optional.of(Map.of(StackGresContext.VERSION_KEY, StackGresVersion.V_1_2.getVersion()));
    }
    return Optional.empty();
  }

}
