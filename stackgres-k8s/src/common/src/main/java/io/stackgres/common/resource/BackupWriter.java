/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.resource;

import io.stackgres.common.crd.sgbackup.StackGresBackup;
import io.stackgres.common.crd.sgbackup.StackGresBackupList;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BackupWriter
    extends AbstractCustomResourceWriter<StackGresBackup, StackGresBackupList> {

  public BackupWriter() {
    super(StackGresBackup.class, StackGresBackupList.class);
  }

}
