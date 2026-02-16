package com.automation.listeners;

import com.automation.models.HealingResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IReporter;
import org.testng.ISuite;
import org.testng.xml.XmlSuite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * TestNG {@link IReporter} that generates a dedicated AI Healing Report.
 * <p>
 * Generates:
 * <ul>
 *   <li>{@code target/surefire-reports/ai-healing-report.html} - Visual HTML report</li>
 *   <li>Console summary at suite end</li>
 * </ul>
 * <p>
 * The report categorizes tests as:
 * <ul>
 *   <li><b>Passed with Healing</b> - Test passed after AI healed a broken selector</li>
 *   <li><b>Failed (Healing Attempted)</b> - AI tried to heal but all suggestions failed</li>
 *   <li><b>Skipped (No Driver)</b> - Healing was skipped because WebDriver was inaccessible</li>
 * </ul>
 */
@Slf4j
public class HealingReportListener implements IReporter {

    private static final Logger AI_AUDIT = LogManager.getLogger("AIHealingAudit");

    @Override
    public void generateReport(List<XmlSuite> xmlSuites, List<ISuite> suites, String outputDirectory) {
        List<HealingResult> results = HealingContext.getResults();
        if (results.isEmpty()) {
            log.info("[HealingReport] No healing events recorded. Skipping report generation.");
            return;
        }

        printConsoleSummary(results);
        generateHtmlReport(results, outputDirectory);
    }

    // ------------------------------------------------------------------ console summary

    private void printConsoleSummary(List<HealingResult> results) {
        long healed = results.stream()
                .filter(r -> r.getOutcome() == HealingResult.HealingOutcome.HEALED
                          || r.getOutcome() == HealingResult.HealingOutcome.CACHE_HIT)
                .count();
        long failed = results.stream()
                .filter(r -> r.getOutcome() == HealingResult.HealingOutcome.FAILED)
                .count();
        long skipped = results.stream()
                .filter(r -> r.getOutcome() == HealingResult.HealingOutcome.SKIPPED)
                .count();

        String banner = String.join("\n",
                "",
                "╔══════════════════════════════════════════════════════════════╗",
                "║               AI SELF-HEALING REPORT SUMMARY                ║",
                "╠══════════════════════════════════════════════════════════════╣",
                String.format("║  Total Healing Events:    %-35d║", results.size()),
                String.format("║  Passed with Healing:     %-35d║", healed),
                String.format("║  Failed (Healing Tried):  %-35d║", failed),
                String.format("║  Skipped:                 %-35d║", skipped),
                "╚══════════════════════════════════════════════════════════════╝",
                ""
        );
        log.info(banner);
        AI_AUDIT.info(banner);

        for (HealingResult r : results) {
            String status = switch (r.getOutcome()) {
                case HEALED, CACHE_HIT -> "HEALED";
                case FAILED -> "FAILED";
                case SKIPPED -> "SKIPPED";
            };
            log.info("  [{}] {}#{} | {} -> {}",
                    status,
                    r.getTestClass(), r.getTestName(),
                    r.getOriginalSelector(),
                    r.getHealedSelector() != null ? r.getHealedSelector() : "(none)");
        }
    }

    // ------------------------------------------------------------------ HTML report

    private void generateHtmlReport(List<HealingResult> results, String outputDirectory) {
        try {
            Path dir = Paths.get(outputDirectory);
            Files.createDirectories(dir);
            Path reportFile = dir.resolve("ai-healing-report.html");

            String html = buildHtmlReport(results);
            Files.writeString(reportFile, html, StandardCharsets.UTF_8);
            log.info("[HealingReport] HTML report generated: {}", reportFile.toAbsolutePath());
        } catch (IOException e) {
            log.error("[HealingReport] Failed to generate HTML report", e);
        }
    }

