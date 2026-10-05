package io.multivers.geoserver.portolan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class PortolanGeoParquetStoreTest {
    @Test
    public void selectsParquetryOnlyForGeoServerCloudWithItsFactory() {
        PortolanGeoParquetStore parquetry =
                PortolanGeoParquetStore.select(name -> true, name -> "Parquet".equals(name));
        PortolanGeoParquetStore vanilla = PortolanGeoParquetStore.select(name -> false, name -> "Parquet".equals(name));
        PortolanGeoParquetStore fallback =
                PortolanGeoParquetStore.select(name -> true, name -> "GeoParquet".equals(name));

        assertSame(PortolanGeoParquetStore.parquetry(), parquetry);
        assertSame(PortolanGeoParquetStore.classic(), vanilla);
        assertSame(PortolanGeoParquetStore.classic(), fallback);
    }

    @Test
    public void describesBothStoreContracts() {
        assertEquals("GeoParquet", PortolanGeoParquetStore.classic().type());
        assertEquals("gs-geoparquet", PortolanGeoParquetStore.classic().installation());
        assertEquals("Parquet", PortolanGeoParquetStore.parquetry().type());
        assertEquals(
                "GeoServer Cloud Parquetry extension",
                PortolanGeoParquetStore.parquetry().installation());
    }
}
