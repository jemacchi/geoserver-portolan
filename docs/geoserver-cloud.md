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

## Install with a host directory

Download the `cloud` ZIP for your GeoServer version. Extract it into a host
directory:

```bash
mkdir -p additional-libs/portolan
unzip geoserver-portolan-*-cloud.zip -d additional-libs/portolan
```

Mount the directory at `/opt/additional_libs` in the Web UI service:

```yaml
services:
  webui:
    volumes:
      - ./additional-libs/portolan:/opt/additional_libs:ro
```

Mount the same libraries in another GeoServer service when that service needs
the module classes. Keep the classpath consistent across replicas of one
service.

GeoParquet, COG, and PMTiles remain separate GeoServer extensions. Enable each
store extension in every service that reads its catalog objects. The Portolan
page reports which store extension is missing.

Restart the affected services after you change the mounted JARs:

```bash
docker compose up -d --force-recreate webui
docker compose logs -f webui
```

Add `-Dloader.debug=true` to `JAVA_OPTS` when you need to inspect additional
library loading.

## Install with Docker Compose

The example [Compose override](../examples/geoserver-cloud/compose.portolan.yml)
uses a one-shot service. It extracts a local `cloud` ZIP into a named volume
before the Web UI starts.

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
It does not remove unrelated additional libraries. Compose waits for this
service to complete before it starts `webui`.

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
```

To inspect the Spring Boot classpath, add `-Dloader.debug=true` to `JAVA_OPTS`
for `webui`, then recreate that service.

## Vanilla isolation

The `slim` and `full` release ZIPs contain no Spring Boot adapter. Install one
of those ZIPs into `WEB-INF/lib` for vanilla GeoServer. Do not copy
`geoserver-portolan-cloud` into a vanilla deployment.

The auto-configuration source lives under
`io.multivers.geoserver.portolan.cloud`. The core module uses the package
`io.multivers.geoserver.portolan`.
