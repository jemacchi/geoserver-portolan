package org.geoserver.portolan;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    public PortolanPlanner(Catalog catalog) {
        this.catalog = catalog;
    }

    public PortolanPublicationPlan plan(Path catalogPath, String workspaceName) {
        return plan(PortolanCatalog.open(catalogPath), workspaceName);
    }

    public PortolanPublicationPlan plan(URI catalogUri, String workspaceName) {
        return plan(PortolanCatalog.open(catalogUri), workspaceName);
    }

    public PortolanPublicationPlan plan(PortolanCatalog portolanCatalog, String workspaceName) {
        String catalogId = fallback(portolanCatalog.id(), "portolan");
        String workspace = fallback(workspaceName, catalogId);
        LOGGER.info(() -> "Planning Portolan catalog " + catalogId + " for workspace " + workspace);
        List<PortolanPublicationEntry> entries = new ArrayList<>();
        for (PortolanCollection collection : portolanCatalog.collections()) {
            PortolanAsset asset = primaryAsset(collection);
            if (asset == null) {
                LOGGER.info(() -> "Skipping collection " + collection.id() + ": no asset");
                entries.add(skip(collection.id(), "no asset"));
                continue;
            }
            PortolanResourceFormat format = format(asset);
            if (format == PortolanResourceFormat.UNKNOWN) {
                LOGGER.info(() -> "Skipping collection " + collection.id() + ": unsupported asset format");
                entries.add(skip(collection.id(), "unsupported asset format"));
                continue;
            }
            String storeName = geoserverName(collection.id());
            PortolanPlanAction action = action(workspace, storeName, format);
            String reason =
                    action == PortolanPlanAction.UNSUPPORTED ? PortolanStoreHandlers.unsupportedReason(format) : null;
            LOGGER.info(() -> "Planned collection "
                    + collection.id()
                    + " as "
                    + format
                    + " store "
                    + storeName
                    + " with action "
                    + action
                    + (reason == null ? "" : ": " + reason));
            entries.add(new PortolanPublicationEntry(
                    collection.id(), storeName, storeName, format, asset.href(), action, reason));
        }
        return new PortolanPublicationPlan(
                catalogId, portolanCatalog.href().toString(), workspace, List.copyOf(entries));
    }

    private PortolanPublicationEntry skip(String collectionId, String reason) {
        String name = geoserverName(collectionId);
        return new PortolanPublicationEntry(
                collectionId, name, name, PortolanResourceFormat.UNKNOWN, null, PortolanPlanAction.SKIP, reason);
    }

    private PortolanPlanAction action(String workspace, String storeName, PortolanResourceFormat format) {
        if (!PortolanStoreHandlers.canProvision(format)) {
            return PortolanPlanAction.UNSUPPORTED;
        }
        StoreInfo store = catalog.getStoreByName(workspace, storeName, StoreInfo.class);
        return store == null ? PortolanPlanAction.CREATE : PortolanPlanAction.EXISTS;
    }

    private PortolanAsset primaryAsset(PortolanCollection collection) {
        List<PortolanAsset> assets = collection.assets();
        for (PortolanAsset asset : assets) {
            if (asset.roles().contains("data")) {
                return asset;
            }
        }
        return assets.isEmpty() ? null : assets.get(0);
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

    public static String geoserverName(String value) {
        String name = value == null ? "" : value.replace("/", "__").replaceAll("[^A-Za-z0-9_.-]+", "_");
        name = name.replaceAll("^[._-]+|[._-]+$", "");
        return name.isBlank() ? "portolan" : name;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
