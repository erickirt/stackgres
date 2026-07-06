#!/bin/sh

UPSTREAM_NAME="OperatorHub"
UPSTREAM_GIT_URL="https://github.com/k8s-operatorhub/community-operators"
FORK_GIT_URL="${FORK_GIT_URL:-$1}"
PROJECT_NAME="stackgres"
DO_PIN_IMAGES=false
PREVIOUS_VERSION=none
DO_ADD_FBC=false
# Set to true after the FBC onboarding PR for this operator has been merged.
# While false, deploy.sh performs the one-time onboarding (no new version).
ONBOARDING_DONE=false

. "$(dirname "$0")/deploy.sh"
