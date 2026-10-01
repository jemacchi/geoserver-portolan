#!/usr/bin/env bash

set -euo pipefail

if [[ $# -lt 6 ]]; then
  echo "Usage: $0 VERSION PORTOLAN_ZIP CLOUD_ADAPTER_JAR OUTPUT_DIR STORE_ZIP..." >&2
  exit 2
fi

version="$1"
portolan_zip="$2"
cloud_adapter_jar="$3"
output_dir="$4"
shift 4
store_zips=("$@")

for archive in "$portolan_zip" "$cloud_adapter_jar" "${store_zips[@]}"; do
  if [[ ! -f "$archive" ]]; then
    echo "Archive does not exist: $archive" >&2
    exit 1
  fi
done

mkdir -p "$output_dir"
output_dir="$(cd "$output_dir" && pwd)"
slim_zip="$output_dir/geoserver-portolan-${version}-slim.zip"
full_zip="$output_dir/geoserver-portolan-${version}-full.zip"
cloud_zip="$output_dir/geoserver-portolan-${version}-cloud.zip"
cp "$portolan_zip" "$slim_zip"

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
full_dir="$work_dir/full"
cloud_dir="$work_dir/cloud"
mkdir -p "$full_dir" "$cloud_dir"

merge_archive() {
  local archive="$1"
  local entry destination candidate
  while IFS= read -r entry; do
    [[ -z "$entry" || "$entry" == */ ]] && continue
    destination="$full_dir/$entry"
    mkdir -p "$(dirname "$destination")"
    candidate="$(mktemp "$work_dir/candidate.XXXXXX")"
    unzip -p "$archive" "$entry" > "$candidate"
    if [[ -f "$destination" ]]; then
      if ! cmp -s "$candidate" "$destination"; then
        echo "Conflicting archive entry: $entry" >&2
        exit 1
      fi
      rm "$candidate"
    else
      mv "$candidate" "$destination"
    fi
  done < <(unzip -Z1 "$archive")
}

merge_archive "$portolan_zip"
for archive in "${store_zips[@]}"; do
  merge_archive "$archive"
done

unzip -q "$portolan_zip" -d "$cloud_dir"
cp "$cloud_adapter_jar" "$cloud_dir/"

(
  cd "$full_dir"
  zip -q "$full_zip" ./*
)
(
  cd "$cloud_dir"
  zip -q "$cloud_zip" ./*
)

require_entry() {
  local archive="$1"
  local pattern="$2"
  local entries
  entries="$(unzip -Z1 "$archive")"
  if ! grep -Eq "$pattern" <<< "$entries"; then
    echo "Missing required archive entry matching: $pattern" >&2
    exit 1
  fi
}

require_entry "$slim_zip" '^geoserver-portolan-.*\.jar$'
require_entry "$slim_zip" '^portolan-java-.*\.jar$'

slim_jar_count="$(unzip -Z1 "$slim_zip" | grep -Ec '\.jar$')"
if [[ "$slim_jar_count" -ne 2 ]]; then
  echo "Slim archive must contain exactly two JARs, found $slim_jar_count" >&2
  exit 1
fi

require_entry "$cloud_zip" '^geoserver-portolan-.*\.jar$'
require_entry "$cloud_zip" '^portolan-java-.*\.jar$'
require_entry "$cloud_zip" '^geoserver-portolan-cloud-.*\.jar$'

cloud_jar_count="$(unzip -Z1 "$cloud_zip" | grep -Ec '\.jar$')"
if [[ "$cloud_jar_count" -ne 3 ]]; then
  echo "Cloud archive must contain exactly three JARs, found $cloud_jar_count" >&2
  exit 1
fi
if [[ "$(stat -c %s "$cloud_zip")" -gt 5242880 ]]; then
  echo "Cloud archive exceeds the 5 MiB release limit" >&2
  exit 1
fi

if unzip -Z1 "$slim_zip" | grep -Eq '^geoserver-portolan-cloud-.*\.jar$'; then
  echo "Slim archive must not contain the GeoServer Cloud adapter" >&2
  exit 1
fi
if [[ "$(stat -c %s "$slim_zip")" -gt 5242880 ]]; then
  echo "Slim archive exceeds the 5 MiB release limit" >&2
  exit 1
fi

for pattern in \
  '^geoserver-portolan-.*\.jar$' \
  '^portolan-java-.*\.jar$' \
  '^gs-geoparquet-.*\.jar$' \
  '^gt-geoparquet-.*\.jar$' \
  '^duckdb_jdbc-.*\.jar$' \
  '^gs-cog-core-.*\.jar$' \
  '^gs-cog-http-.*\.jar$' \
  '^imageio-ext-cog-rangereader-http-.*\.jar$' \
  '^gs-pmtiles-store-.*\.jar$' \
  '^gt-pmtiles-.*\.jar$'; do
  require_entry "$full_zip" "$pattern"
done

if unzip -Z1 "$full_zip" | grep -Eq '^geoserver-portolan-cloud-.*\.jar$'; then
  echo "Full archive must not contain the GeoServer Cloud adapter" >&2
  exit 1
fi

provided_pattern='^(gs-main|gs-platform|gs-web-core|gs-wms-core|gt-main)-.*\.jar$'
full_entries="$(unzip -Z1 "$full_zip")"
if grep -Eq "$provided_pattern" <<< "$full_entries"; then
  echo "Full archive contains GeoServer or GeoTools core modules" >&2
  exit 1
fi

sha256sum "$slim_zip" "$full_zip" "$cloud_zip"
unzip -l "$slim_zip"
unzip -l "$full_zip"
unzip -l "$cloud_zip"
