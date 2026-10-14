package io.multivers.geoserver.portolan;

import java.util.List;

/** Result of applying a Portolan publication plan to GeoServer. */
public final class PortolanProvisionResult {
    private final String workspace;
    private final int created;
    private final int skipped;
    private final List<String> messages;
    private final List<PortolanStyleResult> styleResults;

    public PortolanProvisionResult(String workspace, int created, int skipped, List<String> messages) {
        this(workspace, created, skipped, messages, List.of());
    }

    public PortolanProvisionResult(
            String workspace, int created, int skipped, List<String> messages, List<PortolanStyleResult> styleResults) {
        this.workspace = workspace;
        this.created = created;
        this.skipped = skipped;
        this.messages = List.copyOf(messages);
        this.styleResults = List.copyOf(styleResults);
    }

    public String workspace() {
        return workspace;
    }

    public int created() {
        return created;
    }

    public int skipped() {
        return skipped;
    }

    public List<String> messages() {
        return messages;
    }

    public List<PortolanStyleResult> styleResults() {
        return styleResults;
    }
}
