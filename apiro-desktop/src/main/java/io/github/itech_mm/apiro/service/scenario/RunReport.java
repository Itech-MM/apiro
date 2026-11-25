package io.github.itech_mm.apiro.service.scenario;

import io.github.itech_mm.apiro.model.RunRecord;
import io.github.itech_mm.apiro.model.RunStep;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class RunReport {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private RunReport() {
    }

    public static String junitXml(RunRecord run) {
        int failures = 0;
        int skipped = 0;
        int errors = 0;
        int tests = run.steps().size();
        for (RunStep step : run.steps()) {
            switch (step.state()) {
                case "PASSED" -> {
                }
                case "FAILED" -> failures++;
                default -> skipped++;
            }
        }
        boolean wholeRunFailed = run.steps().isEmpty() && !run.state().equals("PASSED")
                && !run.state().equals("CANCELLED");
        if (wholeRunFailed) {
            tests = 1;
            errors = 1;
        }
        String name = run.scenarioName().isBlank() ? "scenario" : run.scenarioName();
        String time = seconds(run.millis());
        String stamp = ISO.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(run.startedAt()), ZoneOffset.UTC)
                .withNano(0));
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testsuites name=\"").append(escape(name)).append("\" tests=\"").append(tests)
                .append("\" failures=\"").append(failures).append("\" errors=\"").append(errors)
                .append("\" skipped=\"").append(skipped).append("\" time=\"").append(time).append("\">\n");
        xml.append("  <testsuite name=\"").append(escape(name)).append("\" tests=\"").append(tests)
                .append("\" failures=\"").append(failures).append("\" errors=\"").append(errors)
                .append("\" skipped=\"").append(skipped).append("\" time=\"").append(time)
                .append("\" timestamp=\"").append(stamp).append("\">\n");
        if (!run.environmentName().isBlank()) {
            xml.append("    <properties>\n      <property name=\"environment\" value=\"")
                    .append(escape(run.environmentName())).append("\"/>\n    </properties>\n");
        }
        for (RunStep step : run.steps()) {
            String label = step.name().isBlank() ? step.stepId() : step.name();
            xml.append("    <testcase classname=\"").append(escape(name)).append("\" name=\"").append(escape(label))
                    .append("\" time=\"").append(seconds(step.millis())).append("\"");
            String body = step.detail().isBlank() ? step.message() : step.detail();
            switch (step.state()) {
                case "PASSED" -> xml.append("/>\n");
                case "FAILED" -> xml.append(">\n      <failure message=\"")
                        .append(escape(step.message().isBlank() ? "Step failed" : step.message()))
                        .append("\" type=\"StepFailure\">").append(escape(body)).append("</failure>\n    </testcase>\n");
                case "CANCELLED" -> xml.append(">\n      <skipped message=\"Cancelled\"/>\n    </testcase>\n");
                default -> xml.append(">\n      <skipped message=\"").append(escape(
                        step.message().isBlank() ? "Not run" : step.message())).append("\"/>\n    </testcase>\n");
            }
        }
        if (wholeRunFailed) {
            xml.append("    <testcase classname=\"").append(escape(name)).append("\" name=\"").append(escape(name))
                    .append("\" time=\"").append(time).append("\">\n      <error message=\"")
                    .append(escape(run.message().isBlank() ? "Scenario could not run" : run.message()))
                    .append("\" type=\"ScenarioError\">").append(escape(run.log())).append("</error>\n    </testcase>\n");
        }
        xml.append("    <system-out>").append(escape(run.log())).append("</system-out>\n");
        xml.append("  </testsuite>\n</testsuites>\n");
        return xml.toString();
    }

    public static String html(RunRecord run, ZoneId zone) {
        long passed = run.count("PASSED");
        long failed = run.count("FAILED");
        long other = run.steps().size() - passed - failed;
        String name = run.scenarioName().isBlank() ? "Scenario" : run.scenarioName();
        String verdict = run.state().toLowerCase(Locale.ROOT);
        StringBuilder page = new StringBuilder("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n");
        page.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n<title>")
                .append(escape(name)).append(" run report</title>\n<style>\n").append(STYLE).append("</style>\n</head>\n<body>\n");
        page.append("<header>\n<h1>").append(escape(name)).append("</h1>\n<span class=\"badge ")
                .append(stateClass(run.state())).append("\">").append(escape(verdict)).append("</span>\n</header>\n");
        page.append("<p class=\"meta\">").append(escape(RunRecords.when(run, zone)));
        if (!run.environmentName().isBlank()) {
            page.append(" &middot; environment ").append(escape(run.environmentName()));
        }
        page.append(" &middot; ").append(escape(StepSummary.duration(run.millis()))).append("</p>\n");
        if (!run.message().isBlank()) {
            page.append("<p class=\"note\">").append(escape(run.message())).append("</p>\n");
        }
        page.append("<section class=\"totals\">\n<div><b>").append(run.steps().size()).append("</b><span>steps</span></div>\n")
                .append("<div class=\"passed\"><b>").append(passed).append("</b><span>passed</span></div>\n")
                .append("<div class=\"failed\"><b>").append(failed).append("</b><span>failed</span></div>\n")
                .append("<div><b>").append(other).append("</b><span>skipped</span></div>\n</section>\n");
        page.append("<table>\n<thead><tr><th>#</th><th>Step</th><th>Result</th><th>HTTP</th><th>Time</th><th>Details</th></tr></thead>\n<tbody>\n");
        int index = 1;
        for (RunStep step : run.steps()) {
            String label = step.name().isBlank() ? step.stepId() : step.name();
            String detail = step.detail().isBlank() ? step.message() : step.detail();
            page.append("<tr class=\"").append(stateClass(step.state())).append("\"><td>").append(index++)
                    .append("</td><td>").append(escape(label)).append("</td><td><span class=\"badge ")
                    .append(stateClass(step.state())).append("\">").append(escape(step.state().toLowerCase(Locale.ROOT)))
                    .append("</span></td><td>").append(step.status() > 0 ? String.valueOf(step.status()) : "")
                    .append("</td><td>").append(escape(StepSummary.duration(step.millis()))).append("</td><td><pre>")
                    .append(escape(detail)).append("</pre></td></tr>\n");
        }
        page.append("</tbody>\n</table>\n<h2>Log</h2>\n<pre class=\"log\">").append(escape(run.log()))
                .append("</pre>\n</body>\n</html>\n");
        return page.toString();
    }

    public static String fileName(RunRecord run, String extension) {
        String base = run.scenarioName().isBlank() ? "scenario" : run.scenarioName();
        String safe = base.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        if (safe.isEmpty()) {
            safe = "scenario";
        }
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
                .withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(run.startedAt()));
        return safe + "-" + stamp + "." + extension;
    }

    static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r' || c == 0xFFFE || c == 0xFFFF) {
                continue;
            }
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.3f", millis / 1000.0);
    }

    private static String stateClass(String state) {
        String lower = state.toLowerCase(Locale.ROOT);
        return lower.equals("passed") || lower.equals("failed") ? lower : "other";
    }

    private static final String STYLE = """
            :root { color-scheme: light dark; --bg: #ffffff; --fg: #1f2328; --muted: #656d76; --line: #d0d7de; --ok: #1a7f37; --bad: #cf222e; --other: #9a6700; --soft: #f6f8fa; }
            @media (prefers-color-scheme: dark) { :root { --bg: #1e1f1c; --fg: #e6e6e0; --muted: #9a9a90; --line: #3a3b36; --ok: #a6e22e; --bad: #f92672; --other: #e6db74; --soft: #272822; } }
            body { margin: 0; padding: 20px 24px; background: var(--bg); color: var(--fg); font: 14px/1.45 system-ui, sans-serif; }
            header { display: flex; align-items: center; gap: 10px; }
            h1 { font-size: 20px; margin: 0; }
            h2 { font-size: 15px; margin: 24px 0 8px; }
            .meta { color: var(--muted); margin: 4px 0 12px; }
            .note { margin: 0 0 12px; }
            .badge { display: inline-block; padding: 1px 8px; border-radius: 10px; font-size: 12px; border: 1px solid var(--line); }
            .badge.passed { color: var(--ok); border-color: var(--ok); }
            .badge.failed { color: var(--bad); border-color: var(--bad); }
            .badge.other { color: var(--other); border-color: var(--other); }
            .totals { display: flex; gap: 12px; margin-bottom: 16px; }
            .totals div { border: 1px solid var(--line); border-radius: 6px; padding: 6px 14px; background: var(--soft); }
            .totals b { display: block; font-size: 18px; }
            .totals span { color: var(--muted); font-size: 12px; }
            .totals .passed b { color: var(--ok); }
            .totals .failed b { color: var(--bad); }
            table { border-collapse: collapse; width: 100%; }
            th, td { text-align: left; vertical-align: top; padding: 5px 10px; border-bottom: 1px solid var(--line); }
            th { color: var(--muted); font-weight: 600; font-size: 12px; }
            pre { margin: 0; white-space: pre-wrap; word-break: break-word; font: 12px/1.4 ui-monospace, Consolas, monospace; }
            pre.log { background: var(--soft); border: 1px solid var(--line); border-radius: 6px; padding: 10px; }
            tr.failed td:first-child { box-shadow: inset 3px 0 var(--bad); }
            """;
}
