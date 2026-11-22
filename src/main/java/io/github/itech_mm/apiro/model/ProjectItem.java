package io.github.itech_mm.apiro.model;

public sealed interface ProjectItem permits Folder, RequestItem {

    String id();

    String name();
}