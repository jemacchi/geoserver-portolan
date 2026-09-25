package org.geoserver.portolan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import org.geoserver.catalog.impl.FeatureTypeInfoImpl;
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

    private static void assertEnvelope(ReferencedEnvelope envelope) {
        assertNotNull(envelope);
        assertEquals(-74.0, envelope.getMinX(), 0.0);
        assertEquals(-55.0, envelope.getMinY(), 0.0);
        assertEquals(-53.0, envelope.getMaxX(), 0.0);
        assertEquals(-21.0, envelope.getMaxY(), 0.0);
    }
}
