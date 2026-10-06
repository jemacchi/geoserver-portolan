#!/usr/bin/env bash

set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_file="$repo_dir/examples/geoserver-cloud/compose.portolan.yml"

for provider in \
  'storage/tileverse-storage-gcs' \
  'storage/tileverse-storage-s3'; do
  if ! grep -Fq "$provider" "$compose_file"; then
    echo "Missing GeoServer Cloud Tileverse compatibility provider: $provider" >&2
    exit 1
  fi
done

if grep -Eq 'tileverse-(storage-(all|azure|core)|pmtiles|tilematrixset|tilestore|vectortiles)' "$compose_file"; then
  echo "GeoServer Cloud installation must not override shared Tileverse runtime libraries" >&2
  exit 1
fi

for service in webui wms wfs wcs wps restconfig gwc; do
  if ! grep -Eq "^  ${service}:" "$compose_file"; then
    echo "Missing additional-libraries mount for GeoServer Cloud service: $service" >&2
    exit 1
  fi
done

echo "GeoServer Cloud distribution limits Tileverse overrides to compatibility providers"
