package io.github.itech_mm.apiro;

import io.github.itech_framework.core.annotations.ComponentScan;
import io.github.itech_framework.core.annotations.api_client.EnableApiClient;
import io.github.itech_framework.core.annotations.jfx.EnableJavaFx;
import io.github.itech_framework.java_fx.ITechJavaFxApplication;
import javafx.stage.Stage;

@ComponentScan(basePackage = "io.github.itech_mm.apiro")
@EnableJavaFx
@EnableApiClient
public class ApiroMain extends ITechJavaFxApplication {

    @Override
    protected void onInit() throws Exception {

    }

    @Override
    public void start(Stage primaryStage) throws Exception {

    }

    public static void main(String[] args) throws Exception {
        ITechJavaFxApplication.run(ApiroMain.class ,args);
    }

}