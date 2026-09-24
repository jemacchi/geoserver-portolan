package org.geoserver.portolan;

import java.util.List;

/** Desired GeoServer state for one Portolan catalog. */
public final class PortolanPublicationPlan {
    private final String catalogId;
    private final String catalogHref;
    private final String workspace;
    private final List<PortolanPublicationEntry> entries;

    public PortolanPublicationPlan(
            String catalogId, String catalogHref, String workspace, List<PortolanPublicationEntry> entries) {
        this.catalogId = catalogId;
        this.catalogHref = catalogHref;
        this.workspace = workspace;
        this.entries = entries;
    }

    public String catalogId() {
        return catalogId;
    }

    public String catalogHref() {
        return catalogHref;
    }

    public String workspace() {
        return workspace;
    }

    public List<PortolanPublicationEntry> entries() {
        return entries;
    }

    public long creatableCount() {
        return entries.stream()
                .filter(entry -> entry.action() == PortolanPlanAction.CREATE)
                .count();
    }
}
