#!/bin/bash

PROJECT_PATH=../../../../
TARGET_PATH=target

cd "$(dirname "$0")"

mkdir -p "$TARGET_PATH"

STACKGRES_ALL_VERSIONS="$(docker run --rm regclient/regctl tag ls quay.io/stackgres/operator-bundle)"
STACKGRES_VERSIONS="$(printf %s "$STACKGRES_ALL_VERSIONS" \
  | grep '^1\.[0-9]\+\.[0-9]\+$' | sort -V -r)"
STACKGRES_RC_VERSIONS="$(printf %s "$STACKGRES_ALL_VERSIONS" \
  | grep '^1\.[0-9]\+\.[0-9]\+-rc[0-9]\+$' | sort -V -r)"
LATEST_STACKGRES_VERSION="$(printf %s "$STACKGRES_VERSIONS" | head -n 1)"
LATEST_STACKGRES_RC_VERSION="$(printf %s "$STACKGRES_RC_VERSIONS" | head -n 1)"
if [ "$(printf '%s\n%s\n' "$LATEST_STACKGRES_VERSION" "${LATEST_STACKGRES_RC_VERSION%-rc*}" | sort -V -r | head -n 1)" = "${LATEST_STACKGRES_RC_VERSION%-rc*}" ]
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
  for BUNDLE_STACKGRES_VERSION in $STACKGRES_VERSIONS
  do
    BUNDLE_IMAGE_NAME="quay.io/stackgres/operator-bundle:$BUNDLE_STACKGRES_VERSION$BUNDLE_TYPE"
    opm render "$BUNDLE_IMAGE_NAME" \
      --output=yaml >> "$CATALOG_PATH/operator-catalog/operator.yaml"
  done
  for BUNDLE_STACKGRES_VERSION in $STACKGRES_RC_VERSIONS
  do
    BUNDLE_IMAGE_NAME="quay.io/stackgres/operator-bundle:$BUNDLE_STACKGRES_VERSION$BUNDLE_TYPE"
    opm render "$BUNDLE_IMAGE_NAME" \
      --output=yaml >> "$CATALOG_PATH/operator-catalog/operator.yaml"
  done
  cat << EOF >> "$CATALOG_PATH/operator-catalog/operator.yaml"
---
schema: olm.channel
package: stackgres
name: stable
entries:
$(
  PREVIOUS_BUNDLE_STACKGRES_VERSION=
  for BUNDLE_STACKGRES_VERSION in $STACKGRES_VERSIONS
  do
    if [ -n "$PREVIOUS_BUNDLE_STACKGRES_VERSION" ]
    then
      cat << INNER_EOF
  - name: stackgres.v$PREVIOUS_BUNDLE_STACKGRES_VERSION
    replaces: stackgres.v$BUNDLE_STACKGRES_VERSION
INNER_EOF
    fi
    PREVIOUS_BUNDLE_STACKGRES_VERSION="$BUNDLE_STACKGRES_VERSION"
  done
      cat << INNER_EOF
  - name: stackgres.v$PREVIOUS_BUNDLE_STACKGRES_VERSION
INNER_EOF
)
---
schema: olm.channel
package: stackgres
name: candidate
entries:
$(
  PREVIOUS_BUNDLE_STACKGRES_VERSION=
  for BUNDLE_STACKGRES_VERSION in $STACKGRES_RC_VERSIONS
  do
    if [ -n "$PREVIOUS_BUNDLE_STACKGRES_VERSION" ]
    then
      cat << INNER_EOF
  - name: stackgres.v$PREVIOUS_BUNDLE_STACKGRES_VERSION
    replaces: stackgres.v$BUNDLE_STACKGRES_VERSION
INNER_EOF
    fi
    PREVIOUS_BUNDLE_STACKGRES_VERSION="$BUNDLE_STACKGRES_VERSION"
  done
      cat << INNER_EOF
  - name: stackgres.v$PREVIOUS_BUNDLE_STACKGRES_VERSION
INNER_EOF
)
EOF
  opm validate "$CATALOG_PATH/operator-catalog"
  (
  cd "$CATALOG_PATH"
  docker build . \
    -f "operator-catalog.Dockerfile" \
    -t "$CATALOG_IMAGE_NAME"
  )
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