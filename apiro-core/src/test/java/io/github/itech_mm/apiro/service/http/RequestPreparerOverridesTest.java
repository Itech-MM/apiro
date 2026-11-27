package io.github.itech_mm.apiro.service.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.AuthConfig;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.KeyValue;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.Variable;
import io.github.itech_mm.apiro.service.http.RequestPreparer.PreparedRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequestPreparerOverridesTest {

    private static RequestItem request(List<KeyValue> headers, AuthConfig auth) {
        return new RequestItem("r", "r", HttpMethod.GET, "http://api.test/x", null, headers, auth, null, null);
    }

    private static Project project(RequestItem request, List<Variable> variables) {
        return new Project(null, 0, "p", "Shop", null, null, null, null, variables, null, List.of(request));
    }

    private static PreparedRequest prepare(RequestItem request, List<Variable> literal, List<KeyValue> overrides) {
        return RequestPreparer.prepare(project(request, null), "", "r", List.of(), literal, overrides);
    }

    private static List<String> values(PreparedRequest prepared, String name) {
        return prepared.headers().stream().filter(header -> header.key().equalsIgnoreCase(name))
                .map(KeyValue::value).toList();
    }

    @Test
    void addsAHeaderTheRequestDoesNotHave() {
        PreparedRequest prepared = prepare(request(List.of(KeyValue.of("X-Env", "a")), null), List.of(),
                List.of(KeyValue.of("X-Trace", "1")));

        assertEquals(List.of("a"), values(prepared, "X-Env"));
        assertEquals(List.of("1"), values(prepared, "X-Trace"));
    }

    @Test
    void replacesTheSameHeaderIgnoringCase() {
        PreparedRequest prepared = prepare(request(List.of(KeyValue.of("authorization", "old")), null), List.of(),
                List.of(KeyValue.of("Authorization", "new")));

        assertEquals(List.of("new"), values(prepared, "Authorization"));
    }

    @Test
    void replacementKeepsThePositionOfTheOriginal() {
        PreparedRequest prepared = prepare(
                request(List.of(KeyValue.of("A", "1"), KeyValue.of("B", "2"), KeyValue.of("C", "3")), null),
                List.of(), List.of(KeyValue.of("B", "changed")));

        assertEquals(List.of("A", "B", "C"), prepared.headers().stream().map(KeyValue::key).toList());
        assertEquals(List.of("changed"), values(prepared, "B"));
    }

    @Test
    void overrideBeatsBearerAuthOfTheRequest() {
        PreparedRequest prepared = prepare(request(null, AuthConfig.bearer("from-auth")),
                List.of(Variable.of("token", "abc")), List.of(KeyValue.of("Authorization", "Bearer ${token}")));

        assertEquals(List.of("Bearer abc"), values(prepared, "Authorization"));
    }

    @Test
    void bearerAuthStillWorksWithoutAnOverride() {
        PreparedRequest prepared = prepare(request(null, AuthConfig.bearer("from-auth")), List.of(), List.of());

        assertEquals(List.of("Bearer from-auth"), values(prepared, "Authorization"));
    }

    @Test
    void disabledOverrideIsIgnored() {
        PreparedRequest prepared = prepare(request(List.of(KeyValue.of("X-Env", "a")), null), List.of(),
                List.of(new KeyValue("X-Env", "b", false), new KeyValue("X-Off", "1", false)));

        assertEquals(List.of("a"), values(prepared, "X-Env"));
        assertTrue(values(prepared, "X-Off").isEmpty());
    }

    @Test
    void blankNameIsIgnored() {
        PreparedRequest prepared = prepare(request(null, null), List.of(), List.of(KeyValue.of("  ", "x")));

        assertTrue(prepared.headers().stream().noneMatch(header -> header.value().equals("x")));
    }

    @Test
    void nameAndValueResolveVariables() {
        PreparedRequest prepared = prepare(request(null, null),
                List.of(Variable.of("h", "X-Trace"), Variable.of("v", "42")),
                List.of(KeyValue.of("${h}", "id-${v}")));

        assertEquals(List.of("id-42"), values(prepared, "X-Trace"));
    }

    @Test
    void extractedValueIsInsertedAsIsAndNeverExpanded() {
        Project project = project(request(null, null), List.of(Variable.of("secret", "hidden")));
        PreparedRequest prepared = RequestPreparer.prepare(project, "", "r", List.of(),
                List.of(Variable.of("token", "${secret}")), List.of(KeyValue.of("Authorization", "Bearer ${token}")));

        assertEquals(List.of("Bearer ${secret}"), values(prepared, "Authorization"));
    }

    @Test
    void undefinedVariableInAnOverrideIsReported() {
        PreparedRequest prepared = prepare(request(null, null), List.of(),
                List.of(KeyValue.of("Authorization", "Bearer ${missing}")));

        assertEquals(List.of("missing"), prepared.unresolved());
    }

    @Test
    void emptyOverridesGiveTheSameResultAsTheOlderOverload() {
        RequestItem request = request(List.of(KeyValue.of("X-Env", "a")), AuthConfig.bearer("t"));
        Project project = project(request, null);

        assertEquals(RequestPreparer.prepare(project, "", "r", List.of(), List.of()),
                RequestPreparer.prepare(project, "", "r", List.of(), List.of(), List.of()));
    }

    @Test
    void overridesUrlAndQueryParametersPerStep() {
        RequestItem req = new RequestItem("r", "r", HttpMethod.GET, "http://api.test/shifts/1",
                List.of(KeyValue.of("counterId", "1"), KeyValue.of("mode", "full")),
                null, null, null, null);
        Project project = project(req, null);
        PreparedRequest prepared = RequestPreparer.prepare(project, "", "r", List.of(),
                List.of(Variable.of("shiftId", "42"), Variable.of("counterId", "7")),
                List.of(), null,
                List.of(KeyValue.of("counterId", "${counterId}"), KeyValue.of("includeClosed", "false")),
                "http://api.test/shifts/${shiftId}/summary");
        assertEquals("http://api.test/shifts/42/summary?counterId=7&mode=full&includeClosed=false", prepared.url());
        assertTrue(prepared.unresolved().isEmpty());
    }

    @Test
    void unresolvedVariableInQueryOverrideIsReported() {
        RequestItem req = request(null, null);
        PreparedRequest prepared = RequestPreparer.prepare(project(req, null), "", "r", List.of(), List.of(),
                List.of(), null, List.of(KeyValue.of("counterId", "${missingCounter}")), "");
        assertEquals(List.of("missingCounter"), prepared.unresolved());
    }
}
