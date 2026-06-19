/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.backup;

import java.time.Duration;
import java.util.Optional;

/**
 * Helpers to convert the backup retry configuration (`retryDelay`, `retryLimit`,
 * `retryMaxDelay`) into the values expected by the `RETRY_DELAY`, `RETRY_LIMIT` and
 * `RETRY_MAX_DELAY` env vars consumed by the backup scripts (delays in milliseconds), applying
 * the same defaults as `shell-utils` (1000ms / 10 / 60000ms).
 */
public interface BackupRetry {

  static String getRetryDelay(String retryDelay) {
    return Optional.ofNullable(retryDelay)
        .map(Duration::parse)
        .map(Duration::toMillis)
        .map(Object::toString)
        .orElse("1000");
  }

  static String getRetryLimit(Integer retryLimit) {
    return Optional.ofNullable(retryLimit)
        .map(Object::toString)
        .orElse("10");
  }

  static String getRetryMaxDelay(String retryMaxDelay) {
    return Optional.ofNullable(retryMaxDelay)
        .map(Duration::parse)
        .map(Duration::toMillis)
        .map(Object::toString)
        .orElse("60000");
  }

}
