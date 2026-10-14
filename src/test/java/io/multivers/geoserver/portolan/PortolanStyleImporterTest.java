package io.multivers.geoserver.portolan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogFactory;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.catalog.StyleHandler;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geotools.api.style.StyledLayerDescriptor;
import org.junit.Test;

public class PortolanStyleImporterTest {
    private static final String STYLE_JSON =
            """
            {"version":8,"layers":[{"id":"roads","type":"line","source":"data"}]}
            """;

    @Test
    public void skipsWithoutFetchingWhenMbStyleIsMissing() {
        Catalog catalog = mock(Catalog.class);
        PortolanStyleImporter importer =
                new PortolanStyleImporter(catalog, new PortolanStyleReadiness(List::of), uri -> {
                    throw new AssertionError("style must not be fetched");
                });

        PortolanStyleResult result = importer.importDefaultStyle(entry(), mock(WorkspaceInfo.class), layer());

        assertEquals(PortolanStyleResultStatus.SKIPPED_UNSUPPORTED, result.status());
        assertTrue(result.reason().contains("gs-mbstyle"));
        verify(catalog, never()).add(any(StyleInfo.class));
    }

    @Test
    public void importsWorkspaceStyleAndAssignsItAsLayerDefault() throws Exception {
        CatalogFixture fixture = catalogFixture(null);
        PortolanStyleImporter importer = new PortolanStyleImporter(
                fixture.catalog, new PortolanStyleReadiness(() -> List.of(fixture.handler)), uri -> STYLE_JSON);
        LayerInfo layer = layer();

        PortolanStyleResult result = importer.importDefaultStyle(entry(), fixture.workspace, layer);

        assertEquals(PortolanStyleResultStatus.IMPORTED, result.status());
        verify(fixture.style).setWorkspace(fixture.workspace);
        verify(fixture.style).setFormat("mbstyle");
        verify(fixture.style).setFilename("demo__roads__roads__default.json");
        verify(fixture.catalog).add(fixture.style);
        verify(fixture.resourcePool).writeStyle(any(StyleInfo.class), any(java.io.InputStream.class));
        verify(layer).setDefaultStyle(fixture.style);
        verify(fixture.catalog).save(layer);
        assertEquals(Boolean.TRUE, fixture.metadata.get(PortolanStyleImporter.METADATA_MANAGED));
        assertEquals(
                "https://styles.example.test/roads.json", fixture.metadata.get(PortolanStyleImporter.METADATA_SOURCE));
    }

    @Test
    public void assignsTheStyleToTheCatalogManagedLayer() {
        CatalogFixture fixture = catalogFixture(null);
        LayerInfo created = layer();
        LayerInfo managed = layer();
        when(created.getId()).thenReturn("layer-id");
        when(fixture.catalog.getLayer("layer-id")).thenReturn(managed);

        PortolanStyleResult result = new PortolanStyleImporter(
                        fixture.catalog, new PortolanStyleReadiness(() -> List.of(fixture.handler)), uri -> STYLE_JSON)
                .importDefaultStyle(entry(), fixture.workspace, created);

        assertEquals(PortolanStyleResultStatus.IMPORTED, result.status());
        verify(managed).setDefaultStyle(fixture.style);
        verify(fixture.catalog).save(managed);
        verify(created, never()).setDefaultStyle(any());
    }

    @Test
    public void reusesUnchangedManagedStyleWithoutWritingItAgain() throws Exception {
        StyleInfo existing = mock(StyleInfo.class);
        MetadataMap metadata = new MetadataMap();
        metadata.put(PortolanStyleImporter.METADATA_MANAGED, Boolean.TRUE);
        metadata.put(PortolanStyleImporter.METADATA_SOURCE, "https://styles.example.test/roads.json");
        metadata.put(PortolanStyleImporter.METADATA_CHECKSUM, PortolanStyleImporter.sha256(STYLE_JSON));
        when(existing.getMetadata()).thenReturn(metadata);
        CatalogFixture fixture = catalogFixture(existing);
        LayerInfo layer = layer();

        PortolanStyleResult result = new PortolanStyleImporter(
                        fixture.catalog, new PortolanStyleReadiness(() -> List.of(fixture.handler)), uri -> STYLE_JSON)
                .importDefaultStyle(entry(), fixture.workspace, layer);

        assertEquals(PortolanStyleResultStatus.REUSED, result.status());
        verify(fixture.resourcePool, never()).writeStyle(any(StyleInfo.class), any(java.io.InputStream.class));
        verify(layer).setDefaultStyle(existing);
    }

