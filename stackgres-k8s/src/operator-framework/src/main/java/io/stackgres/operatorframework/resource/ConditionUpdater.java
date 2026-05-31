/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operatorframework.resource;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;

public abstract class ConditionUpdater<T extends HasMetadata, C extends Condition> {

  public static final int MAX_MESSAGE_LENGTH = 4096;

  public void updateCondition(C condition, T context) {
    // observedGeneration is intentionally never recorded. StackGres CRDs do not enable the status
    // subresource, so every write to status increments metadata.generation. A persisted
    // observedGeneration could therefore never catch up to metadata.generation, and kubectl (which
    // requires observedGeneration >= generation whenever the field is present) would never consider
    // the condition met, e.g. `kubectl wait --for=condition=...` would always fail. Keeping the
    // field absent lets kubectl match on the condition status alone.
    condition.setObservedGeneration(null);

    if (condition.getMessage() != null
        && condition.getMessage().length() > MAX_MESSAGE_LENGTH) {
      condition.setMessage(condition.getMessage().substring(0, MAX_MESSAGE_LENGTH));
    }

    Optional<C> existing = getConditions(context).stream()
        .filter(c -> Objects.equals(c.getType(), condition.getType()))
        .findFirst();

    if (existing.isPresent()
        && Objects.equals(existing.get().getStatus(), condition.getStatus())) {
      // The status did not change: this is not a transition, so keep the transition time
      // (Kubernetes semantics).
      condition.setLastTransitionTime(Optional.ofNullable(existing.get().getLastTransitionTime())
          .orElseGet(() -> Instant.now().toString()));
      if (Objects.equals(existing.get().getReason(), condition.getReason())
          && Objects.equals(existing.get().getMessage(), condition.getMessage())
          && existing.get().getObservedGeneration() == null) {
        // Nothing changed and there is no stale observedGeneration left over from a previous
        // version to clear: skip the write to avoid bumping metadata.generation.
        return;
      }
      // Either reason/message changed, or a stale observedGeneration must be cleared: fall through
      // and persist the condition (with observedGeneration unset).
    } else {
      // The status changed (or the condition is new): record the transition time.
      condition.setLastTransitionTime(Instant.now().toString());
    }

    // copy list of current conditions removing the one being replaced
    List<C> copyList =
        getConditions(context).stream()
            .filter(c -> !condition.getType().equals(c.getType()))
            .collect(Collectors.toList());

    copyList.addFirst(condition);

    setConditions(context, copyList);
  }

  protected abstract List<C> getConditions(T context);

  protected abstract void setConditions(T context, List<C> conditions);

}
