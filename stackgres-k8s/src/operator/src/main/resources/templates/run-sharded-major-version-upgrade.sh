#!/bin/sh

run_op() {
  set -e

  update_status init

  echo "Starting sharded dbops $NORMALIZED_OP_NAME"

  local DBOPS_STATUS_SET
  DBOPS_STATUS_SET="$(kubectl get "$SHARDED_CLUSTER_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_CLUSTER_NAME" \
    --template='{{ if .status.dbOps }}{{ if .status.dbOps.majorVersionUpgrade }}true{{ end }}{{ end }}')"
  if [ "$DBOPS_STATUS_SET" != true ]
  then
    echo "Setting $NORMALIZED_OP_NAME status for $SHARDED_CLUSTER_CRD_KIND $SHARDED_CLUSTER_NAME"
    if ! kubectl patch "$SHARDED_CLUSTER_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_CLUSTER_NAME" --type merge \
      -p "{\"status\":{\"dbOps\":{\"majorVersionUpgrade\":{\"sourcePostgresVersion\":$(printf %s "$SOURCE_POSTGRES_VERSION" | to_json_string),\"targetPostgresVersion\":$(printf %s "$POSTGRES_VERSION" | to_json_string),\"sourceSgPostgresConfig\":$(printf %s "$SOURCE_SG_POSTGRES_CONFIG" | to_json_string)}}}}" \
      > /tmp/dbops-update-sharded-cluster 2>&1
    then
      echo "FAILURE=$NORMALIZED_OP_NAME failed. Can not update $SHARDED_CLUSTER_CRD_KIND status: $(cat /tmp/dbops-update-sharded-cluster)" >> "$SHARED_PATH/$KEBAB_OP_NAME.out"
      exit 1
    fi
  else
    SOURCE_POSTGRES_VERSION="$(kubectl get "$SHARDED_CLUSTER_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_CLUSTER_NAME" \
      --template='{{ .status.dbOps.majorVersionUpgrade.sourcePostgresVersion }}')"
  fi

  echo "Setting postgres version $POSTGRES_VERSION and SGPostgresConfig $SG_POSTGRES_CONFIG for $SHARDED_CLUSTER_CRD_KIND $SHARDED_CLUSTER_NAME"
  if ! kubectl patch "$SHARDED_CLUSTER_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_CLUSTER_NAME" --type merge \
    -p "{\"spec\":{\"postgres\":{\"version\":$(printf %s "$POSTGRES_VERSION" | to_json_string)},\"coordinator\":{\"configurations\":{\"sgPostgresConfig\":$(printf %s "$SG_POSTGRES_CONFIG" | to_json_string)}},\"workers\":{\"configurations\":{\"sgPostgresConfig\":$(printf %s "$SG_POSTGRES_CONFIG" | to_json_string)}}}}" \
    > /tmp/dbops-update-sharded-cluster 2>&1
  then
    echo "FAILURE=$NORMALIZED_OP_NAME failed. Can not update $SHARDED_CLUSTER_CRD_KIND: $(cat /tmp/dbops-update-sharded-cluster)" >> "$SHARED_PATH/$KEBAB_OP_NAME.out"
    exit 1
  fi

  rm -f /tmp/current-dbops /tmp/completed-dbops
  local CLUSTER_NAME
  local DBOPS_NAME
  local BACKUP_PATH
  local OPTIONAL_FIELDS
  local INDEX=1
  for CLUSTER_NAME in $CLUSTER_NAMES
  do
    DBOPS_NAME="${SHARDED_DBOPS_NAME}-${CLUSTER_NAME#${SHARDED_CLUSTER_NAME}-}"
    echo "Creating $DBOPS_CRD_KIND $DBOPS_NAME for $CLUSTER_CRD_KIND $CLUSTER_NAME"
    echo "$DBOPS_NAME" >> /tmp/current-dbops
    BACKUP_PATH="$(printf %s "$BACKUP_PATHS" | cut -d ' ' -f "$INDEX")"
    OPTIONAL_FIELDS=""
    if [ -n "$BACKUP_PATH" ]
    then
      OPTIONAL_FIELDS="$OPTIONAL_FIELDS
    backupPath: $(printf %s "$BACKUP_PATH" | to_json_string)"
    fi
    if [ -n "$POSTGRES_EXTENSIONS_JSON" ]
    then
      OPTIONAL_FIELDS="$OPTIONAL_FIELDS
    postgresExtensions: $POSTGRES_EXTENSIONS_JSON"
    fi
    if [ -n "$TO_INSTALL_POSTGRES_EXTENSIONS_JSON" ]
    then
      OPTIONAL_FIELDS="$OPTIONAL_FIELDS
    toInstallPostgresExtensions: $TO_INSTALL_POSTGRES_EXTENSIONS_JSON"
    fi
    if [ -n "$MAX_ERRORS_AFTER_UPGRADE" ]
    then
      OPTIONAL_FIELDS="$OPTIONAL_FIELDS
    maxErrorsAfterUpgrade: $MAX_ERRORS_AFTER_UPGRADE"
    fi
    DBOPS_YAML="$(cat << EOF
apiVersion: $DBOPS_CRD_APIVERSION
kind: $DBOPS_CRD_KIND
metadata:
  namespace: $CLUSTER_NAMESPACE
  name: $DBOPS_NAME
  labels: $DBOPS_LABELS_JSON
  ownerReferences:
  - apiVersion: $SHARDED_DBOPS_CRD_APIVERSION
    kind: $SHARDED_DBOPS_CRD_KIND
    name: $SHARDED_DBOPS_NAME
    uid: $SHARDED_DBOPS_UID
spec:
  sgCluster: $CLUSTER_NAME
  op: majorVersionUpgrade
  majorVersionUpgrade:
    postgresVersion: $(printf %s "$POSTGRES_VERSION" | to_json_string)
    sgPostgresConfig: $(printf %s "$SG_POSTGRES_CONFIG" | to_json_string)
    link: $LINK
    clone: $CLONE
    check: $CHECK$OPTIONAL_FIELDS
EOF
)"
    if ! printf %s "$DBOPS_YAML" | kubectl replace --force -f - > /tmp/dbops-create-dbops 2>&1
    then
      echo "FAILURE=$NORMALIZED_OP_NAME failed. Can not create SGDbOps: $(cat /tmp/dbops-create-dbops)" >> "$SHARED_PATH/$KEBAB_OP_NAME.out"
      exit 1
    fi
    cat /tmp/dbops-create-dbops
    INDEX="$((INDEX + 1))"
  done

  echo "Waiting for SGDbOps $(cat /tmp/current-dbops | tr '\n' ' ' | tr -s ' ') to complete"
  touch /tmp/completed-dbops
  while true
  do
    local COMPLETED=true
    for DBOPS_NAME in $(cat /tmp/current-dbops)
    do
      if ! grep -qxF "$DBOPS_NAME" /tmp/completed-dbops
      then
        DBOPS_STATUS="$(kubectl get "$DBOPS_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$DBOPS_NAME" \
          --template '{{ range .status.conditions }}{{ if eq .status "True" }} {{ .type }} {{ end }}{{ end }}')"
        if ! printf %s "$DBOPS_STATUS" | grep -q " \($DBOPS_COMPLETED\|$DBOPS_FAILED\) "
        then
          COMPLETED=false
          continue
        fi
        printf '%s\n' "$DBOPS_NAME" >> /tmp/completed-dbops
        update_status
        if printf %s "$DBOPS_STATUS" | grep -q " $DBOPS_FAILED "
        then
          echo "...$DBOPS_NAME failed"
          echo "FAILURE=$NORMALIZED_OP_NAME failed. SGDbOps $DBOPS_NAME failed" >> "$SHARED_PATH/$KEBAB_OP_NAME.out"
          exit 1
        fi
        echo "...$DBOPS_NAME completed"
      fi
    done
    if "$COMPLETED"
    then
      break
    fi
    sleep 2
  done

  echo "Removing $NORMALIZED_OP_NAME status from $SHARDED_CLUSTER_CRD_KIND $SHARDED_CLUSTER_NAME"
  if ! kubectl patch "$SHARDED_CLUSTER_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_CLUSTER_NAME" --type json \
    -p '[{"op":"remove","path":"/status/dbOps"}]' \
    > /tmp/dbops-update-sharded-cluster 2>&1
  then
    echo "FAILURE=$NORMALIZED_OP_NAME failed. Can not update $SHARDED_CLUSTER_CRD_KIND status: $(cat /tmp/dbops-update-sharded-cluster)" >> "$SHARED_PATH/$KEBAB_OP_NAME.out"
    exit 1
  fi

  echo "Sharded DbOps $NORMALIZED_OP_NAME completed"
}

