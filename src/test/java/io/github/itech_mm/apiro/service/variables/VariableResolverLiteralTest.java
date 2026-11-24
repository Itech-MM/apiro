package io.github.itech_mm.apiro.service.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.Variable;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariableResolverLiteralTest {

    private static Project project() {
        RequestItem request = new RequestItem("r", "r", HttpMethod.GET, "", null, null, null, null, null);
        List<ProjectItem> items = List.of(request);
        List<Variable> variables = List.of(Variable.of("secret", "hidden"), Variable.of("auth", "Bearer ${token}"));
        return new Project(null, 0, null, "Shop", null, null, null, null, variables, List.of(), items);
    }

    @Test
    void literalValueIsInsertedAsIs() {
        VariableResolver resolver = VariableResolver.forItem(project(), "", "r", List.of(),
                List.of(Variable.of("token", "${secret}")));
        assertEquals("${secret}", resolver.resolve("${token}"));
    }

    @Test
    void literalValueSurvivesInsideDeclaredValue() {
        VariableResolver resolver = VariableResolver.forItem(project(), "", "r", List.of(),
                List.of(Variable.of("token", "${secret}")));
        assertEquals("Bearer ${secret}", resolver.resolve("${auth}"));
    }

    @Test
    void literalBeatsDeclaredVariable() {
        VariableResolver resolver = VariableResolver.forItem(project(), "", "r", List.of(),
                List.of(Variable.of("secret", "from-response")));
        assertEquals("from-response", resolver.resolve("${secret}"));
    }

    @Test
    void runtimeLayerStillResolvesNested() {
        VariableResolver resolver = VariableResolver.forItem(project(), "", "r",
                List.of(Variable.of("alias", "${secret}")), List.of());
        assertEquals("hidden", resolver.resolve("${alias}"));
    }

    @Test
    void nestedDeclaredValuesStillResolveWithMixedText() {
        VariableResolver resolver = VariableResolver.of(List.of(List.of(Variable.of("url", "${host}/api"),
                Variable.of("host", "example.com"))));
        assertEquals("https://example.com/api/x", resolver.resolve("https://${url}/x"));
    }
}
