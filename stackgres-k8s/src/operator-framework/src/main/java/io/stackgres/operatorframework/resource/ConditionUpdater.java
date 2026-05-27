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

  public void updateCondition(C condition, T context) {
    condition.setObservedGeneration(Optional.ofNullable(context.getMetadata())
        .map(ObjectMeta::getGeneration)
        .orElse(null));

    Optional<C> existing = getConditions(context).stream()
        .filter(c -> Objects.equals(c.getType(), condition.getType()))
        .findFirst();

    if (existing.isPresent()
        && Objects.equals(existing.get().getStatus(), condition.getStatus())) {
      // The status did not change: keep the transition time (Kubernetes semantics) and only
      // replace the condition when its reason, message or observed generation changed.
      condition.setLastTransitionTime(Optional.ofNullable(existing.get().getLastTransitionTime())
          .orElseGet(() -> Instant.now().toString()));
      if (Objects.equals(existing.get().getReason(), condition.getReason())
          && Objects.equals(existing.get().getMessage(), condition.getMessage())
          && Objects.equals(existing.get().getObservedGeneration(),
              condition.getObservedGeneration())) {
        return;
      }
    } else {
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
