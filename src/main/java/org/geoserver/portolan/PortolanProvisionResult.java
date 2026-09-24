package org.geoserver.portolan;

import java.util.List;

/** Result of applying a Portolan publication plan to GeoServer. */
public final class PortolanProvisionResult {
    private final String workspace;
    private final int created;
    private final int skipped;
    private final List<String> messages;

    public PortolanProvisionResult(
            String workspace, int created, int skipped, List<String> messages) {
        this.workspace = workspace;
        this.created = created;
        this.skipped = skipped;
        this.messages = messages;
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
}
