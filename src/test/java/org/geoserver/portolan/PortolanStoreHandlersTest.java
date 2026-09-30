package org.geoserver.portolan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PortolanStoreHandlersTest {
    @Test
    public void reportsUnknownFormatsAsUnsupported() {
        assertFalse(PortolanStoreHandlers.canProvision(PortolanResourceFormat.UNKNOWN));
        assertEquals(
                "unsupported asset format", PortolanStoreHandlers.unsupportedReason(PortolanResourceFormat.UNKNOWN));
    }

    @Test
    public void detectsClassesWithoutInitializingThem() {
        assertTrue(PortolanStoreHandlers.isClassPresent("java.lang.String"));
        assertFalse(PortolanStoreHandlers.isClassPresent("org.example.MissingPortolanHandler"));
    }

    @Test
    public void acceptsCogWhenCogModuleIsOnClasspath() {
        assertNull(PortolanStoreHandlers.unsupportedReason(PortolanResourceFormat.COG));
        assertTrue(PortolanStoreHandlers.canProvision(PortolanResourceFormat.COG));
    }

    @Test
    public void acceptsInstalledVectorStoreExtensions() {
        assertNull(PortolanStoreHandlers.unsupportedReason(PortolanResourceFormat.GEOPARQUET));
        assertNull(PortolanStoreHandlers.unsupportedReason(PortolanResourceFormat.PMTILES));
    }
}
