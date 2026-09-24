# GeoServer Cloud integration

GeoServer Cloud has a different source layout. Keep this repository as the
source of truth and mount it as a submodule under `src/extensions`.

Use this layout:

```text
geoserver-cloud/
  src/
    extensions/
      portolan/       -> git submodule for geoserver-portolan
```

Add the submodule from the GeoServer Cloud repository:

```bash
git checkout geoserver-portolan
git submodule add git@github.com:jemacchi/geoserver-portolan.git src/extensions/portolan
```

Then add `portolan` to `src/extensions/pom.xml`.

```xml
<module>portolan</module>
```

This first module targets the GeoServer Web UI API. A later GeoServer Cloud
starter can wrap it if the deployment needs Spring Boot auto-configuration.
