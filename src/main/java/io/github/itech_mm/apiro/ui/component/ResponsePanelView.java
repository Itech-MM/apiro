package io.github.itech_mm.apiro.ui.component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.itech_framework.api_client.interceptor.HeaderRedactor;
import io.github.itech_framework.api_client.utils.response.RedirectHop;
import io.github.itech_framework.api_client.utils.response.SentRequest;
import io.github.itech_framework.code_editor.CodeEditor;
import io.github.itech_framework.core.annotations.methods.OnInit;
import io.github.itech_framework.java_fx.annotations.components.FxComponent;
import io.github.itech_framework.java_fx.annotations.components.Root;
import io.github.itech_framework.java_fx.ui.dialog.AlertDialog;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.ResponseData;
import io.github.itech_mm.apiro.service.http.ExchangeResult;
import io.github.itech_mm.apiro.service.http.FailureKind;
import io.github.itech_mm.apiro.ui.EditorStyle;
import io.github.itech_mm.apiro.ui.JsonPretty;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.fxmisc.richtext.CodeArea;

@FxComponent("/views/components/response-panel.fxml")
public class ResponsePanelView {

    private enum ResponseTab {
        BODY, HEADERS, SENT, COOKIES, TIMELINE
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
    private HBox timingBar;

    @FXML
    private Region timingHeaders;

    @FXML
    private Region timingBody;

    @FXML
    private CodeEditor viewer;
    
    @FXML
    private ProgressIndicator sendingSpinner;
    
    private static final int PREVIEW_LIMIT = 1_000_000;

    @FXML
    private Hyperlink fullBodyButton;
    
    @FXML
    private ToggleButton prettyToggle;

    @FXML
    private ToggleButton wrapToggle;

    @FXML
    private Button copyButton;

    @FXML
    private Button saveButton;

    @FXML
    private Button clearButton;

    private boolean fullBody;

    private boolean sending;

    private static final double FULL_WIDTH = 1060;
    private static final double MEDIUM_WIDTH = 820;

    private final Tooltip statusTip = new Tooltip();

    private Timeline ticker;

    private final ToggleGroup group = new ToggleGroup();
    private final Map<ResponseTab, ToggleButton> tabs = new EnumMap<>(ResponseTab.class);
    private ResponseData data;
    private ExchangeResult exchange;
    private List<String> unresolved = List.of();
    private String notice = "";

    @OnInit
    private void init() {
        viewer.setEditable(false);
        EditorStyle.apply(viewer);

        for (ResponseTab tab : ResponseTab.values()) {
            ToggleButton button = new ToggleButton();
            button.getStyleClass().add("resp-tab");
            button.setMinWidth(Region.USE_PREF_SIZE);
            button.setToggleGroup(group);
            button.setUserData(tab);
            tabs.put(tab, button);
            tabBox.getChildren().add(button);
        }
        group.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> onSelectionChanged(oldToggle, newToggle));
        group.selectToggle(tabs.get(ResponseTab.BODY));
        prettyToggle.selectedProperty().addListener((observable, oldValue, newValue) -> renderContent());
        wrapToggle.selectedProperty().addListener((observable, oldValue, newValue) -> applyWrap());
        viewer.skinProperty().addListener((observable, oldValue, newValue) -> Platform.runLater(this::applyWrap));
        root.widthProperty().addListener((observable, oldValue, newValue) -> applyCompact(newValue.doubleValue()));
        wrapToggle.setTooltip(new Tooltip("Soft-wrap long lines"));
        copyButton.setTooltip(new Tooltip("Copy response"));
        saveButton.setTooltip(new Tooltip("Save response to a file"));
        clearButton.setTooltip(new Tooltip("Clear response"));
        clear();
    }

    public VBox getRoot() {
        return root;
    }

    public void showResult(ExchangeResult result, List<String> undefined) {
    	fullBody = false;
        setSending(false);
        exchange = result;
        data = result.response();
        unresolved = undefined == null ? List.of() : List.copyOf(undefined);
        notice = "";
        show(statusBox, true);
        updateStatus(data);
        updateTabLabels();
        renderContent();
    }

    public void showFailure(FailureKind kind, String message) {
        showNotice("Failed", label(kind), message == null ? "" : message, "status-error");
    }

    public void showCancelled() {
        showNotice("Cancelled", "", "The request was cancelled.", "status-info");
    }

    public void clear() {
        setSending(false);
        data = null;
        exchange = null;
        unresolved = List.of();
        notice = "";
        show(statusBox, false);
        setStatusTip(null);
        updateTabLabels();
        renderContent();
    }
    
