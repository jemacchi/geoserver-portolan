package io.multivers.geoserver.portolan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.impl.CatalogImpl;
import org.geoserver.catalog.impl.FeatureTypeInfoImpl;
import org.geotools.api.coverage.grid.GridCoverageReader;
import org.geotools.api.data.DataAccess;
import org.geotools.api.feature.Feature;
import org.geotools.api.feature.type.FeatureType;
import org.geotools.api.feature.type.Name;
import org.geotools.feature.NameImpl;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.junit.Test;

public class PortolanProvisionerTest {
    @Test
    public void appliesPortolanBoundsToResource() {
        PortolanProvisioner provisioner = new PortolanProvisioner(null);
        FeatureTypeInfoImpl resource = new FeatureTypeInfoImpl(null);
        PortolanPublicationEntry entry = entry(new double[] {-74.0, -55.0, -53.0, -21.0});

        assertTrue(provisioner.applyPortolanBounds(entry, resource));

        assertEnvelope(resource.getNativeBoundingBox());
        assertEnvelope(resource.getLatLonBoundingBox());
        assertEquals("EPSG:4326", resource.getSRS());
        assertNotNull(resource.getNativeCRS());
    }

    @Test
    public void overwritesExistingBoundsWithPortolanBounds() {
        PortolanProvisioner provisioner = new PortolanProvisioner(null);
        FeatureTypeInfoImpl resource = new FeatureTypeInfoImpl(null);
        resource.setNativeBoundingBox(new ReferencedEnvelope(0, 1, 0, 1, DefaultGeographicCRS.WGS84));
        resource.setLatLonBoundingBox(new ReferencedEnvelope(0, 1, 0, 1, DefaultGeographicCRS.WGS84));
        PortolanPublicationEntry entry = entry(new double[] {-74.0, -55.0, -53.0, -21.0});

        assertTrue(provisioner.applyPortolanBounds(entry, resource));

        assertEnvelope(resource.getNativeBoundingBox());
        assertEnvelope(resource.getLatLonBoundingBox());
    }

    @Test
    public void leavesResourceUntouchedWhenEntryHasNoBounds() {
        PortolanProvisioner provisioner = new PortolanProvisioner(null);
        FeatureTypeInfoImpl resource = new FeatureTypeInfoImpl(null);

        assertFalse(provisioner.applyPortolanBounds(entry(null), resource));
        assertEquals(null, resource.getNativeBoundingBox());
        assertEquals(null, resource.getLatLonBoundingBox());
    }

    @Test
    public void preservesExistingSpatialReferenceDetails() {
        PortolanProvisioner provisioner = new PortolanProvisioner(null);
        FeatureTypeInfoImpl resource = new FeatureTypeInfoImpl(null);
        resource.setSRS("EPSG:3857");
        resource.setNativeCRS(DefaultGeographicCRS.WGS84_3D);

        assertTrue(provisioner.applyPortolanBounds(entry(new double[] {-1, -2, 3, 4}), resource));

        assertEquals("EPSG:3857", resource.getSRS());
        assertSame(DefaultGeographicCRS.WGS84_3D, resource.getNativeCRS());
    }

    @Test
    public void skipsUnsupportedMissingAndHandlerlessEntries() {
        Catalog catalog = new CatalogImpl();
        PortolanPublicationPlan plan = plan(List.of(
                entry("skip", PortolanResourceFormat.UNKNOWN, null, PortolanPlanAction.SKIP, "no asset"),
                entry(
                        "unsupported",
                        PortolanResourceFormat.PMTILES,
                        URI.create("https://example.test/map.pmtiles"),
                        PortolanPlanAction.UNSUPPORTED,
                        "PMTiles unavailable"),
                entry("missing", PortolanResourceFormat.GEOPARQUET, null, PortolanPlanAction.CREATE, null)));

        PortolanProvisionResult result = new PortolanProvisioner(
                        catalog, (entry, store) -> List.of("  created layer: target:" + entry.layerName()))
                .provision(plan);

        assertEquals(0, result.created());
        assertEquals(3, result.skipped());
        assertTrue(result.messages().contains("skip: SKIP: no asset"));
        assertTrue(result.messages().contains("unsupported: UNSUPPORTED: PMTiles unavailable"));
        assertTrue(result.messages().contains("missing: no GeoServer store handler for GEOPARQUET"));
        assertNotNull(catalog.getWorkspaceByName("target"));
        assertEquals(
                "https://www.portolan-sdi.org/ns/target",
                catalog.getNamespaceByPrefix("target").getURI());
    }

