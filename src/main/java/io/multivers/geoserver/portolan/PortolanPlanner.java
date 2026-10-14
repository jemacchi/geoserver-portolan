package io.multivers.geoserver.portolan;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.logging.Logger;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.StoreInfo;
import org.geotools.util.logging.Logging;
import org.portolan.AssetFormat;
import org.portolan.PortolanAsset;
import org.portolan.PortolanCatalog;
import org.portolan.PortolanCollection;

/** Builds a GeoServer publication plan from a Portolan catalog. */
public final class PortolanPlanner {
    private static final Logger LOGGER = Logging.getLogger(PortolanPlanner.class);

    private final Catalog catalog;
    private final Function<PortolanResourceFormat, String> unsupportedReason;

    public PortolanPlanner(Catalog catalog) {
        this(catalog, new PortolanStoreReadiness()::unsupportedReason);
    }

    public PortolanPlanner(Catalog catalog, PortolanStoreReadiness readiness) {
        this(catalog, readiness::unsupportedReason);
    }

    PortolanPlanner(Catalog catalog, Function<PortolanResourceFormat, String> unsupportedReason) {
        this.catalog = catalog;
        this.unsupportedReason = unsupportedReason;
    }

    public PortolanPublicationPlan plan(Path catalogPath, String workspaceName) {
        return plan(PortolanCatalog.open(catalogPath), workspaceName, false);
    }

    public PortolanPublicationPlan plan(Path catalogPath, String workspaceName, boolean importDefaultStyles) {
        return plan(PortolanCatalog.open(catalogPath), workspaceName, importDefaultStyles);
    }

    public PortolanPublicationPlan plan(URI catalogUri, String workspaceName) {
        return plan(PortolanCatalog.open(catalogUri), workspaceName, false);
    }

    public PortolanPublicationPlan plan(URI catalogUri, String workspaceName, boolean importDefaultStyles) {
        return plan(PortolanCatalog.open(catalogUri), workspaceName, importDefaultStyles);
    }

    public PortolanPublicationPlan plan(PortolanCatalog portolanCatalog, String workspaceName) {
        return plan(portolanCatalog, workspaceName, false);
    }

    public PortolanPublicationPlan plan(
            PortolanCatalog portolanCatalog, String workspaceName, boolean importDefaultStyles) {
        String catalogId = fallback(portolanCatalog.id(), "portolan");
        String workspace = fallback(workspaceName, catalogId);
        LOGGER.info(() -> "Planning Portolan catalog " + catalogId + " for workspace " + workspace);
        List<PortolanPublicationEntry> entries = new ArrayList<>();
        for (PortolanCollection collection : portolanCatalog.collections()) {
            List<PortolanAsset> assets = publishableAssets(collection);
            if (assets.isEmpty()) {
                String reason = collection.assets().isEmpty() ? "no asset" : "unsupported asset format";
                LOGGER.info(() -> "Skipping collection " + collection.id() + ": " + reason);
                entries.add(skip(collection.id(), reason));
                continue;
            }
            for (PortolanAsset asset : assets) {
                PortolanResourceFormat format = format(asset);
                String storeName = publicationName(collection, asset, assets.size());
                String reason = unsupportedReason.apply(format);
                PortolanPlanAction action = action(workspace, storeName, reason);
                LOGGER.info(() -> "Planned collection "
                        + collection.id()
                        + " asset "
                        + asset.key()
                        + " as "
                        + format
                        + " store "
                        + storeName
                        + " with action "
                        + action
                        + (reason == null ? "" : ": " + reason));
                entries.add(new PortolanPublicationEntry(
                        collection.id(),
                        storeName,
                        storeName,
                        format,
                        asset.href(),
                        bbox(collection),
                        action,
                        reason,
                        defaultStyle(catalogId, collection, storeName, importDefaultStyles)));
            }
        }
        return new PortolanPublicationPlan(
                catalogId, portolanCatalog.href().toString(), workspace, List.copyOf(entries), importDefaultStyles);
    }

    private PortolanPublicationEntry skip(String collectionId, String reason) {
        String name = geoserverName(collectionId);
        return new PortolanPublicationEntry(
                collectionId, name, name, PortolanResourceFormat.UNKNOWN, null, null, PortolanPlanAction.SKIP, reason);
    }

    private PortolanPlanAction action(String workspace, String storeName, String unsupportedReason) {
        if (unsupportedReason != null) {
            return PortolanPlanAction.UNSUPPORTED;
        }
        StoreInfo store = catalog.getStoreByName(workspace, storeName, StoreInfo.class);
        return store == null ? PortolanPlanAction.CREATE : PortolanPlanAction.EXISTS;
    }

