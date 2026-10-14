package io.multivers.geoserver.portolan;

import java.net.URI;
import java.util.List;

/** Default style selected from a Portolan collection for one publication entry. */
public record PortolanDefaultStyle(
        PortolanStylePlanAction action,
        String assetKey,
        URI href,
        String mediaType,
        List<String> roles,
        String styleName,
        String reason) {

    public PortolanDefaultStyle {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public static PortolanDefaultStyle disabled() {
        return new PortolanDefaultStyle(
                PortolanStylePlanAction.DISABLED, null, null, null, List.of(), null, "style import not requested");
    }

    public static PortolanDefaultStyle skipped(PortolanStylePlanAction action, String reason) {
        return new PortolanDefaultStyle(action, null, null, null, List.of(), null, reason);
    }
}
