package io.github.itech_mm.apiro.model;

import java.util.List;

public record ImportedSnapshot(String method, String path, String url, Handler handler, List<Parameter> parameters,
                               String bodyContentType, String bodySchemaHash) {

    public static final String ANY = "ANY";

    public record Handler(String className, String methodName, List<String> parameterTypes) {

        public Handler {
            className = className == null ? "" : className;
            methodName = methodName == null ? "" : methodName;
            parameterTypes = parameterTypes == null ? List.of() : List.copyOf(parameterTypes);
        }
    }

    public record Parameter(String name, String in, boolean required, String schemaHash) {

        public Parameter {
            name = name == null ? "" : name;
            in = in == null ? "" : in;
        }
    }

    public ImportedSnapshot {
        method = method == null ? "" : method;
        path = path == null ? "" : path;
        url = url == null ? "" : url;
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    public String routeKey() {
        return method.isEmpty() || path.isEmpty() ? null : method + " " + path;
    }

    public String handlerKey() {
        if (method.isEmpty() || handler == null || handler.className().isEmpty() || handler.methodName().isEmpty()) {
            return null;
        }
        return method + "#" + handler.className() + "." + handler.methodName() + "("
                + String.join(",", handler.parameterTypes()) + ")";
    }
}