    @Test
    public void createsStoresWithConnectionParametersAndProvenance() {
        Catalog catalog = new CatalogImpl();
        PortolanPublicationPlan plan = plan(List.of(
                entry(
                        "roads",
                        PortolanResourceFormat.GEOPARQUET,
                        URI.create("https://example.test/roads.parquet"),
                        PortolanPlanAction.CREATE,
                        null),
                entry(
                        "imagery",
                        PortolanResourceFormat.COG,
                        URI.create("https://example.test/image.tif"),
                        PortolanPlanAction.CREATE,
                        null),
                entry(
                        "tiles",
                        PortolanResourceFormat.PMTILES,
                        URI.create("https://example.test/map.pmtiles"),
                        PortolanPlanAction.CREATE,
                        null)));

        PortolanProvisionResult result = new PortolanProvisioner(
                        catalog, (entry, store) -> List.of("  created layer: target:" + entry.layerName()))
                .provision(plan);

        WorkspaceInfo workspace = catalog.getWorkspaceByName("target");
        DataStoreInfo geoParquet = catalog.getDataStoreByName(workspace, "roads");
        CoverageStoreInfo cog = catalog.getCoverageStoreByName(workspace, "imagery");
        DataStoreInfo pmtiles = catalog.getDataStoreByName(workspace, "tiles");
        assertEquals("GeoParquet", geoParquet.getType());
        assertEquals("geoparquet", geoParquet.getConnectionParameters().get("dbtype"));
        assertEquals(
                "https://example.test/roads.parquet",
                geoParquet.getConnectionParameters().get("uri"));
        assertEquals(
                "https://www.portolan-sdi.org/ns/target",
                geoParquet.getConnectionParameters().get("namespace"));
        assertEquals("GeoTIFF", cog.getType());
        assertEquals("cog://https://example.test/image.tif", cog.getURL());
        assertEquals("PMTiles", pmtiles.getType());
        assertEquals(
                "https://example.test/map.pmtiles",
                pmtiles.getConnectionParameters().get("pmtiles"));
        assertFalse(pmtiles.getConnectionParameters().containsKey("storage.s3.anonymous"));
        assertStoreMetadata(geoParquet, "roads", "https://example.test/roads.parquet");
        assertStoreMetadata(cog, "imagery", "https://example.test/image.tif");
        assertStoreMetadata(pmtiles, "tiles", "https://example.test/map.pmtiles");
        assertEquals(3, result.created());
        assertEquals(0, result.skipped());
    }

    @Test
    public void createsParquetryStoreWithItsNativeConnectionContract() {
        Catalog catalog = new CatalogImpl();
        PortolanPublicationPlan plan = plan(List.of(entry(
                "roads",
                PortolanResourceFormat.GEOPARQUET,
                URI.create("https://example.test/roads.parquet"),
                PortolanPlanAction.CREATE,
                null)));

        new PortolanProvisioner(
                        catalog,
                        (entry, store) -> List.of("  created layer: target:" + entry.layerName()),
                        PortolanGeoParquetStore.parquetry())
                .provision(plan);

        WorkspaceInfo workspace = catalog.getWorkspaceByName("target");
        DataStoreInfo store = catalog.getDataStoreByName(workspace, "roads");
        assertEquals("Parquet", store.getType());
        assertEquals(
                "https://example.test/roads.parquet",
                store.getConnectionParameters().get("geoparquet"));
        assertEquals(
                "https://www.portolan-sdi.org/ns/target",
                store.getConnectionParameters().get("namespace"));
        assertFalse(store.getConnectionParameters().containsKey("dbtype"));
        assertFalse(store.getConnectionParameters().containsKey("uri"));
        assertFalse(store.getConnectionParameters().containsKey("storage.s3.anonymous"));
    }

