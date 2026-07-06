/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.common.kubernetesclient;

import static io.stackgres.common.RetryUtil.retry;
import static io.stackgres.common.RetryUtil.retryWithLimit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ListMeta;
import io.fabric8.kubernetes.api.model.ListOptionsBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.Listable;
import io.stackgres.common.OperatorProperty;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface KubernetesClientUtil {

  static Logger LOGGER = LoggerFactory.getLogger(KubernetesClientUtil.class);

  /**
   * Default number of resources retrieved on each paginated LIST call. May be overridden through
   * the {@code stackgres.listLimit} property. A value lesser than or equal to {@code 0} disables
   * pagination falling back to a single unbounded LIST call.
   */
  static int DEFAULT_LIST_LIMIT = 500;

  /**
   * Return true when exception is a conflict (409) error.
   */
  static boolean isConflict(Throwable ex) {
    final boolean isConflict = ex instanceof KubernetesClientException kce
        && kce.getCode() == Response.Status.CONFLICT.getStatusCode()
        // The HTTP 409 conflict may be returned when a resource has been removed
        //  making the function to loop forever since such situation can not be recovered
        //  by simply retrying. To avoid this situation we have to filter the error
        //  message.
        && kce.getMessage().contains("the object has been modified");
    if (isConflict && LOGGER.isTraceEnabled()) {
      LOGGER.trace("A conflict error occurred", ex);
    }
    return isConflict;
  }

  /**
   * Retry on conflict (409) error with back-off.
   */
  static void retryOnConflict(Runnable runnable) {
    retryOnConflict((Supplier<Void>) () -> {
      runnable.run();
      return null;
    });
  }

  /**
   * Retry on conflict (409) error with back-off.
   */
  static <T> T retryOnConflict(Supplier<T> supplier) {
    return retry(supplier, KubernetesClientUtil::isConflict,
        OperatorProperty.CONFLICT_INITIAL_SLEEP_MILLISECONDS.getIntOrDefault(10),
        OperatorProperty.CONFLICT_MAX_SLEEP_MILLISECONDS.getIntOrDefault(600),
        OperatorProperty.CONFLICT_SLEEP_MILLISECONDS.getIntOrDefault(10));
  }

  /**
   * Retry on error.
   */
  static void retryOnError(Runnable runnable, int retryLimit) {
    retryOnError((Supplier<Void>) () -> {
      runnable.run();
      return null;
    }, retryLimit);
  }

  /**
   * Retry on error.
   */
  static <T> T retryOnError(Supplier<T> supplier, int retryLimit) {
    return retryWithLimit(supplier, ex -> true, retryLimit,
        OperatorProperty.ERROR_INITIAL_SLEEP_MILLISECONDS.getIntOrDefault(3000),
        OperatorProperty.ERROR_MAX_SLEEP_MILLISECONDS.getIntOrDefault(30000),
        OperatorProperty.ERROR_SLEEP_MILLISECONDS.getIntOrDefault(1000));
  }

  /**
   * List all the resources matching the given operation transparently paginating the Kubernetes
   * API {@code LIST} call using the {@code limit} and {@code continue} parameters.
   *
   * <p>This turns a single unbounded LIST (served directly from etcd and potentially very large)
   * into multiple bounded LIST calls, reducing the memory and load pressure on the Kubernetes API
   * server and avoiding request timeouts on large installations.</p>
   *
   * <p>The page size is taken from the {@code stackgres.listLimit} property (defaulting to
   * {@link #DEFAULT_LIST_LIMIT}). When it is lesser than or equal to {@code 0} pagination is
   * disabled and a single unbounded LIST is performed.</p>
   *
   * <p>Any label or field selector already applied to the operation (e.g. through
   * {@code withLabels(...)}) is preserved on each paginated call.</p>
   */
  @SuppressWarnings("unchecked")
  static <T extends HasMetadata> List<T> listPaginated(
      Listable<? extends KubernetesResourceList<T>> listable) {
    return (List<T>) listPaginatedHasMetadata(listable);
  }

  /**
   * Same as {@link #listPaginated(Listable)} but returning a {@code List<HasMetadata>}. Useful when
   * the concrete resource type can not be inferred (e.g. when the operation is typed with a wildcard
   * upper bounded by {@link HasMetadata}).
   */
  static List<HasMetadata> listPaginatedHasMetadata(
      Listable<? extends KubernetesResourceList<? extends HasMetadata>> listable) {
    final int limit = OperatorProperty.LIST_LIMIT.getIntOrDefault(DEFAULT_LIST_LIMIT);
    if (limit <= 0) {
      return new ArrayList<>(listable.list().getItems());
    }
    final List<HasMetadata> items = new ArrayList<>();
    String continueToken = null;
    do {
      final KubernetesResourceList<? extends HasMetadata> list = listable.list(
          new ListOptionsBuilder()
          .withLimit((long) limit)
          .withContinue(continueToken)
          .build());
      final List<? extends HasMetadata> pageItems = list.getItems();
      if (pageItems != null) {
        items.addAll(pageItems);
      }
      continueToken = Optional.ofNullable(list.getMetadata())
          .map(ListMeta::getContinue)
          .filter(Predicate.not(String::isBlank))
          .orElse(null);
    } while (continueToken != null);
    return items;
  }

  static <I> List<I> listOrEmptyOnForbiddenOrNotFound(Supplier<List<I>> supplier) {
    try {
      return supplier.get();
    } catch (KubernetesClientException ex) {
      if (ex.getCode() == Response.Status.FORBIDDEN.getStatusCode()
          || ex.getCode() == Response.Status.NOT_FOUND.getStatusCode()) {
        LOGGER.trace("An error occurred, returning an empty list", ex);
        return List.of();
      }
      throw ex;
    }
  }

}
