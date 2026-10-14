package io.multivers.geoserver.portolan;

/** Outcome of importing and assigning a default style to one GeoServer layer. */
public enum PortolanStyleResultStatus {
    IMPORTED,
    REUSED,
    SKIPPED_NO_STYLE,
    SKIPPED_UNSUPPORTED,
    FAILED
}
