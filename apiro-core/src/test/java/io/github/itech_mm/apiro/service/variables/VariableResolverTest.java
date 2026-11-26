package io.github.itech_mm.apiro.service.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.Environment;
import io.github.itech_mm.apiro.model.Folder;
import io.github.itech_mm.apiro.model.HttpMethod;
import io.github.itech_mm.apiro.model.Project;
import io.github.itech_mm.apiro.model.ProjectItem;
import io.github.itech_mm.apiro.model.RequestItem;
import io.github.itech_mm.apiro.model.Variable;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariableResolverTest {

    private static VariableResolver single(Variable... variables) {
        return VariableResolver.of(List.of(List.of(variables)));
    }

    private static Project layeredProject() {
        RequestItem inFolder = new RequestItem("inFolder", "inFolder", HttpMethod.GET, "", null, null, null, null,
                null);
        RequestItem atRoot = new RequestItem("atRoot", "atRoot", HttpMethod.GET, "", null, null, null, null, null);
        Folder folder = new Folder("f", "f", null, null, List.of(Variable.of("baseUrl", "folder")), null,
                List.of(inFolder));
        Environment env = new Environment("e1", "Dev", List.of(Variable.of("baseUrl", "env")));
        List<ProjectItem> items = List.of(folder, atRoot);
        return new Project(null, 0, null, "Shop", null, null, null, null,
                List.of(Variable.of("baseUrl", "project")), List.of(env), items);
    }

    @Test
    void folderBeatsEnvironmentBeatsProject() {
        Project project = layeredProject();
        assertEquals("folder", VariableResolver.forItem(project, "e1", "inFolder").resolve("${baseUrl}"));
        assertEquals("env", VariableResolver.forItem(project, "e1", "atRoot").resolve("${baseUrl}"));
        assertEquals("project", VariableResolver.forItem(project, "", "atRoot").resolve("${baseUrl}"));
    }

    @Test
    void disabledVariableIsIgnored() {
        VariableResolver resolver = single(new Variable("a", "1", false, false));
        assertFalse(resolver.isDefined("a"));
        assertEquals("${a}", resolver.resolve("${a}"));
    }

    @Test
    void unknownReferenceStaysUntouched() {
        assertEquals("x/${missing}", single(Variable.of("a", "x")).resolve("${a}/${missing}"));
    }

    @Test
    void allowsSpacesInsideBraces() {
        assertEquals("1", single(Variable.of("a", "1")).resolve("${ a }"));
    }

    @Test
    void resolvesNestedVariables() {
        VariableResolver resolver = single(Variable.of("url", "${host}/api"), Variable.of("host", "example.com"));
        assertEquals("example.com/api", resolver.resolve("${url}"));
    }

    @Test
    void cyclicVariableDoesNotLoopForever() {
        assertEquals("${a}", single(Variable.of("a", "${a}")).resolve("${a}"));
    }

    @Test
    void nullTextResolvesToEmpty() {
        assertEquals("", single().resolve(null));
    }

    @Test
    void oldDoubleBraceSyntaxIsPlainText() {
        assertEquals("{{a}}", single(Variable.of("a", "1")).resolve("{{a}}"));
    }

    @Test
    void dollarWithoutBracesIsPlainText() {
        assertEquals("$a and $", single(Variable.of("a", "1")).resolve("$a and $"));
    }

    @Test
    void scanReportsPositionsAndDefinedFlag() {
        List<VariableResolver.Reference> references = single(Variable.of("a", "1")).scan("${a}/${ b }");
        assertEquals(2, references.size());
        assertEquals(new VariableResolver.Reference(0, 4, "a", true), references.get(0));
        assertEquals(new VariableResolver.Reference(5, 11, "b", false), references.get(1));
    }

    @Test
    void namesListsEnabledVariables() {
        VariableResolver resolver = single(Variable.of("a", "1"), new Variable("b", "2", false, false));
        assertEquals(List.of("a"), resolver.names());
        assertTrue(resolver.value("a").isPresent());
    }
}