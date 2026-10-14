package io.multivers.geoserver.portolan.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.multivers.geoserver.portolan.PortolanGeoParquetStore;
import io.multivers.geoserver.portolan.PortolanPlanAction;
import io.multivers.geoserver.portolan.PortolanProvisionResult;
import io.multivers.geoserver.portolan.PortolanPublicationEntry;
import io.multivers.geoserver.portolan.PortolanPublicationPlan;
import io.multivers.geoserver.portolan.PortolanRegistryFacade;
import io.multivers.geoserver.portolan.PortolanResourceFormat;
import io.multivers.geoserver.portolan.PortolanStoreReadiness;
import io.multivers.geoserver.portolan.PortolanStyleReadiness;
import io.multivers.geoserver.portolan.PortolanStyleResult;
import io.multivers.geoserver.portolan.PortolanStyleResultStatus;
import java.net.URI;
import java.util.List;
import org.apache.wicket.util.tester.FormTester;
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
        String rendered = PortolanPage.renderResult(new PortolanProvisionResult(
                "target",
                2,
                1,
                List.of("roads: created", "tiles: skipped"),
                List.of(new PortolanStyleResult(
                        "roads",
                        "roads",
                        "demo__roads__default",
                        PortolanStyleResultStatus.IMPORTED,
                        "style imported and assigned"))));

        assertTrue(rendered.contains("Workspace: target"));
        assertTrue(rendered.contains("Created: 2"));
        assertTrue(rendered.contains("Skipped: 1"));
        assertTrue(rendered.contains("roads: created\ntiles: skipped"));
        assertTrue(rendered.contains("Default style results:"));
        assertTrue(rendered.contains("roads / roads: IMPORTED (demo__roads__default)"));
    }

    @Test
    public void rendersStoreReadinessAndInstallationInstructions() {
        PortolanStoreReadiness readiness = new PortolanStoreReadiness(
                name -> !"PMTiles".equals(name), className -> true, PortolanGeoParquetStore.classic());

        String rendered = PortolanPage.renderReadiness(readiness);

        assertTrue(rendered.contains("Portolan store readiness: INCOMPLETE"));
        assertTrue(rendered.contains("GeoParquet: AVAILABLE"));
        assertTrue(rendered.contains("PMTiles: MISSING"));
        assertTrue(rendered.contains("Install gs-pmtiles-store."));
    }

    @Test
    public void pageRequiresAuthenticationAndExecutesRegistryActions() {
        PortolanRegistryFacade facade = mock(PortolanRegistryFacade.class);
        when(facade.listCatalogs(anyString()))
                .thenReturn(List.of(
                        new RegistryCatalogEntry("demo", "https://example.test/catalog.json", "Demo", "valid")));
        when(facade.planRegistryCatalog(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new PortolanPublicationPlan(
                        "demo",
                        "https://example.test/catalog.json",
                        "target",
                        List.of(entry(
                                "roads",
                                PortolanPlanAction.CREATE,
                                null,
                                URI.create("https://example.test/roads.parquet")))));
        when(facade.provision(any(PortolanPublicationPlan.class)))
                .thenReturn(new PortolanProvisionResult("target", 1, 0, List.of("created")));
        login();
        TestPage page = new TestPage(
                facade,
                new PortolanStoreReadiness(name -> true, className -> true),
                new PortolanStyleReadiness(List::of));
        assertEquals(ComponentAuthorizer.AUTHENTICATED, page.authorizer());
        tester.startPage(page);
        tester.assertEnabled("form:plan");
        tester.assertDisabled("form:provision");
        tester.assertDisabled("form:importDefaultStyles");
        assertTrue(tester.getComponentFromLastRenderedPage("form:styleReadiness")
                .getDefaultModelObjectAsString()
                .contains("MISSING"));
        assertTrue(tester.getComponentFromLastRenderedPage("form:readiness")
                .getDefaultModelObjectAsString()
                .contains("Portolan store readiness: COMPLETE"));
        FormTester form = tester.newFormTester("form");
        form.setValue("registryUrl", "https://example.test/registry.json");
        form.setValue("catalogId", "demo");
        form.setValue("workspace", "target");
        form.submit("load");
        tester.assertModelValue("form:output", "demo | valid | Demo\nhttps://example.test/catalog.json");

        form = tester.newFormTester("form");
        form.submit("plan");
        tester.assertEnabled("form:provision");
        assertTrue(tester.getComponentFromLastRenderedPage("form:output")
                .getDefaultModelObjectAsString()
                .contains("Action: CREATE"));

        form = tester.newFormTester("form");
        form.submit("provision");
        assertTrue(tester.getComponentFromLastRenderedPage("form:output")
                .getDefaultModelObjectAsString()
                .contains("Created: 1"));
    }

    @Test
    public void disablesProvisionWhenTheSelectedCatalogNeedsAMissingStore() {
        PortolanRegistryFacade facade = mock(PortolanRegistryFacade.class);
        PortolanPublicationPlan blocked = new PortolanPublicationPlan(
                "demo",
                "https://example.test/catalog.json",
                "target",
                List.of(entry(
                        "tiles",
                        PortolanPlanAction.UNSUPPORTED,
                        "PMTiles store extension is not installed. Install gs-pmtiles-store.",
                        URI.create("https://example.test/tiles.pmtiles"))));
        when(facade.planRegistryCatalog(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(blocked);
        login();
        tester.startPage(new TestPage(
                facade,
                new PortolanStoreReadiness(name -> !"PMTiles".equals(name), className -> true),
                new PortolanStyleReadiness(List::of)));

        FormTester form = tester.newFormTester("form");
        form.setValue("registryUrl", "https://example.test/registry.json");
        form.setValue("catalogId", "demo");
        form.setValue("workspace", "target");
        form.submit("plan");

        tester.assertDisabled("form:provision");
        String output = tester.getComponentFromLastRenderedPage("form:output").getDefaultModelObjectAsString();
        assertTrue(output.contains("Provisioning: BLOCKED"));
        assertTrue(output.contains("Install gs-pmtiles-store."));
        verify(facade, never()).provision(any(PortolanPublicationPlan.class));
    }

    @Test
    public void rechecksThePlanBeforeProvisioning() {
        PortolanRegistryFacade facade = mock(PortolanRegistryFacade.class);
        PortolanPublicationPlan ready = new PortolanPublicationPlan(
                "demo",
                "https://example.test/catalog.json",
                "target",
                List.of(entry(
                        "roads", PortolanPlanAction.CREATE, null, URI.create("https://example.test/roads.parquet"))));
        PortolanPublicationPlan blocked = new PortolanPublicationPlan(
                "demo",
                "https://example.test/catalog.json",
                "target",
                List.of(entry(
                        "roads",
                        PortolanPlanAction.UNSUPPORTED,
                        "GeoParquet store extension is not installed. Install gs-geoparquet.",
                        URI.create("https://example.test/roads.parquet"))));
        when(facade.planRegistryCatalog(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(ready, blocked);
        login();
        tester.startPage(new TestPage(
                facade,
                new PortolanStoreReadiness(name -> true, className -> true),
                new PortolanStyleReadiness(List::of)));

        FormTester form = tester.newFormTester("form");
        form.setValue("registryUrl", "https://example.test/registry.json");
        form.setValue("catalogId", "demo");
        form.setValue("workspace", "target");
        form.submit("plan");
        tester.assertEnabled("form:provision");

        form = tester.newFormTester("form");
        form.submit("provision");

        tester.assertDisabled("form:provision");
        assertTrue(tester.getComponentFromLastRenderedPage("form:output")
                .getDefaultModelObjectAsString()
                .contains("Provisioning: BLOCKED"));
        verify(facade, never()).provision(any(PortolanPublicationPlan.class));
    }

    @Test
    public void enablesOptionalStyleImportWhenMbStyleIsAvailable() {
        PortolanRegistryFacade facade = mock(PortolanRegistryFacade.class);
        PortolanPublicationPlan plan = new PortolanPublicationPlan(
                "demo",
                "https://example.test/catalog.json",
                "target",
                List.of(entry(
                        "roads", PortolanPlanAction.CREATE, null, URI.create("https://example.test/roads.parquet"))),
                true);
        when(facade.planRegistryCatalog(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(plan);
        login();
        tester.startPage(new TestPage(
                facade,
                new PortolanStoreReadiness(name -> true, className -> true),
                new PortolanStyleReadiness(() -> List.of(mbStyleHandler()))));

        tester.assertEnabled("form:importDefaultStyles");
        FormTester form = tester.newFormTester("form");
        form.setValue("registryUrl", "https://example.test/registry.json");
        form.setValue("catalogId", "demo");
        form.setValue("workspace", "target");
        form.setValue("importDefaultStyles", true);
        form.submit("plan");

        verify(facade).planRegistryCatalog("https://example.test/registry.json", "demo", "target", true);
        assertTrue(tester.getComponentFromLastRenderedPage("form:styleReadiness")
                .getDefaultModelObjectAsString()
                .contains("AVAILABLE"));
    }

    private static org.geoserver.catalog.StyleHandler mbStyleHandler() {
        org.geoserver.catalog.StyleHandler handler = mock(org.geoserver.catalog.StyleHandler.class);
        when(handler.getFormat()).thenReturn("mbstyle");
        return handler;
    }

    private static PortolanPublicationEntry entry(String id, PortolanPlanAction action, String reason, URI href) {
        return new PortolanPublicationEntry(id, id, id, PortolanResourceFormat.GEOPARQUET, href, null, action, reason);
    }

    private static final class TestPage extends PortolanPage {
        private final PortolanRegistryFacade facade;

        private TestPage(
                PortolanRegistryFacade facade,
                PortolanStoreReadiness readiness,
                PortolanStyleReadiness styleReadiness) {
            super(readiness, styleReadiness);
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
