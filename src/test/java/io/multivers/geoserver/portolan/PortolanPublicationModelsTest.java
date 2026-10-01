package io.multivers.geoserver.portolan;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.List;
import org.junit.Test;

public class PortolanPublicationModelsTest {
    @Test
    public void entryCopiesBoundsAndExposesValues() {
        double[] source = {-1, -2, 3, 4};
        PortolanPublicationEntry entry = new PortolanPublicationEntry(
                "roads",
                "roads-store",
                "roads-layer",
                PortolanResourceFormat.GEOPARQUET,
                URI.create("https://example.test/roads.parquet"),
                source,
                PortolanPlanAction.CREATE,
                null);
        source[0] = 99;
        double[] returned = entry.bbox();
        returned[1] = 99;

        assertEquals("roads", entry.collectionId());
        assertEquals("roads-store", entry.storeName());
        assertEquals("roads-layer", entry.layerName());
        assertEquals(PortolanResourceFormat.GEOPARQUET, entry.format());
        assertEquals(URI.create("https://example.test/roads.parquet"), entry.href());
        assertArrayEquals(new double[] {-1, -2, 3, 4}, entry.bbox(), 0.0);
        assertEquals(PortolanPlanAction.CREATE, entry.action());
        assertNull(entry.reason());
    }

    @Test
    public void planCountsOnlyCreatableEntries() {
        PortolanPublicationEntry create = entry(PortolanPlanAction.CREATE);
        PortolanPublicationEntry exists = entry(PortolanPlanAction.EXISTS);
        PortolanPublicationPlan plan = new PortolanPublicationPlan(
                "demo", "https://example.test/catalog.json", "demo", List.of(create, exists));

        assertEquals("demo", plan.catalogId());
        assertEquals("https://example.test/catalog.json", plan.catalogHref());
        assertEquals("demo", plan.workspace());
        assertEquals(List.of(create, exists), plan.entries());
        assertEquals(1, plan.creatableCount());
        assertTrue(plan.provisionable());
    }

    @Test
    public void planIsNotProvisionableWhenARequiredStoreIsUnavailable() {
        PortolanPublicationPlan plan = new PortolanPublicationPlan(
                "demo",
                "https://example.test/catalog.json",
                "target",
                List.of(entry(PortolanPlanAction.CREATE), entry(PortolanPlanAction.UNSUPPORTED)));

        assertFalse(plan.provisionable());
    }

    @Test
    public void resultExposesProvisionSummary() {
        PortolanProvisionResult result = new PortolanProvisionResult("demo", 2, 1, List.of("created"));

        assertEquals("demo", result.workspace());
        assertEquals(2, result.created());
        assertEquals(1, result.skipped());
        assertEquals(List.of("created"), result.messages());
    }

    private static PortolanPublicationEntry entry(PortolanPlanAction action) {
        return new PortolanPublicationEntry(
                "roads",
                "roads",
                "roads",
                PortolanResourceFormat.GEOPARQUET,
                URI.create("https://example.test/roads.parquet"),
                null,
                action,
                null);
    }
}
