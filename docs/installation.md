# Install on GeoServer

Use a Portolan release built for the same GeoServer version as the target
server. Do not mix extension and server versions.

The `full` ZIP contains Portolan and the GeoParquet, COG HTTP, and PMTiles
store assemblies. Use the `slim` ZIP when those stores are already installed.

## Install into an existing server

Stop GeoServer. Extract one distribution into the deployed GeoServer
`WEB-INF/lib` directory:

```bash
unzip geoserver-portolan-<version>-full.zip \
  -d /path/to/geoserver/WEB-INF/lib
```

Start GeoServer and sign in. Open **Utilities**, then select **Portolan**.
The readiness panel must report each store that the selected catalog needs.

## Build a vanilla GeoServer image

The official GeoServer image loads JARs from `/opt/additional_libs` and copies
them into the web application at startup. The example
[Dockerfile](../examples/vanilla/Dockerfile) adds the `full` distribution to
that directory.

Create a build directory and download a release asset into it:

```bash
mkdir geoserver-portolan-image
cd geoserver-portolan-image
cp /path/to/geoserver-portolan/examples/vanilla/Dockerfile .

export PORTOLAN_VERSION=<release-version-without-v>
curl --fail --location --remote-name \
  "https://github.com/jemacchi/geoserver-portolan/releases/download/v${PORTOLAN_VERSION}/geoserver-portolan-${PORTOLAN_VERSION}-full.zip"
```

Build the image with the GeoServer version used by that Portolan release:

```bash
export GEOSERVER_VERSION=<matching-geoserver-version>
docker build \
  --build-arg GEOSERVER_VERSION="${GEOSERVER_VERSION}" \
  --build-arg PORTOLAN_VERSION="${PORTOLAN_VERSION}" \
  --tag "geoserver-portolan:${PORTOLAN_VERSION}" \
  .
```

Run the derived image like the official image:

```bash
docker run --rm --publish 8080:8080 \
  --name geoserver-portolan \
  "geoserver-portolan:${PORTOLAN_VERSION}"
```

Open `http://localhost:8080/geoserver`. Preserve the normal GeoServer data
directory mounts and environment variables when you adapt this command for a
persistent deployment.

## Verify the installation

Check that the image contains the Portolan JARs:

```bash
docker exec geoserver-portolan \
  find /opt/additional_libs -maxdepth 1 -name '*portolan*.jar' -print
```

The Web UI must show **Portolan** under **Utilities** after authentication.
See [Administration](administration.md) for readiness, planning, and
provisioning.
