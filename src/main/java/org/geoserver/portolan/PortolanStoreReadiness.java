package org.geoserver.portolan;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import org.geotools.api.data.DataStoreFactorySpi;
import org.geotools.api.data.DataStoreFinder;

/** Reports whether GeoServer can provision each Portolan asset format. */
public final class PortolanStoreReadiness {
    public static final String GEOPARQUET_TYPE = "GeoParquet";
    public static final String PMTILES_TYPE = "PMTiles";
    public static final String GEOTIFF_TYPE = "GeoTIFF";

    private static final String COG_SETTINGS_CLASS = "org.geoserver.cog.CogSettings";
    private static final String COG_HTTP_CLASS = "it.geosolutions.imageioimpl.plugins.cog.HttpRangeReader";

    private final Predicate<String> dataStoreAvailable;
    private final Predicate<String> classAvailable;

    public PortolanStoreReadiness() {
        this(PortolanStoreReadiness::hasDataStoreFactory, PortolanStoreReadiness::isClassPresent);
    }

    /** Creates a readiness service with custom detectors, primarily for embedding and tests. */
    public PortolanStoreReadiness(Predicate<String> dataStoreAvailable, Predicate<String> classAvailable) {
        this.dataStoreAvailable = dataStoreAvailable;
        this.classAvailable = classAvailable;
    }

    public List<PortolanStoreStatus> statuses() {
        return List.of(
                status(PortolanResourceFormat.GEOPARQUET),
                status(PortolanResourceFormat.COG),
                status(PortolanResourceFormat.PMTILES));
    }

    public PortolanStoreStatus status(PortolanResourceFormat format) {
        if (format == PortolanResourceFormat.GEOPARQUET) {
            return new PortolanStoreStatus(
                    format, GEOPARQUET_TYPE, "gs-geoparquet", dataStoreAvailable.test(GEOPARQUET_TYPE));
        }
        if (format == PortolanResourceFormat.COG) {
            boolean available = classAvailable.test(COG_SETTINGS_CLASS) && classAvailable.test(COG_HTTP_CLASS);
            return new PortolanStoreStatus(format, "COG HTTP", "gs-cog-core and gs-cog-http", available);
        }
        if (format == PortolanResourceFormat.PMTILES) {
            return new PortolanStoreStatus(
                    format, PMTILES_TYPE, "gs-pmtiles-store", dataStoreAvailable.test(PMTILES_TYPE));
        }
        throw new IllegalArgumentException("No store extension exists for " + format);
    }

    public boolean isComplete() {
        return statuses().stream().allMatch(PortolanStoreStatus::available);
    }

    public String unsupportedReason(PortolanResourceFormat format) {
        if (format == PortolanResourceFormat.UNKNOWN) {
            return "unsupported asset format";
        }
        PortolanStoreStatus status = status(format);
        return status.available() ? null : status.unavailableReason();
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

    static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, PortolanStoreReadiness.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }
}
