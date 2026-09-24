package org.geoserver.portolan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.geoserver.catalog.Catalog;
import org.portolan.PortolanRegistry;
import org.portolan.RegistryCatalogEntry;

/** Registry workflow used by the GeoServer Web UI. */
public final class PortolanRegistryFacade {
    private final PortolanPlanner planner;
    private final PortolanProvisioner provisioner;

    public PortolanRegistryFacade(Catalog catalog) {
        this.planner = new PortolanPlanner(catalog);
        this.provisioner = new PortolanProvisioner(catalog);
    }

    public List<RegistryCatalogEntry> listCatalogs(String registryUrl) {
        return PortolanRegistry.loadRegistryEntries(registryUrlOrDefault(registryUrl), null, null, false, null);
    }

    public PortolanPublicationPlan planRegistryCatalog(String registryUrl, String catalogId, String workspaceName) {
        Path localCatalog = download(registryUrl, catalogId);
        return planner.plan(localCatalog, workspaceName);
    }

    public PortolanProvisionResult provisionRegistryCatalog(
            String registryUrl, String catalogId, String workspaceName) {
        PortolanPublicationPlan plan = planRegistryCatalog(registryUrl, catalogId, workspaceName);
        return provisioner.provision(plan);
    }

    private Path download(String registryUrl, String catalogId) {
        try {
            List<RegistryCatalogEntry> entries = PortolanRegistry.loadRegistryEntries(
                    registryUrlOrDefault(registryUrl), null, Set.of(catalogId), false, 1);
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("Catalog not found in registry: " + catalogId);
            }
            Path cacheDir = Files.createTempDirectory("geoserver-portolan-");
            return PortolanRegistry.downloadRegistryCatalog(entries.get(0).url(), cacheDir, null);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load Portolan catalog " + catalogId, e);
        }
    }

    private String registryUrlOrDefault(String registryUrl) {
        return registryUrl == null || registryUrl.isBlank() ? PortolanRegistry.DEFAULT_REGISTRY_URL : registryUrl;
    }
}
