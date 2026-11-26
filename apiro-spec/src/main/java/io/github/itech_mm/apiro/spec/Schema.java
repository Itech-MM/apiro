package io.github.itech_mm.apiro.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Schema {
    private String type;
    private String format;
    private String ref;
    private Schema items;
    private Map<String, Schema> properties = new LinkedHashMap<>();
    private List<String> required = new ArrayList<>();
    private List<String> enumValues = new ArrayList<>();
    private Boolean nullable;
    private Double minimum;
    private Double maximum;
    private Integer minLength;
    private Integer maxLength;
    private String pattern;
    private Integer minItems;
    private Integer maxItems;
    private String example;

    public String getType() {
        return type;
    }

    public Schema setType(String type) {
        this.type = type;
        return this;
    }

    public String getFormat() {
        return format;
    }

    public Schema setFormat(String format) {
        this.format = format;
        return this;
    }

    public String getRef() {
        return ref;
    }

    public Schema setRef(String ref) {
        this.ref = ref;
        return this;
    }

    public Schema getItems() {
        return items;
    }

    public Schema setItems(Schema items) {
        this.items = items;
        return this;
    }

    public Map<String, Schema> getProperties() {
        return properties;
    }

    public Schema setProperties(Map<String, Schema> properties) {
        this.properties = properties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(properties);
        return this;
    }

    public List<String> getRequired() {
        return required;
    }

    public Schema setRequired(List<String> required) {
        this.required = required == null ? new ArrayList<>() : new ArrayList<>(required);
        return this;
    }

    public List<String> getEnumValues() {
        return enumValues;
    }

    public Schema setEnumValues(List<String> enumValues) {
        this.enumValues = enumValues == null ? new ArrayList<>() : new ArrayList<>(enumValues);
        return this;
    }

    public Boolean getNullable() {
        return nullable;
    }

    public Schema setNullable(Boolean nullable) {
        this.nullable = nullable;
        return this;
    }

    public Double getMinimum() {
        return minimum;
    }

    public Schema setMinimum(Double minimum) {
        this.minimum = minimum;
        return this;
    }

    public Double getMaximum() {
        return maximum;
    }

    public Schema setMaximum(Double maximum) {
        this.maximum = maximum;
        return this;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public Schema setMinLength(Integer minLength) {
        this.minLength = minLength;
        return this;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public Schema setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public String getPattern() {
        return pattern;
    }

    public Schema setPattern(String pattern) {
        this.pattern = pattern;
        return this;
    }

    public Integer getMinItems() {
        return minItems;
    }

    public Schema setMinItems(Integer minItems) {
        this.minItems = minItems;
        return this;
    }

    public Integer getMaxItems() {
        return maxItems;
    }

    public Schema setMaxItems(Integer maxItems) {
        this.maxItems = maxItems;
        return this;
    }

    public String getExample() {
        return example;
    }

    public Schema setExample(String example) {
        this.example = example;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Schema)) {
            return false;
        }
        Schema other = (Schema) o;
        return Objects.equals(type, other.type)
                && Objects.equals(format, other.format)
                && Objects.equals(ref, other.ref)
                && Objects.equals(items, other.items)
                && Objects.equals(properties, other.properties)
                && Objects.equals(required, other.required)
                && Objects.equals(enumValues, other.enumValues)
                && Objects.equals(nullable, other.nullable)
                && Objects.equals(minimum, other.minimum)
                && Objects.equals(maximum, other.maximum)
                && Objects.equals(minLength, other.minLength)
                && Objects.equals(maxLength, other.maxLength)
                && Objects.equals(pattern, other.pattern)
                && Objects.equals(minItems, other.minItems)
                && Objects.equals(maxItems, other.maxItems)
                && Objects.equals(example, other.example);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, format, ref, items, properties, required, enumValues, nullable, minimum, maximum, minLength, maxLength, pattern, minItems, maxItems, example);
    }

    @Override
    public String toString() {
        return "Schema{" +
                "type=" + type +
                ", format=" + format +
                ", ref=" + ref +
                ", items=" + items +
                ", properties=" + properties +
                ", required=" + required +
                ", enumValues=" + enumValues +
                ", nullable=" + nullable +
                ", minimum=" + minimum +
                ", maximum=" + maximum +
                ", minLength=" + minLength +
                ", maxLength=" + maxLength +
                ", pattern=" + pattern +
                ", minItems=" + minItems +
                ", maxItems=" + maxItems +
                ", example=" + example +
                "}";
    }
}
