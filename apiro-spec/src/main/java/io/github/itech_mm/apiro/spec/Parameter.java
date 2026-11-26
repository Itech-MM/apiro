package io.github.itech_mm.apiro.spec;

import java.util.Objects;

/**
 * A path, query, header, cookie, form or multipart parameter.
 *
 * <p>Plain bean: no JSON library, no framework types. Collections are never null.
 * Absent optional values are null (or false / empty).
 */
public final class Parameter {

    private String name;
    private String in;
    private boolean required;
    private String defaultValue;
    private String description;
    private Schema schema;

    public String getName() {
        return name;
    }

    public Parameter setName(String name) {
        this.name = name;
        return this;
    }

    public String getIn() {
        return in;
    }

    public Parameter setIn(String in) {
        this.in = in;
        return this;
    }

    public boolean isRequired() {
        return required;
    }

    public Parameter setRequired(boolean required) {
        this.required = required;
        return this;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public Parameter setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Parameter setDescription(String description) {
        this.description = description;
        return this;
    }

    public Schema getSchema() {
        return schema;
    }

    public Parameter setSchema(Schema schema) {
        this.schema = schema;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Parameter)) {
            return false;
        }
        Parameter other = (Parameter) o;
        return Objects.equals(name, other.name)
                && Objects.equals(in, other.in)
                && required == other.required
                && Objects.equals(defaultValue, other.defaultValue)
                && Objects.equals(description, other.description)
                && Objects.equals(schema, other.schema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, in, required, defaultValue, description, schema);
    }

    @Override
    public String toString() {
        return "Parameter{" +
                "name=" + name +
                ", in=" + in +
                ", required=" + required +
                ", defaultValue=" + defaultValue +
                ", description=" + description +
                ", schema=" + schema +
                "}";
    }
}
