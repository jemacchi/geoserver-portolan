package io.multivers.geoserver.portolan;

import java.net.URI;
import java.util.Locale;
import org.geoserver.catalog.DataStoreInfo;

/** Applies connection parameters shared by Tileverse-backed stores. */
final class PortolanStorageParameters {
    static final String S3_ANONYMOUS = "storage.s3.anonymous";

    private PortolanStorageParameters() {}

    static boolean configurePublicAwsS3(DataStoreInfo store, URI href) {
        if (!isAwsS3HttpUrl(href)) {
            return false;
        }
        Object current = store.getConnectionParameters().get(S3_ANONYMOUS);
        if (Boolean.parseBoolean(String.valueOf(current))) {
            return false;
        }
        store.getConnectionParameters().put(S3_ANONYMOUS, Boolean.TRUE);
        return true;
    }

    private static boolean isAwsS3HttpUrl(URI href) {
        String scheme = href.getScheme();
        String host = href.getHost();
        if (scheme == null || host == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!normalizedHost.endsWith(".amazonaws.com")) {
            return false;
        }
        return normalizedHost.equals("s3.amazonaws.com")
                || normalizedHost.startsWith("s3.")
                || normalizedHost.startsWith("s3-")
                || normalizedHost.contains(".s3.")
                || normalizedHost.contains(".s3-");
    }
}
