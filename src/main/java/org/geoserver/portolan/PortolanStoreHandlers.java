package org.geoserver.portolan;

import java.util.Iterator;
import org.geotools.api.data.DataStoreFactorySpi;
import org.geotools.api.data.DataStoreFinder;

final class PortolanStoreHandlers {
    static final String GEOPARQUET_TYPE = "GeoParquet";
    static final String PMTILES_TYPE = "PMTiles";
    static final String GEOTIFF_TYPE = "GeoTIFF";

    private PortolanStoreHandlers() {}

    static boolean canProvision(PortolanResourceFormat format) {
        return unsupportedReason(format) == null;
    }

    static String unsupportedReason(PortolanResourceFormat format) {
        if (format == PortolanResourceFormat.GEOPARQUET && !hasDataStoreFactory(GEOPARQUET_TYPE)) {
            return "GeoParquet store extension is not installed";
        }
        if (format == PortolanResourceFormat.PMTILES && !hasDataStoreFactory(PMTILES_TYPE)) {
            return "PMTiles store extension is not installed";
        }
        if (format == PortolanResourceFormat.COG && !isClassPresent("org.geoserver.cog.CogSettings")) {
            return "COG store extension is not installed";
        }
        if (format == PortolanResourceFormat.UNKNOWN) {
            return "unsupported asset format";
        }
        return null;
    }

    private static boolean hasDataStoreFactory(String displayName) {
        DataStoreFinder.scanForPlugins();
        for (Iterator<DataStoreFactorySpi> factories = DataStoreFinder.getAvailableDataStores();
                factories.hasNext(); ) {
            DataStoreFactorySpi factory = factories.next();
            if (displayName.equalsIgnoreCase(factory.getDisplayName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, PortolanStoreHandlers.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }
}
