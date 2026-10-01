package io.multivers.geoserver.portolan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.geoserver.catalog.Catalog;
import org.portolan.PortolanRegistry;
import org.portolan.RegistryCatalogEntry;

/** Registry workflow used by the GeoServer Web UI. */
public final class PortolanRegistryFacade {
    private final PortolanPlanner planner;
    private final Function<PortolanPublicationPlan, PortolanProvisionResult> provision;
    private final RegistryAccess registry;

    public PortolanRegistryFacade(Catalog catalog) {
        this(catalog, new PortolanStoreReadiness());
    }

    public PortolanRegistryFacade(Catalog catalog, PortolanStoreReadiness readiness) {
        this(
                new PortolanPlanner(catalog, readiness),
                new PortolanProvisioner(catalog)::provision,
                new PortolanJavaRegistryAccess());
    }

    PortolanRegistryFacade(
            PortolanPlanner planner,
            Function<PortolanPublicationPlan, PortolanProvisionResult> provision,
            RegistryAccess registry) {
        this.planner = planner;
        this.provision = provision;
        this.registry = registry;
    }

    public List<RegistryCatalogEntry> listCatalogs(String registryUrl) {
        return registry.load(registryUrlOrDefault(registryUrl), null, null);
    }

    public PortolanPublicationPlan planRegistryCatalog(String registryUrl, String catalogId, String workspaceName) {
        Path localCatalog = download(registryUrl, catalogId);
        return planner.plan(localCatalog, workspaceName);
    }

    public PortolanProvisionResult provisionRegistryCatalog(
            String registryUrl, String catalogId, String workspaceName) {
        PortolanPublicationPlan plan = planRegistryCatalog(registryUrl, catalogId, workspaceName);
        return provision.apply(plan);
    }

    public PortolanProvisionResult provision(PortolanPublicationPlan plan) {
        return provision.apply(plan);
    }

    private Path download(String registryUrl, String catalogId) {
        try {
            List<RegistryCatalogEntry> entries = registry.load(registryUrlOrDefault(registryUrl), Set.of(catalogId), 1);
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("Catalog not found in registry: " + catalogId);
            }
            Path cacheDir = Files.createTempDirectory("geoserver-portolan-");
            return registry.download(entries.get(0).url(), cacheDir);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load Portolan catalog " + catalogId, e);
        }
    }

    private String registryUrlOrDefault(String registryUrl) {
        return registryUrl == null || registryUrl.isBlank() ? PortolanRegistry.DEFAULT_REGISTRY_URL : registryUrl;
    }

    interface RegistryAccess {
        List<RegistryCatalogEntry> load(String registryUrl, Set<String> catalogIds, Integer limit);

        Path download(String catalogUrl, Path outputDirectory);
    }

    private static final class PortolanJavaRegistryAccess implements RegistryAccess {
        @Override
        public List<RegistryCatalogEntry> load(String registryUrl, Set<String> catalogIds, Integer limit) {
            return PortolanRegistry.loadRegistryEntries(registryUrl, null, catalogIds, false, limit);
        }

        @Override
        public Path download(String catalogUrl, Path outputDirectory) {
            return PortolanRegistry.downloadRegistryCatalog(catalogUrl, outputDirectory, null);
        }
    }
}