    @Test
    public void configuresAnonymousAccessForPublicAwsS3Assets() {
        Catalog catalog = new CatalogImpl();
        PortolanPublicationPlan plan = plan(List.of(
                entry(
                        "bathymetry",
                        PortolanResourceFormat.GEOPARQUET,
                        URI.create("https://overturemaps-us-west-2.s3.us-west-2.amazonaws.com/release/data.parquet"),
                        PortolanPlanAction.CREATE,
                        null),
                entry(
                        "address",
                        PortolanResourceFormat.PMTILES,
                        URI.create(
                                "https://overturemaps-extras-us-west-2.s3.us-west-2.amazonaws.com/tiles/addresses.pmtiles"),
                        PortolanPlanAction.CREATE,
                        null)));

        new PortolanProvisioner(
                        catalog,
                        (entry, store) -> List.of("  created layer: target:" + entry.layerName()),
                        PortolanGeoParquetStore.parquetry())
                .provision(plan);

        WorkspaceInfo workspace = catalog.getWorkspaceByName("target");
        DataStoreInfo parquet = catalog.getDataStoreByName(workspace, "bathymetry");
        DataStoreInfo pmtiles = catalog.getDataStoreByName(workspace, "address");
        assertEquals(Boolean.TRUE, parquet.getConnectionParameters().get("storage.s3.anonymous"));
        assertEquals(Boolean.TRUE, pmtiles.getConnectionParameters().get("storage.s3.anonymous"));
    }

    @Test
    public void repairsAnonymousAccessOnExistingPublicAwsS3Store() {
        Catalog catalog = new CatalogImpl();
        WorkspaceInfo workspace = catalog.getFactory().createWorkspace();
        workspace.setName("target");
        catalog.add(workspace);
        DataStoreInfo existing = catalog.getFactory().createDataStore();
        existing.setName("address");
        existing.setWorkspace(workspace);
        existing.setType("PMTiles");
        catalog.add(existing);

        new PortolanProvisioner(catalog, (entry, store) -> List.of("  layer exists: target:address"))
                .provision(plan(List.of(entry(
                        "address",
                        PortolanResourceFormat.PMTILES,
                        URI.create(
                                "https://overturemaps-extras-us-west-2.s3.us-west-2.amazonaws.com/tiles/addresses.pmtiles"),
                        PortolanPlanAction.EXISTS,
                        null))));

        assertEquals(Boolean.TRUE, existing.getConnectionParameters().get("storage.s3.anonymous"));
    }

    @Test
    public void selectsMatchingNativeFeatureTypeFromSharedStore() {
        PortolanProvisioner provisioner = new PortolanProvisioner(null);
        PortolanPublicationEntry entry = entry(
                "land",
                PortolanResourceFormat.PMTILES,
                URI.create("https://example.test/base.pmtiles"),
                PortolanPlanAction.CREATE,
                null);
        List<Name> names = List.of(new NameImpl("bathymetry"), new NameImpl("land"), new NameImpl("water"));

        assertEquals(
                List.of("land"),
                provisioner.nativeFeatureNames(entry, names).stream()
                        .map(Name::getLocalPart)
                        .toList());
    }

    @Test
    public void keepsAllNativeFeatureTypesWhenNoneMatchesCollection() {
        PortolanProvisioner provisioner = new PortolanProvisioner(null);
        PortolanPublicationEntry entry = entry(
                "basemap",
                PortolanResourceFormat.PMTILES,
                URI.create("https://example.test/base.pmtiles"),
                PortolanPlanAction.CREATE,
                null);
        List<Name> names = List.of(new NameImpl("land"), new NameImpl("water"));

        assertEquals(names, provisioner.nativeFeatureNames(entry, names));
    }

    @Test
    public void reusesExistingStoreAndExistingWorkspace() {
        Catalog catalog = new CatalogImpl();
        WorkspaceInfo workspace = catalog.getFactory().createWorkspace();
        workspace.setName("target");
        catalog.add(workspace);
        DataStoreInfo existing = catalog.getFactory().createDataStore();
        existing.setName("roads");
        existing.setWorkspace(workspace);
        existing.setType("GeoParquet");
        catalog.add(existing);

        PortolanProvisionResult result = new PortolanProvisioner(
                        catalog, (entry, store) -> List.of("  layer exists: target:" + entry.layerName()))
                .provision(plan(List.of(entry(
                        "roads",
                        PortolanResourceFormat.GEOPARQUET,
                        URI.create("https://example.test/roads.parquet"),
                        PortolanPlanAction.EXISTS,
                        null))));

        assertEquals(
                existing.getId(), catalog.getDataStoreByName(workspace, "roads").getId());
        assertEquals(0, result.created());
        assertTrue(result.messages().stream().anyMatch(message -> message.contains("layer exists")));
    }

