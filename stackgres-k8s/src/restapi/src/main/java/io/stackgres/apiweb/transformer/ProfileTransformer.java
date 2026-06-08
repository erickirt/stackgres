/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.transformer;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.stackgres.apiweb.dto.profile.ProfileDto;
import io.stackgres.apiweb.dto.profile.ProfileSpec;
import io.stackgres.apiweb.dto.profile.ProfileStatus;
import io.stackgres.common.crd.sgprofile.StackGresInstanceProfile;
import io.stackgres.common.crd.sgprofile.StackGresInstanceProfileSpec;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ProfileTransformer
    extends AbstractDependencyResourceTransformer<ProfileDto, StackGresInstanceProfile> {

  private final ObjectMapper mapper;

  @Inject
  public ProfileTransformer(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public StackGresInstanceProfile toCustomResource(ProfileDto source, StackGresInstanceProfile original) {
    StackGresInstanceProfile transformation = Optional.ofNullable(original)
        .map(o -> mapper.convertValue(original, StackGresInstanceProfile.class))
        .orElseGet(StackGresInstanceProfile::new);
    transformation.setMetadata(getCustomResourceMetadata(source, original));
    transformation.setSpec(getCustomResourceSpec(source.getSpec()));
    return transformation;
  }

  @Override
  public ProfileDto toResource(StackGresInstanceProfile source, List<String> clusters) {
    ProfileDto transformation = new ProfileDto();
    transformation.setMetadata(getResourceMetadata(source));
    transformation.setSpec(getResourceSpec(source.getSpec()));
    if (transformation.getStatus() == null) {
      transformation.setStatus(new ProfileStatus());
    }
    transformation.getStatus().setClusters(clusters);
    return transformation;
  }

  private StackGresInstanceProfileSpec getCustomResourceSpec(ProfileSpec source) {
    return mapper.convertValue(source, StackGresInstanceProfileSpec.class);
  }

  private ProfileSpec getResourceSpec(StackGresInstanceProfileSpec source) {
    return mapper.convertValue(source, ProfileSpec.class);
  }

}
