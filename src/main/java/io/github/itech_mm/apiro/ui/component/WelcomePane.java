package io.github.itech_mm.apiro.ui.component;

import io.github.itech_mm.apiro.ui.AppCommands;
import io.github.itech_mm.apiro.ui.AppCommands.Command;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

public final class WelcomePane extends VBox {

    private static final List<String> PROJECT_TIPS = List.of(
            "Type ${ in any field to insert a variable.",
            "Press Ctrl+Enter to send the selected request.",
            "Press F2 to rename and Delete to remove an item in the tree.",
            "Drag requests in the tree to reorder or move them.",
            "Open several requests in tabs and send them at the same time.");

    private static final List<String> EMPTY_TIPS = List.of(
            "A project is one .apiro.json file with requests, folders and environments.",
            "Folders can share auth, headers and settings with the requests inside them.");

    private final AppCommands commands;

    private final Label subtitle = new Label();

    private final VBox actions = new VBox(2);

    private final VBox tips = new VBox(6);

    public WelcomePane(AppCommands commands) {
        this.commands = commands;
        getStyleClass().add("welcome-pane");
        setAlignment(Pos.CENTER);
        setSpacing(18);
        Label title = new Label("Apiro");
        title.getStyleClass().add("welcome-title");
        subtitle.getStyleClass().addAll("welcome-subtitle", "muted");
        Label actionsTitle = new Label("QUICK ACTIONS");
        actionsTitle.getStyleClass().add("welcome-section");
        Label tipsTitle = new Label("TIPS");
        tipsTitle.getStyleClass().add("welcome-section");
        VBox content = new VBox(18, new VBox(4, title, subtitle), new VBox(6, actionsTitle, actions),
                new VBox(6, tipsTitle, tips));
        content.getStyleClass().add("welcome-content");
        content.setMaxWidth(460);
        content.setMinWidth(320);
        getChildren().add(content);
        refresh(null);
    }

    public void refresh(String projectName) {
        boolean open = projectName != null;
        subtitle.setText(open ? "Project: " + projectName + "  ·  no request is open"
                : "Create or open a project to get started");
        actions.getChildren().clear();
        if (open) {
            actions.getChildren().addAll(action("New request", Command.NEW_REQUEST),
                    action("New folder", Command.NEW_FOLDER), action("Save project", Command.SAVE),
                    action("Open another project", Command.OPEN_PROJECT));
        } else {
            actions.getChildren().addAll(action("New project", Command.NEW_PROJECT),
                    action("Open project", Command.OPEN_PROJECT));
        }
        tips.getChildren().clear();
        for (String tip : open ? PROJECT_TIPS : EMPTY_TIPS) {
            Label label = new Label("•  " + tip);
            label.setWrapText(true);
            label.getStyleClass().add("welcome-tip");
            tips.getChildren().add(label);
        }
    }

    private Button action(String text, Command command) {
        Label name = new Label(text);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, name, spacer);
        row.setAlignment(Pos.CENTER_LEFT);
        String shortcut = command.shortcutText();
        if (!shortcut.isEmpty()) {
            Label chip = new Label(shortcut);
            chip.getStyleClass().add("welcome-shortcut");
            row.getChildren().add(chip);
        }
        Button button = new Button();
        button.setGraphic(row);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.getStyleClass().add("welcome-action");
        button.setOnAction(event -> commands.run(command));
        return button;
    }
}