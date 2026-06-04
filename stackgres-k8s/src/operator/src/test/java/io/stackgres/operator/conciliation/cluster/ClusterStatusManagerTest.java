/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.stackgres.common.crd.Condition;
import io.stackgres.common.crd.sgcluster.ClusterStatusCondition;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.common.crd.sgcluster.StackGresClusterInstalledExtension;
import io.stackgres.common.crd.sgcluster.StackGresClusterStatus;
import io.stackgres.common.fixture.Fixtures;
import io.stackgres.common.labels.LabelFactoryForCluster;
import io.stackgres.common.patroni.PatroniCtl;
import io.stackgres.common.patroni.PatroniCtlInstance;
import io.stackgres.common.resource.CustomResourceFinder;
import io.stackgres.common.resource.ResourceFinder;
import io.stackgres.common.resource.ResourceScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClusterStatusManagerTest {

  @Mock
  private LabelFactoryForCluster labelFactory;
  @Mock
  private CustomResourceFinder<io.stackgres.common.crd.sgscript.StackGresScript> scriptFinder;
  @Mock
  private ResourceFinder<StatefulSet> statefulSetFinder;
  @Mock
  private ResourceScanner<Pod> podScanner;
  @Mock
  private PatroniCtl patroniCtl;
  @Mock
  private PatroniCtlInstance patroniCtlInstance;

  private ClusterStatusManager statusManager;
  private StackGresCluster cluster;

  @BeforeEach
  void setUp() {
    statusManager = new ClusterStatusManager(
        labelFactory, scriptFinder, statefulSetFinder, podScanner, patroniCtl);
    cluster = Fixtures.cluster().loadDefault().get();
    // Pre-populate status fields that the appenders would set during context-building.
    if (cluster.getStatus() == null) {
      cluster.setStatus(new StackGresClusterStatus());
    }
    cluster.getStatus().setPostgresVersion("16.4");
    cluster.getStatus().setExtensions(List.of());

    lenient().when(statefulSetFinder.findByNameAndNamespace(any(), any()))
        .thenReturn(Optional.empty());
    lenient().when(podScanner.getResourcesInNamespaceWithLabels(any(), any()))
        .thenReturn(List.of());
    lenient().when(patroniCtl.instanceFor(any())).thenReturn(patroniCtlInstance);
    lenient().when(patroniCtlInstance.list()).thenReturn(List.of());
    lenient().when(labelFactory.clusterLabels(any())).thenReturn(Map.of("app", "test"));
  }

  @Test
  void givenLatestMinorAndLatestMajorAndNoExtensionUpgrades_isUpToDate() {
    cluster.getStatus().setLatestPostgresMinor(null);
    cluster.getStatus().setLatestPostgresMajor(null);

    statusManager.refreshCondition(cluster);

    final Condition condition = getComponentsUpdatedCondition(cluster);
    assertEquals(ClusterStatusCondition.Status.TRUE.getStatus(), condition.getStatus());
    assertEquals("UpToDate", condition.getReason());
    assertTrue(
        condition.getMessage().contains("PostgreSQL 16.4 is the latest minor version for major 16"),
        condition.getMessage());
  }

  @Test
  void givenLatestMinorAndNewerMajorAvailable_isNotLatestMajor() {
    cluster.getStatus().setLatestPostgresMinor(null);
    cluster.getStatus().setLatestPostgresMajor("17.2");

    statusManager.refreshCondition(cluster);

    final Condition condition = getComponentsUpdatedCondition(cluster);
    assertEquals(ClusterStatusCondition.Status.TRUE.getStatus(), condition.getStatus());
    assertEquals("NotLatestMajor", condition.getReason());
    assertTrue(
        condition.getMessage().contains("A newer major version 17.2 is available"),
        condition.getMessage());
  }

  @Test
  void givenLatestMinorAndExtensionUpgradesAvailable_isAvailableExtensionsUpgrade() {
    cluster.getStatus().setLatestPostgresMinor(null);
    cluster.getStatus().setLatestPostgresMajor(null);
    cluster.getStatus().setExtensions(List.of(
        installedExtension("pgvector", "0.5.1", "0.6.0"),
        installedExtension("plpgsql", "1.0.0", null)));

    statusManager.refreshCondition(cluster);

    final Condition condition = getComponentsUpdatedCondition(cluster);
    assertEquals(ClusterStatusCondition.Status.TRUE.getStatus(), condition.getStatus());
    assertEquals("AvailableExtensionsUpgrade", condition.getReason());
    assertTrue(condition.getMessage().contains("1 extension can be upgraded"),
        condition.getMessage());
    assertTrue(condition.getMessage().contains("pgvector (0.5.1->0.6.0)"),
        condition.getMessage());
  }

  @Test
  void givenLatestMinorAndNewerMajorAndExtensions_isNotLatestMajorAndAvailableExtensionsUpgrade() {
    cluster.getStatus().setLatestPostgresMinor(null);
    cluster.getStatus().setLatestPostgresMajor("17.2");
    cluster.getStatus().setExtensions(List.of(
        installedExtension("pgvector", "0.5.1", "0.6.0")));

    statusManager.refreshCondition(cluster);

    final Condition condition = getComponentsUpdatedCondition(cluster);
    assertEquals(ClusterStatusCondition.Status.TRUE.getStatus(), condition.getStatus());
    assertEquals("NotLatestMajorAnd-AvailableExtensionsUpgrade", condition.getReason());
  }

  @Test
  void givenNotLatestMinorOnly_isNotLatestMinor() {
    cluster.getStatus().setLatestPostgresMinor("16.5");
    cluster.getStatus().setLatestPostgresMajor(null);

    statusManager.refreshCondition(cluster);

    final Condition condition = getComponentsUpdatedCondition(cluster);
    assertEquals(ClusterStatusCondition.Status.FALSE.getStatus(), condition.getStatus());
    assertEquals("NotLatestMinor", condition.getReason());
    assertTrue(condition.getMessage().contains(
        "PostgreSQL 16.4 is not the latest minor version for major 16 (latest is 16.5)"),
        condition.getMessage());
  }

  @Test
  void givenNotLatestMinorAndNewerMajor_isNotLatestMinorDashNotLatestMajor() {
    cluster.getStatus().setLatestPostgresMinor("16.5");
    cluster.getStatus().setLatestPostgresMajor("17.2");

    statusManager.refreshCondition(cluster);

    final Condition condition = getComponentsUpdatedCondition(cluster);
    assertEquals(ClusterStatusCondition.Status.FALSE.getStatus(), condition.getStatus());
    assertEquals("NotLatestMinor-NotLatestMajor", condition.getReason());
    assertTrue(
        condition.getMessage().contains("A newer major version 17.2 is available"),
        condition.getMessage());
  }

  @Test
  void givenAllThreeUpgradesAvailable_picksFullCombinedReason() {
    cluster.getStatus().setLatestPostgresMinor("16.5");
    cluster.getStatus().setLatestPostgresMajor("17.2");
    cluster.getStatus().setExtensions(List.of(
        installedExtension("pgvector", "0.5.1", "0.6.0"),
        installedExtension("postgis", "3.3", "3.5"),
        installedExtension("plpgsql", "1.0.0", null)));

    statusManager.refreshCondition(cluster);

    final Condition condition = getComponentsUpdatedCondition(cluster);
    assertEquals(ClusterStatusCondition.Status.FALSE.getStatus(), condition.getStatus());
    assertEquals(
        "NotLatestMinor-NotLatestMajor-AvailableExtensionsUpgrade", condition.getReason());
    assertTrue(condition.getMessage().contains("2 extensions can be upgraded"),
        condition.getMessage());
    assertTrue(condition.getMessage().contains("pgvector (0.5.1->0.6.0)"),
        condition.getMessage());
    assertTrue(condition.getMessage().contains("postgis (3.3->3.5)"),
        condition.getMessage());
  }

  @Test
  void givenMissingPostgresVersion_doesNotSetTheCondition() {
    cluster.getStatus().setPostgresVersion(null);

    statusManager.refreshCondition(cluster);

    assertEquals(
        0L,
        cluster.getStatus().getConditions().stream()
            .filter(c -> ClusterStatusCondition.Type.COMPONENTS_UPDATED.getType()
                .equals(c.getType()))
            .count());
  }

  private static Condition getComponentsUpdatedCondition(StackGresCluster cluster) {
    final Condition condition = cluster.getStatus().getConditions().stream()
        .filter(c -> ClusterStatusCondition.Type.COMPONENTS_UPDATED.getType()
            .equals(c.getType()))
        .findFirst()
        .orElse(null);
    assertNotNull(condition, "ComponentsUpdated condition not found");
    return condition;
  }

  private static StackGresClusterInstalledExtension installedExtension(
      String name, String version, String latest) {
    final StackGresClusterInstalledExtension extension =
        new StackGresClusterInstalledExtension();
    extension.setName(name);
    extension.setPublisher("com.ongres");
    extension.setRepository("https://extensions.stackgres.io/postgres/repository");
    extension.setVersion(version);
    extension.setPostgresVersion("16");
    extension.setLatest(latest);
    return extension;
  }

}
