#!/usr/bin/env bash

set -euo pipefail

if [[ $# -lt 5 ]]; then
  echo "Usage: $0 VERSION PORTOLAN_ZIP OUTPUT_DIR STORE_ZIP..." >&2
  exit 2
fi

version="$1"
portolan_zip="$2"
output_dir="$3"
shift 3
store_zips=("$@")

for archive in "$portolan_zip" "${store_zips[@]}"; do
  if [[ ! -f "$archive" ]]; then
    echo "Archive does not exist: $archive" >&2
    exit 1
  fi
done

mkdir -p "$output_dir"
output_dir="$(cd "$output_dir" && pwd)"
slim_zip="$output_dir/geoserver-portolan-${version}-slim.zip"
full_zip="$output_dir/geoserver-portolan-${version}-full.zip"
cp "$portolan_zip" "$slim_zip"

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
full_dir="$work_dir/full"
mkdir -p "$full_dir"

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

(
  cd "$full_dir"
  zip -q "$full_zip" ./*
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

require_entry "$slim_zip" '^gs-portolan-.*\.jar$'
require_entry "$slim_zip" '^portolan-java-.*\.jar$'

slim_jar_count="$(unzip -Z1 "$slim_zip" | grep -Ec '\.jar$')"
if [[ "$slim_jar_count" -ne 2 ]]; then
  echo "Slim archive must contain exactly two JARs, found $slim_jar_count" >&2
  exit 1
fi
if [[ "$(stat -c %s "$slim_zip")" -gt 5242880 ]]; then
  echo "Slim archive exceeds the 5 MiB release limit" >&2
  exit 1
fi

for pattern in \
  '^gs-portolan-.*\.jar$' \
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

provided_pattern='^(gs-main|gs-platform|gs-web-core|gs-wms-core|gt-main)-.*\.jar$'
full_entries="$(unzip -Z1 "$full_zip")"
if grep -Eq "$provided_pattern" <<< "$full_entries"; then
  echo "Full archive contains GeoServer or GeoTools core modules" >&2
  exit 1
fi

sha256sum "$slim_zip" "$full_zip"
unzip -l "$slim_zip"
unzip -l "$full_zip"