    @Test
    public void updatesAChangedStyleThatPortolanOwns() throws Exception {
        StyleInfo existing = mock(StyleInfo.class);
        MetadataMap metadata = new MetadataMap();
        metadata.put(PortolanStyleImporter.METADATA_MANAGED, Boolean.TRUE);
        metadata.put(PortolanStyleImporter.METADATA_SOURCE, "https://styles.example.test/roads.json");
        metadata.put(PortolanStyleImporter.METADATA_CHECKSUM, "old-checksum");
        when(existing.getMetadata()).thenReturn(metadata);
        CatalogFixture fixture = catalogFixture(existing);
        LayerInfo layer = layer();

        PortolanStyleResult result = new PortolanStyleImporter(
                        fixture.catalog, new PortolanStyleReadiness(() -> List.of(fixture.handler)), uri -> STYLE_JSON)
                .importDefaultStyle(entry(), fixture.workspace, layer);

        assertEquals(PortolanStyleResultStatus.IMPORTED, result.status());
        assertEquals(PortolanStyleImporter.sha256(STYLE_JSON), metadata.get(PortolanStyleImporter.METADATA_CHECKSUM));
        verify(fixture.resourcePool).writeStyle(eq(existing), any(java.io.InputStream.class));
        verify(fixture.catalog).save(existing);
        verify(layer).setDefaultStyle(existing);
    }

    @Test
    public void removesANewCatalogStyleWhenWritingItsContentFails() throws Exception {
        CatalogFixture fixture = catalogFixture(null);
        LayerInfo layer = layer();
        doThrow(new java.io.IOException("disk full"))
                .when(fixture.resourcePool)
                .writeStyle(any(StyleInfo.class), any(java.io.InputStream.class));

        PortolanStyleResult result = new PortolanStyleImporter(
                        fixture.catalog, new PortolanStyleReadiness(() -> List.of(fixture.handler)), uri -> STYLE_JSON)
                .importDefaultStyle(entry(), fixture.workspace, layer);

        assertEquals(PortolanStyleResultStatus.FAILED, result.status());
        assertTrue(result.reason().contains("disk full"));
        verify(fixture.catalog).remove(fixture.style);
        verify(layer, never()).setDefaultStyle(any());
    }

    @Test
    public void restoresThePreviousLayerStyleWhenAssignmentFails() {
        CatalogFixture fixture = catalogFixture(null);
        LayerInfo layer = layer();
        StyleInfo previous = mock(StyleInfo.class);
        when(layer.getDefaultStyle()).thenReturn(previous);
        doThrow(new IllegalStateException("catalog unavailable"))
                .when(fixture.catalog)
                .save(layer);

        PortolanStyleResult result = new PortolanStyleImporter(
                        fixture.catalog, new PortolanStyleReadiness(() -> List.of(fixture.handler)), uri -> STYLE_JSON)
                .importDefaultStyle(entry(), fixture.workspace, layer);

        assertEquals(PortolanStyleResultStatus.FAILED, result.status());
        verify(layer).setDefaultStyle(fixture.style);
        verify(layer).setDefaultStyle(previous);
        verify(fixture.catalog).remove(fixture.style);
    }

    @Test
    public void skipsStylesThatCannotMapUnambiguouslyToTheTargetLayer() {
        CatalogFixture fixture = catalogFixture(null);
        LayerInfo layer = layer();
        String mismatch =
                """
                {"version":8,"layers":[
                  {"id":"buildings","type":"fill","source":"data","source-layer":"buildings"}
                ]}
                """;
        String noSourceLayer =
                """
                {"version":8,"layers":[{"id":"roads","type":"line","source":"data"}]}
                """;

        PortolanStyleResult mismatchResult = new PortolanStyleImporter(
                        fixture.catalog, new PortolanStyleReadiness(() -> List.of(fixture.handler)), uri -> mismatch)
                .importDefaultStyle(entry(), fixture.workspace, layer, 1);
        PortolanStyleResult multipleTargetsResult = new PortolanStyleImporter(
                        fixture.catalog,
                        new PortolanStyleReadiness(() -> List.of(fixture.handler)),
                        uri -> noSourceLayer)
                .importDefaultStyle(entry(), fixture.workspace, layer, 2);

        assertEquals(PortolanStyleResultStatus.SKIPPED_UNSUPPORTED, mismatchResult.status());
        assertTrue(mismatchResult.reason().contains("does not match"));
        assertEquals(PortolanStyleResultStatus.SKIPPED_UNSUPPORTED, multipleTargetsResult.status());
        assertTrue(multipleTargetsResult.reason().contains("ambiguous"));
        verify(layer, never()).setDefaultStyle(any());
    }

    @Test
    public void doesNotOverwriteAStyleThatPortolanDoesNotOwn() {
        StyleInfo existing = mock(StyleInfo.class);
        when(existing.getMetadata()).thenReturn(new MetadataMap());
        CatalogFixture fixture = catalogFixture(existing);
        LayerInfo layer = layer();

        PortolanStyleResult result = new PortolanStyleImporter(
                        fixture.catalog, new PortolanStyleReadiness(() -> List.of(fixture.handler)), uri -> STYLE_JSON)
                .importDefaultStyle(entry(), fixture.workspace, layer);

        assertEquals(PortolanStyleResultStatus.SKIPPED_UNSUPPORTED, result.status());
        assertTrue(result.reason().contains("manually managed"));
        verify(layer, never()).setDefaultStyle(any());
    }

