package io.github.itech_mm.apiro.ui.settings;

import io.github.itech_mm.apiro.service.manifest.ManifestDiff;
import io.github.itech_mm.apiro.service.manifest.ManifestDiff.Added;
import io.github.itech_mm.apiro.service.manifest.ManifestDiff.Match;
import io.github.itech_mm.apiro.service.manifest.ManifestDiff.Removed;
import io.github.itech_mm.apiro.service.manifest.ManifestDiff.Updated;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class ManifestReviewPage {

    private static final String BLOCKED = "Unsaved changes in an open tab. Save or close that tab, then import again.";

    private final Map<String, CheckBox> boxes = new LinkedHashMap<>();

    private final Label summary = new Label();

    private final VBox rows = new VBox(6);

    private final VBox root;

    public ManifestReviewPage() {
        summary.getStyleClass().add("settings-page-hint");
        summary.setWrapText(true);
        HBox.setHgrow(summary, Priority.ALWAYS);
        summary.setMaxWidth(Double.MAX_VALUE);
        Button all = new Button("Select all");
        all.getStyleClass().add("settings-browse-button");
        all.setOnAction(event -> select(true));
        Button none = new Button("Select none");
        none.getStyleClass().add("settings-browse-button");
        none.setOnAction(event -> select(false));
        HBox header = new HBox(8, summary, all, none);
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("settings-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root = new VBox(12, header, scroll);
        showEmpty();
    }

    public SettingsPage page() {
        return new SettingsPage("Review", root);
    }

    public Set<String> accepted() {
        Set<String> keys = new LinkedHashSet<>();
        boxes.forEach((key, box) -> {
            if (box.isSelected() && !box.isDisabled()) {
                keys.add(key);
            }
        });
        return keys;
    }

    public void show(ManifestDiff diff, Set<String> blocked) {
        boxes.clear();
        rows.getChildren().clear();
        summary.setText(diff.added().size() + " new, " + diff.updated().size() + " changed, "
                + diff.removed().size() + " no longer in code, " + diff.unchanged() + " unchanged"
                + scopeNote(diff));
        if (!diff.added().isEmpty()) {
            rows.getChildren().add(section("New requests (" + diff.added().size() + ")"));
            for (Added entry : diff.added()) {
                rows.getChildren().add(row(entry.key(), entry.folderName() + " / " + entry.request().name(), false,
                        entry.request().method().name() + " " + entry.request().url()));
            }
        }
        if (!diff.updated().isEmpty()) {
            rows.getChildren().add(section("Changed (" + diff.updated().size() + ")"));
            for (Updated entry : diff.updated()) {
                boolean stuck = blocked.contains(entry.requestId());
                rows.getChildren().add(row(entry.key(), entry.requestName() + matched(entry.match()), stuck,
                        stuck ? new String[] { BLOCKED } : entry.changes().toArray(String[]::new)));
            }
        }
        if (!diff.removed().isEmpty()) {
            rows.getChildren().add(section("No longer in code (" + diff.removed().size() + ")"));
            rows.getChildren().add(detail("These stay in your project. Delete them yourself if you do not need them."));
            for (Removed entry : diff.removed()) {
                rows.getChildren().add(detail(entry.requestName() + "   " + entry.endpointId()));
            }
        }
        if (!diff.warnings().isEmpty()) {
            rows.getChildren().add(section("Notes (" + diff.warnings().size() + ")"));
            diff.warnings().forEach(note -> rows.getChildren().add(detail(note)));
        }
        if (rows.getChildren().isEmpty()) {
            rows.getChildren().add(detail("Everything is up to date."));
        }
    }

    private static String scopeNote(ManifestDiff diff) {
        if (diff.scope() == null) {
            return "";
        }
        int folders = diff.scope().size();
        return " (compared with " + folders + (folders == 1 ? " folder" : " folders") + ")";
    }

    private void showEmpty() {
        summary.setText("");
        rows.getChildren().setAll(detail("Load a manifest on the Source page first."));
    }

    private void select(boolean on) {
        boxes.values().forEach(box -> {
            if (!box.isDisabled()) {
                box.setSelected(on);
            }
        });
    }

    private Node row(String key, String title, boolean disabled, String... details) {
        CheckBox box = new CheckBox(title);
        box.setSelected(!disabled);
        box.setDisable(disabled);
        boxes.put(key, box);
        VBox row = new VBox(2, box);
        for (String line : details) {
            row.getChildren().add(detail(line));
        }
        return row;
    }

    private static String matched(Match match) {
        return switch (match) {
            case ID -> "";
            case ROUTE -> "  (found by route)";
            case HANDLER -> "  (found by handler)";
        };
    }

    private static Label section(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("settings-review-section");
        return label;
    }

    private static Label detail(String text) {
        Label label = new Label(text);
        label.getStyleClass().addAll("settings-page-hint", "settings-review-detail");
        label.setWrapText(true);
        return label;
    }
}
