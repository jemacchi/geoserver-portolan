package io.multivers.geoserver.portolan;

import java.net.URI;

/** One desired GeoServer resource derived from a Portolan collection asset. */
public final class PortolanPublicationEntry {
    private final String collectionId;
    private final String storeName;
    private final String layerName;
    private final PortolanResourceFormat format;
    private final URI href;
    private final double[] bbox;
    private final PortolanPlanAction action;
    private final String reason;
    private final PortolanDefaultStyle defaultStyle;

    public PortolanPublicationEntry(
            String collectionId,
            String storeName,
            String layerName,
            PortolanResourceFormat format,
            URI href,
            double[] bbox,
            PortolanPlanAction action,
            String reason) {
        this(collectionId, storeName, layerName, format, href, bbox, action, reason, PortolanDefaultStyle.disabled());
    }

    public PortolanPublicationEntry(
            String collectionId,
            String storeName,
            String layerName,
            PortolanResourceFormat format,
            URI href,
            double[] bbox,
            PortolanPlanAction action,
            String reason,
            PortolanDefaultStyle defaultStyle) {
        this.collectionId = collectionId;
        this.storeName = storeName;
        this.layerName = layerName;
        this.format = format;
        this.href = href;
        this.bbox = bbox == null ? null : bbox.clone();
        this.action = action;
        this.reason = reason;
        this.defaultStyle = defaultStyle == null ? PortolanDefaultStyle.disabled() : defaultStyle;
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

    public double[] bbox() {
        return bbox == null ? null : bbox.clone();
    }

    public PortolanPlanAction action() {
        return action;
    }

    public String reason() {
        return reason;
    }

    public PortolanDefaultStyle defaultStyle() {
        return defaultStyle;
    }
}
