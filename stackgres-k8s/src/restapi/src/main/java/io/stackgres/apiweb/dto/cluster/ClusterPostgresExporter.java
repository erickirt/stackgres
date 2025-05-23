/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.dto.cluster;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.stackgres.common.StackGresUtil;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ClusterPostgresExporter {

  private Map<String, Object> queries;

  public Map<String, Object> getQueries() {
    return queries;
  }

  public void setQueries(Map<String, Object> queries) {
    this.queries = queries;
  }

  public String toString() {
    return StackGresUtil.toPrettyYaml(this);
  }
}