update_status() {
  if [ "$1" = "init" ]
  then
    PENDING_TO_RESTART_CLUSTERS="$CLUSTER_NAMES"
    RESTARTED_CLUSTERS=""
  else
    DBOPS_STATUSES="$(kubectl get "$DBOPS_CRD_NAME" -n "$CLUSTER_NAMESPACE" -l "$DBOPS_LABELS" \
      --template '{{ range .items }}{{ .spec.sgCluster }}/{{ range .status.conditions }}{{ if eq .status "True" }} {{ .type }} {{ end }}{{ end }}{{ "\n" }}{{ end }}')"
    PENDING_TO_RESTART_CLUSTERS="$(echo "$CLUSTER_NAMES" | tr ' ' '\n' | grep -vxF '' \
      | while read CLUSTER
        do
          if ! printf '%s' "$DBOPS_STATUSES" | cut -d / -f 1 | grep -q "^$CLUSTER$" \
            || ! printf '%s' "$DBOPS_STATUSES" | grep -q "^$CLUSTER/.* $DBOPS_COMPLETED .*$"
          then
            echo "$CLUSTER"
          fi
        done)"
    RESTARTED_CLUSTERS="$(echo "$CLUSTER_NAMES" | tr ' ' '\n' \
      | while read CLUSTER
        do
          if printf '%s' "$DBOPS_STATUSES" | grep -q "^$CLUSTER/.* $DBOPS_COMPLETED .*$"
          then
            echo "$CLUSTER"
          fi
        done)"
  fi
  PENDING_TO_RESTART_CLUSTERS_COUNT="$(echo "$PENDING_TO_RESTART_CLUSTERS" | tr ' ' 's' | tr '\n' ' ' | wc -w)"
  echo "Pending to $NORMALIZED_OP_NAME clusters:"
  if [ "$PENDING_TO_RESTART_CLUSTERS_COUNT" = 0 ]
  then
    echo '<none>'
  else
    echo "$PENDING_TO_RESTART_CLUSTERS" | tr ' ' '\n' | grep -vxF '' | sed 's/^/ - /'
  fi
  echo

  OPERATION="$(kubectl get "$SHARDED_DBOPS_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_DBOPS_NAME" \
    --template='{{ if .status.majorVersionUpgrade }}replace{{ else }}add{{ end }}')"
  kubectl patch "$SHARDED_DBOPS_CRD_NAME" -n "$CLUSTER_NAMESPACE" "$SHARDED_DBOPS_NAME" --type=json \
    -p "$(cat << EOF
[
  {"op":"$OPERATION","path":"/status/majorVersionUpgrade","value":{
      "sourcePostgresVersion": $(printf %s "$SOURCE_POSTGRES_VERSION" | to_json_string),
      "targetPostgresVersion": $(printf %s "$POSTGRES_VERSION" | to_json_string),
      "pendingToRestartSgClusters": [$(
        FIRST=true
        for CLUSTER in $PENDING_TO_RESTART_CLUSTERS
        do
          if "$FIRST"
          then
            printf '%s' "\"$CLUSTER\""
            FIRST=false
          else
            printf '%s' ",\"$CLUSTER\""
          fi
        done
        )],
      "restartedSgClusters": [$(
        FIRST=true
        for CLUSTER in $RESTARTED_CLUSTERS
        do
          if "$FIRST"
          then
            printf '%s' "\"$CLUSTER\""
            FIRST=false
          else
            printf '%s' ",\"$CLUSTER\""
          fi
        done
        )]
    }
  }
]
EOF
    )"
}
