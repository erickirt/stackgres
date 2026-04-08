/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.cluster.common;

import static io.stackgres.common.patroni.StackGresPasswordKeys.RESTAPI_PASSWORD_KEY;
import static io.stackgres.common.patroni.StackGresPasswordKeys.RESTAPI_USERNAME_KEY;

import java.util.Optional;
import java.util.regex.Pattern;

import com.ongres.process.FluentProcess;
import io.fabric8.kubernetes.api.model.Secret;
import io.stackgres.common.ClusterContext;
import io.stackgres.common.resource.ResourceFinder;
import io.stackgres.operatorframework.resource.ResourceUtil;
import org.jooq.lambda.tuple.Tuple;

public interface PatroniCommandUtil {

  Pattern PATRONI_COMMAND_PATTERN =
      Pattern.compile("^(/[^/]+)+/python[^ ]* (/[^/]+)+/patroni .*$");

  static void reloadPatroniConfig() {
    final String patroniPid = findPatroniPid();
    FluentProcess.start("sh", "-c",
        String.format("kill -s HUP %s", patroniPid)).join();
  }

  static String findPatroniPid() {
    return ProcessHandle.allProcesses()
        .filter(process -> process.info().commandLine()
            .map(command -> PATRONI_COMMAND_PATTERN.matcher(command).matches())
            .orElse(false))
        .map(ProcessHandle::pid)
        .map(String::valueOf)
        .findAny()
        .orElseThrow(() -> new IllegalStateException(
            "Process with pattern " + PATRONI_COMMAND_PATTERN + " not found"));
  }

  static Credentials getPatorniCredentials(
      ClusterContext context, ResourceFinder<Secret> secretFinder) {
    Secret secret = secretFinder.findByNameAndNamespace(
        context.getCluster().getMetadata().getName(),
        context.getCluster().getMetadata().getNamespace())
        .orElseThrow(() -> new RuntimeException("Can not find secret "
            + context.getCluster().getMetadata().getName()));
    return Optional.of(secret).map(Secret::getData)
        .filter(data -> data.get(RESTAPI_USERNAME_KEY) != null
            && data.get(RESTAPI_PASSWORD_KEY) != null)
        .map(data -> Tuple.tuple(
            data.get(RESTAPI_USERNAME_KEY),
            data.get(RESTAPI_PASSWORD_KEY))
            .map1(ResourceUtil::decodeSecret)
            .map2(ResourceUtil::decodeSecret))
        .map(t -> new Credentials(t.v1, t.v2))
        .orElseThrow(() -> new RuntimeException("Can not find key "
            + RESTAPI_PASSWORD_KEY + " and/or " + RESTAPI_USERNAME_KEY + " in secret "
            + context.getCluster().getMetadata().getName()));
  }

  record Credentials(String username, String password) {
  }

}
