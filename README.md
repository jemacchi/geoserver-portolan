# GeoServer Portolan

Native Portolan catalog integration for GeoServer.

`geoserver-portolan` allows GeoServer to understand a Portolan catalog and provision native GeoServer catalog objects from its contents.

The module adds a Web UI page, reads a Portolan registry through `portolan-java`, builds a publication plan, and creates native GeoServer stores, resources, and layers for GeoParquet, COG, and PMTiles assets.

The module uses `portolan-java` to implement Portolan semantics and delegates actual geospatial data access to existing GeoServer/GeoTools stores.

## Important distinction

This project is **not a Portolan GeoTools DataStore**.

Portolan is a catalog and interoperability specification, not a new feature storage format.

The module therefore does not attempt to expose Portolan as:

```text
PortolanDataStore
      ↓
Features
```

Instead it acts as a catalog integration/provisioning layer:

```text
                    GeoServer
                        │
               Portolan Module
                        │
                 portolan-java
                        │
                Portolan Catalog
                        │
          ┌─────────────┼─────────────┐
          ▼             ▼             ▼
      Collection    Collection    Collection
          │             │             │
     GeoParquet         COG           ...
          │             │
          ▼             ▼
     GeoParquet       COG
      DataStore    CoverageStore
          │             │
          ▼             ▼
     FeatureType      Coverage
          │             │
          └──────┬──────┘
                 ▼
               Layers
```

## Purpose

A GeoServer administrator should eventually be able to configure a Portolan catalog URI:

```text
https://example.com/portolan/catalog.json
```

and allow the module to discover compatible collections and assets.

For example:

```text
Portolan collection: buildings

Asset:
    media type: GeoParquet
    href: s3://bucket/buildings.parquet
```

may produce:

```text
GeoServer Workspace
       │
       └── GeoParquet DataStore
               │
               └── FeatureType: buildings
                       │
                       └── Layer: buildings
```

while:

```text
Portolan collection: orthophoto

Asset:
    type: COG
    href: s3://bucket/orthophoto.tif
```

may produce:

```text
GeoServer Workspace
       │
       └── COG CoverageStore
               │
               └── Coverage: orthophoto
                       │
                       └── Layer: orthophoto
```

## Core principle

> **Portolan describes the catalog. GeoServer decides how the data is served.**

The module therefore should not contain implementations for reading GeoParquet, COG, PMTiles, or other Portolan-supported formats.

Instead, it maps Portolan assets to appropriate GeoServer capabilities.

## Architecture

The primary dependency is:

```text
geoserver-portolan
        │
        ▼
  portolan-java
```

`portolan-java` provides:

* catalog parsing;
* Portolan domain objects;
* STAC/Portolan semantics;
* link traversal;
* HREF resolution;
* validation;
* conformance.

`geoserver-portolan` adds:

* GeoServer mapping rules;
* store creation;
* resource and layer publication hooks;
* provenance;
* synchronization;
* GeoServer configuration/UI where appropriate.

The module creates stores, resources, and layers through the installed GeoServer and GeoTools handlers. It records Portolan provenance metadata on each managed object.

## Mapping layer

The module should avoid hard-coding all behavior in a single catalog importer.

A mapping abstraction should allow asset handlers to determine how a Portolan asset maps to GeoServer.

Conceptually:

```java
public interface PortolanAssetHandler {

    boolean supports(PortolanAsset asset);

    PublicationPlan plan(
        PortolanCollection collection,
        PortolanAsset asset,
        GeoServerContext context
    );
}
```

Implementations might include:

```text
GeoParquetAssetHandler
CogAssetHandler
FuturePmtilesAssetHandler
...
```

This allows GeoServer support to evolve independently from `portolan-java`.

Critically, these handlers configure existing GeoServer stores. They do not implement the underlying data formats.

## Catalog synchronization

The module should eventually support reconciliation between a Portolan catalog and the GeoServer catalog.