    @Test
    public void skipsStoreWhenPublisherFindsNoLayers() {
        Catalog catalog = new CatalogImpl();
        PortolanProvisionResult result = new PortolanProvisioner(catalog, (entry, store) -> List.of())
                .provision(plan(List.of(entry(
                        "roads",
                        PortolanResourceFormat.GEOPARQUET,
                        URI.create("https://example.test/roads.parquet"),
                        PortolanPlanAction.CREATE,
                        null))));

        assertEquals(0, result.created());
        assertEquals(1, result.skipped());
        assertTrue(result.messages().contains("roads: no layers published from roads"));
    }

    @Test
    public void handlesUnknownFormatsAndNullSkipReasons() {
        Catalog catalog = new CatalogImpl();
        PortolanProvisionResult result = new PortolanProvisioner(catalog)
                .provision(plan(List.of(
                        entry(
                                "unknown",
                                PortolanResourceFormat.UNKNOWN,
                                URI.create("https://example.test/data.bin"),
                                PortolanPlanAction.CREATE,
                                null),
                        entry("skip", PortolanResourceFormat.UNKNOWN, null, PortolanPlanAction.SKIP, null))));

        assertEquals(2, result.skipped());
        assertTrue(result.messages().contains("unknown: no GeoServer store handler for UNKNOWN"));
        assertTrue(result.messages().contains("skip: SKIP"));
    }

    @Test
    public void usesWorkspaceNameWhenNoNamespaceExists() {
        Catalog catalog = new CatalogImpl();
        WorkspaceInfo workspace = catalog.getFactory().createWorkspace();
        workspace.setName("target");
        catalog.add(workspace);
        PortolanProvisionResult result = new PortolanProvisioner(
                        catalog, (entry, store) -> List.of("  created layer: target:" + entry.layerName()))
                .provision(plan(List.of(entry(
                        "roads",
                        PortolanResourceFormat.GEOPARQUET,
                        URI.create("https://example.test/roads.parquet"),
                        PortolanPlanAction.CREATE,
                        null))));

        DataStoreInfo store = catalog.getDataStoreByName(workspace, "roads");
        assertEquals("target", store.getConnectionParameters().get("namespace"));
        assertEquals(1, result.created());
    }

