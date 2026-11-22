package io.github.itech_mm.apiro.ui.component;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.itech_framework.code_editor.CodeEditor;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.components.FxComponent;
import io.github.itech_framework.java_fx.annotations.components.Root;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.ResponseData;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

@FxComponent("/views/components/response-panel.fxml")
public class ResponsePanelView {

    private enum ResponseTab {
        BODY, HEADERS, SENT, COOKIES
    }

    private static final double BAR_WIDTH = 120;
    private static final List<String> STATUS_STYLES = List.of("status-ok", "status-redirect", "status-error", "status-info");

    @FXML
    @Root
    private VBox root;

    @FXML
    private HBox tabBox;

    @FXML
    private HBox statusBox;

    @FXML
    private Region statusDot;

    @FXML
    private Label statusLabel;

    @FXML
    private Label metaLabel;

    @FXML
    private Label headersTimeLabel;

    @FXML
    private Region timingHeaders;

    @FXML
    private Region timingBody;

    @FXML
    private CodeEditor viewer;

    private final ToggleGroup group = new ToggleGroup();
    private final Map<ResponseTab, ToggleButton> tabs = new EnumMap<>(ResponseTab.class);
    private ResponseData data;

    @OnInit
    private void init() {
        viewer.setEditable(false);
        viewer.setTheme("light-plus");

        for (ResponseTab tab : ResponseTab.values()) {
            ToggleButton button = new ToggleButton();
            button.getStyleClass().add("resp-tab");
            button.setToggleGroup(group);
            button.setUserData(tab);
            tabs.put(tab, button);
            tabBox.getChildren().add(button);
        }
        group.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> onSelectionChanged(oldToggle, newToggle));
        group.selectToggle(tabs.get(ResponseTab.BODY));
        clear();
    }

    public VBox getRoot() {
        return root;
    }

    public void showResponse(ResponseData response) {
        data = response;
        show(statusBox, true);
        updateStatus(response);
        updateTabLabels();
        renderContent();
    }

    public void clear() {
        data = null;
        show(statusBox, false);
        updateTabLabels();
        renderContent();
    }

    private void onSelectionChanged(Toggle oldToggle, Toggle newToggle) {
        if (newToggle == null) {
            group.selectToggle(oldToggle);
            return;
        }
        renderContent();
    }

    private ResponseTab selectedTab() {
        Toggle selected = group.getSelectedToggle();
        return selected == null ? ResponseTab.BODY : (ResponseTab) selected.getUserData();
    }

    private void updateStatus(ResponseData response) {
        String style = styleFor(response.status());
        applyStyle(statusDot, style);
        applyStyle(statusLabel, style);
        statusLabel.setText((response.status() + " " + response.statusText()).trim());
        metaLabel.setText(response.totalMillis() + " ms \u00b7 " + formatSize(response.sizeBytes()) + " \u00b7 "
                + response.attempts() + (response.attempts() == 1 ? " attempt" : " attempts"));

        long total = Math.max(1, response.totalMillis());
        double ratio = Math.min(1.0, Math.max(0.0, (double) response.headersMillis() / total));
        setWidth(timingHeaders, BAR_WIDTH * ratio);
        setWidth(timingBody, BAR_WIDTH * (1 - ratio));
        headersTimeLabel.setText("headers " + response.headersMillis() + " ms");
    }

    private void updateTabLabels() {
        tabs.get(ResponseTab.BODY).setText("Body");
        tabs.get(ResponseTab.HEADERS).setText(withCount("Headers", data == null ? 0 : data.headers().size()));
        tabs.get(ResponseTab.SENT).setText("Sent");
        tabs.get(ResponseTab.COOKIES).setText(withCount("Cookies", data == null ? 0 : data.cookies().size()));
    }

    private void renderContent() {
        if (data == null) {
            display("plaintext", "");
            return;
        }
        switch (selectedTab()) {
            case BODY -> display(looksLikeJson(data.body()) ? "json" : "plaintext", data.body());
            case HEADERS -> display("plaintext", lines(data.headers(), ": "));
            case SENT -> display("plaintext", "Not available yet");
            case COOKIES -> display("plaintext", lines(data.cookies(), "="));
        }
    }

    private void display(String language, String text) {
        viewer.setLanguage(language);
        viewer.setText(text);
    }

    private static String lines(List<KeyValue> items, String separator) {
        return items.stream()
                .map(item -> item.key() + separator + item.value())
                .collect(Collectors.joining("\n"));
    }

    private static boolean looksLikeJson(String body) {
        String trimmed = body.stripLeading();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private static String withCount(String title, int count) {
        return count > 0 ? title + " " + count : title;
    }

    private static String styleFor(int status) {
        if (status >= 200 && status < 300) {
            return "status-ok";
        }
        if (status >= 300 && status < 400) {
            return "status-redirect";
        }
        if (status >= 400) {
            return "status-error";
        }
        return "status-info";
    }

    private static void applyStyle(Node node, String style) {
        node.getStyleClass().removeAll(STATUS_STYLES);
        node.getStyleClass().add(style);
    }

    private static void setWidth(Region region, double width) {
        region.setMinWidth(width);
        region.setPrefWidth(width);
        region.setMaxWidth(width);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}