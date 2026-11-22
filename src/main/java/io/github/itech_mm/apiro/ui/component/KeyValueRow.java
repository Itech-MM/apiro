package io.github.itech_mm.apiro.ui.component;

import io.github.itech_mm.apiro.model.KeyValue;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class KeyValueRow {

    private final StringProperty key = new SimpleStringProperty("");
    private final StringProperty value = new SimpleStringProperty("");
    private final BooleanProperty enabled = new SimpleBooleanProperty(true);

    public KeyValueRow() {
    }

    public KeyValueRow(String key, String value, boolean enabled) {
        this.key.set(key);
        this.value.set(value);
        this.enabled.set(enabled);
    }

    public static KeyValueRow from(KeyValue keyValue) {
        return new KeyValueRow(keyValue.key(), keyValue.value(), keyValue.enabled());
    }

    public KeyValue toKeyValue() {
        return new KeyValue(key.get(), value.get(), enabled.get());
    }

    public StringProperty keyProperty() {
        return key;
    }

    public StringProperty valueProperty() {
        return value;
    }

    public BooleanProperty enabledProperty() {
        return enabled;
    }

    public String getKey() {
        return key.get();
    }

    public String getValue() {
        return value.get();
    }

    public boolean isEnabled() {
        return enabled.get();
    }
}