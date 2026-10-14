package io.multivers.geoserver.portolan;

import java.util.List;

/** Desired GeoServer state for one Portolan catalog. */
public final class PortolanPublicationPlan {
    private final String catalogId;
    private final String catalogHref;
    private final String workspace;
    private final List<PortolanPublicationEntry> entries;
    private final boolean importDefaultStyles;

    public PortolanPublicationPlan(
            String catalogId, String catalogHref, String workspace, List<PortolanPublicationEntry> entries) {
        this(catalogId, catalogHref, workspace, entries, false);
    }

    public PortolanPublicationPlan(
            String catalogId,
            String catalogHref,
            String workspace,
            List<PortolanPublicationEntry> entries,
            boolean importDefaultStyles) {
        this.catalogId = catalogId;
        this.catalogHref = catalogHref;
        this.workspace = workspace;
        this.entries = List.copyOf(entries);
        this.importDefaultStyles = importDefaultStyles;
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

    public boolean importDefaultStyles() {
        return importDefaultStyles;
    }

    public long creatableCount() {
        return entries.stream()
                .filter(entry -> entry.action() == PortolanPlanAction.CREATE)
                .count();
    }

    public boolean provisionable() {
        return entries.stream().noneMatch(entry -> entry.action() == PortolanPlanAction.UNSUPPORTED);
    }
}
