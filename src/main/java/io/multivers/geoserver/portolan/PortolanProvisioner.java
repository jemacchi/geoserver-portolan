package io.multivers.geoserver.portolan;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
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
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
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
    private final BiFunction<PortolanPublicationEntry, StoreInfo, LayerPublication> layerPublisher;
    private final PortolanGeoParquetStore geoParquetStore;
    private final StyleAssignment styleAssignment;

    public PortolanProvisioner(Catalog catalog) {
        this(catalog, PortolanGeoParquetStore.detect());
    }

    PortolanProvisioner(Catalog catalog, PortolanGeoParquetStore geoParquetStore) {
        this.catalog = catalog;
        this.layerPublisher = this::publishLayers;
        this.geoParquetStore = geoParquetStore;
        PortolanStyleImporter importer = new PortolanStyleImporter(catalog);
        this.styleAssignment = importer::importDefaultStyle;
    }

    PortolanProvisioner(Catalog catalog, BiFunction<PortolanPublicationEntry, StoreInfo, List<String>> layerPublisher) {
        this(catalog, layerPublisher, PortolanGeoParquetStore.detect());
    }

    PortolanProvisioner(
            Catalog catalog,
            BiFunction<PortolanPublicationEntry, StoreInfo, List<String>> layerPublisher,
            PortolanGeoParquetStore geoParquetStore) {
        this.catalog = catalog;
        this.layerPublisher = (entry, store) -> new LayerPublication(layerPublisher.apply(entry, store), List.of());
        this.geoParquetStore = geoParquetStore;
        this.styleAssignment = (entry, workspace, layer, targetLayerCount) -> null;
    }

    PortolanProvisioner(
            Catalog catalog,
            BiFunction<PortolanPublicationEntry, StoreInfo, List<String>> layerPublisher,
            PortolanGeoParquetStore geoParquetStore,
            StyleAssignment styleAssignment) {
        this.catalog = catalog;
        this.layerPublisher = (entry, store) -> new LayerPublication(layerPublisher.apply(entry, store), List.of());
        this.geoParquetStore = geoParquetStore;
        this.styleAssignment = styleAssignment;
    }

    public PortolanProvisionResult provision(PortolanPublicationPlan plan) {
        LOGGER.info(() -> "Provisioning Portolan catalog " + plan.catalogId() + " into workspace " + plan.workspace());
        WorkspaceInfo workspace = ensureWorkspace(plan.workspace());
        int created = 0;
        int skipped = 0;
        List<String> messages = new ArrayList<>();
        List<PortolanStyleResult> styleResults = new ArrayList<>();
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
            LayerPublication publication = layerPublisher.apply(entry, store);
            if (publication.messages().isEmpty()) {
                skipped++;
                messages.add(entry.collectionId() + ": no layers published from " + store.getName());
                continue;
            }
            created += countCreated(publication.messages());
            messages.add(entry.collectionId() + ": " + store.getType() + " store " + store.getName());
            messages.addAll(publication.messages());
            styleResults.addAll(publication.styleResults());
        }
        return new PortolanProvisionResult(
                plan.workspace(), created, skipped, List.copyOf(messages), List.copyOf(styleResults));
    }

    private StoreInfo ensureStore(
            PortolanPublicationPlan plan, PortolanPublicationEntry entry, WorkspaceInfo workspace) {
        if (entry.href() == null) {
            return null;
        }
        StoreInfo existing = catalog.getStoreByName(workspace, entry.storeName(), StoreInfo.class);
        if (existing != null) {
            repairPublicAwsS3Access(entry, existing);
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

    private void repairPublicAwsS3Access(PortolanPublicationEntry entry, StoreInfo store) {
        if (!(store instanceof DataStoreInfo dataStore)) {
            return;
        }
        boolean tileverseStore = entry.format() == PortolanResourceFormat.PMTILES
                || (entry.format() == PortolanResourceFormat.GEOPARQUET
                        && PortolanGeoParquetStore.parquetry().type().equalsIgnoreCase(dataStore.getType()));
        if (tileverseStore && PortolanStorageParameters.configurePublicAwsS3(dataStore, entry.href())) {
            catalog.save(dataStore);
            LOGGER.info(() -> "Enabled anonymous access for public AWS S3 store " + dataStore.getName());
        }
    }

    private DataStoreInfo createGeoParquetStore(
            PortolanPublicationPlan plan, PortolanPublicationEntry entry, WorkspaceInfo workspace) {
        CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.setWorkspace(workspace);
        DataStoreInfo store = builder.buildDataStore(entry.storeName());
        store.setDescription("Portolan collection " + entry.collectionId());
        geoParquetStore.configure(store, entry.href(), namespace(workspace));
        tagStore(plan, entry, store);
        catalog.add(store);
        LOGGER.info(() -> "Created " + store.getType() + " store " + store.getName() + " from " + entry.href());
        return store;
    }

    private DataStoreInfo createPmtilesStore(
            PortolanPublicationPlan plan, PortolanPublicationEntry entry, WorkspaceInfo workspace) {
        CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.setWorkspace(workspace);
        DataStoreInfo store = builder.buildDataStore(entry.storeName());
        store.setType(PortolanStoreReadiness.PMTILES_TYPE);
        store.setDescription("Portolan collection " + entry.collectionId());
        store.getConnectionParameters().put("pmtiles", entry.href().toString());
        store.getConnectionParameters().put("namespace", namespace(workspace));
        PortolanStorageParameters.configurePublicAwsS3(store, entry.href());
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
        store.setType(PortolanStoreReadiness.GEOTIFF_TYPE);
        store.setDescription("Portolan collection " + entry.collectionId());
        store.setURL(cogUrl(entry));
        tagStore(plan, entry, store);
        catalog.add(store);
        LOGGER.info(() -> "Created COG coverage store " + store.getName() + " from " + entry.href());
        return store;
    }

    private LayerPublication publishLayers(PortolanPublicationEntry entry, StoreInfo store) {
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
            return new LayerPublication(List.of("  layer publication failed: " + exception.getMessage()), List.of());
        }
        return LayerPublication.empty();
    }

    private LayerPublication publishFeatureLayers(PortolanPublicationEntry entry, DataStoreInfo store)
            throws Exception {
        DataAccess<? extends FeatureType, ? extends Feature> dataAccess = store.getDataStore(null);
        List<Name> names = nativeFeatureNames(entry, dataAccess.getNames());
        if (names.isEmpty()) {
            LOGGER.info(() -> "Store " + store.getName() + " has no feature type names");
            return new LayerPublication(List.of("  no feature types found"), List.of());
        }

        CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.setWorkspace(store.getWorkspace());
        builder.setStore(store);
        List<String> messages = new ArrayList<>();
        List<PortolanStyleResult> styleResults = new ArrayList<>();
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
            LayerPublication publication = publishLayer(entry, resource, layerName, names.size());
            messages.addAll(publication.messages());
            styleResults.addAll(publication.styleResults());
        }
        return new LayerPublication(List.copyOf(messages), List.copyOf(styleResults));
    }

    List<Name> nativeFeatureNames(PortolanPublicationEntry entry, List<Name> names) {
        List<Name> matches = names.stream()
                .filter(name ->
                        PortolanPlanner.geoserverName(name.getLocalPart()).equals(entry.layerName()))
                .toList();
        return matches.isEmpty() ? names : matches;
    }

    private LayerPublication publishCoverageLayers(PortolanPublicationEntry entry, CoverageStoreInfo store)
            throws Exception {
        GridCoverageReader reader = store.getGridCoverageReader(null, null);
        String[] names = reader.getGridCoverageNames();
        if (names.length == 0) {
            LOGGER.info(() -> "Store " + store.getName() + " has no coverage names");
            return new LayerPublication(List.of("  no coverages found"), List.of());
        }

        CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.setWorkspace(store.getWorkspace());
        builder.setStore(store);
        List<String> messages = new ArrayList<>();
        List<PortolanStyleResult> styleResults = new ArrayList<>();
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
            LayerPublication publication = publishLayer(entry, resource, layerName, names.length);
            messages.addAll(publication.messages());
            styleResults.addAll(publication.styleResults());
        }
        return new LayerPublication(List.copyOf(messages), List.copyOf(styleResults));
    }

    private boolean ensureBounds(PortolanPublicationEntry entry, CatalogBuilder builder, ResourceInfo resource) {
        try {
            if (nativeBoundsCrsMismatch(resource)) {
                resource.setNativeBoundingBox(null);
                resource.setLatLonBoundingBox(null);
                LOGGER.info(() -> "Recalculating bounds stored with the wrong CRS for Portolan resource "
                        + resourceName(resource));
            }
            builder.setupBounds(resource);
            if (hasUsableBounds(resource)) {
                LOGGER.info(() -> "Using datastore bounds for Portolan resource " + resourceName(resource));
                return true;
            }
        } catch (Exception exception) {
            LOGGER.log(
                    Level.INFO,
                    "Could not calculate bounds for Portolan resource " + resource.prefixedName(),
                    exception);
        }
        return applyPortolanBounds(entry, resource);
    }

    boolean applyPortolanBounds(PortolanPublicationEntry entry, ResourceInfo resource) {
        if (hasUsableBounds(resource)) {
            return false;
        }
        double[] bbox = entry.bbox();
        if (bbox == null) {
            LOGGER.info(() -> "No Portolan bbox available for resource " + resourceName(resource));
            return false;
        }
        double west = bbox[0] <= bbox[2] ? bbox[0] : -180.0;
        double east = bbox[0] <= bbox[2] ? bbox[2] : 180.0;
        ReferencedEnvelope geographic =
                new ReferencedEnvelope(west, east, bbox[1], bbox[3], DefaultGeographicCRS.WGS84);
        try {
            CoordinateReferenceSystem nativeCrs = nativeCrs(resource);
            ReferencedEnvelope nativeBounds = CRS.equalsIgnoreMetadata(nativeCrs, DefaultGeographicCRS.WGS84)
                    ? geographic
                    : geographic.transform(nativeCrs, true);
            resource.setNativeBoundingBox(nativeBounds);
            resource.setLatLonBoundingBox(geographic);
        } catch (Exception exception) {
            LOGGER.log(
                    Level.WARNING,
                    "Could not transform Portolan bounds for resource " + resourceName(resource),
                    exception);
            return false;
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

    private CoordinateReferenceSystem nativeCrs(ResourceInfo resource) throws Exception {
        CoordinateReferenceSystem nativeCrs = resource.getNativeCRS();
        if (nativeCrs != null) {
            return nativeCrs;
        }
        String srs = resource.getSRS();
        if (srs != null && !srs.isBlank()) {
            nativeCrs = CRS.decode(srs, true);
        } else {
            nativeCrs = DefaultGeographicCRS.WGS84;
            resource.setSRS("EPSG:4326");
        }
        resource.setNativeCRS(nativeCrs);
        return nativeCrs;
    }

    private boolean hasUsableBounds(ResourceInfo resource) {
        ReferencedEnvelope nativeBounds = resource.getNativeBoundingBox();
        if (!isUsable(nativeBounds) || !isUsable(resource.getLatLonBoundingBox())) {
            return false;
        }
        return !nativeBoundsCrsMismatch(resource);
    }

    private boolean nativeBoundsCrsMismatch(ResourceInfo resource) {
        ReferencedEnvelope nativeBounds = resource.getNativeBoundingBox();
        if (nativeBounds == null) {
            return false;
        }
        CoordinateReferenceSystem boundsCrs = nativeBounds.getCoordinateReferenceSystem();
        CoordinateReferenceSystem nativeCrs = resource.getNativeCRS();
        return boundsCrs != null && nativeCrs != null && !CRS.equalsIgnoreMetadata(boundsCrs, nativeCrs);
    }

    private boolean isUsable(ReferencedEnvelope envelope) {
        return envelope != null
                && Double.isFinite(envelope.getMinX())
                && Double.isFinite(envelope.getMinY())
                && Double.isFinite(envelope.getMaxX())
                && Double.isFinite(envelope.getMaxY())
                && envelope.getMinX() <= envelope.getMaxX()
                && envelope.getMinY() <= envelope.getMaxY();
    }

    private String resourceName(ResourceInfo resource) {
        try {
            return resource.prefixedName();
        } catch (RuntimeException exception) {
            String name = resource.getName();
            return name == null || name.isBlank() ? "<unnamed>" : name;
        }
    }

    LayerPublication publishLayer(PortolanPublicationEntry entry, ResourceInfo resource, String layerName)
            throws Exception {
        return publishLayer(entry, resource, layerName, 1);
    }

    private LayerPublication publishLayer(
            PortolanPublicationEntry entry, ResourceInfo resource, String layerName, int targetLayerCount)
            throws Exception {
        LayerInfo existing = catalog.getLayerByName(resource.prefixedName());
        String message;
        LayerInfo layer;
        if (existing != null) {
            LOGGER.info(() -> "Layer already exists: " + resource.prefixedName());
            message = "  layer exists: " + resource.prefixedName();
            layer = existing;
        } else {
            CatalogBuilder builder = new CatalogBuilder(catalog);
            layer = builder.buildLayer(resource);
            layer.setName(layerName);
            catalog.add(layer);
            LOGGER.info(() -> "Created layer " + resource.prefixedName());
            message = "  created layer: " + resource.prefixedName();
        }
        if (entry.defaultStyle().action() == PortolanStylePlanAction.DISABLED) {
            return new LayerPublication(List.of(message), List.of());
        }
        PortolanStyleResult styleResult =
                styleAssignment.apply(entry, resource.getStore().getWorkspace(), layer, targetLayerCount);
        return new LayerPublication(List.of(message), styleResult == null ? List.of() : List.of(styleResult));
    }

    String layerName(PortolanPublicationEntry entry, String nativeName, int nativeCount) {
        if (nativeCount <= 1) {
            return entry.layerName();
        }
        return PortolanPlanner.geoserverName(entry.layerName() + "__" + nativeName);
    }

    int countCreated(List<String> messages) {
        int created = 0;
        for (String message : messages) {
            if (message.contains("created layer:")) {
                created++;
            }
        }
        return created;
    }

    String cogUrl(PortolanPublicationEntry entry) {
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

    record LayerPublication(List<String> messages, List<PortolanStyleResult> styleResults) {
        private static LayerPublication empty() {
            return new LayerPublication(List.of(), List.of());
        }
    }

    @FunctionalInterface
    interface StyleAssignment {
        PortolanStyleResult apply(
                PortolanPublicationEntry entry, WorkspaceInfo workspace, LayerInfo layer, int targetLayerCount);
    }
}
