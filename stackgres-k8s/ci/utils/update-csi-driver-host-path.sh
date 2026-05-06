#!/usr/bin/env bash

# Regenerate the vendored csi-driver-host-path install.yaml files under
# stackgres-k8s/e2e/resources/csi-driver-host-path/. One install.yaml per
# (csi-driver-host-path version, deploy/kubernetes-X.Y) combination used by
# stackgres-k8s/e2e/envs/kind. Each output is the result of running the
# upstream deploy.sh through the same kubectlw mock the kind env uses
# (which rewrites every namespace to kube-system), but with kubectl
# captured to a file instead of applied to a cluster. The csi-snapshotter
# `--timeout=3m` arg is baked in afterwards so the kind script no longer
# needs the post-deploy `kubectl patch sts` step.

set -e
set -o pipefail

CI_UTILS_PATH="$(cd "$(dirname "$0")" && pwd)"
PROJECT_PATH="${PROJECT_PATH:-$CI_UTILS_PATH/../../..}"
RESOURCES_PATH="$PROJECT_PATH/stackgres-k8s/e2e/resources/csi-driver-host-path"

# Matrix: <csi-driver-host-path branch/tag> <deploy subdir> <output dir name>
# Output name format: <csi-version>-k8s-<X.Y> where X.Y is the deploy/kubernetes-X.Y
# directory the kind env points at for the given Kubernetes version range.
CONFIGS=(
  "v1.7.2  deploy/kubernetes-1.18  v1.7.2-k8s-1.18"
  "v1.7.3  deploy/kubernetes-1.19  v1.7.3-k8s-1.19"
  "v1.9.0  deploy/kubernetes-1.21  v1.9.0-k8s-1.21"
  "v1.10.0 deploy/kubernetes-1.22  v1.10.0-k8s-1.22"
  "v1.11.0 deploy/kubernetes-1.24  v1.11.0-k8s-1.24"
  "v1.11.0 deploy/kubernetes-1.25  v1.11.0-k8s-1.25"
  "v1.12.0 deploy/kubernetes-1.26  v1.12.0-k8s-1.26"
  "v1.12.0 deploy/kubernetes-1.27  v1.12.0-k8s-1.27"
  "master  deploy/kubernetes-1.30  master-k8s-1.30"
)

REAL_KUBECTL="$(command -v kubectl)"
if [ -z "$REAL_KUBECTL" ]; then
  echo "kubectl not found on PATH" >&2
  exit 1
fi
if ! command -v yq >/dev/null; then
  echo "yq (python-yq, the jq-based one) not found on PATH" >&2
  exit 1
fi
if ! command -v python3 >/dev/null; then
  echo "python3 not found on PATH" >&2
  exit 1
fi

