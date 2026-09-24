package org.geoserver.portolan;

import java.net.URI;

/** One desired GeoServer resource derived from a Portolan collection asset. */
public final class PortolanPublicationEntry {
    private final String collectionId;
    private final String storeName;
    private final String layerName;
    private final PortolanResourceFormat format;
    private final URI href;
    private final PortolanPlanAction action;
    private final String reason;

    public PortolanPublicationEntry(
            String collectionId,
            String storeName,
            String layerName,
            PortolanResourceFormat format,
            URI href,
            PortolanPlanAction action,
            String reason) {
        this.collectionId = collectionId;
        this.storeName = storeName;
        this.layerName = layerName;
        this.format = format;
        this.href = href;
        this.action = action;
        this.reason = reason;
    }

    public String collectionId() {
        return collectionId;
    }

    public String storeName() {
        return storeName;
    }

    public String layerName() {
        return layerName;
    }

    public PortolanResourceFormat format() {
        return format;
    }

    public URI href() {
        return href;
    }

    public PortolanPlanAction action() {
        return action;
    }

    public String reason() {
        return reason;
    }
}
