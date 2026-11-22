package io.github.itech_mm.apiro;

import java.util.Objects;

import io.github.itech_framework.core.annotations.ComponentScan;
import io.github.itech_framework.core.annotations.api_client.EnableApiClient;
import io.github.itech_framework.core.annotations.jfx.EnableJavaFx;
import io.github.itech_framework.core.annotations.properties.Property;
import io.github.itech_framework.java_fx.ITechJavaFxApplication;
import io.github.itech_framework.java_fx.router.config.RouterConfig;
import io.github.itech_framework.java_fx.ui.layout.LayoutConfig;
import io.github.itech_mm.apiro.ui.MonoFont;
import io.github.itech_mm.apiro.ui.WorkspaceController;
import javafx.stage.Stage;

@ComponentScan(basePackage = "io.github.itech_mm.apiro")
@EnableJavaFx
@EnableApiClient
public class ApiroMain extends ITechJavaFxApplication {

    @Property(key = "app.name", defaultValue = "Apiro")
    private String appName;

    @Override
    protected void onInit() throws Exception {
        RouterConfig.setDarkModeKey("isDarkMode");
        router.getConfig().addStyleSheets(Objects.requireNonNull(getClass().getResource("/static/css/app.css")).toExternalForm());

        router.getConfig().addLayoutConfig("main", new LayoutConfig("/views/layout/main.fxml"));

        router.registerRoute("workspace", "/views/workspace-view.fxml", WorkspaceController.class, null,"main");
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle(appName);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        router.initialize(ApiroMain.class, primaryStage);
        router.getConfig().addStyleSheets(MonoFont.stylesheet());
        router.to("workspace");
        primaryStage.setMaximized(true);
        primaryStage.show();
    }

    public static void main(String[] args) throws Exception {
        ITechJavaFxApplication.run(ApiroMain.class, args);
    }
}