package io.multivers.geoserver.portolan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.catalog.StyleHandler;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geotools.util.logging.Logging;

/** Imports selected Portolan MBStyle assets and assigns them as layer defaults. */
final class PortolanStyleImporter {
    static final String METADATA_MANAGED = "portolan.style.managed";
    static final String METADATA_SOURCE = "portolan.style.source";
    static final String METADATA_ASSET = "portolan.style.asset";
    static final String METADATA_CHECKSUM = "portolan.style.checksum";

    private static final Logger LOGGER = Logging.getLogger(PortolanStyleImporter.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Catalog catalog;
    private final PortolanStyleReadiness readiness;
    private final StyleContentFetcher fetcher;

    PortolanStyleImporter(Catalog catalog) {
        this(catalog, new PortolanStyleReadiness(), new PortolanStyleFetcher());
    }

    PortolanStyleImporter(Catalog catalog, PortolanStyleReadiness readiness, StyleContentFetcher fetcher) {
        this.catalog = catalog;
        this.readiness = readiness;
        this.fetcher = fetcher;
    }

    PortolanStyleResult importDefaultStyle(PortolanPublicationEntry entry, WorkspaceInfo workspace, LayerInfo layer) {
        return importDefaultStyle(entry, workspace, layer, 1);
    }

    PortolanStyleResult importDefaultStyle(
            PortolanPublicationEntry entry, WorkspaceInfo workspace, LayerInfo layer, int targetLayerCount) {
        PortolanDefaultStyle selected = entry.defaultStyle();
        if (selected.action() == PortolanStylePlanAction.SKIPPED_NO_STYLE) {
            return result(entry, layer, null, PortolanStyleResultStatus.SKIPPED_NO_STYLE, selected.reason());
        }
        if (selected.action() != PortolanStylePlanAction.IMPORT) {
            return result(entry, layer, null, PortolanStyleResultStatus.SKIPPED_UNSUPPORTED, selected.reason());
        }
        StyleHandler handler = readiness.handler();
        if (handler == null) {
            return result(
                    entry,
                    layer,
                    styleName(selected, layer),
                    PortolanStyleResultStatus.SKIPPED_UNSUPPORTED,
                    "MBStyle extension is not installed. Install gs-mbstyle.");
        }
        try {
            String content = fetcher.fetch(selected.href());
            String mappingError = validateMapStyle(content, entry, layer, targetLayerCount);
            if (mappingError != null) {
                return result(
                        entry,
                        layer,
                        styleName(selected, layer),
                        PortolanStyleResultStatus.SKIPPED_UNSUPPORTED,
                        mappingError);
            }
            String sourceError = validateStyleSource(content, selected.href(), entry);
            if (sourceError != null) {
                return result(
                        entry,
                        layer,
                        styleName(selected, layer),
                        PortolanStyleResultStatus.SKIPPED_UNSUPPORTED,
                        sourceError);
            }
            String attributeError = validateStyleAttributes(content, layer);
            if (attributeError != null) {
                return result(
                        entry,
                        layer,
                        styleName(selected, layer),
                        PortolanStyleResultStatus.SKIPPED_UNSUPPORTED,
                        attributeError);
            }
            List<Exception> errors = handler.validate(content, null, null);
            if (!errors.isEmpty()) {
                return result(
                        entry,
                        layer,
                        styleName(selected, layer),
                        PortolanStyleResultStatus.SKIPPED_UNSUPPORTED,
                        "MBStyle is not supported by the installed parser: " + safeMessage(errors.get(0)));
            }
            handler.parse(content, null, null, null);
            return persist(entry, workspace, layer, selected, styleName(selected, layer), content, handler);
        } catch (Exception exception) {
            LOGGER.log(
                    Level.WARNING,
                    "Could not import Portolan default style from " + PortolanStyleFetcher.displayUri(selected.href()),
                    exception);
            return result(
                    entry, layer, styleName(selected, layer), PortolanStyleResultStatus.FAILED, safeMessage(exception));
        }
    }

    private PortolanStyleResult persist(
            PortolanPublicationEntry entry,
            WorkspaceInfo workspace,
            LayerInfo layer,
            PortolanDefaultStyle selected,
            String styleName,
            String content,
            StyleHandler handler)
            throws IOException {
        String checksum = sha256(content);
        StyleInfo style = catalog.getStyleByName(workspace, styleName);
        if (style != null) {
            MetadataMap metadata = style.getMetadata();
            if (!Boolean.TRUE.equals(metadata.get(METADATA_MANAGED))
                    || !selected.href().toString().equals(metadata.get(METADATA_SOURCE))) {
                return result(
                        entry,
                        layer,
                        styleName,
                        PortolanStyleResultStatus.SKIPPED_UNSUPPORTED,
                        "style name belongs to a manually managed or different style");
            }
            if (checksum.equals(metadata.get(METADATA_CHECKSUM))) {
                assign(layer, style);
                return result(
                        entry, layer, styleName, PortolanStyleResultStatus.REUSED, "unchanged managed style reused");
            }
            catalog.getResourcePool()
                    .writeStyle(style, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            tag(style, selected, checksum);
            catalog.save(style);
            assign(layer, style);
            return result(entry, layer, styleName, PortolanStyleResultStatus.IMPORTED, "managed style updated");
        }

        style = catalog.getFactory().createStyle();
        style.setName(styleName);
        style.setWorkspace(workspace);
        style.setFilename(styleName + "." + handler.getFileExtension());
        style.setFormat(handler.getFormat());
        style.setFormatVersion(handler.versionForMimeType(PortolanStyleReadiness.MIME_TYPE));
        tag(style, selected, checksum);
        catalog.add(style);
        try {
            catalog.getResourcePool()
                    .writeStyle(style, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            assign(layer, style);
        } catch (IOException | RuntimeException exception) {
            catalog.remove(style);
            throw exception;
        }
        return result(entry, layer, styleName, PortolanStyleResultStatus.IMPORTED, "style imported and assigned");
    }

    private void tag(StyleInfo style, PortolanDefaultStyle selected, String checksum) {
        style.getMetadata().put(METADATA_MANAGED, Boolean.TRUE);
        style.getMetadata().put(METADATA_SOURCE, selected.href().toString());
        style.getMetadata().put(METADATA_ASSET, selected.assetKey());
        style.getMetadata().put(METADATA_CHECKSUM, checksum);
    }

    private void assign(LayerInfo layer, StyleInfo style) {
        LayerInfo managedLayer = layer.getId() == null ? null : catalog.getLayer(layer.getId());
        if (managedLayer == null) {
            managedLayer = layer;
        }
        StyleInfo previous = managedLayer.getDefaultStyle();
        managedLayer.setDefaultStyle(style);
        try {
            catalog.save(managedLayer);
        } catch (RuntimeException exception) {
            managedLayer.setDefaultStyle(previous);
            throw exception;
        }
    }

    static String validateMapStyle(
            String content, PortolanPublicationEntry entry, LayerInfo layer, int targetLayerCount) {
        JsonNode root;
        try {
            root = JSON.readTree(content);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid MBStyle JSON", exception);
        }
        if (!root.isObject() || root.path("version").asInt(-1) != 8) {
            throw new IllegalArgumentException("MBStyle must be a version 8 JSON object");
        }
        JsonNode layers = root.path("layers");
        if (!layers.isArray() || layers.isEmpty()) {
            throw new IllegalArgumentException("MBStyle must contain a nonempty layers array");
        }
        Set<String> sourceLayers = new HashSet<>();
        Set<String> sources = new HashSet<>();
        for (JsonNode styleLayer : layers) {
            if (!styleLayer.isObject()) {
                return "MBStyle layer mapping is ambiguous";
            }
            if (styleLayer.path("source-layer").isTextual()) {
                sourceLayers.add(styleLayer.path("source-layer").asText());
            }
            if (styleLayer.path("source").isTextual()) {
                sources.add(styleLayer.path("source").asText());
            }
        }
        Set<String> targetNames = new HashSet<>();
        targetNames.add(entry.collectionId());
        targetNames.add(entry.layerName());
        targetNames.add(layer.getName());
        if (layers.size() == 1 && sourceLayers.size() == 1) {
            return targetNames.contains(sourceLayers.iterator().next())
                    ? null
                    : "MBStyle source-layer does not match GeoServer layer " + layer.getName();
        }
        if (layers.size() == 1 && targetLayerCount == 1) {
            return null;
        }
        if (sourceLayers.size() == 1
                && targetNames.contains(sourceLayers.iterator().next())) {
            return null;
        }
        if (sourceLayers.isEmpty() && sources.size() == 1 && targetLayerCount == 1) {
            return null;
        }
        return "MBStyle layer mapping is ambiguous for GeoServer layer " + layer.getName();
    }

    private static String validateStyleSource(String content, URI styleUri, PortolanPublicationEntry entry)
            throws JsonProcessingException {
        JsonNode root = JSON.readTree(content);
        JsonNode sources = root.path("sources");
        if (!sources.isObject()) {
            return null;
        }
        Set<String> usedSources = new HashSet<>();
        root.path("layers").forEach(layer -> {
            if (layer.path("source").isTextual()) {
                usedSources.add(layer.path("source").asText());
            }
        });
        Set<String> sourceUris = new HashSet<>();
        for (String sourceName : usedSources) {
            JsonNode url = sources.path(sourceName).path("url");
            if (url.isTextual()) {
                sourceUris.add(normalizeSourceUri(url.asText(), styleUri));
            }
        }
        if (sourceUris.isEmpty() || sourceUris.contains(entry.href().normalize().toString())) {
            return null;
        }
        return "MBStyle source targets a different asset than " + entry.href();
    }

    private static String normalizeSourceUri(String value, URI styleUri) {
        String normalized = value;
        if (normalized.startsWith("pmtiles://")) {
            normalized = normalized.substring("pmtiles://".length());
        } else if (normalized.startsWith("cog://")) {
            normalized = normalized.substring("cog://".length());
        }
        return styleUri.resolve(URI.create(normalized)).normalize().toString();
    }

    private static String validateStyleAttributes(String content, LayerInfo layer) throws IOException {
        if (!(layer.getResource() instanceof FeatureTypeInfo featureTypeInfo)) {
            return null;
        }
        Set<String> referencedAttributes = new TreeSet<>();
        collectGetAttributes(JSON.readTree(content), referencedAttributes);
        if (referencedAttributes.isEmpty()) {
            return null;
        }
        Set<String> missingAttributes = new TreeSet<>();
        var featureType = featureTypeInfo.getFeatureType();
        for (String attribute : referencedAttributes) {
            if (featureType.getDescriptor(attribute) == null) {
                missingAttributes.add(attribute);
            }
        }
        return missingAttributes.isEmpty()
                ? null
                : "MBStyle references attributes missing from GeoServer layer "
                        + layer.getName()
                        + ": "
                        + String.join(", ", missingAttributes);
    }

    private static void collectGetAttributes(JsonNode node, Set<String> attributes) {
        if (node.isArray()
                && node.size() >= 2
                && node.get(0).isTextual()
                && "get".equals(node.get(0).asText())
                && node.get(1).isTextual()) {
            attributes.add(node.get(1).asText());
        }
        node.elements().forEachRemaining(child -> collectGetAttributes(child, attributes));
    }

    private static String styleName(PortolanDefaultStyle selected, LayerInfo layer) {
        String layerName = PortolanPlanner.geoserverName(layer.getName());
        return selected.styleName().contains("__" + layerName + "__")
                ? selected.styleName()
                : PortolanPlanner.geoserverName(selected.styleName() + "__" + layerName);
    }

    static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private PortolanStyleResult result(
            PortolanPublicationEntry entry,
            LayerInfo layer,
            String styleName,
            PortolanStyleResultStatus status,
            String reason) {
        PortolanStyleResult result =
                new PortolanStyleResult(entry.collectionId(), layer.getName(), styleName, status, reason);
        LOGGER.info(() -> "Portolan default style "
                + status
                + " for collection "
                + entry.collectionId()
                + ", layer "
                + layer.getName()
                + (reason == null ? "" : ": " + reason));
        return result;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    @FunctionalInterface
    interface StyleContentFetcher {
        String fetch(URI uri);
    }
}
