package io.multivers.geoserver.portolan;

import java.util.List;
import java.util.function.Supplier;
import org.geoserver.catalog.StyleHandler;
import org.geoserver.catalog.Styles;

/** Reports whether the optional GeoServer MBStyle extension is available. */
public final class PortolanStyleReadiness {
    public static final String FORMAT = "mbstyle";
    public static final String MIME_TYPE = "application/vnd.geoserver.mbstyle+json";

    private final Supplier<List<StyleHandler>> handlers;

    public PortolanStyleReadiness() {
        this(Styles::handlers);
    }

    /** Creates a readiness service with a custom handler source for tests and embedding. */
    public PortolanStyleReadiness(Supplier<List<StyleHandler>> handlers) {
        this.handlers = handlers;
    }

    public boolean available() {
        return handler() != null;
    }

    public String message() {
        return available()
                ? "MBStyle: AVAILABLE"
                : "MBStyle: MISSING\n  Default style import is unavailable. Install gs-mbstyle.";
    }

    StyleHandler handler() {
        return handlers.get().stream()
                .filter(handler -> FORMAT.equalsIgnoreCase(handler.getFormat()))
                .findFirst()
                .orElse(null);
    }
}
