package io.multivers.geoserver.portolan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.geoserver.catalog.impl.CatalogImpl;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.portolan.PortolanRegistry;
import org.portolan.RegistryCatalogEntry;

public class PortolanRegistryFacadeTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void listsCatalogsUsingDefaultAndExplicitRegistryUrls() {
        FakeRegistry registry = new FakeRegistry();
        PortolanRegistryFacade facade =
                facade(registry, plan -> new PortolanProvisionResult("unused", 0, 0, List.of()));

        assertEquals(1, facade.listCatalogs(" ").size());
        assertEquals(PortolanRegistry.DEFAULT_REGISTRY_URL, registry.lastRegistryUrl);
        facade.listCatalogs("https://example.test/registry.json");
        assertEquals("https://example.test/registry.json", registry.lastRegistryUrl);
    }

    @Test
    public void plansAndProvisionsDownloadedCatalog() throws Exception {
        Path catalog = writeCatalog();
        FakeRegistry registry = new FakeRegistry();
        registry.catalogPath = catalog;
        AtomicReference<PortolanPublicationPlan> provisioned = new AtomicReference<>();
        PortolanRegistryFacade facade = facade(registry, plan -> {
            provisioned.set(plan);
            return new PortolanProvisionResult(plan.workspace(), 1, 0, List.of("created"));
        });

        PortolanPublicationPlan plan = facade.planRegistryCatalog(null, "demo", "target");
        PortolanProvisionResult result = facade.provisionRegistryCatalog(null, "demo", "target");

        assertEquals("target", plan.workspace());
        assertEquals(Set.of("demo"), registry.lastCatalogIds);
        assertEquals(Integer.valueOf(1), registry.lastLimit);
        assertEquals("https://example.test/demo/catalog.json", registry.lastDownloadUrl);
        assertEquals("target", result.workspace());
        assertEquals("demo", provisioned.get().catalogId());
    }

    @Test
    public void reportsMissingCatalogAndTransportFailures() {
        FakeRegistry missing = new FakeRegistry();
        missing.entries = List.of();
        IllegalStateException notFound = assertThrows(IllegalStateException.class, () -> facade(missing, plan -> null)
                .planRegistryCatalog(null, "missing", null));
        assertTrue(notFound.getCause() instanceof IllegalArgumentException);
        assertTrue(notFound.getMessage().contains("missing"));

        FakeRegistry failing = new FakeRegistry();
        failing.failure = new RuntimeException("offline");
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> facade(failing, plan -> null)
                .planRegistryCatalog(null, "demo", null));
        assertEquals("offline", failure.getCause().getMessage());
    }

    @Test
    public void defaultAdapterReadsAndDownloadsHttpRegistryCatalog() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/catalog.json", exchange -> respond(
                exchange,
                "{\"type\":\"Catalog\",\"stac_version\":\"1.1.0\",\"id\":\"local\",\"links\":[]}"));
        server.createContext("/registry.json", exchange -> respond(
                exchange,
                "{\"links\":[{\"rel\":\"child\",\"href\":\"/catalog.json\","
                        + "\"portolan_registry:id\":\"local\","
                        + "\"portolan_registry:status\":\"valid\",\"title\":\"Local\"}]}"));
        server.start();
        try {
            String registryUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/registry.json";
            PortolanRegistryFacade facade = new PortolanRegistryFacade(new CatalogImpl());

            assertEquals("local", facade.listCatalogs(registryUrl).get(0).id());
            PortolanPublicationPlan plan =
                    facade.planRegistryCatalog(registryUrl, "local", "target");
            assertEquals("local", plan.catalogId());
            assertEquals("target", plan.workspace());
        } finally {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, String content) throws IOException {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var response = exchange.getResponseBody()) {
            response.write(body);
        }
    }

    private PortolanRegistryFacade facade(
            FakeRegistry registry,
            java.util.function.Function<PortolanPublicationPlan, PortolanProvisionResult> provision) {
        return new PortolanRegistryFacade(new PortolanPlanner(new CatalogImpl(), format -> null), provision, registry);
    }

    private Path writeCatalog() throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        Files.writeString(
                root.resolve("catalog.json"),
                "{\"type\":\"Catalog\",\"stac_version\":\"1.1.0\",\"id\":\"demo\",\"links\":[]}");
        return root.resolve("catalog.json");
    }

    private static final class FakeRegistry implements PortolanRegistryFacade.RegistryAccess {
        private List<RegistryCatalogEntry> entries =
                List.of(new RegistryCatalogEntry("demo", "https://example.test/demo/catalog.json", "Demo", "valid"));
        private Path catalogPath;
        private RuntimeException failure;
        private String lastRegistryUrl;
        private Set<String> lastCatalogIds;
        private Integer lastLimit;
        private String lastDownloadUrl;

        @Override
        public List<RegistryCatalogEntry> load(String registryUrl, Set<String> catalogIds, Integer limit) {
            if (failure != null) {
                throw failure;
            }
            lastRegistryUrl = registryUrl;
            lastCatalogIds = catalogIds;
            lastLimit = limit;
            return entries;
        }

        @Override
        public Path download(String catalogUrl, Path outputDirectory) {
            lastDownloadUrl = catalogUrl;
            return catalogPath;
        }
    }
}
