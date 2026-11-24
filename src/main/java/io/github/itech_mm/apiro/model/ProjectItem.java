package io.github.itech_mm.apiro.model;

public sealed interface ProjectItem permits Folder, RequestItem, ScenarioItem {

    String id();

    String name();
}