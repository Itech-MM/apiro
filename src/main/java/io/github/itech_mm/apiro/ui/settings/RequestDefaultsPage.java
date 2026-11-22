package io.github.itech_mm.apiro.ui.settings;

import io.github.itech_mm.apiro.model.RequestSettings;
import javafx.beans.InvalidationListener;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class RequestDefaultsPage {

    private static final RequestSettings DEFAULTS = RequestSettings.defaults().withDefaults();

    private static final String DEFAULT_CHOICE = DEFAULTS.followRedirects() ? "Default (follow)" : "Default (do not follow)";

    private static final String FOLLOW_CHOICE = "Follow redirects";

    private static final String STOP_CHOICE = "Do not follow";

    private static final double FIELD_WIDTH = 220;

    private final ProjectDraft draft;

    private final TextField connect = new TextField();

    private final TextField socket = new TextField();

    private final ComboBox<String> redirects = new ComboBox<>();

    private final TextField maxRedirects = new TextField();

    private final TextField proxyHost = new TextField();

    private final TextField proxyPort = new TextField();

    private final Label error = new Label();

    private final VBox root;

    public RequestDefaultsPage(ProjectDraft draft) {
        this.draft = draft;
        RequestSettings current = draft.getSettings();
        connect.setText(text(current.connectTimeoutMillis()));
        socket.setText(text(current.socketTimeoutMillis()));
        maxRedirects.setText(text(current.maxRedirects()));
        proxyHost.setText(current.proxyHost() == null ? "" : current.proxyHost());
        proxyPort.setText(text(current.proxyPort()));
        connect.setPromptText(String.valueOf(DEFAULTS.connectTimeoutMillis()));
        socket.setPromptText(String.valueOf(DEFAULTS.socketTimeoutMillis()));
        maxRedirects.setPromptText(String.valueOf(DEFAULTS.maxRedirects()));
        proxyHost.setPromptText("none");
        proxyPort.setPromptText("none");
        redirects.getItems().setAll(DEFAULT_CHOICE, FOLLOW_CHOICE, STOP_CHOICE);
        redirects.getSelectionModel().select(choiceOf(current.followRedirects()));

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(Region.USE_PREF_SIZE);
        form.getColumnConstraints().add(labels);
        row(form, 0, "Connect timeout (ms)", connect);
        row(form, 1, "Socket timeout (ms)", socket);
        row(form, 2, "Follow redirects", redirects);
        row(form, 3, "Max redirects", maxRedirects);
        row(form, 4, "Proxy host", proxyHost);
        row(form, 5, "Proxy port", proxyPort);

        Label hint = new Label("Used by every request in this project unless a folder or request sets its own. "
                + "Leave a field empty to use the default shown in grey. A proxy needs both a host and a port, "
                + "and an empty host means no proxy.");
        hint.setWrapText(true);
        hint.getStyleClass().add("settings-page-hint");
        error.setWrapText(true);
        error.getStyleClass().add("settings-error");
        root = new VBox(14, hint, form, error);

        InvalidationListener refresh = observable -> refresh();
        connect.textProperty().addListener(refresh);
        socket.textProperty().addListener(refresh);
        maxRedirects.textProperty().addListener(refresh);
        proxyHost.textProperty().addListener(refresh);
        proxyPort.textProperty().addListener(refresh);
        refresh();
    }

    public SettingsPage page() {
        return new SettingsPage("Request defaults", root, this::commit, this::problem);
    }

    private void row(GridPane form, int index, String label, Node field) {
        Label text = new Label(label);
        text.getStyleClass().add("settings-field-label");
        if (field instanceof Region region) {
            region.setPrefWidth(FIELD_WIDTH);
            region.setMaxWidth(FIELD_WIDTH);
        }
        form.add(text, 0, index);
        form.add(field, 1, index);
    }

    private RequestSettingsParser.Result parse() {
        return RequestSettingsParser.parse(connect.getText(), socket.getText(), follow(), maxRedirects.getText(),
                proxyHost.getText(), proxyPort.getText());
    }

    private String problem() {
        return parse().problem();
    }

    private void commit() {
        RequestSettingsParser.Result result = parse();
        if (result.valid()) {
            draft.setSettings(result.settings());
        }
    }

    private void refresh() {
        String problem = problem();
        error.setText(problem);
        error.setVisible(!problem.isEmpty());
        error.setManaged(!problem.isEmpty());
    }

    private Boolean follow() {
        String choice = redirects.getValue();
        if (FOLLOW_CHOICE.equals(choice)) {
            return Boolean.TRUE;
        }
        if (STOP_CHOICE.equals(choice)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static String choiceOf(Boolean follow) {
        if (follow == null) {
            return DEFAULT_CHOICE;
        }
        return follow ? FOLLOW_CHOICE : STOP_CHOICE;
    }

    private static String text(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }
}