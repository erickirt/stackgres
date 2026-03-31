#!/bin/sh

set -e

[ "$DEBUG" != true ] || set -x

cd "$(dirname "$0")/../../.." || exit 1

CONFIG_FILE=stackgres-k8s/ci/build/config.yml

mkdir -p stackgres-k8s/ci/build/target

pin_build_images() {
  local IMAGE_KEY IMAGE IMAGE_NAME DIGEST PINNED_IMAGE

  # Build the new .pinned_images section
  printf '.pinned_images:\n' > stackgres-k8s/ci/build/target/pinned_images.yml
  yq -r '.".images" | to_entries[] | "\(.key) \(.value)"' "$CONFIG_FILE" \
    | while read -r IMAGE_KEY IMAGE
      do
        IMAGE_NAME="${IMAGE%%:*}"
        IMAGE_NAME="${IMAGE_NAME%%@sha256}"
        DIGEST="$(docker buildx imagetools inspect "$IMAGE" 2>/dev/null \
          | grep '^Digest:' | tr -d ' ' | cut -d : -f 2-)"
        if [ -z "$DIGEST" ]
        then
          echo "WARNING: Digest not found for image $IMAGE, skipping" >&2
          continue
        fi
        PINNED_IMAGE="$IMAGE_NAME@$DIGEST"
        echo "Pinning $IMAGE_KEY: $IMAGE -> $PINNED_IMAGE"
        printf '  %s: &%s %s\n' "$IMAGE_KEY" "$IMAGE_KEY" "$PINNED_IMAGE" \
          >> stackgres-k8s/ci/build/target/pinned_images.yml
      done

  # Remove existing .pinned_images section if present
  if grep -q '^\.pinned_images:' "$CONFIG_FILE"
  then
    sed -i '/^\.pinned_images:/,/^[^ ]/{/^[^ ]/!d}' "$CONFIG_FILE"
    sed -i '/^\.pinned_images:/d' "$CONFIG_FILE"
  fi

  # Find the line after .images section ends (the next top-level key)
  IMAGES_END_LINE="$(grep -n '^[^ ]' "$CONFIG_FILE" \
    | sed -n '/^[0-9]*:\.images:/{ n; p; }' | cut -d : -f 1)"

  if [ -n "$IMAGES_END_LINE" ]
  then
    sed -i "$((IMAGES_END_LINE - 1))r stackgres-k8s/ci/build/target/pinned_images.yml" "$CONFIG_FILE"
  else
    cat stackgres-k8s/ci/build/target/pinned_images.yml >> "$CONFIG_FILE"
  fi

  rm -f stackgres-k8s/ci/build/target/pinned_images.yml
  echo "Pinning done! Updated $CONFIG_FILE"
}

pin_build_images
