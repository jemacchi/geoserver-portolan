package org.geoserver.portolan.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import org.apache.wicket.util.tester.FormTester;
import org.geoserver.portolan.PortolanPlanAction;
import org.geoserver.portolan.PortolanProvisionResult;
import org.geoserver.portolan.PortolanPublicationEntry;
import org.geoserver.portolan.PortolanPublicationPlan;
import org.geoserver.portolan.PortolanRegistryFacade;
import org.geoserver.portolan.PortolanResourceFormat;
import org.geoserver.web.ComponentAuthorizer;
import org.geoserver.web.GeoServerWicketTestSupport;
import org.junit.Test;
import org.portolan.RegistryCatalogEntry;

public class PortolanPageTest extends GeoServerWicketTestSupport {
    @Test
    public void rendersEmptyAndPopulatedRegistry() {
        assertEquals("No registry catalogs found.", PortolanPage.renderRegistry(List.of()));

        String rendered = PortolanPage.renderRegistry(List.of(
                new RegistryCatalogEntry("demo", "https://example.test/catalog.json", "Demo catalog", "valid")));

        assertTrue(rendered.contains("demo | valid | Demo catalog"));
        assertTrue(rendered.contains("https://example.test/catalog.json"));
    }

    @Test
    public void rendersPlanActionsAssetsAndReasons() {
        PortolanPublicationPlan plan = new PortolanPublicationPlan(
                "demo",
                "https://example.test/catalog.json",
                "target",
                List.of(
                        entry(
                                "roads",
                                PortolanPlanAction.CREATE,
                                null,
                                URI.create("https://example.test/roads.parquet")),
                        entry("tiles", PortolanPlanAction.UNSUPPORTED, "PMTiles unavailable", null)));

        String rendered = PortolanPage.renderPlan(plan);

        assertTrue(rendered.contains("Catalog: demo"));
        assertTrue(rendered.contains("Workspace: target"));
        assertTrue(rendered.contains("Creatable: 1"));
        assertTrue(rendered.contains("Action: CREATE"));
        assertTrue(rendered.contains("Reason: PMTiles unavailable"));
        assertTrue(rendered.contains("Asset: null"));
    }

    @Test
    public void rendersProvisionResult() {
        String rendered = PortolanPage.renderResult(
                new PortolanProvisionResult("target", 2, 1, List.of("roads: created", "tiles: skipped")));

        assertTrue(rendered.contains("Workspace: target"));
        assertTrue(rendered.contains("Created: 2"));
        assertTrue(rendered.contains("Skipped: 1"));
        assertTrue(rendered.contains("roads: created\ntiles: skipped"));
    }

    @Test
    public void pageRequiresAuthenticationAndExecutesRegistryActions() {
        PortolanRegistryFacade facade = mock(PortolanRegistryFacade.class);
        when(facade.listCatalogs(anyString()))
                .thenReturn(List.of(
                        new RegistryCatalogEntry("demo", "https://example.test/catalog.json", "Demo", "valid")));
        when(facade.planRegistryCatalog(anyString(), anyString(), anyString()))
                .thenReturn(new PortolanPublicationPlan(
                        "demo",
                        "https://example.test/catalog.json",
                        "target",
                        List.of(entry(
                                "roads",
                                PortolanPlanAction.CREATE,
                                null,
                                URI.create("https://example.test/roads.parquet")))));
        when(facade.provisionRegistryCatalog(anyString(), anyString(), anyString()))
                .thenReturn(new PortolanProvisionResult("target", 1, 0, List.of("created")));
        login();
        TestPage page = new TestPage(facade);
        assertEquals(ComponentAuthorizer.AUTHENTICATED, page.authorizer());
        tester.startPage(page);
        FormTester form = tester.newFormTester("form");
        form.setValue("registryUrl", "https://example.test/registry.json");
        form.setValue("catalogId", "demo");
        form.setValue("workspace", "target");
        form.submit("load");
        tester.assertModelValue("form:output", "demo | valid | Demo\nhttps://example.test/catalog.json");

        form = tester.newFormTester("form");
        form.submit("plan");
        assertTrue(tester.getComponentFromLastRenderedPage("form:output")
                .getDefaultModelObjectAsString()
                .contains("Action: CREATE"));

        form = tester.newFormTester("form");
        form.submit("provision");
        assertTrue(tester.getComponentFromLastRenderedPage("form:output")
                .getDefaultModelObjectAsString()
                .contains("Created: 1"));
    }

    private static PortolanPublicationEntry entry(String id, PortolanPlanAction action, String reason, URI href) {
        return new PortolanPublicationEntry(id, id, id, PortolanResourceFormat.GEOPARQUET, href, null, action, reason);
    }

    private static final class TestPage extends PortolanPage {
        private final PortolanRegistryFacade facade;

        private TestPage(PortolanRegistryFacade facade) {
            this.facade = facade;
        }

        @Override
        protected PortolanRegistryFacade facade() {
            return facade;
        }

        private ComponentAuthorizer authorizer() {
            return getPageAuthorizer();
        }
    }
}
