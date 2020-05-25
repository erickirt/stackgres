/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.transformer;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;
import io.stackgres.apiweb.distributedlogs.dto.Metadata;
import io.stackgres.apiweb.distributedlogs.dto.ResourceDto;

public abstract class AbstractDependencyResourceTransformer<T extends ResourceDto,
    R extends CustomResource> implements DependencyResourceTransformer<T, R> {

  protected ObjectMeta getCustomResourceMetadata(T source, R original) {
    ObjectMeta metadata = original != null ? original.getMetadata() : new ObjectMeta();
    if (source.getMetadata() != null) {
      metadata.setNamespace(source.getMetadata().getNamespace());
      metadata.setName(source.getMetadata().getName());
      metadata.setUid(source.getMetadata().getUid());
    }
    return metadata;
  }

  protected Metadata getResourceMetadata(R source) {
    Metadata metadata = new Metadata();
    if (source.getMetadata() != null) {
      metadata.setNamespace(source.getMetadata().getNamespace());
      metadata.setName(source.getMetadata().getName());
      metadata.setUid(source.getMetadata().getUid());
    }
    return metadata;
  }

}
