package io.github.itech_mm.apiro.model;

public record Edge(String from, String to) {

    public Edge {
        from = from == null ? "" : from;
        to = to == null ? "" : to;
    }
}