```text
Portolan Catalog
       │
       ▼
Desired GeoServer State
       │
       ▼
Compare
       │
       ├── CREATE
       ├── UPDATE
       ├── UNCHANGED
       └── REMOVE / ORPHAN
```

The exact removal policy should be explicit and conservative.

A first implementation may support only discovery and creation before introducing automatic reconciliation.

## Provenance

Resources created from Portolan should retain provenance metadata.

Possible keys include:

```text
portolan.managed
portolan.catalog
portolan.collection
portolan.asset
portolan.checksum
```

This information allows the module to distinguish managed resources from resources created independently by GeoServer administrators.

## Relationship with `portolan-java`

This module must use the public API of `portolan-java`.

## Build

Install `portolan-java` first:

```bash
cd ../portolan-java
make install
```

Then build this module:

```bash
GEOSERVER_SRC=/path/to/geoserver make build
```

When this repository is linked into GeoServer, place it at:

```text
geoserver/src/community/portolan
```

and build from the GeoServer reactor with the community profile that includes the module.

## Test

Run the unit and Web UI tests inside a matching GeoServer source tree:

```bash
GEOSERVER_SRC=/path/to/geoserver make test
```

Generate the JaCoCo report and enforce the 85% line coverage gate:

```bash
GEOSERVER_SRC=/path/to/geoserver make coverage
```

The command prints the absolute path to `target/site/jacoco/index.html`.
The build fails when line coverage falls below 85%.

See [Testing](docs/testing.md) for the test boundaries and [Administration](docs/administration.md) for the Web UI workflow.

## Release ZIPs

This repository can publish GeoServer extension ZIPs without moving the source
code into the GeoServer repository.

Each release provides `slim` and `full` distributions.

The `slim` ZIP contains:

* `gs-portolan`;
* `portolan-java`.

The `slim` ZIP does not duplicate GeoServer core, GeoTools core, Spring, or Jackson
libraries that the matching GeoServer installation already provides.

Install the matching GeoServer community extension for each format that the
server must publish:

* `gs-cog-core` and `gs-cog-http` for COG over HTTP;
* `gs-geoparquet` for GeoParquet;
* `gs-pmtiles-store` for PMTiles.

The Web UI remains available when any store extension is absent. Its readiness
section names each missing artifact. Planning remains available, while
provisioning stays disabled only when the selected catalog needs a missing
store. The module reports `COMPLETE` when all three stores are present.

Install the COG S3, Azure, or Google Cloud transport only when catalog asset
URLs use that provider-specific protocol. GeoServer owns these store
dependencies and publishes their complete runtime assemblies separately.

The `full` ZIP combines `slim` with the official GeoServer assemblies for
GeoParquet, COG over HTTP, and PMTiles. It is larger because GeoParquet includes
DuckDB and PMTiles includes its supported object-storage clients. It excludes
GeoServer and GeoTools core modules. The official store assemblies can repeat
shared third-party JARs from the base installation. Use `full` for a new
matching GeoServer installation. Use `slim` when the three store extensions are
already installed and managed separately. The `full` ZIP can exceed 150 MiB.

Create a release by pushing a tag from `main`:

```bash
git checkout main
git pull --ff-only
git tag v0.1.1
git push origin v0.1.1
```

The GitHub Actions workflow builds the module inside the GeoServer
`geoserver-portolan` integration branch. It uses each store module's official
assembly descriptor for the `full` ZIP. It rejects conflicting files and
GeoServer or GeoTools core modules. The 5 MiB limit applies to `slim`. Both
filenames include the Portolan tag version.

Install the ZIP into a matching GeoServer build:

```bash
unzip geoserver-portolan-*-slim.zip -d /path/to/geoserver/WEB-INF/lib
```

For a new installation without the required stores, use:

```bash
unzip geoserver-portolan-*-full.zip -d /path/to/geoserver/WEB-INF/lib
```

Then restart GeoServer.

## Use