    private String buildHtmlReport(List<HealingResult> results) {
        long healed = results.stream()
                .filter(r -> r.getOutcome() == HealingResult.HealingOutcome.HEALED
                          || r.getOutcome() == HealingResult.HealingOutcome.CACHE_HIT)
                .count();
        long failed = results.stream()
                .filter(r -> r.getOutcome() == HealingResult.HealingOutcome.FAILED)
                .count();
        long skipped = results.stream()
                .filter(r -> r.getOutcome() == HealingResult.HealingOutcome.SKIPPED)
                .count();

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>AI Self-Healing Report</title>\n");
        sb.append("<style>\n");
        sb.append(CSS);
        sb.append("</style>\n</head>\n<body>\n");

        // Header
        sb.append("<div class=\"header\">\n");
        sb.append("  <h1>AI Self-Healing Test Report</h1>\n");
        sb.append("  <p>Generated: ").append(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</p>\n");
        sb.append("</div>\n");

        // Summary cards
        sb.append("<div class=\"summary\">\n");
        sb.append(summaryCard("Total Events", results.size(), "#6366f1"));
        sb.append(summaryCard("Healed", healed, "#22c55e"));
        sb.append(summaryCard("Failed", failed, "#ef4444"));
        sb.append(summaryCard("Skipped", skipped, "#f59e0b"));
        sb.append("</div>\n");

        // Results table
        sb.append("<table>\n<thead>\n<tr>\n");
        sb.append("  <th>Status</th><th>Test Class</th><th>Test Method</th>");
        sb.append("<th>Original Selector</th><th>Healed Selector</th>");
        sb.append("<th>AI Provider</th><th>Duration (ms)</th><th>Suggestions</th>\n");
        sb.append("</tr>\n</thead>\n<tbody>\n");

        for (HealingResult r : results) {
            String badgeClass = switch (r.getOutcome()) {
                case HEALED, CACHE_HIT -> "badge-healed";
                case FAILED -> "badge-failed";
                case SKIPPED -> "badge-skipped";
            };
            String label = switch (r.getOutcome()) {
                case HEALED -> "PASSED WITH HEALING";
                case CACHE_HIT -> "CACHE HIT";
                case FAILED -> "FAILED";
                case SKIPPED -> "SKIPPED";
            };

            sb.append("<tr>\n");
            sb.append("  <td><span class=\"badge ").append(badgeClass).append("\">")
              .append(label).append("</span></td>\n");
            sb.append("  <td>").append(esc(r.getTestClass())).append("</td>\n");
            sb.append("  <td>").append(esc(r.getTestName())).append("</td>\n");
            sb.append("  <td><code>").append(esc(r.getOriginalSelector())).append("</code></td>\n");
            sb.append("  <td><code>").append(esc(r.getHealedSelector() != null
                    ? r.getHealedSelector() : "-")).append("</code></td>\n");
            sb.append("  <td>").append(esc(r.getAiProvider())).append("</td>\n");
            sb.append("  <td>").append(r.getHealingDurationMs()).append("</td>\n");
            sb.append("  <td>");
            if (r.getSuggestedSelectors() != null && !r.getSuggestedSelectors().isEmpty()) {
                sb.append("<ol class=\"suggestions\">");
                for (int i = 0; i < r.getSuggestedSelectors().size(); i++) {
                    String cls = (i + 1 == r.getWinningIndex()) ? " class=\"winner\"" : "";
                    sb.append("<li").append(cls).append("><code>")
                      .append(esc(r.getSuggestedSelectors().get(i)))
                      .append("</code></li>");
                }
                sb.append("</ol>");
            } else {
                sb.append("-");
            }
            sb.append("</td>\n");
            sb.append("</tr>\n");
        }

        sb.append("</tbody>\n</table>\n");
        sb.append("<div class=\"footer\">AI Self-Healing Test Engine v1.0</div>\n");
        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private String summaryCard(String title, long value, String color) {
        return String.format(
                "<div class=\"card\" style=\"border-top: 4px solid %s;\">" +
                "<div class=\"card-value\" style=\"color: %s;\">%d</div>" +
                "<div class=\"card-title\">%s</div></div>\n",
                color, color, value, title);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ------------------------------------------------------------------ CSS
    private static final String CSS = """
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Segoe UI', system-ui, -apple-system, sans-serif; background: #f1f5f9; color: #1e293b; padding: 2rem; }
        .header { text-align: center; margin-bottom: 2rem; }
        .header h1 { font-size: 1.75rem; color: #1e293b; }
        .header p { color: #64748b; margin-top: .25rem; }
        .summary { display: flex; gap: 1rem; justify-content: center; margin-bottom: 2rem; flex-wrap: wrap; }
        .card { background: #fff; border-radius: .75rem; padding: 1.25rem 2rem; text-align: center; min-width: 160px; box-shadow: 0 1px 3px rgba(0,0,0,.1); }
        .card-value { font-size: 2rem; font-weight: 700; }
        .card-title { font-size: .85rem; color: #64748b; margin-top: .25rem; }
        table { width: 100%; border-collapse: collapse; background: #fff; border-radius: .75rem; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,.1); }
        thead { background: #1e293b; color: #fff; }
        th { padding: .75rem 1rem; text-align: left; font-weight: 600; font-size: .85rem; }
        td { padding: .75rem 1rem; border-bottom: 1px solid #e2e8f0; font-size: .85rem; vertical-align: top; }
        tr:hover { background: #f8fafc; }
        code { background: #f1f5f9; padding: .15rem .4rem; border-radius: .25rem; font-size: .8rem; word-break: break-all; }
        .badge { padding: .25rem .6rem; border-radius: .375rem; font-size: .75rem; font-weight: 600; display: inline-block; }
        .badge-healed { background: #dcfce7; color: #166534; }
        .badge-failed { background: #fee2e2; color: #991b1b; }
        .badge-skipped { background: #fef3c7; color: #92400e; }
        .suggestions { margin: 0; padding-left: 1.2rem; }
        .suggestions li { margin-bottom: .15rem; }
        .suggestions .winner { font-weight: 700; }
        .suggestions .winner code { background: #dcfce7; }
        .footer { text-align: center; margin-top: 2rem; color: #94a3b8; font-size: .8rem; }
    """;
}
