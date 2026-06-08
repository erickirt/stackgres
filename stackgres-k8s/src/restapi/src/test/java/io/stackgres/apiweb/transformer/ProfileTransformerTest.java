/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.transformer;

import java.util.List;
import java.util.Optional;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import io.stackgres.apiweb.dto.profile.ProfileDto;
import io.stackgres.apiweb.dto.profile.ProfileSpec;
import io.stackgres.apiweb.dto.profile.ProfileStatus;
import io.stackgres.common.KubernetesTestServerSetup;
import io.stackgres.common.crd.sgprofile.StackGresInstanceProfile;
import io.stackgres.common.crd.sgprofile.StackGresInstanceProfileSpec;
import io.stackgres.testutil.StringUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@WithKubernetesTestServer(setup = KubernetesTestServerSetup.class)
@QuarkusTest
class ProfileTransformerTest {

  @Inject
  ProfileTransformer transformer;

  public static TransformerTuple<ProfileDto, StackGresInstanceProfile> createProfile() {

    StackGresInstanceProfile source = new StackGresInstanceProfile();
    ProfileDto target = new ProfileDto();

    var metadata = TransformerTestUtil.createMetadataTuple();
    source.setMetadata(metadata.source());
    target.setMetadata(metadata.target());

    var spec = createSpec();
    source.setSpec(spec.source());
    target.setSpec(spec.target());

    target.setStatus(new ProfileStatus());
    target.getStatus().setClusters(List.of(StringUtils.getRandomResourceName()));

    return new TransformerTuple<>(target, source);
  }

  private static TransformerTuple<ProfileSpec, StackGresInstanceProfileSpec> createSpec() {
    TransformerTuple<ProfileSpec, StackGresInstanceProfileSpec> tuple = TransformerTestUtil
        .fillTupleWithRandomData(
            ProfileSpec.class,
            StackGresInstanceProfileSpec.class
        );

    return tuple;
  }

  @Test
  void testProfileTransformation() {
    var tuple = createProfile();

    final List<String> clusters = Optional.of(tuple.target())
        .map(ProfileDto::getStatus)
        .map(ProfileStatus::getClusters).orElse(List.of());

    TransformerTestUtil.assertTransformation(transformer, tuple, clusters);
  }
}
