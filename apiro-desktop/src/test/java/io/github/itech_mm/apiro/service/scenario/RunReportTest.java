package io.github.itech_mm.apiro.service.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itech_mm.apiro.model.RunRecord;
import io.github.itech_mm.apiro.model.RunStep;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunReportTest {

    private static RunRecord run(String state, List<RunStep> steps) {
        return new RunRecord("1", 1_700_000_000_000L, "Order <flow>", "staging", state, "", 2500, steps,
                "log & more");
    }

    private static RunStep step(String name, String state, int status, long millis, String message) {
        return new RunStep("id-" + name, name, state, status, millis, message, "");
    }

    @Test
    void junitCountsFailuresAndSkips() {
        RunRecord record = run("FAILED", List.of(step("Login", "PASSED", 200, 120, ""),
                step("Create", "FAILED", 500, 80, "Expected 201"), step("Get", "SKIPPED", 0, 0, ""),
                step("Stop", "CANCELLED", 0, 0, "")));

        String xml = RunReport.junitXml(record);

        assertTrue(xml.contains("<testsuite name=\"Order &lt;flow&gt;\" tests=\"4\" failures=\"1\" errors=\"0\" skipped=\"2\" time=\"2.500\""));
        assertTrue(xml.contains("<failure message=\"Expected 201\" type=\"StepFailure\">"));
        assertTrue(xml.contains("<testcase classname=\"Order &lt;flow&gt;\" name=\"Login\" time=\"0.120\"/>"));
        assertTrue(xml.contains("<skipped message=\"Cancelled\"/>"));
        assertTrue(xml.contains("<property name=\"environment\" value=\"staging\"/>"));
        assertTrue(xml.contains("<system-out>log &amp; more</system-out>"));
    }

    @Test
    void junitReportsAScenarioThatNeverStartedAsAnError() {
        RunRecord record = new RunRecord("2", 0, "Empty", "", "INVALID", "Step 1 has no request", 0, List.of(), "");

        String xml = RunReport.junitXml(record);

        assertTrue(xml.contains("tests=\"1\" failures=\"0\" errors=\"1\""));
        assertTrue(xml.contains("<error message=\"Step 1 has no request\" type=\"ScenarioError\">"));
    }

    @Test
    void cancelledRunWithoutStepsIsNotAnError() {
        String xml = RunReport.junitXml(run("CANCELLED", List.of()));

        assertTrue(xml.contains("tests=\"0\" failures=\"0\" errors=\"0\""));
    }

    @Test
    void controlCharactersAreDroppedFromXml() {
        assertEquals("ab", RunReport.escape("a\u0000b"));
        assertEquals("a&amp;&lt;&gt;&quot;&#39;", RunReport.escape("a&<>\"'"));
        assertEquals("line\nnext\t", RunReport.escape("line\nnext\t"));
    }

    @Test
    void htmlEscapesContentAndShowsTotals() {
        RunRecord record = run("FAILED", List.of(step("<script>x</script>", "FAILED", 404, 10, "not found"),
                step("Ok", "PASSED", 200, 5, "")));

        String html = RunReport.html(record, ZoneOffset.UTC);

        assertFalse(html.contains("<script>x</script>"));
        assertTrue(html.contains("&lt;script&gt;x&lt;/script&gt;"));
        assertTrue(html.contains("<b>2</b><span>steps</span>"));
        assertTrue(html.contains("<div class=\"passed\"><b>1</b>"));
        assertTrue(html.contains("<div class=\"failed\"><b>1</b>"));
        assertTrue(html.contains("<td>404</td>"));
        assertTrue(html.contains("2023-11-14"));
    }

    @Test
    void fileNamesAreSafe() {
        RunRecord record = run("PASSED", List.of());

        assertEquals("Order-flow-20231114-221320.xml", RunReport.fileName(record, "xml"));
        RunRecord odd = new RunRecord("3", 0, "///", "", "PASSED", "", 0, List.of(), "");
        assertEquals("scenario-19700101-000000.html", RunReport.fileName(odd, "html"));
    }
}
