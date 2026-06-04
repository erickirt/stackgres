#!/bin/bash

PROJECT_PATH=../../../../
TARGET_PATH=target

cd "$(dirname "$0")"

mkdir -p "$TARGET_PATH"

STACKGRES_ALL_VERSIONS="$(docker run --rm regclient/regctl tag ls quay.io/stackgres/operator-bundle)"
STACKGRES_VERSIONS="$({ printf '%s\n' "$STACKGRES_ALL_VERSIONS"; } 2>/dev/null \
  | grep '^1\.[0-9]\+\.[0-9]\+$' | sort -V)"
STACKGRES_RC_VERSIONS="$({ printf '%s\n' "$STACKGRES_ALL_VERSIONS"; } 2>/dev/null \
  | grep '^1\.[0-9]\+\.[0-9]\+-rc[0-9]\+$' | sort -V)"
STACKGRES_WITH_RC_VERSIONS="$({ printf '%s\n' "$STACKGRES_VERSIONS"; } 2>/dev/null \
  | {
    PREVIOUS_BUNDLE_VERSION=
    while read BUNDLE_VERSION
    do
      if [ "${PREVIOUS_BUNDLE_VERSION%.*}" != "${BUNDLE_VERSION%.*}" ]
      then
        { printf '%s\n' "$STACKGRES_RC_VERSIONS"; } 2>/dev/null | grep "^${BUNDLE_VERSION%.*}\.[0-9]\+-rc"
      fi
      echo "$BUNDLE_VERSION"
      PREVIOUS_BUNDLE_VERSION="$BUNDLE_VERSION"
    done
    })"
LATEST_STACKGRES_VERSION="$({ printf '%s\n' "$STACKGRES_VERSIONS"; } 2>/dev/null | tail -n 1)"
LATEST_STACKGRES_RC_VERSION="$({ printf '%s\n' "$STACKGRES_RC_VERSIONS"; } 2>/dev/null | tail -n 1)"
if [ "$(printf '%s\n%s\n' "$LATEST_STACKGRES_VERSION" "${LATEST_STACKGRES_RC_VERSION%-rc*}" | sort -V | tail -n 1)" = "${LATEST_STACKGRES_RC_VERSION%-rc*}" ]
then
  LATEST_STACKGRES_VERSION="$LATEST_STACKGRES_RC_VERSION"
fi

build_catalog() {
  BUNDLE_TYPE="${1:-}"
  CATALOG_IMAGE_NAME="quay.io/stackgres/operator-catalog:$LATEST_STACKGRES_VERSION$BUNDLE_TYPE"
  CATALOG_PATH="$TARGET_PATH/operator-catalog"
  rm -rf "$CATALOG_PATH"
  mkdir -p "$CATALOG_PATH/operator-catalog"
  opm generate dockerfile "$CATALOG_PATH/operator-catalog"
  echo "StackGres Operator Catalog $LATEST_STACKGRES_VERSION" > "$CATALOG_PATH/README.md"
  opm init stackgres \
    --default-channel=stable \
    --description="$CATALOG_PATH/README.md" \
    --output yaml > "$CATALOG_PATH/operator-catalog/operator.yaml"
  STACKGRES_ENTRIES="$(get_stable_entries)"
  STACKGRES_WITH_RC_ENTRIES="$(get_candidate_entries)"
  for BUNDLE_VERSION in $STACKGRES_VERSIONS
  do
    if ! { printf '%s\n' "$STACKGRES_ENTRIES"; } 2>/dev/null | grep -q "^$BUNDLE_VERSION "
    then
      continue
    fi
    BUNDLE_IMAGE_NAME="quay.io/stackgres/operator-bundle:$BUNDLE_VERSION$BUNDLE_TYPE"
    opm render "$BUNDLE_IMAGE_NAME" \
      --output=yaml >> "$CATALOG_PATH/operator-catalog/operator.yaml"
  done
  for BUNDLE_VERSION in $STACKGRES_RC_VERSIONS
  do
    if ! { printf '%s\n' "$STACKGRES_WITH_RC_ENTRIES"; } 2>/dev/null | grep -q "^$BUNDLE_VERSION "
    then
      continue
    fi
    BUNDLE_IMAGE_NAME="quay.io/stackgres/operator-bundle:$BUNDLE_VERSION$BUNDLE_TYPE"
    opm render "$BUNDLE_IMAGE_NAME" \
      --output=yaml >> "$CATALOG_PATH/operator-catalog/operator.yaml"
  done
  cat << EOF >> "$CATALOG_PATH/operator-catalog/operator.yaml"
---
$(get_channel stable "$STACKGRES_ENTRIES")
---
$(get_channel candidate "$STACKGRES_WITH_RC_ENTRIES")
EOF
  opm validate "$CATALOG_PATH/operator-catalog"
  (
  cd "$CATALOG_PATH"
  docker build . \
    -f "operator-catalog.Dockerfile" \
    -t "$CATALOG_IMAGE_NAME"
  )
}

get_channels() {
  CATALOG_PATH="$TARGET_PATH/operator-catalog"
  rm -rf "$CATALOG_PATH"
  mkdir -p "$CATALOG_PATH/operator-catalog"
  STACKGRES_ENTRIES="$(get_stable_entries)"
  STACKGRES_WITH_RC_ENTRIES="$(get_candidate_entries)"
  cat << EOF
---
$(get_channel stable "$STACKGRES_ENTRIES")
---
$(get_channel candidate "$STACKGRES_ENTRIES")
EOF
}

