package io.github.itech_mm.apiro.spec;

import java.util.Objects;

public final class Body {
    private String contentType;
    private boolean required;
    private Schema schema;

    public String getContentType() {
        return contentType;
    }

    public Body setContentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public boolean isRequired() {
        return required;
    }

    public Body setRequired(boolean required) {
        this.required = required;
        return this;
    }

    public Schema getSchema() {
        return schema;
    }

    public Body setSchema(Schema schema) {
        this.schema = schema;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Body)) {
            return false;
        }
        Body other = (Body) o;
        return Objects.equals(contentType, other.contentType)
                && required == other.required
                && Objects.equals(schema, other.schema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentType, required, schema);
    }

    @Override
    public String toString() {
        return "Body{" +
                "contentType=" + contentType +
                ", required=" + required +
                ", schema=" + schema +
                "}";
    }
}