    private List<PortolanAsset> publishableAssets(PortolanCollection collection) {
        List<PortolanAsset> explicit = collection.assets().stream()
                .filter(asset -> format(asset) != PortolanResourceFormat.UNKNOWN)
                .filter(asset -> asset.roles().contains("data") || asset.roles().contains("visual"))
                .toList();
        if (!explicit.isEmpty()) {
            return explicit;
        }
        return collection.assets().stream()
                .filter(asset -> format(asset) != PortolanResourceFormat.UNKNOWN)
                .toList();
    }

    private String publicationName(PortolanCollection collection, PortolanAsset asset, int assetCount) {
        return assetCount == 1 ? geoserverName(collection.id()) : geoserverName(collection.id() + "__" + asset.key());
    }

    private PortolanDefaultStyle defaultStyle(
            String catalogId, PortolanCollection collection, String layerName, boolean requested) {
        if (!requested) {
            return PortolanDefaultStyle.disabled();
        }
        List<PortolanAsset> styles = collection.assets().stream()
                .filter(asset -> asset.roles().contains("style"))
                .toList();
        if (styles.isEmpty()) {
            return PortolanDefaultStyle.skipped(
                    PortolanStylePlanAction.SKIPPED_NO_STYLE, "collection has no style asset");
        }
        List<PortolanAsset> defaults = styles.stream()
                .filter(asset -> asset.roles().contains("default"))
                .toList();
        if (defaults.size() > 1) {
            return PortolanDefaultStyle.skipped(
                    PortolanStylePlanAction.SKIPPED_UNSUPPORTED, "collection declares multiple default style assets");
        }
        if (defaults.isEmpty() && styles.size() > 1) {
            return PortolanDefaultStyle.skipped(
                    PortolanStylePlanAction.SKIPPED_UNSUPPORTED,
                    "collection declares multiple style assets without one default");
        }
        PortolanAsset selected = defaults.isEmpty() ? styles.get(0) : defaults.get(0);
        if (!isMbStyleMediaType(selected.mediaType())) {
            return PortolanDefaultStyle.skipped(
                    PortolanStylePlanAction.SKIPPED_UNSUPPORTED, "default style is not declared as MBStyle or JSON");
        }
        String styleName = geoserverName(catalogId + "__" + collection.id() + "__" + layerName + "__" + selected.key());
        return new PortolanDefaultStyle(
                PortolanStylePlanAction.IMPORT,
                selected.key(),
                selected.href(),
                selected.mediaType(),
                selected.roles(),
                styleName,
                null);
    }

    static boolean isMbStyleMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return true;
        }
        String normalized = mediaType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return normalized.equals("application/json")
                || normalized.equals("application/vnd.mapbox.style+json")
                || normalized.equals("application/vnd.geoserver.mbstyle+json");
    }

    private PortolanResourceFormat format(PortolanAsset asset) {
        if (asset.format() == AssetFormat.GEOPARQUET) {
            return PortolanResourceFormat.GEOPARQUET;
        }
        if (asset.format() == AssetFormat.COG) {
            return PortolanResourceFormat.COG;
        }
        if (asset.format() == AssetFormat.PMTILES) {
            return PortolanResourceFormat.PMTILES;
        }
        return PortolanResourceFormat.UNKNOWN;
    }

    private double[] bbox(PortolanCollection collection) {
        JsonNode bbox = collection.data().at("/extent/spatial/bbox/0");
        if (!bbox.isArray() || bbox.size() < 4) {
            bbox = collection.data().path("bbox");
        }
        if (!bbox.isArray() || bbox.size() < 4) {
            return null;
        }
        double minx = bbox.get(0).asDouble(Double.NaN);
        double miny = bbox.get(1).asDouble(Double.NaN);
        double maxx = bbox.get(2).asDouble(Double.NaN);
        double maxy = bbox.get(3).asDouble(Double.NaN);
        if (!Double.isFinite(minx) || !Double.isFinite(miny) || !Double.isFinite(maxx) || !Double.isFinite(maxy)) {
            return null;
        }
        return new double[] {minx, miny, maxx, maxy};
    }

    public static String geoserverName(String value) {
        String name = value == null ? "" : value.replace("/", "__").replaceAll("[^A-Za-z0-9_.-]+", "_");
        name = name.replaceAll("^[._-]+|[._-]+$", "");
        return name.isBlank() ? "portolan" : name;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
