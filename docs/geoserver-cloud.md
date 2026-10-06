# Install on GeoServer Cloud

GeoServer Cloud can load Portolan without a source change or a custom build.
The `cloud` release ZIP contains these JARs:

```text
geoserver-portolan-<version>.jar
geoserver-portolan-cloud-<version>.jar
portolan-java-<version>.jar
```

This installation follows the GeoServer Cloud
[additional libraries mechanism](https://geoserver.org/geoserver-cloud/user-guide/additional-libs-and-fonts/).

The adapter registers `io.multivers.geoserver.portolan` through Spring Boot
auto-configuration. It activates only when the GeoServer Web UI and the core
Portolan module are present. It also requires
`geoserver.service.webui.enabled=true`.

## Choose the Cloud distribution

Use the `cloud` ZIP. Do not use the `full` ZIP in `/opt/additional_libs`.
GeoServer Cloud images manage their store extensions separately.

The Portolan `cloud` ZIP does not contain GeoParquet, COG, or PMTiles store
implementations. Enable those extensions in each Cloud service that loads the
corresponding catalog objects.

## GeoParquet backend

Portolan keeps GeoParquet as the catalog asset format in every deployment.
The GeoServer store implementation depends on the runtime:

- GeoServer Cloud uses the `Parquet` store from Parquetry when its factory is
  available.
- Vanilla GeoServer uses the `GeoParquet` store from `gs-geoparquet`.
- GeoServer Cloud falls back to `GeoParquet` when Parquetry is unavailable.

The Parquetry store receives the asset URL through its `geoparquet`
connection parameter. The classic store keeps its `dbtype` and `uri`
parameters. Existing stores keep their original type.

The Portolan readiness panel reports `Parquet (Parquetry)` when the Cloud
backend is active.

## Install with a host directory

Download the `cloud` ZIP for your GeoServer version. Extract it into a host
directory:

```bash
mkdir -p additional-libs/portolan
unzip geoserver-portolan-*-cloud.zip -d additional-libs/portolan
```

Mount the directory at `/opt/additional_libs` in every GeoServer service. Each
service loads the shared catalog and must recognize its store types:

```yaml
services:
  webui:
    volumes:
      - ./additional-libs/portolan:/opt/additional_libs:ro
  wms:
    volumes:
      - ./additional-libs/portolan:/opt/additional_libs:ro
  wfs:
    volumes:
      - ./additional-libs/portolan:/opt/additional_libs:ro
# Repeat the same mount for wcs, wps, restconfig, and gwc.
```

Keep the classpath consistent across all replicas. Mounting the libraries only
in `webui` lets Portolan create catalog entries, but service requests then fail
because `wms` or `wfs` cannot open their stores.

GeoParquet, COG, and PMTiles remain separate GeoServer extensions. Enable each
store extension in every service that reads its catalog objects. The Portolan
page reports which store extension is missing.

Restart the affected services after you change the mounted JARs:

```bash
docker compose up -d --force-recreate webui wms wfs wcs wps restconfig gwc
docker compose logs -f webui wms wfs
```

Add `-Dloader.debug=true` to `JAVA_OPTS` when you need to inspect additional
library loading.

## Install with Docker Compose

The example [Compose override](../examples/geoserver-cloud/compose.portolan.yml)
uses a one-shot service. It extracts a local `cloud` ZIP into a named volume
before the GeoServer services start.

Download the release asset and set its absolute path:

```bash
export PORTOLAN_VERSION=<release-version-without-v>
curl --fail --location --remote-name \
  "https://github.com/jemacchi/geoserver-portolan/releases/download/v${PORTOLAN_VERSION}/geoserver-portolan-${PORTOLAN_VERSION}-cloud.zip"
export PORTOLAN_CLOUD_ZIP="$PWD/geoserver-portolan-${PORTOLAN_VERSION}-cloud.zip"
```

Add the override after the GeoServer Cloud Compose file:

```bash
docker compose \
  -f /path/to/geoserver-cloud/compose/compose.yml \
  -f /path/to/geoserver-portolan/examples/geoserver-cloud/compose.portolan.yml \
  up -d
```

The `portolan-libs-init` service removes older Portolan JARs from its volume.
It also installs the Tileverse `2.1-M2` libraries required by the PMTiles
store in the current GeoServer Cloud snapshot. Compose waits for this service
to complete before it starts the GeoServer services.

The GeoServer Cloud image already contains `gs-pmtiles-store`. Its
`gt-pmtiles` dependency uses the Tileverse `2.1-M2` API. The override keeps
those libraries aligned and prevents a `NoSuchFieldError` during factory
discovery.

Use the same override on every start. Omitting it creates a different Compose
model and leaves the Web UI without the named volume.

## Install with a Kubernetes init container

The example
[deployment patch](../examples/geoserver-cloud/webui-portolan-patch.yml)
downloads the release into an `emptyDir` volume before the Web UI starts.
Replace `REPLACE_WITH_RELEASE_VERSION` with a release version, without `v`.

Patch the Web UI workload used by your deployment. Match the deployment and
container names before applying it:

```bash
kubectl patch deployment geoserver-cloud-webui \
  --type strategic \
  --patch-file webui-portolan-patch.yml
kubectl rollout status deployment/geoserver-cloud-webui
```

For a controlled production environment, mirror the ZIP or package the three
JARs in an internal init image. Pin that image by digest. This removes a
GitHub download from pod startup.

Mount the required store extensions in all services that load their catalog
objects. GeoServer Cloud documents this requirement because every service can
load the shared catalog.

## Verify the installation

Check the one-shot Compose service and the Web UI logs:

```bash
docker compose logs portolan-libs-init
docker compose logs webui | grep -i portolan
docker compose logs webui | grep 'factory enabled: PMTiles'
```

The final command must report
`org.geotools.pmtiles.store.PMTilesDataStoreFactory`.

To inspect the Spring Boot classpath, add `-Dloader.debug=true` to `JAVA_OPTS`
for `webui`, then recreate that service.

## Vanilla isolation

The `slim` and `full` release ZIPs contain no Spring Boot adapter. Install one
of those ZIPs into `WEB-INF/lib` for vanilla GeoServer. Do not copy
`geoserver-portolan-cloud` into a vanilla deployment.

The auto-configuration source lives under
`io.multivers.geoserver.portolan.cloud`. The core module uses the package
`io.multivers.geoserver.portolan`.
