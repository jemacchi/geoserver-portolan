package io.multivers.geoserver.portolan.cloud;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ImportResource;

class PortolanCloudAutoConfigurationTest {

    @Test
    void registersAutoConfigurationMetadata() throws IOException {
        var resource =
                getClass()
                        .getClassLoader()
                        .getResourceAsStream(
                                "META-INF/spring/"
                                        + "org.springframework.boot.autoconfigure.AutoConfiguration.imports");

        assertThat(resource).isNotNull();
        assertThat(new String(resource.readAllBytes(), StandardCharsets.UTF_8).trim())
                .isEqualTo(PortolanCloudAutoConfiguration.class.getName());
    }

    @Test
    void activatesOnlyForTheCloudWebUiWithTheCorePluginPresent() {
        var type = PortolanCloudAutoConfiguration.class;

        assertThat(type).hasAnnotation(AutoConfiguration.class);
        assertThat(type.getAnnotation(ConditionalOnClass.class).name())
                .contains(
                        "org.geoserver.config.GeoServer",
                        "org.geoserver.web.GeoServerApplication",
                        "io.multivers.geoserver.portolan.web.PortolanPage");
        assertThat(type.getAnnotation(ConditionalOnProperty.class).name())
                .containsExactly("geoserver.service.webui.enabled");
        assertThat(type.getAnnotation(ConditionalOnProperty.class).havingValue()).isEqualTo("true");
        assertThat(type.getAnnotation(ConditionalOnProperty.class).matchIfMissing()).isFalse();
        assertThat(type.getAnnotation(ImportResource.class).value())
                .containsExactly("classpath:/io/multivers/geoserver/portolan/applicationContext.xml");
    }
}
