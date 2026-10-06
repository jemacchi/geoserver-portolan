package io.multivers.geoserver.portolan;

import java.net.URI;
import java.util.Iterator;
import java.util.function.Predicate;
import org.geoserver.catalog.DataStoreInfo;
import org.geotools.api.data.DataStoreFactorySpi;
import org.geotools.api.data.DataStoreFinder;

/** Maps a Portolan GeoParquet asset to an available GeoServer data store. */
public final class PortolanGeoParquetStore {
    static final String CLOUD_LAUNCHER = "org.geoserver.cloud.app.GeoServerApplicationLauncher";

    private static final PortolanGeoParquetStore CLASSIC =
            new PortolanGeoParquetStore("GeoParquet", "GeoParquet", "gs-geoparquet", "uri");
    private static final PortolanGeoParquetStore PARQUETRY = new PortolanGeoParquetStore(
            "Parquet", "Parquet (Parquetry)", "GeoServer Cloud Parquetry extension", "geoparquet");

    private final String type;
    private final String displayName;
    private final String installation;
    private final String hrefParameter;

    private PortolanGeoParquetStore(String type, String displayName, String installation, String hrefParameter) {
        this.type = type;
        this.displayName = displayName;
        this.installation = installation;
        this.hrefParameter = hrefParameter;
    }

    public static PortolanGeoParquetStore classic() {
        return CLASSIC;
    }

    public static PortolanGeoParquetStore parquetry() {
        return PARQUETRY;
    }

    static PortolanGeoParquetStore detect() {
        return select(PortolanStoreReadiness::isClassPresent, PortolanGeoParquetStore::hasDataStoreFactory);
    }

    static PortolanGeoParquetStore select(Predicate<String> classAvailable, Predicate<String> dataStoreAvailable) {
        if (classAvailable.test(CLOUD_LAUNCHER) && dataStoreAvailable.test(PARQUETRY.type)) {
            return PARQUETRY;
        }
        return CLASSIC;
    }

    String type() {
        return type;
    }

    String displayName() {
        return displayName;
    }

    String installation() {
        return installation;
    }

    void configure(DataStoreInfo store, URI href, String namespace) {
        store.setType(type);
        if (this == CLASSIC) {
            store.getConnectionParameters().put("dbtype", "geoparquet");
        }
        store.getConnectionParameters().put(hrefParameter, href.toString());
        store.getConnectionParameters().put("namespace", namespace);
        if (this == PARQUETRY) {
            PortolanStorageParameters.configurePublicAwsS3(store, href);
        }
    }

    static boolean hasDataStoreFactory(String displayName) {
        DataStoreFinder.scanForPlugins();
        for (Iterator<DataStoreFactorySpi> factories = DataStoreFinder.getAvailableDataStores();
                factories.hasNext(); ) {
            if (displayName.equalsIgnoreCase(factories.next().getDisplayName())) {
                return true;
            }
        }
        return false;
    }
}
