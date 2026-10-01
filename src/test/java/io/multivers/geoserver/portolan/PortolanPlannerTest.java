package io.multivers.geoserver.portolan;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.impl.CatalogImpl;
import org.geoserver.catalog.impl.DataStoreInfoImpl;
import org.geoserver.catalog.impl.WorkspaceInfoImpl;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PortolanPlannerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void geoserverNameNormalizesCatalogIds() {
        assertEquals("roads__2024", PortolanPlanner.geoserverName("roads/2024"));
        assertEquals("roads_2024", PortolanPlanner.geoserverName("roads 2024"));
        assertEquals("roads", PortolanPlanner.geoserverName("._roads--"));
        assertEquals("portolan", PortolanPlanner.geoserverName(null));
        assertEquals("portolan", PortolanPlanner.geoserverName("   "));
    }

    @Test
    public void plansSupportedCollectionsAndUsesCatalogIdAsWorkspace() throws Exception {
        Path catalogPath = writeCatalog(
                "demo",
                List.of(
                        collection("roads", "roads.parquet", "application/vnd.apache.parquet", "[-71,-35,-70,-34]"),
                        collection("imagery", "image.tif", null, "[-72,-36,-69,-33]"),
                        collection("tiles", "map.pmtiles", null, null)));
        PortolanPlanner planner = new PortolanPlanner(new CatalogImpl(), format -> null);

        PortolanPublicationPlan plan = planner.plan(catalogPath, " ");

        assertEquals("demo", plan.catalogId());
        assertEquals("demo", plan.workspace());
        assertEquals(3, plan.entries().size());
        assertEntry(plan.entries().get(0), PortolanResourceFormat.GEOPARQUET, PortolanPlanAction.CREATE);
        assertArrayEquals(
                new double[] {-71, -35, -70, -34}, plan.entries().get(0).bbox(), 0.0);
        assertEntry(plan.entries().get(1), PortolanResourceFormat.COG, PortolanPlanAction.CREATE);
        assertEntry(plan.entries().get(2), PortolanResourceFormat.PMTILES, PortolanPlanAction.CREATE);
        assertNull(plan.entries().get(2).bbox());
    }

    @Test
    public void plansUriAndFallsBackForMissingCatalogId() throws Exception {
        Path catalogPath = writeCatalog(null, List.of(collectionWithTopLevelBbox()));
        PortolanPlanner planner = new PortolanPlanner(new CatalogImpl(), format -> null);

        PortolanPublicationPlan plan = planner.plan(catalogPath.toUri(), null);

        assertEquals("portolan", plan.catalogId());
        assertEquals("portolan", plan.workspace());
        assertArrayEquals(new double[] {-10, -5, 10, 5}, plan.entries().get(0).bbox(), 0.0);
    }

    @Test
    public void marksExistingUnsupportedAndSkippedCollections() throws Exception {
        Catalog catalog = new CatalogImpl();
        WorkspaceInfoImpl workspace = new WorkspaceInfoImpl();
        workspace.setName("target");
        catalog.add(workspace);
        DataStoreInfo store = new DataStoreInfoImpl(catalog);
        store.setName("roads");
        store.setWorkspace(workspace);
        catalog.add(store);
        Path catalogPath = writeCatalog(
                "demo",
                List.of(
                        collection("roads", "roads.parquet", null, "[-1,-1,1,1]"),
                        collection("image", "image.tif", null, "[0,0,1,1]"),
                        collection("unknown", "data.bin", null, "[0,0,1,1]"),
                        collectionWithoutAssets()));
        PortolanPlanner planner =
                new PortolanPlanner(catalog, format -> format == PortolanResourceFormat.COG ? "COG unavailable" : null);

        PortolanPublicationPlan plan = planner.plan(catalogPath, "target");

        assertEquals(PortolanPlanAction.EXISTS, plan.entries().get(0).action());
        assertEquals(PortolanPlanAction.UNSUPPORTED, plan.entries().get(1).action());
        assertEquals("COG unavailable", plan.entries().get(1).reason());
        assertEquals(PortolanPlanAction.SKIP, plan.entries().get(2).action());
        assertEquals("unsupported asset format", plan.entries().get(2).reason());
        assertEquals(PortolanPlanAction.SKIP, plan.entries().get(3).action());
        assertEquals("no asset", plan.entries().get(3).reason());
    }

    @Test
    public void rejectsNonFiniteBoundsAndPrefersDataRole() throws Exception {
        Path catalogPath = writeCatalog("demo", List.of(collectionWithPreviewAndInvalidBounds()));
        PortolanPlanner planner = new PortolanPlanner(new CatalogImpl(), format -> null);

        PortolanPublicationEntry entry =
                planner.plan(catalogPath, "target").entries().get(0);

        assertEquals(PortolanResourceFormat.GEOPARQUET, entry.format());
        assertEquals("data.parquet", Path.of(entry.href()).getFileName().toString());
        assertNull(entry.bbox());
    }

    private Path writeCatalog(String id, List<String> collections) throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        String links = java.util.stream.IntStream.range(0, collections.size())
                .mapToObj(index -> "{\"rel\":\"child\",\"href\":\"./c" + index + "/collection.json\"}")
                .collect(java.util.stream.Collectors.joining(","));
        String idField = id == null ? "" : "\"id\":\"" + id + "\",";
        Files.writeString(
                root.resolve("catalog.json"),
                "{\"type\":\"Catalog\",\"stac_version\":\"1.1.0\"," + idField + "\"links\":[" + links + "]}");
        for (int index = 0; index < collections.size(); index++) {
            Path directory = Files.createDirectories(root.resolve("c" + index));
            Files.writeString(directory.resolve("collection.json"), collections.get(index));
        }
        return root.resolve("catalog.json");
    }

    private static String collection(String id, String href, String mediaType, String bbox) {
        String type = mediaType == null ? "" : ",\"type\":\"" + mediaType + "\"";
        String extent = bbox == null ? "" : ",\"extent\":{\"spatial\":{\"bbox\":[" + bbox + "]}}";
        return "{\"type\":\"Collection\",\"id\":\"" + id + "\",\"links\":[],\"assets\":{" + "\"data\":{\"href\":\""
                + href + "\"" + type + ",\"roles\":[\"data\"]}}" + extent + "}";
    }

    private static String collectionWithTopLevelBbox() {
        return "{\"type\":\"Collection\",\"id\":\"fallback\",\"links\":[],"
                + "\"bbox\":[-10,-5,10,5],\"assets\":{\"data\":{\"href\":\"roads.parquet\"}}}";
    }

    private static String collectionWithoutAssets() {
        return "{\"type\":\"Collection\",\"id\":\"empty\",\"links\":[],\"assets\":{}}";
    }

    private static String collectionWithPreviewAndInvalidBounds() {
        return "{\"type\":\"Collection\",\"id\":\"mixed\",\"links\":[],"
                + "\"extent\":{\"spatial\":{\"bbox\":[[\"NaN\",0,1,2]]}},\"assets\":{"
                + "\"preview\":{\"href\":\"preview.tif\",\"roles\":[\"overview\"]},"
                + "\"data\":{\"href\":\"data.parquet\",\"roles\":[\"data\"]}}}";
    }

    private static void assertEntry(
            PortolanPublicationEntry entry, PortolanResourceFormat format, PortolanPlanAction action) {
        assertEquals(format, entry.format());
        assertEquals(action, entry.action());
    }
}
