# GeoServer community integration

This repository is intended to live as a Git submodule under a GeoServer source
tree.

Use this layout for GeoServer vanilla:

```text
geoserver/
  src/
    community/
      portolan/       -> git submodule for geoserver-portolan
```

Add the submodule from the GeoServer repository:

```bash
git checkout geoserver-portolan
git submodule add git@github.com:jemacchi/geoserver-portolan.git src/community/portolan
```

Then add `portolan` to `src/community/pom.xml` as a module.

```xml
<module>portolan</module>
```

Build from the GeoServer repository:

```bash
mvn -pl :gs-portolan -am install
```

The module contributes a Web UI menu page through `applicationContext.xml`.
The page appears as `Portolan registry` in the Portolan menu category.

## Dependency on portolan-java

The module depends on:

```xml
<groupId>org.portolan</groupId>
<artifactId>portolan-java</artifactId>
<version>0.1.0-SNAPSHOT</version>
```

Install `portolan-java` locally before building GeoServer if the artifact is
not available from a Maven repository:

```bash
cd /home/jmacchi/prg/jemacchi/portolan/portolan-java
mvn install
```
