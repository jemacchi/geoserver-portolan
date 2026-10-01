package io.multivers.geoserver.portolan;

/** Availability of one GeoServer store extension required by Portolan. */
public final class PortolanStoreStatus {
    private final PortolanResourceFormat format;
    private final String name;
    private final String installation;
    private final boolean available;

    PortolanStoreStatus(PortolanResourceFormat format, String name, String installation, boolean available) {
        this.format = format;
        this.name = name;
        this.installation = installation;
        this.available = available;
    }

    public PortolanResourceFormat format() {
        return format;
    }

    public String name() {
        return name;
    }

    public String installation() {
        return installation;
    }

    public boolean available() {
        return available;
    }

    public String unavailableReason() {
        return name + " store extension is not installed. Install " + installation + ".";
    }
}
