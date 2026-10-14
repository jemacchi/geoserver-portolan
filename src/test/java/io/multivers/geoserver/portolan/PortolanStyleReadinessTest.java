package io.multivers.geoserver.portolan;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.geoserver.catalog.StyleHandler;
import org.junit.Test;

public class PortolanStyleReadinessTest {
    @Test
    public void reportsOptionalMbStyleAvailability() {
        StyleHandler mbstyle = org.mockito.Mockito.mock(StyleHandler.class);
        org.mockito.Mockito.when(mbstyle.getFormat()).thenReturn("mbstyle");

        PortolanStyleReadiness available = new PortolanStyleReadiness(() -> List.of(mbstyle));
        PortolanStyleReadiness missing = new PortolanStyleReadiness(List::of);

        assertTrue(available.available());
        assertFalse(missing.available());
        assertTrue(missing.message().contains("Install gs-mbstyle"));
    }
}
