package io.github.itech_mm.apiro.service.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.itech_mm.apiro.model.BodyOverride;
import io.github.itech_mm.apiro.model.BodyType;
import io.github.itech_mm.apiro.model.FieldOverride;
import io.github.itech_mm.apiro.model.FieldOverride.ValueType;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.RequestBody;
import io.github.itech_mm.apiro.model.RequestItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class BodyOverridesTest {

    private static RequestItem request(BodyType type, String text) {
        return new RequestItem("r", "R", HttpMethod.POST, "http://h", null, null, null, RequestBody.of(type, text),
                null);
    }

    private static String patched(String body, FieldOverride... fields) {
        return BodyOverrides.apply(request(BodyType.JSON, body), BodyOverride.patch(List.of(fields))).body().text();
    }

    private static FieldOverride set(String path, ValueType type, String value) {
        return FieldOverride.of(path, type, value);
    }

    @Test
    void nullOverrideReturnsTheSameRequest() {
        RequestItem original = request(BodyType.JSON, "{}");

        assertSame(original, BodyOverrides.apply(original, null));
    }

    @Test
    void replaceUsesTheStepText() {
        RequestItem result = BodyOverrides.apply(request(BodyType.JSON, "{\"a\":1}"), BodyOverride.replace("{\"b\":2}"));

        assertEquals("{\"b\":2}", result.body().text());
        assertEquals(BodyType.JSON, result.body().type());
    }

    @Test
    void replaceOnARequestWithoutAJsonBodyBecomesJson() {
        RequestItem result = BodyOverrides.apply(request(BodyType.NONE, ""), BodyOverride.replace("{\"b\":2}"));

        assertEquals(BodyType.JSON, result.body().type());
    }

    @Test
    void patchSetsTypedValuesAndKeepsTheRest() {
        String json = patched("{\"name\":\"a\",\"age\":1,\"vip\":false,\"tags\":[\"x\"]}",
                set("$.name", ValueType.STRING, "Bob"), set("$.age", ValueType.NUMBER, "42"),
                set("$.vip", ValueType.BOOLEAN, "true"), set("$.nick", ValueType.NULL, ""),
                set("$.tags[0]", ValueType.STRING, "y"));

        assertEquals(JsonParser.parseString("{\"name\":\"Bob\",\"age\":42,\"vip\":true,\"tags\":[\"y\"],\"nick\":null}"),
                JsonParser.parseString(json));
    }

    @Test
    void patchRemovesFieldsAndArrayItems() {
        String json = patched("{\"a\":1,\"b\":[1,2,3]}", set("$.a", ValueType.REMOVE, ""),
                set("$.b[1]", ValueType.REMOVE, ""), set("$.missing.deep", ValueType.REMOVE, ""));

        assertEquals(JsonParser.parseString("{\"b\":[1,3]}"), JsonParser.parseString(json));
    }

    @Test
    void patchCreatesMissingObjectsAndFields() {
        String json = patched("{}", set("$.user.address.city", ValueType.STRING, "Yangon"));

        assertEquals(JsonParser.parseString("{\"user\":{\"address\":{\"city\":\"Yangon\"}}}"), JsonParser.parseString(json));
    }

    @Test
    void jsonTypedValueIsInsertedAsStructure() {
        String json = patched("{\"a\":1}", set("$.items", ValueType.JSON, "[{\"id\":1},{\"id\":2}]"));

        assertEquals(JsonParser.parseString("{\"a\":1,\"items\":[{\"id\":1},{\"id\":2}]}"), JsonParser.parseString(json));
    }

    @Test
    void bareVariablesInTheOriginalBodySurvive() {
        String json = patched("{\"id\": ${userId}, \"name\": \"${name}\"}", set("$.name", ValueType.STRING, "Bob"));

        assertTrue(json.contains("\"id\": ${userId}"));
        assertTrue(json.contains("\"name\": \"Bob\""));
    }

    @Test
    void numberAndBooleanValuesMayBeVariables() {
        String json = patched("{}", set("$.age", ValueType.NUMBER, "${age}"), set("$.on", ValueType.BOOLEAN, "${flag}"),
                set("$.label", ValueType.STRING, "id-${age}"));

        assertTrue(json.contains("\"age\": ${age}"));
        assertTrue(json.contains("\"on\": ${flag}"));
        assertTrue(json.contains("\"label\": \"id-${age}\""));
    }

    @Test
    void disabledFieldsAreIgnored() {
        RequestItem original = request(BodyType.JSON, "{\"a\":1}");
        BodyOverride override = BodyOverride.patch(List.of(new FieldOverride("$.a", ValueType.NUMBER, "9", false)));

        assertEquals("{\"a\":1}", BodyOverrides.apply(original, override).body().text());
    }

    @Test
    void badInputsFailWithAReadableMessage() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> patched("{\"a\":1}",
                set("$.a", ValueType.NUMBER, "abc"))).getMessage().contains("not a number"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> patched("{\"a\":1}",
                set("$.a", ValueType.BOOLEAN, "yes"))).getMessage().contains("true or false"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> patched("{\"a\":[1]}",
                set("$.a[5]", ValueType.NUMBER, "1"))).getMessage().contains("was not found"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> patched("{\"a\":1}",
                set("$.a[", ValueType.NUMBER, "1"))).getMessage().contains("Invalid JSON path"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> patched("not json",
                set("$.a", ValueType.NUMBER, "1"))).getMessage().contains("not valid JSON"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> BodyOverrides.apply(request(BodyType.XML, "<a/>"),
                BodyOverride.patch(List.of(set("$.a", ValueType.NUMBER, "1"))))).getMessage().contains("Use Replace"));
    }

    @Test
    void emptyBodyCanBePatchedIntoJson() {
        RequestItem result = BodyOverrides.apply(request(BodyType.NONE, ""),
                BodyOverride.patch(List.of(set("$.a", ValueType.NUMBER, "1"))));

        assertEquals(BodyType.JSON, result.body().type());
        assertEquals(JsonParser.parseString("{\"a\":1}"), JsonParser.parseString(result.body().text()));
    }
}
