package org.geoserver.portolan;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogBuilder;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geotools.api.coverage.grid.GridCoverageReader;
import org.geotools.api.data.DataAccess;
import org.geotools.api.feature.Feature;
import org.geotools.api.feature.type.FeatureType;
import org.geotools.api.feature.type.Name;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.util.logging.Logging;

/** Applies the GeoServer catalog mutations for a Portolan publication plan. */
public final class PortolanProvisioner {
    private static final Logger LOGGER = Logging.getLogger(PortolanProvisioner.class);

    public static final String METADATA_MANAGED = "portolan.managed";
    public static final String METADATA_CATALOG = "portolan.catalog";
    public static final String METADATA_COLLECTION = "portolan.collection";
    public static final String METADATA_ASSET = "portolan.asset";

    private final Catalog catalog;

    public PortolanProvisioner(Catalog catalog) {
        this.catalog = catalog;
    }

    public PortolanProvisionResult provision(PortolanPublicationPlan plan) {
        LOGGER.info(() -> "Provisioning Portolan catalog " + plan.catalogId() + " into workspace " + plan.workspace());
        WorkspaceInfo workspace = ensureWorkspace(plan.workspace());
        int created = 0;
        int skipped = 0;
        List<String> messages = new ArrayList<>();
        for (PortolanPublicationEntry entry : plan.entries()) {
            LOGGER.info(() -> "Processing Portolan collection "
                    + entry.collectionId()
                    + " as "
                    + entry.format()
                    + " store "
                    + entry.storeName());
            if (entry.action() == PortolanPlanAction.SKIP || entry.action() == PortolanPlanAction.UNSUPPORTED) {
                skipped++;
                messages.add(entry.collectionId() + ": " + entry.action() + reason(entry));
                continue;
            }
            StoreInfo store = ensureStore(plan, entry, workspace);
            if (store == null) {
                skipped++;
                messages.add(entry.collectionId() + ": no GeoServer store handler for " + entry.format());
                continue;
            }
            List<String> layerMessages = publishLayers(entry, store);
            if (layerMessages.isEmpty()) {
                skipped++;
                messages.add(entry.collectionId() + ": no layers published from " + store.getName());
                continue;
            }
            created += countCreated(layerMessages);
            messages.add(entry.collectionId() + ": " + store.getType() + " store " + store.getName());
            messages.addAll(layerMessages);
        }
        return new PortolanProvisionResult(plan.workspace(), created, skipped, List.copyOf(messages));
    }

    private StoreInfo ensureStore(
            PortolanPublicationPlan plan, PortolanPublicationEntry entry, WorkspaceInfo workspace) {
        if (entry.href() == null) {
            return null;
        }
        StoreInfo existing = catalog.getStoreByName(workspace, entry.storeName(), StoreInfo.class);
        if (existing != null) {
            LOGGER.info(() -> "Reusing existing store " + existing.getName());
            return existing;
        }
        if (entry.format() == PortolanResourceFormat.GEOPARQUET) {
            return createGeoParquetStore(plan, entry, workspace);
        }
        if (entry.format() == PortolanResourceFormat.COG) {
            return createCogStore(plan, entry, workspace);
        }
        if (entry.format() == PortolanResourceFormat.PMTILES) {
            return createPmtilesStore(plan, entry, workspace);
        }
        return null;
    }

    private DataStoreInfo createGeoParquetStore(
            PortolanPublicationPlan plan, PortolanPublicationEntry entry, WorkspaceInfo workspace) {
        CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.setWorkspace(workspace);
        DataStoreInfo store = builder.buildDataStore(entry.storeName());
        store.setType(PortolanStoreHandlers.GEOPARQUET_TYPE);
        store.setDescription("Portolan collection " + entry.collectionId());
        store.getConnectionParameters().put("dbtype", "geoparquet");
        store.getConnectionParameters().put("uri", entry.href().toString());
        store.getConnectionParameters().put("namespace", namespace(workspace));
        tagStore(plan, entry, store);
        catalog.add(store);
        LOGGER.info(() -> "Created GeoParquet store " + store.getName() + " from " + entry.href());
        return store;
    }

    private DataStoreInfo createPmtilesStore(
            PortolanPublicationPlan plan, PortolanPublicationEntry entry, WorkspaceInfo workspace) {
        CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.setWorkspace(workspace);
        DataStoreInfo store = builder.buildDataStore(entry.storeName());
        store.setType(PortolanStoreHandlers.PMTILES_TYPE);
        store.setDescription("Portolan collection " + entry.collectionId());
        store.getConnectionParameters().put("pmtiles", entry.href().toString());
        store.getConnectionParameters().put("namespace", namespace(workspace));
        tagStore(plan, entry, store);
        catalog.add(store);
        LOGGER.info(() -> "Created PMTiles store " + store.getName() + " from " + entry.href());
        return store;
    }