    @Test
    public void derivesCogUrlsLayerNamesAndCreatedCount() {
        PortolanProvisioner provisioner = new PortolanProvisioner(null);
        PortolanPublicationEntry cog = entry(
                "image",
                PortolanResourceFormat.COG,
                URI.create("cog://https://example.test/image.tif"),
                PortolanPlanAction.CREATE,
                null);

        assertEquals("cog://https://example.test/image.tif", provisioner.cogUrl(cog));
        assertEquals("image", provisioner.layerName(cog, "native", 1));
        assertEquals("image__native_name", provisioner.layerName(cog, "native name", 2));
        assertEquals(
                2,
                provisioner.countCreated(
                        List.of("  created layer: target:a", "  layer exists: target:b", "  created layer: target:c")));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void reportsDataStoreWithoutFeatureTypes() throws Exception {
        Catalog catalog = mock(Catalog.class);
        WorkspaceInfo workspace = mock(WorkspaceInfo.class);
        DataStoreInfo store = mock(DataStoreInfo.class);
        DataAccess<? extends FeatureType, ? extends Feature> dataAccess = mock(DataAccess.class);
        when(catalog.getWorkspaceByName("target")).thenReturn(workspace);
        when(catalog.getStoreByName(workspace, "roads", StoreInfo.class)).thenReturn(store);
        when(store.getName()).thenReturn("roads");
        when(store.getType()).thenReturn("GeoParquet");
        when(store.getDataStore(isNull())).thenReturn((DataAccess) dataAccess);
        when(dataAccess.getNames()).thenReturn(List.of());

        PortolanProvisionResult result = new PortolanProvisioner(catalog)
                .provision(plan(List.of(entry(
                        "roads",
                        PortolanResourceFormat.GEOPARQUET,
                        URI.create("https://example.test/roads.parquet"),
                        PortolanPlanAction.EXISTS,
                        null))));

        assertTrue(result.messages().contains("  no feature types found"));
    }

    @Test
    public void reportsCoverageStoreWithoutCoverages() throws Exception {
        Catalog catalog = mock(Catalog.class);
        WorkspaceInfo workspace = mock(WorkspaceInfo.class);
        CoverageStoreInfo store = mock(CoverageStoreInfo.class);
        GridCoverageReader reader = mock(GridCoverageReader.class);
        when(catalog.getWorkspaceByName("target")).thenReturn(workspace);
        when(catalog.getStoreByName(workspace, "imagery", StoreInfo.class)).thenReturn(store);
        when(store.getName()).thenReturn("imagery");
        when(store.getType()).thenReturn("GeoTIFF");
        when(store.getGridCoverageReader(isNull(), isNull())).thenReturn(reader);
        when(reader.getGridCoverageNames()).thenReturn(new String[0]);

        PortolanProvisionResult result = new PortolanProvisioner(catalog)
                .provision(plan(List.of(entry(
                        "imagery",
                        PortolanResourceFormat.COG,
                        URI.create("https://example.test/image.tif"),
                        PortolanPlanAction.EXISTS,
                        null))));

        assertTrue(result.messages().contains("  no coverages found"));
    }

    @Test
    public void reportsLayerPublicationFailureAndUnknownStoreType() throws Exception {
        Catalog failingCatalog = mock(Catalog.class);
        WorkspaceInfo workspace = mock(WorkspaceInfo.class);
        DataStoreInfo failingStore = mock(DataStoreInfo.class);
        when(failingCatalog.getWorkspaceByName("target")).thenReturn(workspace);
        when(failingCatalog.getStoreByName(workspace, "roads", StoreInfo.class)).thenReturn(failingStore);
        when(failingStore.getName()).thenReturn("roads");
        when(failingStore.getType()).thenReturn("GeoParquet");
        when(failingStore.getDataStore(any())).thenThrow(new java.io.IOException("offline"));

        PortolanProvisionResult failure = new PortolanProvisioner(failingCatalog)
                .provision(plan(List.of(entry(
                        "roads",
                        PortolanResourceFormat.GEOPARQUET,
                        URI.create("https://example.test/roads.parquet"),
                        PortolanPlanAction.EXISTS,
                        null))));
        assertTrue(failure.messages().contains("  layer publication failed: offline"));

        Catalog unknownCatalog = mock(Catalog.class);
        StoreInfo unknownStore = mock(StoreInfo.class);
        when(unknownCatalog.getWorkspaceByName("target")).thenReturn(workspace);
        when(unknownCatalog.getStoreByName(workspace, "roads", StoreInfo.class)).thenReturn(unknownStore);
        when(unknownStore.getName()).thenReturn("roads");
        PortolanProvisionResult unknown = new PortolanProvisioner(unknownCatalog)
                .provision(plan(List.of(entry(
                        "roads",
                        PortolanResourceFormat.GEOPARQUET,
                        URI.create("https://example.test/roads.parquet"),
                        PortolanPlanAction.EXISTS,
                        null))));
        assertEquals(1, unknown.skipped());
    }

    private static PortolanPublicationEntry entry(double[] bbox) {
        return new PortolanPublicationEntry(
                "collection",
                "store",
                "layer",
                PortolanResourceFormat.GEOPARQUET,
                URI.create("https://example.test/data.parquet"),
                bbox,
                PortolanPlanAction.CREATE,
                null);
    }

    private static PortolanPublicationEntry entry(
            String id, PortolanResourceFormat format, URI href, PortolanPlanAction action, String reason) {
        return new PortolanPublicationEntry(id, id, id, format, href, null, action, reason);
    }

    private static PortolanPublicationPlan plan(List<PortolanPublicationEntry> entries) {
        return new PortolanPublicationPlan("demo", "https://example.test/catalog.json", "target", entries);
    }

    private static void assertStoreMetadata(StoreInfo store, String collection, String asset) {
        assertEquals(Boolean.TRUE, store.getMetadata().get(PortolanProvisioner.METADATA_MANAGED));
        assertEquals(
                "https://example.test/catalog.json", store.getMetadata().get(PortolanProvisioner.METADATA_CATALOG));
        assertEquals(collection, store.getMetadata().get(PortolanProvisioner.METADATA_COLLECTION));
        assertEquals(asset, store.getMetadata().get(PortolanProvisioner.METADATA_ASSET));
    }

    private static void assertEnvelope(ReferencedEnvelope envelope) {
        assertNotNull(envelope);
        assertEquals(-74.0, envelope.getMinX(), 0.0);
        assertEquals(-55.0, envelope.getMinY(), 0.0);
        assertEquals(-53.0, envelope.getMaxX(), 0.0);
        assertEquals(-21.0, envelope.getMaxY(), 0.0);
    }
}