    public void showSending(long startedNanos) {
        data = null;
        exchange = null;
        unresolved = List.of();
        notice = "Waiting for the server to respond...";
        setSending(true);
        show(statusBox, true);
        applyStyle(statusLabel, "status-info");
        statusLabel.setText("Sending request...");
        setWidth(timingHeaders, 0);
        setWidth(timingBody, 0);
        headersTimeLabel.setText("");
        updateElapsed(startedNanos);
        ticker = new Timeline(new KeyFrame(Duration.millis(100), event -> updateElapsed(startedNanos)));
        ticker.setCycleCount(Timeline.INDEFINITE);
        ticker.play();
        updateTabLabels();
        renderContent();
    }

    private void updateElapsed(long startedNanos) {
        double seconds = (System.nanoTime() - startedNanos) / 1_000_000_000.0;
        metaLabel.setText(String.format(Locale.ROOT, "%.1f s", seconds));
    }

    private void setStatusTip(String text) {
        Tooltip.uninstall(statusBox, statusTip);
        if (text != null && !text.isBlank()) {
            statusTip.setText(text);
            Tooltip.install(statusBox, statusTip);
        }
    }

    private void applyCompact(double width) {
        boolean full = width >= FULL_WIDTH;
        boolean medium = width >= MEDIUM_WIDTH;
        show(timingBar, full);
        show(headersTimeLabel, full);
        show(metaLabel, medium);
    }

    private void setSending(boolean on) {
        sending = on;
        if (on) {
            setStatusTip(null);
        }
        if (ticker != null) {
            ticker.stop();
            ticker = null;
        }
        show(sendingSpinner, on);
        show(statusDot, !on);
    }

