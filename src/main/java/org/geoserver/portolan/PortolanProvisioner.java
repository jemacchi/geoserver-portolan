package org.geoserver.portolan;

import java.util.ArrayList;
import java.util.List;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogBuilder;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WorkspaceInfo;

/** Applies the GeoServer catalog mutations for a Portolan publication plan. */
public final class PortolanProvisioner {
    public static final String METADATA_MANAGED = "portolan.managed";
    public static final String METADATA_CATALOG = "portolan.catalog";
    public static final String METADATA_COLLECTION = "portolan.collection";
    public static final String METADATA_ASSET = "portolan.asset";

    private final Catalog catalog;

    public PortolanProvisioner(Catalog catalog) {
        this.catalog = catalog;
    }

    public PortolanProvisionResult provision(PortolanPublicationPlan plan) {
        WorkspaceInfo workspace = ensureWorkspace(plan.workspace());
        int created = 0;
        int skipped = 0;
        List<String> messages = new ArrayList<>();
        for (PortolanPublicationEntry entry : plan.entries()) {
            if (entry.action() != PortolanPlanAction.CREATE) {
                skipped++;
                messages.add(entry.collectionId() + ": " + entry.action());
                continue;
            }
            StoreInfo store = createStore(plan, entry, workspace);
            if (store == null) {
                skipped++;
                messages.add(
                        entry.collectionId()
                                + ": no GeoServer store handler for "
                                + entry.format());
                continue;
            }
            created++;
            messages.add(
                    entry.collectionId()
                            + ": created "
                            + store.getType()
                            + " store "
                            + store.getName());
        }
        return new PortolanProvisionResult(
                plan.workspace(), created, skipped, List.copyOf(messages));
    }

    private StoreInfo createStore(
            PortolanPublicationPlan plan, PortolanPublicationEntry entry, WorkspaceInfo workspace) {
        if (entry.href() == null) {
            return null;
        }
        if (entry.format() == PortolanResourceFormat.GEOPARQUET) {
            return createGeoParquetStore(plan, entry, workspace);
        }
        if (entry.format() == PortolanResourceFormat.COG) {
            return createCogStore(plan, entry, workspace);
        }
        return null;
    }

    private DataStoreInfo createGeoParquetStore(
            PortolanPublicationPlan plan, PortolanPublicationEntry entry, WorkspaceInfo workspace) {
        CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.setWorkspace(workspace);
        DataStoreInfo store = builder.buildDataStore(entry.storeName());
        store.setType("GeoParquet");
        store.setDescription("Portolan collection " + entry.collectionId());
        store.getConnectionParameters().put("url", entry.href().toString());
        tagStore(plan, entry, store);
        catalog.add(store);
        return store;
    }

    private CoverageStoreInfo createCogStore(
            PortolanPublicationPlan plan, PortolanPublicationEntry entry, WorkspaceInfo workspace) {
        CatalogBuilder builder = new CatalogBuilder(catalog);
        builder.setWorkspace(workspace);
        CoverageStoreInfo store = builder.buildCoverageStore(entry.storeName());
        store.setType("GeoTIFF");
        store.setDescription("Portolan collection " + entry.collectionId());
        store.setURL(cogUrl(entry));
        tagStore(plan, entry, store);
        catalog.add(store);
        return store;
    }

    private String cogUrl(PortolanPublicationEntry entry) {
        String href = entry.href().toString();
        return href.startsWith("cog://") ? href : "cog://" + href;
    }

    private void tagStore(
            PortolanPublicationPlan plan, PortolanPublicationEntry entry, StoreInfo store) {
        store.getMetadata().put(METADATA_MANAGED, Boolean.TRUE);
        store.getMetadata().put(METADATA_CATALOG, plan.catalogHref());
        store.getMetadata().put(METADATA_COLLECTION, entry.collectionId());
        store.getMetadata().put(METADATA_ASSET, entry.href().toString());
    }

    private WorkspaceInfo ensureWorkspace(String workspaceName) {
        WorkspaceInfo workspace = catalog.getWorkspaceByName(workspaceName);
        if (workspace != null) {
            return workspace;
        }
        workspace = catalog.getFactory().createWorkspace();
        workspace.setName(workspaceName);
        catalog.add(workspace);

        NamespaceInfo namespace = catalog.getNamespaceByPrefix(workspaceName);
        if (namespace == null) {
            namespace = catalog.getFactory().createNamespace();
            namespace.setPrefix(workspaceName);
            namespace.setURI("https://www.portolan-sdi.org/ns/" + workspaceName);
            catalog.add(namespace);
        }
        return workspace;
    }
}
