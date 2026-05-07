/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseSpec;

/**
 * Helpers for interpreting and producing {@link Lease} objects used as distributed locks.
 *
 * <p>The lock holder identity is encoded as {@code "<serviceAccount>/<podName>"} in the
 * {@link LeaseSpec#getHolderIdentity() holderIdentity} field. The lease is considered held
 * while {@code renewTime + leaseDurationSeconds > now}.
 */
public interface LeaseLockUtil {

  String LEASE_NAME_PREFIX_OPERATOR = "stackgres-operator";
  /**
   * Per-resource Lease names use the protected resource's lowercased Kind and UID:
   * {@code <kind>-<uid>} (e.g. {@code sgcluster-<uid>}). Using UID keeps the name length
   * deterministic — well within the 63-char DNS label limit even for SGShardedCluster
   * (longest prefix, 53 chars total) — and avoids collisions across resource recreations.
   */
  String LEASE_NAME_PREFIX_CLUSTER = "sgcluster-";
  String LEASE_NAME_PREFIX_SHARDED_CLUSTER = "sgshardedcluster-";
  String LEASE_NAME_PREFIX_STREAM = "sgstream-";

  String HOLDER_IDENTITY_SEPARATOR = "/";

  static String holderIdentity(String serviceAccount, String podName) {
    return serviceAccount + HOLDER_IDENTITY_SEPARATOR + podName;
  }

  static Optional<String> getServiceAccountFromHolderIdentity(String holderIdentity) {
    if (holderIdentity == null) {
      return Optional.empty();
    }
    int sep = holderIdentity.indexOf(HOLDER_IDENTITY_SEPARATOR);
    if (sep < 0) {
      return Optional.empty();
    }
    return Optional.of(holderIdentity.substring(0, sep));
  }

  static Optional<String> getPodFromHolderIdentity(String holderIdentity) {
    if (holderIdentity == null) {
      return Optional.empty();
    }
    int sep = holderIdentity.indexOf(HOLDER_IDENTITY_SEPARATOR);
    if (sep < 0) {
      return Optional.empty();
    }
    return Optional.of(holderIdentity.substring(sep + HOLDER_IDENTITY_SEPARATOR.length()));
  }

  /**
   * {@code sgcluster-<uid>} — 46 chars.
   **/
  static String leaseNameForCluster(String clusterUid) {
    return LEASE_NAME_PREFIX_CLUSTER + clusterUid;
  }

  /**
   * {@code sgshardedcluster-<uid>} — 53 chars.
   **/
  static String leaseNameForShardedCluster(String shardedClusterUid) {
    return LEASE_NAME_PREFIX_SHARDED_CLUSTER + shardedClusterUid;
  }

  /**
   * {@code sgstream-<uid>} — 45 chars.
   **/
  static String leaseNameForStream(String streamUid) {
    return LEASE_NAME_PREFIX_STREAM + streamUid;
  }

  static boolean isHeld(Lease lease) {
    return isHeld(lease, ZonedDateTime.now(ZoneOffset.UTC));
  }

  static boolean isHeld(Lease lease, ZonedDateTime checkTime) {
    return Optional.ofNullable(lease)
        .map(Lease::getSpec)
        .filter(spec -> spec.getHolderIdentity() != null
            && !spec.getHolderIdentity().isEmpty()
            && spec.getRenewTime() != null
            && spec.getLeaseDurationSeconds() != null)
        .map(spec -> spec.getRenewTime()
            .plus(Duration.ofSeconds(spec.getLeaseDurationSeconds()))
            .isAfter(checkTime))
        .orElse(false);
  }

  static boolean isHeldBy(Lease lease, String holderIdentity) {
    return isHeldBy(lease, holderIdentity, ZonedDateTime.now(ZoneOffset.UTC));
  }

  static boolean isHeldBy(Lease lease, String holderIdentity, ZonedDateTime checkTime) {
    if (holderIdentity == null) {
      return false;
    }
    return Optional.ofNullable(lease)
        .map(Lease::getSpec)
        .filter(spec -> holderIdentity.equals(spec.getHolderIdentity())
            && spec.getRenewTime() != null
            && spec.getLeaseDurationSeconds() != null)
        .map(spec -> spec.getRenewTime()
            .plus(Duration.ofSeconds(spec.getLeaseDurationSeconds()))
            .isAfter(checkTime))
        .orElse(false);
  }

  static Optional<String> getHolderIdentity(Lease lease) {
    return Optional.ofNullable(lease)
        .map(Lease::getSpec)
        .map(LeaseSpec::getHolderIdentity)
        .filter(holder -> !holder.isEmpty());
  }

  static Optional<String> getServiceAccount(Lease lease) {
    return getHolderIdentity(lease)
        .flatMap(LeaseLockUtil::getServiceAccountFromHolderIdentity);
  }

  /**
   * Builder for the Lease name of a cluster-controller protected resource based on
   * its kind and UID.
   */
  static String leaseNameFor(HasMetadata resource) {
    String kind = resource.getKind();
    String uid = resource.getMetadata().getUid();
    return switch (kind) {
      case "SGCluster" -> leaseNameForCluster(uid);
      case "SGShardedCluster" -> leaseNameForShardedCluster(uid);
      case "SGStream" -> leaseNameForStream(uid);
      default -> throw new IllegalArgumentException(
          "No Lease naming convention defined for kind " + kind);
    };
  }

}