get_bundles() {
  BUNDLE_TYPE="${BUNDLE_TYPE:-}"
  CATALOG_PATH="$TARGET_PATH/operator-catalog"
  rm -rf "$CATALOG_PATH"
  mkdir -p "$CATALOG_PATH/operator-catalog"
  STACKGRES_WITH_RC_ENTRIES="$(get_candidate_entries)"
  { printf '%s\n' "$STACKGRES_WITH_RC_ENTRIES"; } 2>/dev/null > "$CATALOG_PATH/all-entries"
  while read BUNDLE_VERSION BUNDLE_REPLACES BUNDLE_SKIPS
  do
    BUNDLE_IMAGE_NAME="quay.io/stackgres/operator-bundle:$BUNDLE_VERSION$BUNDLE_TYPE"
    cat << EOF
- image: ${BUNDLE_IMAGE_NAME}
  schema: olm.bundle
EOF
  done < "$CATALOG_PATH/all-entries"
}

get_stable_entries() {
  get_entries "$STACKGRES_VERSIONS"
}

get_candidate_entries() {
  get_entries "$STACKGRES_WITH_RC_VERSIONS"
}

get_entries() {
  local VERSIONS="$1"
  PREVIOUS_BUNDLE_VERSION=
  for BUNDLE_VERSION in $VERSIONS
  do
    if [ "${PREVIOUS_BUNDLE_VERSION%.*}" != "${BUNDLE_VERSION%.*}" ]
    then
      SKIPS_BUNDLE_VERSIONS=
      PREVIOUS_MINOR_BUNDLE_VERSION="$PREVIOUS_BUNDLE_VERSION"
      LAST_BUNDLE_VERSION="$({ printf '%s\n' "$VERSIONS"; } 2>/dev/null | tr ' ' '\n' | grep "^${BUNDLE_VERSION%.*}\." | tail -n 1)"
    else
      SKIPS_BUNDLE_VERSIONS="$SKIPS_BUNDLE_VERSIONS $PREVIOUS_BUNDLE_VERSION"
    fi
    if [ "$BUNDLE_VERSION" != "$LAST_BUNDLE_VERSION" ]
    then
      PREVIOUS_BUNDLE_VERSION="$BUNDLE_VERSION"
      continue
    fi
    if [ -z "$PREVIOUS_BUNDLE_VERSION" ]
    then
      echo "$BUNDLE_VERSION"
    else
      REPLACES_BUNDLE_VERSION="$PREVIOUS_MINOR_BUNDLE_VERSION"
      echo "$BUNDLE_VERSION $REPLACES_BUNDLE_VERSION $SKIPS_BUNDLE_VERSIONS"
    fi
    PREVIOUS_BUNDLE_VERSION="$BUNDLE_VERSION"
  done
}

get_channel() {
  local NAME="$1"
  local ENTRIES="$2"
  { printf '%s\n' "$ENTRIES"; } 2>/dev/null > "$CATALOG_PATH/$NAME-entries"
  cat << EOF
schema: olm.channel
package: stackgres
name: $NAME
entries:
$(
  while read BUNDLE_ENTRY
  do
    cat << INNER_EOF
$(get_channel_entry $BUNDLE_ENTRY)
INNER_EOF
  done < "$CATALOG_PATH/$NAME-entries"
)
EOF
}

get_channel_entry() {
  local BUNDLE_VERSION="$1"
  if [ "$#" -gt 1 ]
  then
    local BUNDLE_REPLACES_VERSION="$2"
    shift 2
    local BUNDLE_SKIPS_VERSIONS="$*"
  else
    local BUNDLE_REPLACES_VERSION=
    local BUNDLE_SKIPS_VERSIONS=
  fi
  cat << EOF
  - name: stackgres.v$BUNDLE_VERSION
EOF
  if [ -n "$BUNDLE_REPLACES_VERSION" ]
  then
    cat << EOF
    replaces: stackgres.v$BUNDLE_REPLACES_VERSION
    skips:
$(
    for SKIPS_BUNDLE_VERSION in $BUNDLE_SKIPS_VERSIONS
    do
      cat << INNER_EOF
      - stackgres.$SKIPS_BUNDLE_VERSION
INNER_EOF
    done
)
EOF
  fi
}

push_catalog() {
  BUNDLE_TYPE="${1:-}"
  CATALOG_IMAGE_NAME="quay.io/stackgres/operator-catalog:$LATEST_STACKGRES_VERSION$BUNDLE_TYPE"
  docker push --platform=linux/"$(uname -m | grep -qxF aarch64 && printf arm64 || printf amd64)" "$CATALOG_IMAGE_NAME"
}

create_catalog_source() {
  BUNDLE_TYPE="${1:-}"
  CATALOG_IMAGE_NAME="quay.io/stackgres/operator-catalog:$LATEST_STACKGRES_VERSION$BUNDLE_TYPE"
  cat << EOF | kubectl create -f -
  apiVersion: operators.coreos.com/v1alpha1
  kind: CatalogSource
  metadata:
    name: stackgres-catalog
    namespace: openshift-marketplace
  spec:
    sourceType: grpc
    image: ${CATALOG_IMAGE_NAME}
    displayName: StackGres v$LATEST_STACKGRES_VERSION Catalog
    publisher: OnGres
EOF
}

"$@"