    private CoverageStoreInfo createCogStore(
            PortolanPublicationPlan plan, PortolanPublicationEntry entry, WorkspaceInfo workspace) {
        CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.setWorkspace(workspace);
        CoverageStoreInfo store = builder.buildCoverageStore(entry.storeName());
        store.setType(PortolanStoreHandlers.GEOTIFF_TYPE);
        store.setDescription("Portolan collection " + entry.collectionId());
        store.setURL(cogUrl(entry));
        tagStore(plan, entry, store);
        catalog.add(store);
        LOGGER.info(() -> "Created COG coverage store " + store.getName() + " from " + entry.href());
        return store;
    }

    private List<String> publishLayers(PortolanPublicationEntry entry, StoreInfo store) {
        try {
            if (store instanceof DataStoreInfo dataStore) {
                return publishFeatureLayers(entry, dataStore);
            }
            if (store instanceof CoverageStoreInfo coverageStore) {
                return publishCoverageLayers(entry, coverageStore);
            }
        } catch (Exception exception) {
            LOGGER.log(
                    Level.WARNING,
                    "Could not publish layers for Portolan collection " + entry.collectionId(),
                    exception);
            return List.of("  layer publication failed: " + exception.getMessage());
        }
        return List.of();
    }

    private List<String> publishFeatureLayers(PortolanPublicationEntry entry, DataStoreInfo store) throws Exception {
        DataAccess<? extends FeatureType, ? extends Feature> dataAccess = store.getDataStore(null);
        List<Name> names = dataAccess.getNames();
        if (names.isEmpty()) {
            LOGGER.info(() -> "Store " + store.getName() + " has no feature type names");
            return List.of("  no feature types found");
        }

        CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.setWorkspace(store.getWorkspace());
        builder.setStore(store);
        List<String> messages = new ArrayList<>();
        for (Name nativeName : names) {
            String layerName = layerName(entry, nativeName.getLocalPart(), names.size());
            FeatureTypeInfo resource = catalog.getFeatureTypeByDataStore(store, layerName);
            if (resource == null) {
                resource = builder.buildFeatureType(nativeName);
                resource.setName(layerName);
                resource.setTitle(entry.collectionId());
                tagResource(entry, resource);
                ensureBounds(entry, builder, resource);
                catalog.add(resource);
                LOGGER.info(() -> "Created feature type " + layerName + " for store " + store.getName());
            } else if (ensureBounds(entry, builder, resource)) {
                catalog.save(resource);
            }
            messages.add(publishLayer(resource, layerName));
        }
        return messages;
    }

    private List<String> publishCoverageLayers(PortolanPublicationEntry entry, CoverageStoreInfo store)
            throws Exception {
        GridCoverageReader reader = store.getGridCoverageReader(null, null);
        String[] names = reader.getGridCoverageNames();
        if (names.length == 0) {
            LOGGER.info(() -> "Store " + store.getName() + " has no coverage names");
            return List.of("  no coverages found");
        }

        CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.setWorkspace(store.getWorkspace());
        builder.setStore(store);
        List<String> messages = new ArrayList<>();
        for (String nativeName : names) {
            String layerName = layerName(entry, nativeName, names.length);
            CoverageInfo resource = catalog.getCoverageByCoverageStore(store, layerName);
            if (resource == null) {
                resource = builder.buildCoverage(nativeName);
                resource.setName(layerName);
                resource.setTitle(entry.collectionId());
                tagResource(entry, resource);
                ensureBounds(entry, builder, resource);
                catalog.add(resource);
                LOGGER.info(() -> "Created coverage " + layerName + " for store " + store.getName());
            } else if (ensureBounds(entry, builder, resource)) {
                catalog.save(resource);
            }
            messages.add(publishLayer(resource, layerName));
        }
        return messages;
    }

    private boolean ensureBounds(PortolanPublicationEntry entry, CatalogBuilder builder, ResourceInfo resource) {
        try {
            builder.setupBounds(resource);
        } catch (Exception exception) {
            LOGGER.log(
                    Level.INFO,
                    "Could not calculate bounds for Portolan resource " + resource.prefixedName(),
                    exception);
        }
        return applyPortolanBounds(entry, resource);
    }

