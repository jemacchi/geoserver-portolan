# Administration

The extension adds Portolan to the GeoServer Utilities menu. The item is visible only to authenticated users.

## Open a registry catalog

1. Sign in to the GeoServer Web UI.
2. Open **Utilities**, then select **Portolan**.
3. Enter a registry export URL.
4. Select **Load registry** to list available catalog IDs.
5. Copy one catalog ID into the **Catalog id** field.
6. Enter the target GeoServer workspace.

An empty workspace field uses the Portolan catalog ID.

## Check store readiness

The Portolan page always opens, even when a required store extension is absent.
The **Store readiness** section reports one state for each supported format:

| Format | Required GeoServer artifacts |
|---|---|
| GeoParquet | `gs-geoparquet` |
| COG over HTTP | `gs-cog-core` and `gs-cog-http` |
| PMTiles | `gs-pmtiles-store` |

The module reports `COMPLETE` when all three stores are available. A missing
store does not prevent GeoServer or the Portolan page from starting.

## Preview a plan

Select **Preview selected catalog plan** before provisioning. The preview does not change the GeoServer catalog.

Each entry shows the collection, store, layer, asset format, and planned action:

- `CREATE` creates a new store and publishes its resources and layers.
- `EXISTS` reuses a store with the same workspace and name.
- `SKIP` identifies a collection without a usable data asset.
- `UNSUPPORTED` identifies a missing GeoServer store extension.

The preview also shows a reason when the module cannot provision an entry.
It reports `Provisioning: BLOCKED` when the selected catalog needs a missing
store extension. The reason identifies the artifact to install.

## Provision a catalog

Select **Provision selected catalog** to apply the plan. The module creates the workspace and namespace when they do not exist.

The button starts disabled. A provisionable plan enables it. A blocked plan
keeps it disabled. The module checks the plan again immediately before it
changes the GeoServer catalog.

The asset mapping is:

| Portolan asset | GeoServer object |
|---|---|
| GeoParquet | `GeoParquet` data store and feature layers |
| COG | `GeoTIFF` coverage store and coverage layers |
| PMTiles | `PMTiles` data store and vector tile layers |

Release assets provide three installation choices:

- `slim` contains Portolan and `portolan-java`. Install the three store extensions separately.
- `full` also contains the GeoParquet, COG HTTP, and PMTiles extension assemblies.
- `cloud` adds the Spring Boot adapter required by GeoServer Cloud `/opt/additional_libs`.

All distributions require the GeoServer version used to build the release.
Stop vanilla GeoServer before you extract `slim` or `full` into `WEB-INF/lib`.
Mount the extracted `cloud` files in the GeoServer Cloud Web UI service.

When a collection provides a spatial bounding box, the module applies it as the native and geographic bounds. It uses `EPSG:4326` when the resource has no declared CRS.

## Provenance

The module writes metadata on managed stores and resources:

```text
portolan.managed
portolan.catalog
portolan.collection
portolan.asset
```

These values identify the source catalog, collection, and asset.

## Logs

GeoServer logs each planning and provisioning step under `io.multivers.geoserver.portolan`.
The messages include the catalog ID, workspace, collection, format, store, action, bounds, and layer result.

Increase the logger level in GeoServer when you need more detail from the surrounding store implementation.

## Failure behavior

The module keeps processing other plan entries when one store cannot publish its layers. The result panel reports the failure message.

Common causes include a missing store extension, an unreachable asset URL, invalid credentials, or a store that exposes no native resource names.