render_one() {
  local CSI_VERSION="$1"
  local DEPLOY_DIR="$2"
  local OUTPUT_FILE="$3"

  local WORK
  WORK="$(mktemp -d)"
  trap 'rm -rf "$WORK"' RETURN

  echo "==> $CSI_VERSION / $DEPLOY_DIR -> $OUTPUT_FILE"
  git clone -b "$CSI_VERSION" --single-branch --depth 1 \
    https://github.com/kubernetes-csi/csi-driver-host-path \
    "$WORK/repo" 2>/dev/null

  local DEPLOY_PATH="$WORK/repo/$DEPLOY_DIR"
  if [ ! -d "$DEPLOY_PATH" ]; then
    echo "  $DEPLOY_DIR not present in csi-driver-host-path@$CSI_VERSION" >&2
    return 1
  fi

  # The kubectlw mirror of the one stackgres-k8s/e2e/envs/kind writes:
  # rewrites every applied resource's namespace to kube-system before delegating
  # to kubectl.
  cat > "$DEPLOY_PATH/kubectlw" <<'KEOF'
set -e
if [ "x$1" = xapply ]
then
  if [ "$#" = 3 ] && [ "x$2" = x--kustomize ]
  then
    for YAML in "$3"/*.yaml
    do
      yq -y -c '
          .
            | if (. | type) == "array" then .[] else . end
            | if (.|has("metadata")) then .metadata.namespace = "kube-system" else . end
          ' "$YAML"  \
        | sed 's#namespace: default#namespace: kube-system#' > "$YAML.tmp"
      mv "$YAML.tmp" "$YAML"
    done
    kubectl -n kube-system apply --kustomize "$3"
  elif [ "$#" = 3 ] && [ "x$2 $3" = 'x-f -' ]
  then
      yq -y -c '
          .
            | if (. | type) == "array" then .[] else . end
            | if (.|has("metadata")) then .metadata.namespace = "kube-system" else . end
          '  \
        | sed 's#namespace: default#namespace: kube-system#' \
        | kubectl -n kube-system apply -f -
  else
    printf '%s\n' "No mock for $*"
    exit 1
  fi
else
  kubectl -n kube-system "$@"
fi
KEOF

  # Fake kubectl: captures `apply` payloads instead of contacting a cluster,
  # passes through `kustomize`, and returns empty for `get` so the deploy.sh
  # readiness wait short-circuits.
  mkdir -p "$WORK/bin"
  cat > "$WORK/bin/kubectl" <<EOF
#!/bin/sh
REAL_KUBECTL="$REAL_KUBECTL"
RENDER_OUTPUT_FILE="$OUTPUT_FILE"
EOF
  cat >> "$WORK/bin/kubectl" <<'EOF'

if [ "$1" = "kustomize" ]; then
  exec "$REAL_KUBECTL" "$@"
fi

if [ "$1" = "-n" ] && [ "$2" = "kube-system" ]; then
  shift; shift
  case "$1" in
    apply)
      shift
      if [ "$1" = "--kustomize" ] && [ -n "$2" ]; then
        "$REAL_KUBECTL" kustomize "$2" >> "$RENDER_OUTPUT_FILE"
        printf -- '---\n' >> "$RENDER_OUTPUT_FILE"
        exit 0
      elif [ "$1" = "-f" ] && [ "$2" = "-" ]; then
        printf -- '---\n' >> "$RENDER_OUTPUT_FILE"
        cat >> "$RENDER_OUTPUT_FILE"
        exit 0
      fi
      echo "fake kubectl: no mock for apply $*" >&2
      exit 1
      ;;
    get)
      exit 0
      ;;
    *)
      exit 0
      ;;
  esac
fi

echo "fake kubectl: unexpected invocation: $*" >&2
exit 1
EOF
  chmod +x "$WORK/bin/kubectl"

  # Match the kind env's substitution: route every kubectl call in deploy.sh
  # through kubectlw.
  sed -i "s#kubectl#sh $DEPLOY_PATH/kubectlw#" "$DEPLOY_PATH/deploy.sh"

  mkdir -p "$(dirname "$OUTPUT_FILE")"
  cat > "$OUTPUT_FILE" <<EOF
# Pre-rendered csi-driver-host-path manifests
# csi-driver-host-path: $CSI_VERSION
# deploy dir: $DEPLOY_DIR
# All resources are namespaced to kube-system to match the kind e2e env's mock.
EOF

  PATH="$WORK/bin:$PATH" \
    IMAGE_TAG="" \
    bash "$DEPLOY_PATH/deploy.sh" >/dev/null

  # Append the storage class (the upstream deploy.sh does not include it).
  local SC_YAML="$WORK/repo/examples/csi-storageclass.yaml"
  if [ -f "$SC_YAML" ]; then
    printf -- '---\n' >> "$OUTPUT_FILE"
    cat "$SC_YAML" >> "$OUTPUT_FILE"
  fi
}

bake_snapshotter_timeout() {
  # The kind env used to `kubectl patch sts csi-hostpathplugin` after deploy
  # to add `--timeout=3m` to the csi-snapshotter sidecar; bake it into the
  # rendered file so the patch step is unnecessary and so the older 1.18/1.19
  # layouts (where csi-snapshotter is at a different container index) get the
  # arg too.
  python3 - "$@" <<'PY'
import sys, yaml
for path in sys.argv[1:]:
    with open(path) as fh:
        text = fh.read()
    lines = text.splitlines(keepends=True)
    hdr = []
    i = 0
    while i < len(lines) and (lines[i].startswith('#') or lines[i].strip() == ''):
        hdr.append(lines[i]); i += 1
    body = ''.join(lines[i:])
    docs = [d for d in yaml.safe_load_all(body) if d]
    for d in docs:
        if d.get('kind') == 'StatefulSet' and d['metadata']['name'] == 'csi-hostpathplugin':
            for c in d['spec']['template']['spec']['containers']:
                if c['name'] == 'csi-snapshotter':
                    args = c.setdefault('args', [])
                    if '--timeout=3m' not in args:
                        args.append('--timeout=3m')
    with open(path, 'w') as fh:
        fh.write(''.join(hdr))
        fh.write(yaml.dump_all(docs, default_flow_style=False, sort_keys=False))
PY
}

mkdir -p "$RESOURCES_PATH"

OUTPUTS=()
for line in "${CONFIGS[@]}"; do
  read -r CSI_VERSION DEPLOY_DIR OUT_DIR <<<"$line"
  OUTPUT_FILE="$RESOURCES_PATH/$OUT_DIR/install.yaml"
  render_one "$CSI_VERSION" "$DEPLOY_DIR" "$OUTPUT_FILE"
  OUTPUTS+=("$OUTPUT_FILE")
done

echo "==> Baking csi-snapshotter --timeout=3m into all rendered files"
bake_snapshotter_timeout "${OUTPUTS[@]}"

echo "==> Done. ${#OUTPUTS[@]} install.yaml files written under $RESOURCES_PATH"
