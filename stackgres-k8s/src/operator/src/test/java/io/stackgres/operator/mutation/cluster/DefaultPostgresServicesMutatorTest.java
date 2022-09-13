/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.mutation.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.JsonPatchOperation;
import io.smallrye.common.constraint.Assert;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgcluster.StackGresClusterPostgresService;
import io.stackgres.common.crd.sgcluster.StackGresClusterPostgresServices;
import io.stackgres.operator.common.StackGresClusterReview;
import io.stackgres.operator.common.fixture.AdmissionReviewFixtures;
import io.stackgres.operatorframework.admissionwebhook.Operation;
import io.stackgres.testutil.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

class DefaultPostgresServicesMutatorTest {

  protected static final JsonMapper JSON_MAPPER = JsonUtil.jsonMapper();

  protected static final JavaPropsMapper PROPS_MAPPER = new JavaPropsMapper();

  private StackGresClusterReview review;
  private DefaultPostgresServicesMutator mutator;

  @BeforeEach
  void setUp() throws NoSuchFieldException, IOException {
    review = AdmissionReviewFixtures.cluster().loadCreate().get();

    mutator = new DefaultPostgresServicesMutator();
    mutator.setObjectMapper(JSON_MAPPER);
    mutator.init();
  }

  @Test
  void reviewWithNotMappedOperation_shouldReturnEmptyListOfServices() {
    review.getRequest().setOperation(Operation.DELETE);
    StackGresCluster actualCluster = mutate(review);
    Assert.assertTrue(actualCluster.getSpec().getPostgresServices() == null);
  }

  @Test
  void clusterWithPostgresService_shouldSetNothing() {
    StackGresClusterPostgresService primary = new StackGresClusterPostgresService();
    StackGresClusterPostgresService replica = new StackGresClusterPostgresService();
    primary.setEnabled(Boolean.TRUE);
    replica.setEnabled(Boolean.FALSE);
    primary.setType("LoadBalancing");
    replica.setType("ClusterIP");

    setPostgresServices(primary, replica);
    StackGresCluster actualCluster = mutate(review);
    StackGresClusterPostgresServices pgServices = actualCluster.getSpec().getPostgresServices();

    assertEquals(Boolean.TRUE, pgServices.getPrimary().getEnabled());
    assertEquals("LoadBalancing", pgServices.getPrimary().getType());
    assertEquals(Boolean.FALSE, pgServices.getReplicas().getEnabled());
    assertEquals("ClusterIP", pgServices.getReplicas().getType());
  }

  @Test
  void clusterWithPostgresServiceNoType_shouldSetClusterIP() {
    StackGresClusterPostgresService primary = new StackGresClusterPostgresService();
    StackGresClusterPostgresService replica = new StackGresClusterPostgresService();
    primary.setEnabled(Boolean.TRUE);
    replica.setEnabled(Boolean.FALSE);

    setPostgresServices(primary, replica);
    StackGresCluster actualCluster = mutate(review);
    StackGresClusterPostgresServices pgServices = actualCluster.getSpec().getPostgresServices();

    assertEquals(Boolean.TRUE, pgServices.getPrimary().getEnabled());
    assertEquals("ClusterIP", pgServices.getPrimary().getType());
    assertEquals(Boolean.FALSE, pgServices.getReplicas().getEnabled());
    assertEquals("ClusterIP", pgServices.getReplicas().getType());
  }

  @Test
  void clusterWithNoPostgresService_shouldSetValue() {
    StackGresClusterReview review = AdmissionReviewFixtures.cluster().loadCreate().get();
    review.getRequest().getObject().getSpec().setPostgresServices(null);

    StackGresCluster actualCluster = mutate(review);

    StackGresClusterPostgresServices pgServices = actualCluster.getSpec().getPostgresServices();
    assertEquals(Boolean.TRUE, pgServices.getPrimary().getEnabled());
    assertEquals("ClusterIP", pgServices.getPrimary().getType());
    assertEquals(Boolean.TRUE, pgServices.getReplicas().getEnabled());
    assertEquals("ClusterIP", pgServices.getReplicas().getType());
  }

  @Test
  void clusterWithPostgresServiceNoPrimary_shouldSetValue() {
    StackGresClusterPostgresService replica = new StackGresClusterPostgresService();
    replica.setEnabled(Boolean.FALSE);
    replica.setType("LoadBalancing");

    setPostgresServices(null, replica);
    StackGresCluster actualCluster = mutate(review);
    StackGresClusterPostgresServices pgServices = actualCluster.getSpec().getPostgresServices();

    assertEquals(Boolean.TRUE, pgServices.getPrimary().getEnabled());
    assertEquals("ClusterIP", pgServices.getPrimary().getType());
    assertEquals(Boolean.FALSE, pgServices.getReplicas().getEnabled());
    assertEquals("LoadBalancing", pgServices.getReplicas().getType());
  }

  @Test
  void clusterWithPostgresServiceNoReplicas_shouldSetValue() {
    StackGresClusterPostgresService primary = new StackGresClusterPostgresService();
    primary.setEnabled(Boolean.FALSE);
    primary.setType("LoadBalancing");

    setPostgresServices(primary, null);
    StackGresCluster actualCluster = mutate(review);
    StackGresClusterPostgresServices pgServices = actualCluster.getSpec().getPostgresServices();

    assertEquals(Boolean.FALSE, pgServices.getPrimary().getEnabled());
    assertEquals("LoadBalancing", pgServices.getPrimary().getType());
    assertEquals(Boolean.TRUE, pgServices.getReplicas().getEnabled());
    assertEquals("ClusterIP", pgServices.getReplicas().getType());
  }

  @Test
  void clusterWithPostgresServiceNoEnabled_shouldSetValue() {
    StackGresClusterPostgresService primary = new StackGresClusterPostgresService();
    primary.setType("LoadBalancing");

    setPostgresServices(primary, null);
    StackGresCluster actualCluster = mutate(review);
    StackGresClusterPostgresServices pgServices = actualCluster.getSpec().getPostgresServices();

    assertEquals(Boolean.TRUE, pgServices.getPrimary().getEnabled());
    assertEquals("LoadBalancing", pgServices.getPrimary().getType());
    assertEquals(Boolean.TRUE, pgServices.getReplicas().getEnabled());
    assertEquals("ClusterIP", pgServices.getReplicas().getType());
  }

  private void setPostgresServices(StackGresClusterPostgresService primary,
      StackGresClusterPostgresService replica) {
    StackGresClusterPostgresServices postgresServices = new StackGresClusterPostgresServices();
    postgresServices.setPrimary(primary);
    postgresServices.setReplicas(replica);

    review.getRequest().getObject().getSpec().setPostgresServices(postgresServices);
  }

  private StackGresCluster mutate(StackGresClusterReview review) {
    try {
      List<JsonPatchOperation> operations = mutator.mutate(review);
      JsonNode crJson = JSON_MAPPER.valueToTree(review.getRequest().getObject());
      JsonNode newConfig = new JsonPatch(operations).apply(crJson);
      return JSON_MAPPER.treeToValue(newConfig, StackGresCluster.class);
    } catch (JsonPatchException | JsonProcessingException | IllegalArgumentException e) {
      throw new AssertionFailedError(e.getMessage(), e);
    }
  }
}
