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
| GeoParquet | Vanilla: `gs-geoparquet`. Cloud: Parquetry `Parquet`, with `gs-geoparquet` as fallback. |
| COG over HTTP | `gs-cog-core` and `gs-cog-http` |
| PMTiles | `gs-pmtiles-store` |

The module reports `COMPLETE` when all three stores are available. A missing
store does not prevent GeoServer or the Portolan page from starting.

The page reports MBStyle readiness separately. Install `gs-mbstyle` from the
same GeoServer release when you need default style import. A missing MBStyle
extension disables only the style checkbox. You can still plan and provision
the catalog data.

## Import default styles

Select **Import and assign default styles to layers** before you preview the
plan. The option is off by default. It stays disabled when GeoServer does not
provide an MBStyle handler.

The planner identifies collection assets whose `roles` include `style`. When
several styles exist, one asset must also have the `default` role. The planner
does not use asset order to choose between candidates.

The planner publishes every supported collection asset with a `data` or
`visual` role. Multiple representations get separate stores and layers. Their
names use the collection ID and asset key, such as `county__data` and
`county__pmtiles`.

The first version supports collection-level MBStyle assets. It accepts Mapbox
Style Specification version 8 JSON. The document must contain at least one
style layer and must map unambiguously to the target GeoServer layer. When the
style declares a source URL, the importer assigns it only to the matching
asset. The importer also checks that referenced attributes exist in the target
layer. Item styles, SLD conversion, CSS conversion, and full-map layer groups
are outside this version's scope.

The importer creates a workspace style and sets it as the layer's default. A
stable name and source checksum make repeated imports idempotent. The importer
updates changed styles only when their metadata shows that Portolan created
them. It does not overwrite a manually managed style with the same name.

The result keeps data provisioning messages separate from these style states:

| State | Meaning |
|---|---|
| `IMPORTED` | The module created or updated the style and assigned it. |
| `REUSED` | The existing managed style has the same source checksum. |
| `SKIPPED_NO_STYLE` | The collection has no style asset. |
| `SKIPPED_UNSUPPORTED` | The asset, mapping, or installed extension is unsupported. |
| `FAILED` | Retrieval, validation, persistence, or assignment failed. |

A style failure does not remove a datastore, resource, or layer that the same
run created successfully. If assignment fails, the layer keeps its previous
default style. Logs omit URL credentials and query strings.

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
| GeoParquet | Vanilla: `GeoParquet` data store. Cloud: Parquetry `Parquet`, with classic fallback. Both create feature layers. |
| COG | `GeoTIFF` coverage store and coverage layers |
| PMTiles | `PMTiles` data store and vector tile layers |

Release assets provide three installation choices:

- `slim` contains Portolan and `portolan-java`. Install the three store extensions separately.
- `full` also contains the GeoParquet, COG HTTP, and PMTiles extension assemblies.
- `cloud` adds the Spring Boot adapter required by GeoServer Cloud `/opt/additional_libs`.

The Cloud backend keeps GeoParquet as the Portolan asset format. It changes
only the GeoServer store implementation. Existing stores retain their original
type.

All distributions require the GeoServer version used to build the release.
Stop vanilla GeoServer before you extract `slim` or `full` into `WEB-INF/lib`.
Mount the extracted `cloud` files in the GeoServer Cloud Web UI service.

The module first asks the datastore to calculate native and geographic bounds.
It preserves those values when both are available. Otherwise, it uses the
collection bbox as a fallback. The fallback transforms WGS84 coordinates into
the native CRS. A bbox that crosses the antimeridian expands to the full
longitude range required by GeoServer.

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
Style import messages include the per-layer state and a concise reason.

Increase the logger level in GeoServer when you need more detail from the surrounding store implementation.

## Failure behavior

The module keeps processing other plan entries when one store cannot publish its layers. The result panel reports the failure message.

Common causes include a missing store extension, an unreachable asset URL, invalid credentials, or a store that exposes no native resource names.

Default style failures can also indicate a missing `gs-mbstyle` extension, an
unsafe style URL, invalid MBStyle JSON, or an ambiguous `source-layer` mapping.
The module skips styles that target another asset or reference missing fields.
