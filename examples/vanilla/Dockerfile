ARG GEOSERVER_VERSION=3.0.0
FROM docker.osgeo.org/geoserver:${GEOSERVER_VERSION}

ARG PORTOLAN_VERSION

USER root

COPY geoserver-portolan-${PORTOLAN_VERSION}-full.zip /tmp/portolan.zip

RUN mkdir -p /opt/additional_libs \
    && unzip -jq /tmp/portolan.zip '*.jar' -d /opt/additional_libs \
    && chmod 0444 /opt/additional_libs/*.jar \
    && rm /tmp/portolan.zip
