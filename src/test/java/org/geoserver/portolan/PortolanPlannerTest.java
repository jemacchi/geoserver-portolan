package org.geoserver.portolan;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PortolanPlannerTest {
    @Test
    public void geoserverNameNormalizesCatalogIds() {
        assertEquals("roads__2024", PortolanPlanner.geoserverName("roads/2024"));
        assertEquals("roads_2024", PortolanPlanner.geoserverName("roads 2024"));
        assertEquals("portolan", PortolanPlanner.geoserverName("   "));
    }
}
