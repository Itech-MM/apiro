package io.github.itech_mm.apiro.service.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.Assertion;
import io.github.itech_mm.apiro.model.Assertion.Operator;
import io.github.itech_mm.apiro.model.Assertion.Source;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.ResponseData;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssertionsTest {

    private static final String BODY = """
            {"status": "CONFIRMED", "total": 12.50, "count": 3, "tags": ["new", "vip"],
             "ok": true, "note": null, "customer": {"id": 7}}
            """;

    private static final ResponseContext CONTEXT = ResponseContext.of(new ResponseData(200, "OK", 340, 90,
            BODY.length(), 1, List.of(KeyValue.of("Content-Type", "application/json; charset=utf-8")), List.of(),
            BODY));

    private static Assertions.Result check(Source source, String expression, Operator operator, String expected) {
        return Assertions.check(new Assertion(source, expression, operator, expected), CONTEXT);
    }

    private static void passes(Source source, String expression, Operator operator, String expected) {
        Assertions.Result result = check(source, expression, operator, expected);
        assertTrue(result.passed(), result.message());
    }

    private static Assertions.Result fails(Source source, String expression, Operator operator, String expected) {
        Assertions.Result result = check(source, expression, operator, expected);
        assertFalse(result.passed(), result.message());
        return result;
    }

    @Test
    void statusCode() {
        passes(Source.STATUS, "", Operator.EQUALS, "200");
        passes(Source.STATUS, "", Operator.NOT_EQUALS, "404");
        passes(Source.STATUS, "", Operator.LESS_THAN, "300");
        passes(Source.STATUS, "", Operator.GREATER_OR_EQUAL, "200");
        assertEquals("Expected status to equal 201 but was 200",
                fails(Source.STATUS, "", Operator.EQUALS, "201").message());
    }

    @Test
    void jsonPathEquals() {
        passes(Source.JSON_PATH, "$.status", Operator.EQUALS, "CONFIRMED");
        passes(Source.JSON_PATH, "$.customer.id", Operator.EQUALS, "7");
        passes(Source.JSON_PATH, "$.total", Operator.EQUALS, "12.5");
        passes(Source.JSON_PATH, "$.ok", Operator.EQUALS, "true");
        passes(Source.JSON_PATH, "$.note", Operator.EQUALS, "null");
        assertEquals("Expected $.status to equal PENDING but was CONFIRMED",
                fails(Source.JSON_PATH, "$.status", Operator.EQUALS, "PENDING").message());
    }

    @Test
    void jsonPathExists() {
        passes(Source.JSON_PATH, "$.status", Operator.EXISTS, "");
        passes(Source.JSON_PATH, "$.note", Operator.EXISTS, "");
        passes(Source.JSON_PATH, "$.missing", Operator.NOT_EXISTS, "");
        assertEquals("$.missing does not exist", fails(Source.JSON_PATH, "$.missing", Operator.EXISTS, "").message());
        fails(Source.JSON_PATH, "$.status", Operator.NOT_EXISTS, "");
    }

    @Test
    void jsonPathContains() {
        passes(Source.JSON_PATH, "$.status", Operator.CONTAINS, "FIRM");
        passes(Source.JSON_PATH, "$.tags", Operator.CONTAINS, "vip");
        fails(Source.JSON_PATH, "$.tags", Operator.CONTAINS, "vi");
        passes(Source.JSON_PATH, "$.tags", Operator.NOT_CONTAINS, "gold");
    }

    @Test
    void jsonPathNumbers() {
        passes(Source.JSON_PATH, "$.count", Operator.GREATER_THAN, "2");
        passes(Source.JSON_PATH, "$.count", Operator.LESS_OR_EQUAL, "3");
        passes(Source.JSON_PATH, "$.total", Operator.GREATER_THAN, "12.49");
        fails(Source.JSON_PATH, "$.count", Operator.LESS_THAN, "3");
        assertTrue(fails(Source.JSON_PATH, "$.status", Operator.GREATER_THAN, "1").message().contains("not a number"));
        assertTrue(fails(Source.JSON_PATH, "$.count", Operator.GREATER_THAN, "many").message()
                .contains("Expected value is not a number"));
    }

    @Test
    void missingPathFailsComparisons() {
        assertEquals("$.nope was not found", fails(Source.JSON_PATH, "$.nope", Operator.EQUALS, "x").message());
    }

    @Test
    void headers() {
        passes(Source.HEADER, "content-type", Operator.CONTAINS, "json");
        passes(Source.HEADER, "Content-Type", Operator.EXISTS, "");
        passes(Source.HEADER, "X-Nope", Operator.NOT_EXISTS, "");
        fails(Source.HEADER, "X-Nope", Operator.EQUALS, "a");
    }

    @Test
    void responseTime() {
        passes(Source.RESPONSE_TIME, "", Operator.LESS_THAN, "500");
        fails(Source.RESPONSE_TIME, "", Operator.LESS_THAN, "100");
    }

    @Test
    void bodyContains() {
        passes(Source.BODY, "", Operator.CONTAINS, "CONFIRMED");
        passes(Source.BODY, "", Operator.NOT_CONTAINS, "ERROR");
        passes(Source.BODY, "", Operator.EXISTS, "");
    }

    @Test
    void emptyBodyDoesNotExist() {
        ResponseContext empty = ResponseContext.of(new ResponseData(204, "No Content", 5, 5, 0, 1, List.of(),
                List.of(), ""));
        assertTrue(Assertions.check(new Assertion(Source.BODY, "", Operator.NOT_EXISTS, ""), empty).passed());
        assertFalse(Assertions.check(new Assertion(Source.BODY, "", Operator.EXISTS, ""), empty).passed());
    }

    @Test
    void invalidPathOrBodyIsAFailureNotACrash() {
        assertTrue(fails(Source.JSON_PATH, "$.", Operator.EQUALS, "x").message().contains("Invalid JSON path"));
        ResponseContext text = ResponseContext.of(new ResponseData(200, "OK", 5, 5, 4, 1, List.of(), List.of(),
                "plain"));
        Assertions.Result result = Assertions.check(new Assertion(Source.JSON_PATH, "$.a", Operator.EQUALS, "x"), text);
        assertFalse(result.passed());
        assertTrue(result.message().contains("not JSON"));
    }

    @Test
    void expectedValueIsResolvedByTheCaller() {
        Assertion assertion = new Assertion(Source.JSON_PATH, "$.customer.id", Operator.EQUALS, "${customerId}");
        assertTrue(Assertions.check(assertion, CONTEXT, text -> text.replace("${customerId}", "7")).passed());
        assertFalse(Assertions.check(assertion, CONTEXT).passed());
    }

    @Test
    void checkAllKeepsOrder() {
        List<Assertions.Result> results = Assertions.checkAll(List.of(
                new Assertion(Source.STATUS, "", Operator.EQUALS, "200"),
                new Assertion(Source.STATUS, "", Operator.EQUALS, "500")), CONTEXT, text -> text);
        assertEquals(List.of(true, false), results.stream().map(Assertions.Result::passed).toList());
    }

    @Test
    void longActualValuesAreShortened() {
        String body = "x".repeat(500);
        ResponseContext big = ResponseContext.of(new ResponseData(200, "OK", 5, 5, 500, 1, List.of(), List.of(),
                body));
        Assertions.Result result = Assertions.check(new Assertion(Source.BODY, "", Operator.EQUALS, "y"), big);
        assertTrue(result.message().length() < 300, result.message());
    }
}
