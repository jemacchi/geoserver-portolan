#!/usr/bin/env bash

set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_file="$repo_dir/examples/geoserver-cloud/compose.portolan.yml"

if grep -Eq 'TILEVERSE_VERSION|repo1\.maven\.org/maven2/io/tileverse' "$compose_file"; then
  echo "GeoServer Cloud installation must not override its bundled Tileverse libraries" >&2
  exit 1
fi

for service in webui wms wfs wcs wps restconfig gwc; do
  if ! grep -Eq "^  ${service}:" "$compose_file"; then
    echo "Missing additional-libraries mount for GeoServer Cloud service: $service" >&2
    exit 1
  fi
done

echo "GeoServer Cloud distribution does not override runtime-managed Tileverse libraries"
