package org.geoserver.portolan.web;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.Model;
import org.geoserver.portolan.PortolanProvisionResult;
import org.geoserver.portolan.PortolanPublicationEntry;
import org.geoserver.portolan.PortolanPublicationPlan;
import org.geoserver.portolan.PortolanRegistryFacade;
import org.geoserver.web.ComponentAuthorizer;
import org.geoserver.web.GeoServerSecuredPage;
import org.portolan.PortolanRegistry;
import org.portolan.RegistryCatalogEntry;

/** Portolan registry page for the GeoServer Web UI. */
public class PortolanPage extends GeoServerSecuredPage {
    private final Model<String> registryUrl = Model.of(PortolanRegistry.DEFAULT_REGISTRY_URL);
    private final Model<String> catalogId = Model.of("");
    private final Model<String> workspace = Model.of("");
    private final Model<String> output = Model.of("Load registry catalogs to begin.");

    public PortolanPage() {
        Form<Void> form = new Form<>("form");
        add(form);

        form.add(new TextField<>("registryUrl", registryUrl));
        form.add(new TextField<>("catalogId", catalogId));
        form.add(new TextField<>("workspace", workspace));
        form.add(new TextArea<>("output", output));
        form.add(new Label("hint", "Paste a catalog id from the registry list, then plan or provision."));
        form.add(new Button("load") {
            @Override
            public void onSubmit() {
                List<RegistryCatalogEntry> entries = facade().listCatalogs(registryUrl.getObject());
                output.setObject(renderRegistry(entries));
            }
        });
        form.add(new Button("plan") {
            @Override
            public void onSubmit() {
                PortolanPublicationPlan plan = facade().planRegistryCatalog(
                                registryUrl.getObject(), catalogId.getObject(), workspace.getObject());
                output.setObject(renderPlan(plan));
            }
        });
        form.add(new Button("provision") {
            @Override
            public void onSubmit() {
                PortolanProvisionResult result = facade().provisionRegistryCatalog(
                                registryUrl.getObject(), catalogId.getObject(), workspace.getObject());
                output.setObject(renderResult(result));
            }
        });
    }

    private PortolanRegistryFacade facade() {
        return new PortolanRegistryFacade(getCatalog());
    }

    @Override
    protected ComponentAuthorizer getPageAuthorizer() {
        return ComponentAuthorizer.AUTHENTICATED;
    }

    private String renderRegistry(List<RegistryCatalogEntry> entries) {
        if (entries.isEmpty()) {
            return "No registry catalogs found.";
        }
        return entries.stream()
                .map(entry -> entry.id() + " | " + entry.status() + " | " + entry.title() + "\n" + entry.url())
                .collect(Collectors.joining("\n\n"));
    }

    private String renderPlan(PortolanPublicationPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("Plan preview\n");
        builder.append("GeoServer will create or reuse the following resources.\n\n");
        builder.append("Catalog: ").append(plan.catalogId()).append('\n');
        builder.append("Workspace: ").append(plan.workspace()).append('\n');
        builder.append("Creatable: ").append(plan.creatableCount()).append("\n\n");
        for (PortolanPublicationEntry entry : plan.entries()) {
            builder.append("Collection: ")
                    .append(entry.collectionId())
                    .append('\n')
                    .append("  Store: ")
                    .append(entry.storeName())
                    .append('\n')
                    .append("  Layer: ")
                    .append(entry.layerName())
                    .append('\n')
                    .append("  Format: ")
                    .append(entry.format())
                    .append('\n')
                    .append("  Action: ")
                    .append(entry.action())
                    .append('\n');
            if (entry.reason() != null) {
                builder.append("  Reason: ").append(entry.reason()).append('\n');
            }
            builder.append("  Asset: ")
                    .append(entry.href())
                    .append("\n\n");
        }
        return builder.toString();
    }

    private String renderResult(PortolanProvisionResult result) {
        return "Workspace: "
                + result.workspace()
                + "\nCreated: "
                + result.created()
                + "\nSkipped: "
                + result.skipped()
                + "\n\n"
                + String.join("\n", result.messages());
    }
}
