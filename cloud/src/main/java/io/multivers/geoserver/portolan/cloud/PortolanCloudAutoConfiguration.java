package io.multivers.geoserver.portolan.cloud;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ImportResource;

/** Activates the Portolan Web UI extension in the GeoServer Cloud Web UI service. */
@AutoConfiguration
@ConditionalOnClass(
        name = {
            "org.geoserver.config.GeoServer",
            "org.geoserver.web.GeoServerApplication",
            "io.multivers.geoserver.portolan.web.PortolanPage"
        })
@ConditionalOnProperty(
        name = "geoserver.service.webui.enabled", havingValue = "true", matchIfMissing = false)
@ImportResource("classpath:/io/multivers/geoserver/portolan/applicationContext.xml")
public class PortolanCloudAutoConfiguration {}
