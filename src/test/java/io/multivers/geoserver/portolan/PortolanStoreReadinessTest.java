package io.multivers.geoserver.portolan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class PortolanStoreReadinessTest {
    @Test
    public void reportsAllRequiredStoresAsAvailable() {
        PortolanStoreReadiness readiness = new PortolanStoreReadiness(name -> true, className -> true);

        assertTrue(readiness.isComplete());
        assertEquals(3, readiness.statuses().size());
        assertTrue(readiness.statuses().stream().allMatch(PortolanStoreStatus::available));
        assertNull(readiness.unsupportedReason(PortolanResourceFormat.GEOPARQUET));
        assertNull(readiness.unsupportedReason(PortolanResourceFormat.COG));
        assertNull(readiness.unsupportedReason(PortolanResourceFormat.PMTILES));
    }

    @Test
    public void namesTheMissingVectorStoreExtensions() {
        PortolanStoreReadiness readiness = new PortolanStoreReadiness(
                name -> PortolanStoreReadiness.GEOPARQUET_TYPE.equals(name), className -> true);

        assertFalse(readiness.isComplete());
        assertEquals(
                "PMTiles store extension is not installed. Install gs-pmtiles-store.",
                readiness.unsupportedReason(PortolanResourceFormat.PMTILES));
        assertTrue(readiness.status(PortolanResourceFormat.GEOPARQUET).available());
        assertFalse(readiness.status(PortolanResourceFormat.PMTILES).available());
    }

    @Test
    public void reportsParquetryAsTheCloudGeoParquetBackend() {
        PortolanStoreReadiness readiness = new PortolanStoreReadiness(
                name -> "Parquet".equals(name), className -> true, PortolanGeoParquetStore.parquetry());

        PortolanStoreStatus status = readiness.status(PortolanResourceFormat.GEOPARQUET);

        assertTrue(status.available());
        assertEquals("Parquet (Parquetry)", status.name());
        assertEquals("GeoServer Cloud Parquetry extension", status.installation());
    }

    @Test
    public void requiresCogCoreAndHttpSupport() {
        PortolanStoreReadiness missingCore =
                new PortolanStoreReadiness(name -> true, className -> !className.endsWith("CogSettings"));
        PortolanStoreReadiness missingHttp =
                new PortolanStoreReadiness(name -> true, className -> !className.endsWith("HttpRangeReader"));

        String reason = "COG HTTP store extension is not installed. Install gs-cog-core and gs-cog-http.";
        assertEquals(reason, missingCore.unsupportedReason(PortolanResourceFormat.COG));
        assertEquals(reason, missingHttp.unsupportedReason(PortolanResourceFormat.COG));
    }

    @Test
    public void reportsUnknownFormatsWithoutAddingThemToStoreStatus() {
        PortolanStoreReadiness readiness = new PortolanStoreReadiness(name -> true, className -> true);

        assertEquals("unsupported asset format", readiness.unsupportedReason(PortolanResourceFormat.UNKNOWN));
        assertEquals(
                List.of(PortolanResourceFormat.GEOPARQUET, PortolanResourceFormat.COG, PortolanResourceFormat.PMTILES),
                readiness.statuses().stream().map(PortolanStoreStatus::format).toList());
    }

    @Test
    public void detectsClassesWithoutInitializingThem() {
        assertTrue(PortolanStoreReadiness.isClassPresent("java.lang.String"));
        assertFalse(PortolanStoreReadiness.isClassPresent("org.example.MissingPortolanHandler"));
    }

    @Test
    public void detectsTheStoreExtensionsOnTheRuntimeClasspath() {
        PortolanStoreReadiness readiness = new PortolanStoreReadiness();

        assertTrue(readiness.isComplete());
        assertTrue(readiness.status(PortolanResourceFormat.GEOPARQUET).available());
        assertTrue(readiness.status(PortolanResourceFormat.COG).available());
        assertTrue(readiness.status(PortolanResourceFormat.PMTILES).available());
        assertThrows(IllegalArgumentException.class, () -> readiness.status(PortolanResourceFormat.UNKNOWN));
    }
}
