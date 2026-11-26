package io.github.itech_mm.apiro.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Handler {
    private String className;
    private String methodName;
    private List<String> parameterTypes = new ArrayList<>();

    public String getClassName() {
        return className;
    }

    public Handler setClassName(String className) {
        this.className = className;
        return this;
    }

    public String getMethodName() {
        return methodName;
    }

    public Handler setMethodName(String methodName) {
        this.methodName = methodName;
        return this;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public Handler setParameterTypes(List<String> parameterTypes) {
        this.parameterTypes = parameterTypes == null ? new ArrayList<>() : new ArrayList<>(parameterTypes);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Handler)) {
            return false;
        }
        Handler other = (Handler) o;
        return Objects.equals(className, other.className)
                && Objects.equals(methodName, other.methodName)
                && Objects.equals(parameterTypes, other.parameterTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, methodName, parameterTypes);
    }

    @Override
    public String toString() {
        return "Handler{" +
                "className=" + className +
                ", methodName=" + methodName +
                ", parameterTypes=" + parameterTypes +
                "}";
    }
}
