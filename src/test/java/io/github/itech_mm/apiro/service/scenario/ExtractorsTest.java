package io.github.itech_mm.apiro.service.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.Extractor;
import io.github.itech_mm.apiro.model.Extractor.Source;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.ResponseData;
import io.github.itech_mm.apiro.model.Variable;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractorsTest {

    private static ResponseContext response(String body) {
        return ResponseContext.of(new ResponseData(201, "Created", 120, 80, body.length(), 1,
                List.of(KeyValue.of("Content-Type", "application/json"), KeyValue.of("X-Request-Id", "r-42")),
                List.of(), body));
    }

    private static Extractors.Result run(Source source, String expression, String body) {
        return Extractors.extract(new Extractor("v", source, expression), response(body));
    }

    @Test
    void jsonPathReadsValues() {
        Extractors.Result result = run(Source.JSON_PATH, "$.data.accessToken", "{\"data\":{\"accessToken\":\"tok\"}}");
        assertTrue(result.success());
        assertEquals("tok", result.value());
    }

    @Test
    void headerLookupIgnoresCase() {
        assertEquals("r-42", run(Source.HEADER, "x-request-id", "").value());
        assertEquals("application/json", run(Source.HEADER, "CONTENT-TYPE", "").value());
    }

    @Test
    void statusIsReadAsText() {
        assertEquals("201", run(Source.STATUS, "", "").value());
    }

    @Test
    void regexUsesFirstGroupOrWholeMatch() {
        assertEquals("991", run(Source.REGEX, "order=(\\d+)", "id order=991 done").value());
        assertEquals("order=991", run(Source.REGEX, "order=\\d+", "id order=991 done").value());
    }

    @Test
    void failuresCarryAReason() {
        Extractors.Result missing = run(Source.JSON_PATH, "$.nope", "{}");
        assertFalse(missing.success());
        assertTrue(missing.error().contains("$.nope"));
        assertTrue(run(Source.JSON_PATH, "$.a", "plain text").error().contains("not JSON"));
        assertTrue(run(Source.JSON_PATH, "$.", "{}").error().contains("Invalid JSON path"));
        assertTrue(run(Source.HEADER, "X-Nope", "").error().contains("X-Nope"));
        assertTrue(run(Source.REGEX, "(", "x").error().contains("Invalid regex"));
        assertTrue(run(Source.REGEX, "zzz", "x").error().contains("did not match"));
        assertTrue(run(Source.REGEX, "(a)|(b)", "b").error().contains("group 1"));
    }

    @Test
    void emptyVariableNameFails() {
        Extractors.Result result = Extractors.extract(new Extractor(" ", Source.STATUS, ""), response(""));
        assertFalse(result.success());
        assertTrue(result.error().contains("Variable name"));
    }

    @Test
    void onlySuccessfulResultsBecomeVariables() {
        ResponseContext context = response("{\"id\":7}");
        List<Extractors.Result> results = Extractors.extractAll(List.of(
                new Extractor("orderId", Source.JSON_PATH, "$.id"),
                new Extractor("gone", Source.JSON_PATH, "$.missing"),
                new Extractor("code", Source.STATUS, "")), context);
        assertEquals(3, results.size());
        List<Variable> variables = Extractors.toVariables(results);
        assertEquals(2, variables.size());
        assertEquals("orderId", variables.get(0).key());
        assertEquals("7", variables.get(0).value());
        assertEquals("201", variables.get(1).value());
    }
}