After the jar is installed in GeoServer, the Web UI gets a Portolan menu entry.

Use it to:

* load catalog entries from a Portolan registry URL;
* select a catalog id;
* plan the GeoServer catalog changes;
* provision the supported stores into the selected workspace.

It should not independently parse Portolan JSON except where strictly necessary for GeoServer integration.

That boundary is intentional:

```text
Portolan specification changes
            │
            ▼
      portolan-java
            │
            ▼
    geoserver-portolan
```

GeoServer should therefore not become another independent implementation of the Portolan specification.

## Relationship with `portolan-geoserver`

Both projects integrate Portolan and GeoServer but from opposite directions.

### `portolan-geoserver`

External orchestration:

```text
Portolan Catalog
       ↓
portolan-python
       ↓
portolan-geoserver
       ↓
python-geoservercloud
       ↓
GeoServer
```

Useful for automation, provisioning, CI/CD, and synchronization from outside GeoServer.

### `geoserver-portolan`

Native integration:

```text
GeoServer
       ↓
geoserver-portolan
       ↓
portolan-java
       ↓
Portolan Catalog
```

Useful when GeoServer itself should understand and manage Portolan catalogs.

These projects are complementary rather than competing implementations.

## Relationship with `portolan-cli`

There should be no runtime dependency on `portolan-cli`.

The architecture intentionally allows GeoServer to consume Portolan without Python:

```text
Portolan catalog
       ↓
portolan-java
       ↓
geoserver-portolan
       ↓
GeoServer
```

This is an important design goal.

Portolan should be consumable from different language ecosystems without requiring the reference CLI.

## Initial development phases

### Phase 1 — Discovery

* add `portolan-java`;
* configure a catalog URI;
* read the catalog;
* list compatible collections/assets;
* expose diagnostics.

### Phase 2 — Planning

Build an internal representation of the desired GeoServer configuration without modifying the GeoServer catalog.

```text
Portolan → PublicationPlan
```

### Phase 3 — Provisioning

Support initial mappings:

```text
GeoParquet → GeoParquet DataStore
COG        → COG CoverageStore
```

and create the corresponding resources and layers.

### Phase 4 — Provenance

Record the Portolan source associated with managed GeoServer resources.

### Phase 5 — Synchronization

Reconcile changes in Portolan catalogs with existing managed GeoServer resources.

### Phase 6 — Extensibility

Allow additional asset-to-GeoServer mappings without increasing coupling between the Portolan core and individual GeoServer data formats.

## Design principles

1. Portolan is a catalog, not a GeoTools DataStore.
2. Use `portolan-java` for Portolan semantics.
3. Use native GeoServer/GeoTools implementations for data access.
4. Do not duplicate format readers.
5. Separate discovery, planning, and mutation.
6. Preserve provenance.
7. Make mappings extensible.
8. Keep GeoServer-specific concepts out of `portolan-java`.
9. Do not require Python or `portolan-cli`.
10. Treat the Portolan specification as the interoperability boundary.

## Current implementation

This repository now contains the first GeoServer Web UI module skeleton:

* `PortolanPlanner` maps Portolan collections and assets to desired GeoServer resources.
* `PortolanRegistryFacade` reads the Portolan registry and downloads selected catalogs.
* `PortolanPage` adds a Web UI menu page for registry discovery and planning.
* `PortolanProvisioner` creates the workspace and defines the mutation boundary for store handlers.
* `PortolanStoreReadiness` reports store availability without making optional extensions startup dependencies.

GeoParquet, COG, and PMTiles are recognized as asset types. The planner marks
an entry as unsupported when its matching GeoServer extension is absent.

## Build and integration

Install `portolan-java` first:

```bash
cd ../portolan-java
mvn install
```

Then build this module:

```bash
make build
```

For GeoServer vanilla integration, see [GeoServer community integration](docs/geoserver-community.md).

For GeoServer Cloud integration, see [GeoServer Cloud integration](docs/geoserver-cloud.md).
