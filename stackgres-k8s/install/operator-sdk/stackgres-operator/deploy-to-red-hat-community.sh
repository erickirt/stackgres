#!/bin/sh

UPSTREAM_NAME="Red Hat Community"
UPSTREAM_GIT_URL="https://github.com/redhat-openshift-ecosystem/community-operators-prod"
FORK_GIT_URL="${FORK_GIT_URL:-$1}"
PROJECT_NAME="stackgres-community"
RENAME_CSV=true
DO_PIN_IMAGES=true
OPERATOR_BUNDLE_IMAGE_TAG_SUFFIX=-openshift
DO_ADD_FBC=true
# Set to true after the FBC onboarding PR for this operator has been merged.
# While false, deploy.sh performs the one-time onboarding (no new version).
ONBOARDING_DONE=true

. "$(dirname "$0")/deploy.sh"
