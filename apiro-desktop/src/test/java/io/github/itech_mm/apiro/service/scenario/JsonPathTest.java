package io.github.itech_mm.apiro.service.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JsonPathTest {

    private static final JsonElement DOC = JsonParser.parseString("""
            {"data": {"token": "abc", "n": 5, "nothing": null},
             "items": [{"id": 1, "tags": ["a", "b"]}, {"id": 2}],
             "user name": "Aung", "content-type": "json"}
            """);

    private static String value(String path) {
        Optional<JsonElement> found = JsonPath.compile(path).evaluate(DOC);
        assertTrue(found.isPresent(), path);
        return ResponseContext.text(found.get());
    }

    @Test
    void readsNestedNames() {
        assertEquals("abc", value("$.data.token"));
        assertEquals("5", value("$.data.n"));
    }

    @Test
    void readsArrayIndexes() {
        assertEquals("1", value("$.items[0].id"));
        assertEquals("2", value("$.items[1].id"));
        assertEquals("b", value("$.items[0].tags[1]"));
    }

    @Test
    void readsQuotedKeys() {
        assertEquals("Aung", value("$['user name']"));
        assertEquals("Aung", value("$[\"user name\"]"));
        assertEquals("abc", value("$['data']['token']"));
    }

    @Test
    void allowsDashesAndMissingDollar() {
        assertEquals("json", value("$.content-type"));
        assertEquals("abc", value("data.token"));
        assertEquals("abc", value(".data.token"));
    }

    @Test
    void rootArrayAndWholeDocument() {
        JsonElement array = JsonParser.parseString("[10, 20]");
        assertEquals("20", ResponseContext.text(JsonPath.compile("$[1]").evaluate(array).orElseThrow()));
        assertTrue(JsonPath.compile("$").evaluate(array).isPresent());
    }

    @Test
    void jsonNullIsPresent() {
        assertEquals("null", value("$.data.nothing"));
    }

    @Test
    void missingPartsGiveEmpty() {
        assertTrue(JsonPath.compile("$.data.nope").evaluate(DOC).isEmpty());
        assertTrue(JsonPath.compile("$.items[9]").evaluate(DOC).isEmpty());
        assertTrue(JsonPath.compile("$.data.token.deeper").evaluate(DOC).isEmpty());
        assertTrue(JsonPath.compile("$.data[0]").evaluate(DOC).isEmpty());
    }

    @Test
    void objectsComeBackAsCompactJson() {
        assertEquals("{\"id\":2}", value("$.items[1]"));
    }

    @Test
    void invalidPathsAreRejected() {
        for (String path : new String[] {"", "  ", "$.", "$..x", "$.items[", "$.items[a]", "$.items[-1]", "$x",
                "$['open", "$.a[0"}) {
            assertThrows(IllegalArgumentException.class, () -> JsonPath.compile(path), path);
        }
    }
}