    private void showNotice(String title, String meta, String message, String style) {
        setSending(false);
        data = null;
        exchange = null;
        unresolved = List.of();
        notice = message;
        show(statusBox, true);
        applyStyle(statusDot, style);
        applyStyle(statusLabel, style);
        statusLabel.setText(title);
        metaLabel.setText(meta);
        setStatusTip(null);
        setWidth(timingHeaders, 0);
        setWidth(timingBody, 0);
        headersTimeLabel.setText("");
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
        String meta = response.totalMillis() + " ms \u00b7 " + formatSize(response.sizeBytes());
        if (response.attempts() > 1) {
            meta += " \u00b7 " + response.attempts() + " attempts";
        }
        metaLabel.setText(meta);
        setStatusTip(statusLabel.getText() + "\n" + meta + "\nHeaders received in " + response.headersMillis()
                + " ms, "
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
        tabs.get(ResponseTab.TIMELINE).setText("Timeline");
    }

    private void renderContent() {
        boolean truncated = false;
        if (data == null) {
            display("plaintext", selectedTab() == ResponseTab.BODY ? notice : "");
        } else {
            switch (selectedTab()) {
                case BODY -> truncated = displayBody(data.body());
                case HEADERS -> display("plaintext", lines(data.headers(), ": "));
                case SENT -> display("plaintext", sentText());
                case COOKIES -> display("plaintext", lines(data.cookies(), "="));
                case TIMELINE -> display("plaintext", timelineText());
            }
        }
        show(fullBodyButton, truncated);
        updateActions();
    }

    private void updateActions() {
        boolean content = !sending && (data != null || !notice.isEmpty());
        copyButton.setDisable(!content);
        saveButton.setDisable(!content);
        clearButton.setDisable(!content);
    }

    private void applyWrap() {
        boolean on = wrapToggle.isSelected();
        for (Node node : viewer.lookupAll(".code-area")) {
            if (node instanceof CodeArea area) {
                area.setWrapText(on);
            }
        }
    }

    private boolean previewing() {
        return data != null && selectedTab() == ResponseTab.BODY && !fullBody && data.body().length() > PREVIEW_LIMIT;
    }

    private String currentText() {
        return previewing() ? data.body() : viewer.getText();
    }

    private String suggestedName() {
        if (data == null) {
            return "response.txt";
        }
        return switch (selectedTab()) {
            case BODY -> looksLikeJson(data.body()) ? "response.json" : "response.txt";
            case HEADERS -> "response-headers.txt";
            case SENT -> "request-sent.txt";
            case COOKIES -> "response-cookies.txt";
            case TIMELINE -> "response-timeline.txt";
        };
    }

    private Stage owner() {
        return root.getScene() != null && root.getScene().getWindow() instanceof Stage stage ? stage : null;
    }

    private void flash(Button anchor, String message) {
        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        if (bounds == null) {
            return;
        }
        Tooltip tip = new Tooltip(message);
        tip.show(anchor, bounds.getMaxX() + 6, bounds.getMinY());
        PauseTransition pause = new PauseTransition(Duration.millis(900));
        pause.setOnFinished(event -> tip.hide());
        pause.play();
    }

    private boolean displayBody(String body) {
        if (fullBody || body.length() <= PREVIEW_LIMIT) {
            boolean json = looksLikeJson(body);
            String text = body;
            if (json && prettyToggle.isSelected()) {
                text = JsonPretty.format(body).orElse(body);
            }
            display(json ? "json" : "plaintext", text);
            return false;
        }
        int end = PREVIEW_LIMIT;
        if (Character.isHighSurrogate(body.charAt(end - 1))) {
            end--;
        }
        display("plaintext", body.substring(0, end) + String.format(Locale.ROOT,
                "%n%n... Preview limited to the first %,d of %,d characters. Choose \"Show full body\" to load all of it.",
                end, body.length()));
        return true;
    }

    private String sentText() {
        StringBuilder text = new StringBuilder();
        if (!unresolved.isEmpty()) {
            text.append("Undefined variables: ")
            .append(unresolved.stream().map(name -> "${" + name + "}").collect(Collectors.joining(", ")))
                    .append("\n\n");
        }
        SentRequest sent = exchange == null ? null : exchange.sent();
        if (sent == null) {
            return text.append("Not available").toString();
        }
        text.append(sent.method()).append(' ').append(sent.uri()).append('\n');
        HeaderRedactor.defaults().redact(sent.headers())
                .forEach((name, values) -> values.forEach(value -> text.append(name).append(": ").append(value).append('\n')));
        return text.toString().stripTrailing();
    }

    private String timelineText() {
        List<RedirectHop> hops = exchange == null ? List.of() : exchange.redirects();
        StringBuilder text = new StringBuilder();
        text.append("Headers received  ").append(data.headersMillis()).append(" ms\n");
        text.append("Total             ").append(data.totalMillis()).append(" ms\n");
        text.append("Attempts          ").append(data.attempts()).append('\n');
        text.append("Redirects         ").append(hops.size());
        for (RedirectHop hop : hops) {
            text.append("\n  ").append(hop.status()).append(' ').append(hop.from()).append(" -> ").append(hop.location());
        }
        if (!hops.isEmpty()) {
            text.append("\n\nHeaders other than Accept, Accept-Charset, Accept-Language, Content-Type and User-Agent")
                    .append(" are dropped when a redirect leaves the origin.");
        }
        return text.toString();
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

    private static String label(FailureKind kind) {
        return switch (kind) {
            case INVALID_REQUEST -> "Invalid request";
            case FILE_MISSING -> "File missing";
            case TIMEOUT -> "Timed out";
            case UNKNOWN_HOST -> "Unknown host";
            case CONNECTION -> "Connection failed";
            case TLS -> "TLS error";
            case BODY_TOO_LARGE -> "Response too large";
            case OTHER -> "Error";
        };
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
    
    @FXML
    private void onShowFullBody() {
        fullBody = true;
        renderContent();
    }

    @FXML
    private void onCopy() {
        ClipboardContent content = new ClipboardContent();
        content.putString(currentText());
        Clipboard.getSystemClipboard().setContent(content);
        flash(copyButton, "Copied");
    }

    @FXML
    private void onSave() {
        String text = currentText();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save response");
        String name = suggestedName();
        chooser.setInitialFileName(name);
        FileChooser.ExtensionFilter json = new FileChooser.ExtensionFilter("JSON", "*.json");
        FileChooser.ExtensionFilter plain = new FileChooser.ExtensionFilter("Text", "*.txt");
        chooser.getExtensionFilters().addAll(json, plain, new FileChooser.ExtensionFilter("All files", "*.*"));
        chooser.setSelectedExtensionFilter(name.endsWith(".json") ? json : plain);
        File file = chooser.showSaveDialog(owner());
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
            flash(saveButton, "Saved");
        } catch (IOException e) {
            AlertDialog.builder().level(AlertDialog.Level.WARNING).title("Save failed")
                    .message("Could not save the response: " + e.getMessage()).addOwner(owner())
                    .addButton("OK", null).build().show();
        }
    }

    @FXML
    private void onClear() {
        clear();
    }
}
