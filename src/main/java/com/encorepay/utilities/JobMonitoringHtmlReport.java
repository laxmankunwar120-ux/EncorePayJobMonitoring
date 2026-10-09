package com.encorepay.utilities;

import com.encorepay.models.JobStatus;
import com.encorepay.models.ReportData;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public final class JobMonitoringHtmlReport {

    private static final String POST_RECEIPTS = "Post Receipts Job";
    private static final String COLLECTIONS = "Encore Download Collection Items Job";
    private static final String UPCOMING = "Encore Up Coming Demands Job";

    private static final String TITLE_POST_RECEIPTS = "1. Post Receipts Job";
    private static final String TITLE_COLLECTIONS = "2. Download Collection Items Job";
    private static final String TITLE_UPCOMING = "3. Upcoming Demand Job";

    private static final int[] WIDTHS_POST = {18, 14, 8, 10, 20, 30};
    private static final int[] WIDTHS_SIMPLE = {24, 22, 26, 28};

    private JobMonitoringHtmlReport() {}

    public static String generate(List<JobStatus> statuses) {
        return generateCombined(statuses, List.of(), List.of());
    }

    public static String generateCombined(List<JobStatus> statuses, List<String> clientFailures, List<String> configuredClients) {
        ReportData data = ReportData.from(statuses, clientFailures, configuredClients);

        StringBuilder html = baseHtml(data);


        if (!data.getPostReceipts().isEmpty()) {
            appendPostTable(html, TITLE_POST_RECEIPTS, data.getPostReceipts(), data.getFailedReceiptReasonsByClient());
        }

        if (!data.getCollectionJobs().isEmpty() && !data.getUpcomingJobs().isEmpty()) {
            html.append("<div class='side-by-side-row'>");
            html.append("<div class='side-by-side-col'>");
            appendSimpleTable(html, TITLE_COLLECTIONS, data.getCollectionJobs());
            html.append("</div>");
            html.append("<div class='side-by-side-col'>");
            appendSimpleTable(html, TITLE_UPCOMING, data.getUpcomingJobs());
            html.append("</div></div>");
        } else if (!data.getCollectionJobs().isEmpty()) {
            appendSimpleTable(html, TITLE_COLLECTIONS, data.getCollectionJobs());
        } else if (!data.getUpcomingJobs().isEmpty()) {
            appendSimpleTable(html, TITLE_UPCOMING, data.getUpcomingJobs());
        }

        appendMissingJobsSection(html, data);

        appendRunFailures(html, data.getClientExceptions());

        html.append("</div></body></html>");

        return write(html, "EncorePay_Multi_Client_Job_Monitoring_");
    }

    private static StringBuilder baseHtml(ReportData data) {
        SimpleDateFormat headerFormat = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
        headerFormat.setTimeZone(TimeZone.getTimeZone(new ConfigReader().getBusinessZone()));
        String timestamp = headerFormat.format(new Date());

        return new StringBuilder()
                .append("<!doctype html><html><head><meta charset='UTF-8'>")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
                .append("<title>EncorePay Job Monitoring Report</title>")
                .append("<style>")
                .append("body{font-family:'Segoe UI',Arial,sans-serif;margin:16px auto;max-width:1200px;color:#222;background:#fafbfc;padding:0 16px}")
                .append(".report-box{background:#fff;border:1px solid #d0d7de;border-radius:6px;padding:16px;box-shadow:0 1px 3px rgba(0,0,0,0.04)}")
                .append(".header-row{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;border-bottom:2px solid #003366;padding-bottom:8px;margin-bottom:10px;gap:12px;}")
                .append(".header-title{font-size:22px;font-weight:700;color:#003366;margin:0;flex:1 1 auto;min-width:0;}")
                .append(".header-meta{font-size:13px;color:#555;text-align:right;flex:0 0 auto;max-width:100%;word-break:break-word;}")
                .append("h2{font-size:15px;font-weight:600;background:#dce6f1;color:#1a365d;padding:6px 12px;margin-top:14px;margin-bottom:6px;border-left:4px solid #003366;border-radius:2px}")
                .append("*,*:before,*:after{box-sizing:border-box}")
                .append("table{border-collapse:collapse;width:100%;max-width:100%;table-layout:fixed;margin-bottom:10px;font-size:13px}")
                .append("th,td{border:1px solid #d0d7de;padding:5px 10px;vertical-align:middle;word-break:break-word;overflow-wrap:anywhere;min-width:0}")
                .append("th{background:#eaf2f8;color:#2d3748;font-weight:600;font-size:12px;letter-spacing:0.3px;text-align:center}")
                .append(".text-left{text-align:left}.text-center{text-align:center}")
                .append(".client-col{font-weight:600;color:#1a202c}")
                .append(".success,.completed{background:#e2f0d9;color:#006100;font-weight:bold;text-align:center;white-space:nowrap}")
                .append(".failed{background:#f4cccc;color:#9c0006;font-weight:bold;text-align:center;white-space:nowrap}")
                .append(".partial{background:#fff2cc;color:#7f6000;font-weight:bold;text-align:center;white-space:nowrap}")
                .append(".other{background:#fff2cc;color:#7f6000;font-weight:bold;text-align:center;white-space:nowrap}")
                .append(".na{background:#e8eaed;color:#5f6368;font-weight:bold;text-align:center;white-space:nowrap}")
                .append(".reason-text{color:#9c0006 !important;font-weight:600;word-break:break-word;line-height:1.4;font-size:12px}")
                .append(".reason-count{font-variant-numeric:tabular-nums;text-align:right;font-weight:600}")
                .append(".receipt-total{font-weight:700;text-align:right;background:#eaf2f8;color:#003366}")
                .append(".failure-text{word-break:break-word;line-height:1.4}")
                .append(".side-by-side-row{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:20px;align-items:start;width:100%;margin-bottom:10px}")
                .append(".side-by-side-col{min-width:0;width:auto}")
                .append(".side-by-side-col section{width:100%;min-width:0}")
                .append(".side-by-side-col table{width:100%;min-width:0}")
                .append(".side-by-side-col td.success,.side-by-side-col td.completed,.side-by-side-col td.failed,.side-by-side-col td.partial,.side-by-side-col td.other,.side-by-side-col td.na{white-space:normal;overflow-wrap:anywhere;font-size:12px;padding:7px 4px}")
                .append(".side-by-side-col td:nth-child(3){font-size:12px;line-height:1.35}")
                .append(".summary-box{margin:20px 0;padding:16px;background:#f8f9fa;border:1px solid #d0d7de;border-radius:6px}")
                .append("@media (max-width:850px){.side-by-side-row{grid-template-columns:minmax(0,1fr);gap:0}.side-by-side-col{width:100%}}")
                .append("</style></head><body>")
                .append("<div class='report-box'>")
                .append("<div class='header-row'>")
                .append("<h1 class='header-title'>EncorePay Job Monitoring Report</h1>")
                .append("<div class='header-meta'><strong>Generated:</strong> ").append(timestamp).append("</div>")
                .append("</div>");
    }

    private static String buildSummary(ReportData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='summary-box'>")
                .append("<strong>Summary</strong><br>")
                .append("Total Clients: ").append(data.getTotalClients()).append(" | ")
                .append("Monitored: ").append(data.getMonitoredClients()).append(" | ")
                .append("Successful Jobs: ").append(data.getSuccessfulJobs()).append(" | ")
                .append("Failed Jobs: ").append(data.getFailedJobs());
        if (data.getPartialJobs() > 0) {
            sb.append(" | Partial Success: ").append(data.getPartialJobs());
        }
        if (data.getAttentionJobs() > 0) {
            sb.append(" | Attention: ").append(data.getAttentionJobs());
        }
        sb.append(" | Total Failed Receipts: ").append(Math.max(0, data.getTotalFailedReceipts()));
        sb.append(" | Total Pending Receipts: ").append(Math.max(0, data.getTotalPendingReceipts()));
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildArtifactLink() {
        String server = System.getenv("GITHUB_SERVER_URL");
        String repository = System.getenv("GITHUB_REPOSITORY");
        String runId = System.getenv("GITHUB_RUN_ID");

        if (server == null || server.isBlank() || repository == null || repository.isBlank() || runId == null || runId.isBlank()) {
            return "";
        }
        String url = server.trim() + "/" + repository.trim() + "/actions/runs/" + runId.trim() + "#artifacts";
        return " <a href='" + url + "' target='_blank' style='color:#003366; text-decoration:underline; font-size:12px;'>[Download Report Artifacts]</a>";
    }

    private static void appendPostTable(StringBuilder html, String title, List<JobStatus> statuses, Map<String, Map<String, Integer>> reasonsByClient) {
        html.append("<section><h2>").append(escape(title)).append("</h2>")
                .append("<table><colgroup>");
        for (int width : WIDTHS_POST) {
            html.append("<col style='width:").append(width).append("%'>");
        }
        html.append("</colgroup><thead><tr>")
                .append("<th class='text-center'>Client</th>")
                .append("<th class='text-center'>Status</th>")
                .append("<th class='text-center'>Failed</th>")
                .append("<th class='text-center'>Pending</th>")
                .append("<th class='text-center'>Date & Time</th>")
                .append("<th class='text-center'>Failure Reason</th>")
                .append("</tr></thead><tbody>");

        for (JobStatus status : statuses) {
            String reason = failureReason(status);
            if (status.getJobName() != null && POST_RECEIPTS.equalsIgnoreCase(status.getJobName())) {
                Map<String, Integer> grouped = reasonsByClient.get(status.getClientName());
                if (grouped != null && !grouped.isEmpty()) {
                    reason = grouped.entrySet().stream()
                            .filter(e -> e.getKey() != null && e.getValue() != null && e.getValue() > 0)
                            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                                    .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
                            .map(e -> e.getKey() + " (" + e.getValue() + ")")
                            .reduce((a, b) -> a + "; " + b).orElse(reason);
                }
            }
            html.append("<tr>")
                    .append("<td class='text-left client-col'>").append(escape(status.getClientName())).append("</td>")
                    .append("<td class='").append(statusClass(status.getStatus())).append("'>").append(escape(status.getStatus())).append("</td>")
                    .append("<td class='text-center'>").append(displayCount(status.getFailedCount())).append("</td>")
                    .append("<td class='text-center'>").append(displayCount(status.getPendingCount())).append("</td>")
                    .append("<td class='text-center'>").append(escape(status.getDateTime())).append("</td>")
                    .append("<td class='text-left reason-text'>").append(escape(reason)).append("</td>")
                    .append("</tr>");
        }
        html.append("</tbody></table></section>");
    }

    private static void appendSimpleTable(StringBuilder html, String title, List<JobStatus> statuses) {
        html.append("<section><h2>").append(escape(title)).append("</h2>")
                .append("<table><colgroup>");
        for (int width : WIDTHS_SIMPLE) {
            html.append("<col style='width:").append(width).append("%'>");
        }
        html.append("</colgroup><thead><tr>")
                .append("<th class='text-center'>Client</th>")
                .append("<th class='text-center'>Status</th>")
                .append("<th class='text-center'>Date & Time</th>")
                .append("<th class='text-center'>Failure Reason</th>")
                .append("</tr></thead><tbody>");

        for (JobStatus status : statuses) {
            String reason = failureReason(status);
            html.append("<tr>")
                    .append("<td class='text-left client-col'>").append(escape(status.getClientName())).append("</td>")
                    .append("<td class='").append(statusClass(status.getStatus())).append("'>").append(escape(status.getStatus())).append("</td>")
                    .append("<td class='text-center'>").append(escape(status.getDateTime())).append("</td>")
                    .append("<td class='text-left failure-text'>").append(escape(reason)).append("</td>")
                    .append("</tr>");
        }
        html.append("</tbody></table></section>");
    }

    private static void appendMissingJobsSection(StringBuilder html, ReportData data) {
        Map<String, List<JobStatus>> grouped = groupByClient(data.getAllStatuses());

        for (Map.Entry<String, List<JobStatus>> entry : grouped.entrySet()) {
            List<String> missing = new ArrayList<>();
            if (findOptional(entry.getValue(), POST_RECEIPTS) == null) missing.add(POST_RECEIPTS);
            if (findOptional(entry.getValue(), COLLECTIONS) == null) missing.add(COLLECTIONS);
            if (!missing.isEmpty()) {
                appendFailure(html, entry.getKey(), "Missing monitoring data: " + String.join(", ", missing));
            }
        }
    }

    private static void appendRunFailures(StringBuilder html, List<ReportData.ClientException> exceptions) {
        if (exceptions.isEmpty()) return;

        
        
        html.append("<section><h2>Clients Requiring Attention</h2>")
                .append("<table><thead><tr><th>Client</th><th>Status</th><th>Reason</th></tr></thead><tbody>");

        for (ReportData.ClientException ex : exceptions) {
            String detail = ex.details.isEmpty() ? "No details available" : String.join("; ", ex.details);
            String label;
            String css;
            if (ex.state == ReportData.ClientState.NOT_RUN) {
                label = "UNMONITORED";
                css = "na";
            } else if (ex.state == ReportData.ClientState.FAILED) {
                label = "FAILED";
                css = "failed";
            } else {
                label = "ATTENTION";
                css = "other";
            }
            html.append("<tr>")
                    .append("<td class='client-col'>").append(escape(ex.client)).append("</td>")
                    .append("<td class='").append(css).append("'>").append(label).append("</td>")
                    .append("<td class='failure-text'>").append(escape(detail)).append("</td>")
                    .append("</tr>");
        }
        html.append("</tbody></table></section>");
    }

    private static void appendFailure(StringBuilder html, String client, String detail) {
        String clean = cleanReasonForDisplay(detail);
        html.append("<section><h2>Client Monitoring Error</h2>")
                .append("<table><thead><tr><th>Client</th><th>Status</th><th>Failure</th></tr></thead><tbody><tr>")
                .append("<td class='client-col'>").append(escape(client)).append("</td>")
                .append("<td class='na'>N/A</td>")
                .append("<td class='failure-text'>").append(escape(clean)).append("</td>")
                .append("</tr></tbody></table></section>");
    }

    private static Map<String, List<JobStatus>> groupByClient(List<JobStatus> statuses) {
        Map<String, List<JobStatus>> grouped = new LinkedHashMap<>();
        if (statuses == null) return grouped;
        for (JobStatus status : statuses) {
            if (status == null) continue;
            String client = status.getClientName() == null || status.getClientName().isBlank() ? "Unknown Client" : status.getClientName();
            grouped.computeIfAbsent(client, k -> new ArrayList<>()).add(status);
        }
        return grouped;
    }

    private static JobStatus findOptional(List<JobStatus> statuses, String name) {
        if (statuses == null) return null;
        return statuses.stream()
                .filter(x -> x != null && name.equalsIgnoreCase(x.getJobName()))
                .findFirst().orElse(null);
    }

    private static String failureReason(JobStatus status) {
        List<String> receiptReasons = status.getFailureReasons() == null ? List.of() : status.getFailureReasons();
        String receiptReasonText = String.join("; ", receiptReasons);
        String jobReason = status.getJobFailureReason() == null ? "" : status.getJobFailureReason().trim();
        String validation = status.getValidationMessage() == null ? "" : status.getValidationMessage().trim();

        if (!validation.isBlank() && !jobReason.isBlank()) jobReason = jobReason + " | " + validation;
        else if (!validation.isBlank()) jobReason = validation;

        if (!receiptReasonText.isBlank() && !jobReason.isBlank()) return receiptReasonText + " | " + jobReason;
        if (!receiptReasonText.isBlank()) return receiptReasonText;
        return jobReason;
    }

    private static String statusClass(String status) {
        if (status == null) return "other";
        String normalized = status.trim().toUpperCase(Locale.ROOT).replace(" ", "_");
        if ("N/A".equals(normalized)) return "na";
        if (normalized.equals("PARTIALLY_SUCCESSFUL")) return "partial";
        if (normalized.contains("SUCCESS") || normalized.contains("COMPLETED") || normalized.equals("SUCCEEDED") || normalized.equals("COMPLETED_SUCCESSFULLY")) return "success";
        if (normalized.contains("FAIL")) return "failed";
        return "other";
    }

    private static String cleanReasonForDisplay(String reason) {
        if (reason == null || reason.isBlank()) return "No details available";
        String clean = reason.replaceAll("\\s+", " ").trim();
        clean = clean.replaceAll("\\s*\\[step=[^\\]]*\\]", "");
        clean = clean.replaceAll("\\s*\\[url=[^\\]]*\\]", "");
        clean = clean.replaceAll("\\s*\\[title=[^\\]]*\\]", "");
        clean = clean.replaceAll("\\s*\\[readyState=[^\\]]*\\]", "");
        clean = clean.replaceAll("\\s*\\[screenshot=[^\\]]*\\]", "");
        clean = clean.trim();
        if (clean.length() > 180) clean = clean.substring(0, 177) + "...";
        return clean.isBlank() ? "No details available" : clean;
    }

    private static String displayCount(int count) {
        return count < 0 ? "N/A" : String.valueOf(count);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value
                .replace("&", "\u0026amp;")
                .replace("<", "\u0026lt;")
                .replace(">", "\u0026gt;")
                .replace("\"", "\u0026quot;")
                .replace("'", "\u0026#39;");
    }

    private static String write(StringBuilder html, String prefix) {
        try {
            File dir = new File(System.getProperty("user.dir"), "test-output/report");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Unable to create report directory.");
            File[] oldReports = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".html"));
            if (oldReports != null) for (File file : oldReports) file.delete();
            SimpleDateFormat stampFormat = new SimpleDateFormat("yyyy-MM-dd_HHmmss");
            stampFormat.setTimeZone(TimeZone.getTimeZone(new ConfigReader().getBusinessZone()));
            String stamp = stampFormat.format(new Date());
            File output = new File(dir, prefix + stamp + ".html");
            Files.writeString(output.toPath(), html.toString(), StandardCharsets.UTF_8);
            return output.getAbsolutePath();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate HTML report: " + e.getMessage(), e);
        }
    }

    public static void cleanReportsDirectory() {
        try {
            File dir = new File(System.getProperty("user.dir"), "test-output/report");
            if (dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> {
                    String lower = name.toLowerCase();
                    return lower.endsWith(".html") || lower.endsWith(".xlsx");
                });
                if (files != null) for (File file : files) file.delete();
            } else if (!dir.exists()) dir.mkdirs();
        } catch (Exception e) {
            System.out.println("Unable to clean reports directory: " + e.getMessage());
        }
    }
}
