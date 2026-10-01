# GeoServer Cloud integration

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

## Install the extension

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

## Vanilla isolation

The `slim` and `full` release ZIPs contain no Spring Boot adapter. Install one
of those ZIPs into `WEB-INF/lib` for vanilla GeoServer. Do not copy
`geoserver-portolan-cloud` into a vanilla deployment.

The auto-configuration source lives under
`io.multivers.geoserver.portolan.cloud`. The core module uses the package
`io.multivers.geoserver.portolan`.