    boolean applyPortolanBounds(PortolanPublicationEntry entry, ResourceInfo resource) {
        double[] bbox = entry.bbox();
        if (bbox == null) {
            LOGGER.info(() -> "No Portolan bbox available for resource " + resourceName(resource));
            return false;
        }
        ReferencedEnvelope envelope =
                new ReferencedEnvelope(bbox[0], bbox[2], bbox[1], bbox[3], DefaultGeographicCRS.WGS84);
        resource.setNativeBoundingBox(envelope);
        resource.setLatLonBoundingBox(envelope);
        if (resource.getSRS() == null || resource.getSRS().isBlank()) {
            resource.setSRS("EPSG:4326");
        }
        if (resource.getNativeCRS() == null) {
            resource.setNativeCRS(DefaultGeographicCRS.WGS84);
        }
        LOGGER.info(() -> "Applied Portolan bbox ["
                + bbox[0]
                + ", "
                + bbox[1]
                + ", "
                + bbox[2]
                + ", "
                + bbox[3]
                + "] to resource "
                + resourceName(resource));
        return true;
    }

    private String resourceName(ResourceInfo resource) {
        try {
            return resource.prefixedName();
        } catch (RuntimeException exception) {
            String name = resource.getName();
            return name == null || name.isBlank() ? "<unnamed>" : name;
        }
    }

    private String publishLayer(ResourceInfo resource, String layerName) throws Exception {
        LayerInfo existing = catalog.getLayerByName(resource.prefixedName());
        if (existing != null) {
            LOGGER.info(() -> "Layer already exists: " + resource.prefixedName());
            return "  layer exists: " + resource.prefixedName();
        }
        CatalogBuilder builder = new CatalogBuilder(catalog);
        LayerInfo layer = builder.buildLayer(resource);
        layer.setName(layerName);
        catalog.add(layer);
        LOGGER.info(() -> "Created layer " + resource.prefixedName());
        return "  created layer: " + resource.prefixedName();
    }

    private String layerName(PortolanPublicationEntry entry, String nativeName, int nativeCount) {
        if (nativeCount <= 1) {
            return entry.layerName();
        }
        return PortolanPlanner.geoserverName(entry.layerName() + "__" + nativeName);
    }

    private int countCreated(List<String> messages) {
        int created = 0;
        for (String message : messages) {
            if (message.contains("created layer:")) {
                created++;
            }
        }
        return created;
    }

    private String cogUrl(PortolanPublicationEntry entry) {
        String href = entry.href().toString();
        return href.startsWith("cog://") ? href : "cog://" + href;
    }

    private void tagStore(PortolanPublicationPlan plan, PortolanPublicationEntry entry, StoreInfo store) {
        store.getMetadata().put(METADATA_MANAGED, Boolean.TRUE);
        store.getMetadata().put(METADATA_CATALOG, plan.catalogHref());
        store.getMetadata().put(METADATA_COLLECTION, entry.collectionId());
        store.getMetadata().put(METADATA_ASSET, entry.href().toString());
    }

    private void tagResource(PortolanPublicationEntry entry, ResourceInfo resource) {
        resource.getMetadata().put(METADATA_MANAGED, Boolean.TRUE);
        resource.getMetadata().put(METADATA_COLLECTION, entry.collectionId());
        resource.getMetadata().put(METADATA_ASSET, entry.href().toString());
    }

    private String reason(PortolanPublicationEntry entry) {
        return entry.reason() == null ? "" : ": " + entry.reason();
    }

    private WorkspaceInfo ensureWorkspace(String workspaceName) {
        WorkspaceInfo workspace = catalog.getWorkspaceByName(workspaceName);
        if (workspace != null) {
            return workspace;
        }
        workspace = catalog.getFactory().createWorkspace();
        workspace.setName(workspaceName);
        catalog.add(workspace);
        LOGGER.info(() -> "Created workspace " + workspaceName);

        NamespaceInfo namespace = catalog.getNamespaceByPrefix(workspaceName);
        if (namespace == null) {
            namespace = catalog.getFactory().createNamespace();
            namespace.setPrefix(workspaceName);
            namespace.setURI("https://www.portolan-sdi.org/ns/" + workspaceName);
            catalog.add(namespace);
            String namespaceUri = namespace.getURI();
            LOGGER.info(() -> "Created namespace " + namespaceUri);
        }
        return workspace;
    }

    private String namespace(WorkspaceInfo workspace) {
        NamespaceInfo namespace = catalog.getNamespaceByPrefix(workspace.getName());
        return namespace == null ? workspace.getName() : namespace.getURI();
    }
}
