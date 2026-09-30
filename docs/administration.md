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

## Preview a plan

Select **Preview selected catalog plan** before provisioning. The preview does not change the GeoServer catalog.

Each entry shows the collection, store, layer, asset format, and planned action:

- `CREATE` creates a new store and publishes its resources and layers.
- `EXISTS` reuses a store with the same workspace and name.
- `SKIP` identifies a collection without a usable data asset.
- `UNSUPPORTED` identifies a missing GeoServer store extension.

The preview also shows a reason when the module cannot provision an entry.

## Provision a catalog

Select **Provision selected catalog** to apply the plan. The module creates the workspace and namespace when they do not exist.

The asset mapping is:

| Portolan asset | GeoServer object |
|---|---|
| GeoParquet | `GeoParquet` data store and feature layers |
| COG | `GeoTIFF` coverage store and coverage layers |
| PMTiles | `PMTiles` data store and vector tile layers |

Install each required GeoServer extension before you provision its format. The
Portolan release ZIP does not duplicate store extensions or their runtime
dependencies.

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

GeoServer logs each planning and provisioning step under `org.geoserver.portolan`.
The messages include the catalog ID, workspace, collection, format, store, action, bounds, and layer result.

Increase the logger level in GeoServer when you need more detail from the surrounding store implementation.

## Failure behavior

The module keeps processing other plan entries when one store cannot publish its layers. The result panel reports the failure message.

Common causes include a missing store extension, an unreachable asset URL, invalid credentials, or a store that exposes no native resource names.
