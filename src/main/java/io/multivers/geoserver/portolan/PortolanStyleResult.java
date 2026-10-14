package io.multivers.geoserver.portolan;

/** Per-layer result for optional Portolan default style import. */
public record PortolanStyleResult(
        String collectionId, String layerName, String styleName, PortolanStyleResultStatus status, String reason) {}
