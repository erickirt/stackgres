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
import io.fabric8.kubernetes.api.model.ObjectMeta;

public abstract class ConditionUpdater<T extends HasMetadata, C extends Condition> {

  public static final int MAX_MESSAGE_LENGTH = 4096;

  public void updateCondition(C condition, T context) {
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
          && Objects.equals(existing.get().getMessage(), condition.getMessage())) {
        // Nothing changed: preserve the observed generation and skip the write. Updating the
        // observed generation here would bump metadata.generation on resources without a status
        // subresource, causing an endless reconciliation loop.
        condition.setObservedGeneration(existing.get().getObservedGeneration());
        return;
      }
    } else {
      // The status changed (or the condition is new): record the transition time.
      condition.setLastTransitionTime(Instant.now().toString());
    }

    // A transition, reason or message change is being persisted: record the generation observed
    // for this change.
    condition.setObservedGeneration(Optional.ofNullable(context.getMetadata())
        .map(ObjectMeta::getGeneration)
        .orElse(null));

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