    @Test
    public void rejectsInvalidAndAmbiguousMapStylesWithoutChangingTheLayer() {
        CatalogFixture fixture = catalogFixture(null);
        LayerInfo layer = layer();
        PortolanStyleImporter invalid = new PortolanStyleImporter(
                fixture.catalog, new PortolanStyleReadiness(() -> List.of(fixture.handler)), uri -> "{not-json");
        PortolanStyleImporter ambiguous = new PortolanStyleImporter(
                fixture.catalog,
                new PortolanStyleReadiness(() -> List.of(fixture.handler)),
                uri ->
                        """
                        {"version":8,"layers":[
                          {"id":"roads","type":"line","source":"a","source-layer":"roads"},
                          {"id":"water","type":"fill","source":"b","source-layer":"water"}
                        ]}
                        """);

        assertEquals(
                PortolanStyleResultStatus.FAILED,
                invalid.importDefaultStyle(entry(), fixture.workspace, layer).status());
        PortolanStyleResult ambiguousResult = ambiguous.importDefaultStyle(entry(), fixture.workspace, layer);
        assertEquals(PortolanStyleResultStatus.SKIPPED_UNSUPPORTED, ambiguousResult.status());
        assertTrue(ambiguousResult.reason().contains("ambiguous"));
        verify(layer, never()).setDefaultStyle(any());
    }

    @Test
    public void reportsRetrievalAndSkipsUnsupportedMbStyleWithoutChangingTheLayer() throws Exception {
        CatalogFixture fixture = catalogFixture(null);
        LayerInfo layer = layer();
        PortolanStyleImporter unreachable = new PortolanStyleImporter(
                fixture.catalog, new PortolanStyleReadiness(() -> List.of(fixture.handler)), uri -> {
                    throw new IllegalArgumentException("remote style unavailable");
                });
        when(fixture.handler.validate(any(), any(), any()))
                .thenReturn(List.of(new IllegalArgumentException("unsupported expression")));
        PortolanStyleImporter rejected = new PortolanStyleImporter(
                fixture.catalog, new PortolanStyleReadiness(() -> List.of(fixture.handler)), uri -> STYLE_JSON);

        PortolanStyleResult unreachableResult = unreachable.importDefaultStyle(entry(), fixture.workspace, layer);
        PortolanStyleResult rejectedResult = rejected.importDefaultStyle(entry(), fixture.workspace, layer);

        assertEquals(PortolanStyleResultStatus.FAILED, unreachableResult.status());
        assertTrue(unreachableResult.reason().contains("unavailable"));
        assertEquals(PortolanStyleResultStatus.SKIPPED_UNSUPPORTED, rejectedResult.status());
        assertTrue(rejectedResult.reason().contains("unsupported expression"));
        verify(layer, never()).setDefaultStyle(any());
    }

    private static PortolanPublicationEntry entry() {
        PortolanDefaultStyle style = new PortolanDefaultStyle(
                PortolanStylePlanAction.IMPORT,
                "default",
                URI.create("https://styles.example.test/roads.json"),
                "application/vnd.mapbox.style+json",
                List.of("style", "default"),
                "demo__roads__roads__default",
                null);
        return new PortolanPublicationEntry(
                "roads",
                "roads",
                "roads",
                PortolanResourceFormat.GEOPARQUET,
                URI.create("https://data.example.test/roads.parquet"),
                null,
                PortolanPlanAction.CREATE,
                null,
                style);
    }

    private static LayerInfo layer() {
        LayerInfo layer = mock(LayerInfo.class);
        when(layer.getName()).thenReturn("roads");
        return layer;
    }

    private static CatalogFixture catalogFixture(StyleInfo existing) {
        Catalog catalog = mock(Catalog.class);
        CatalogFactory factory = mock(CatalogFactory.class);
        ResourcePool resourcePool = mock(ResourcePool.class);
        WorkspaceInfo workspace = mock(WorkspaceInfo.class);
        StyleInfo style = mock(StyleInfo.class);
        MetadataMap metadata = new MetadataMap();
        StyleHandler handler = mock(StyleHandler.class);
        when(catalog.getFactory()).thenReturn(factory);
        when(catalog.getResourcePool()).thenReturn(resourcePool);
        when(catalog.getStyleByName(workspace, "demo__roads__roads__default")).thenReturn(existing);
        when(factory.createStyle()).thenReturn(style);
        when(style.getMetadata()).thenReturn(metadata);
        when(handler.getFormat()).thenReturn("mbstyle");
        when(handler.getFileExtension()).thenReturn("json");
        try {
            when(handler.validate(any(), any(), any())).thenReturn(List.of());
            when(handler.parse(any(), any(), any(), any())).thenReturn(mock(StyledLayerDescriptor.class));
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
        return new CatalogFixture(catalog, factory, resourcePool, workspace, style, metadata, handler);
    }

    private record CatalogFixture(
            Catalog catalog,
            CatalogFactory factory,
            ResourcePool resourcePool,
            WorkspaceInfo workspace,
            StyleInfo style,
            MetadataMap metadata,
            StyleHandler handler) {}
}
