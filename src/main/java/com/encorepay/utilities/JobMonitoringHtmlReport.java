package com.encorepay.utilities;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.encorepay.models.JobStatus;

public final class JobMonitoringHtmlReport {

    private static final String JOB_POST_RECEIPTS = "Post Receipts Job";
    private static final String JOB_COLLECTION_ITEMS = "Encore Download Collection Items Job";
    private static final String JOB_UPCOMING_DEMAND = "Encore Up Coming Demands Job";

    private static final String TITLE_POST_RECEIPTS = "1. Post Receipt Job";
    private static final String TITLE_COLLECTION_ITEMS = "2. Download Collection Item Job";
    private static final String TITLE_UPCOMING_DEMAND = "3. Upcoming Demand Job";

    private static final int[] WIDTHS_COUNTS_REASON = {18, 21, 8, 13, 17, 23};
    private static final int[] WIDTHS_COUNTS_ONLY = {28, 16, 12, 20, 24};
    private static final int[] WIDTHS_REASON_ONLY = {28, 16, 24, 32};
    private static final int[] WIDTHS_PLAIN = {38, 24, 38};

    private JobMonitoringHtmlReport() {
    }

    public static String generate(List<JobStatus> statuses) {
        JobStatus post = find(statuses, JOB_POST_RECEIPTS);
        JobStatus collection = find(statuses, JOB_COLLECTION_ITEMS);
        JobStatus upcoming = findOptional(statuses, JOB_UPCOMING_DEMAND);

        StringBuilder html = baseHtml();

        appendPostRows(html, TITLE_POST_RECEIPTS, List.of(post));

        if (upcoming != null) {
            html.append("<div class='side-by-side-row'>")
                    .append("<div class='side-by-side-col'>");
            appendSimpleRows(html, TITLE_COLLECTION_ITEMS, List.of(collection));
            html.append("</div>")
                    .append("<div class='side-by-side-col'>");
            appendSimpleRows(html, TITLE_UPCOMING_DEMAND, List.of(upcoming));
            html.append("</div></div>");
        } else {
            appendSimpleRows(html, TITLE_COLLECTION_ITEMS, List.of(collection));
        }

        html.append("</div></body></html>");
        return write(html, "EncorePay_Job_Monitoring_");
    }

    public static String generateCombined(List<JobStatus> statuses, List<String> failures) {
        StringBuilder html = baseHtml();

        int totalClients = (int) statuses.stream()
                .map(JobStatus::getClientName)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .count();
        int unmonitored = failures == null ? 0 : failures.size();
        int monitored = Math.max(0, totalClients - unmonitored);
        long successful = statuses.stream()
                .filter(s -> s != null && isSuccessful(s.getStatus()))
                .count();
        long failed = statuses.stream()
                .filter(s -> s != null && isFailed(s.getStatus()))
                .count();

        html.append(buildSummary(totalClients, monitored, successful, failed, unmonitored));

        Map<String, List<JobStatus>> grouped = groupByClient(statuses);

        addUnmonitoredClients(grouped, failures);

        List<JobStatus> posts = new ArrayList<>();
        List<JobStatus> collections = new ArrayList<>();
        List<JobStatus> upcomings = new ArrayList<>();

        for (List<JobStatus> clientStatuses : grouped.values()) {
            JobStatus post = findOptional(clientStatuses, JOB_POST_RECEIPTS);
            JobStatus collection = findOptional(clientStatuses, JOB_COLLECTION_ITEMS);
            JobStatus upcoming = findOptional(clientStatuses, JOB_UPCOMING_DEMAND);

            if (post != null) {
                posts.add(post);
            }
            if (collection != null) {
                collections.add(collection);
            }
            if (upcoming != null) {
                upcomings.add(upcoming);
            }
        }

        if (!posts.isEmpty()) {
            appendPostRows(html, TITLE_POST_RECEIPTS, posts);
        }

        if (!collections.isEmpty() && !upcomings.isEmpty()) {
            html.append("<div class='side-by-side-row'>");
            html.append("<div class='side-by-side-col'>");
            appendSimpleRows(html, TITLE_COLLECTION_ITEMS, collections);
            html.append("</div>");
            html.append("<div class='side-by-side-col'>");
            appendSimpleRows(html, TITLE_UPCOMING_DEMAND, upcomings);
            html.append("</div>");
            html.append("</div>");
        } else if (!collections.isEmpty()) {
            appendSimpleRows(html, TITLE_COLLECTION_ITEMS, collections);
        } else if (!upcomings.isEmpty()) {
            appendSimpleRows(html, TITLE_UPCOMING_DEMAND, upcomings);
        }

        for (Map.Entry<String, List<JobStatus>> entry : grouped.entrySet()) {
            List<String> missing = new ArrayList<>();
            if (findOptional(entry.getValue(), JOB_POST_RECEIPTS) == null) {
                missing.add(JOB_POST_RECEIPTS);
            }
            if (findOptional(entry.getValue(), JOB_COLLECTION_ITEMS) == null) {
                missing.add(JOB_COLLECTION_ITEMS);
            }
            if (!missing.isEmpty()) {
                appendFailure(html, entry.getKey(), "Missing monitoring data: " + String.join(", ", missing));
            }
        }

        if (failures != null && !failures.isEmpty()) {
            appendRunFailures(html, failures);
        }

        html.append("</div></body></html>");

        return write(html, "EncorePay_Multi_Client_Job_Monitoring_");
    }

    private static void appendRunFailures(StringBuilder html, List<String> failures) {
        html.append("<section>")
                .append("<h2>Unmonitored Clients</h2>")
                .append("<table><thead><tr>")
                .append("<th>Client</th>")
                .append("<th>Status</th>")
                .append("<th>Reason</th>")
                .append("</tr></thead><tbody>");

        for (String failure : failures) {
            int separator = failure.indexOf(" :: ");
            String client = separator > 0 ? failure.substring(0, separator) : "Unknown Client";
            String detail = separator > 0 ? failure.substring(separator + 4).trim() : failure;

            html.append("<tr>")
                    .append("<td class='client-col'>").append(escape(client)).append("</td>")
                    .append("<td class='na'>UNMONITORED</td>")
                    .append("<td class='failure-text'>").append(escape(detail)).append("</td>")
                    .append("</tr>");
        }

        html.append("</tbody></table></section>");
    }

    private static void addUnmonitoredClients(
            Map<String, List<JobStatus>> grouped,
            List<String> failures) {

        if (failures == null || failures.isEmpty()) {
            return;
        }

        for (String failure : failures) {
            int separator = failure.indexOf(" :: ");
            if (separator <= 0) {
                continue;
            }

            String client = failure.substring(0, separator).trim();
            if (client.isEmpty() || grouped.containsKey(client)) {
                continue;
            }

            String detail = failure.substring(separator + 4).trim();
            detail = cleanReasonForDisplay(detail);

            List<JobStatus> placeholders = new ArrayList<>();
            placeholders.add(
                    placeholder(client, "Post Receipts Job", detail));
            placeholders.add(
                    placeholder(client, "Encore Download Collection Items Job", detail));
            grouped.put(client, placeholders);
        }
    }

    private static JobStatus placeholder(String client, String jobName, String detail) {
        JobStatus status = new JobStatus();
        status.setClientName(client);
        status.setJobName(jobName);
        status.setStatus("N/A");
        status.setDateTime("N/A");
        status.setJobFailureReason(detail);
        return status;
    }

    private static StringBuilder baseHtml() {
        SimpleDateFormat headerFormat = new SimpleDateFormat(
                        "dd-MMM-yyyy HH:mm:ss"
                );
                headerFormat.setTimeZone(
                        TimeZone.getTimeZone(new ConfigReader().getBusinessZone()));
        String timestamp = headerFormat.format(new Date());

        return new StringBuilder()
                .append("<!doctype html><html><head><meta charset='UTF-8'>")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
                .append("<title>EncorePay Job Monitoring Report</title>")
                .append("<style>")

                .append("body{")
                .append("font-family:'Segoe UI',Arial,sans-serif;")
                .append("margin:32px auto;")
                .append("max-width:1200px;")
                .append("color:#222;")
                .append("background:#fafbfc;")
                .append("padding:0 16px")
                .append("}")
                .append(".report-box{")
                .append("background:#fff;")
                .append("border:1px solid #d0d7de;")
                .append("border-radius:6px;")
                .append("padding:24px;")
                .append("box-shadow:0 1px 3px rgba(0,0,0,0.04)")
                .append("}")
                .append(".header-row{")
                .append("display:flex;")
                .append("justify-content:space-between;")
                .append("align-items:center;")
                .append("flex-wrap:wrap;")
                .append("border-bottom:2px solid #003366;")
                .append("padding-bottom:14px;")
                .append("margin-bottom:20px;")
                .append("gap:12px;")
                .append("}")
                .append(".header-title{")
                .append("font-size:22px;")
                .append("font-weight:700;")
                .append("color:#003366;")
                .append("margin:0;")
                .append("flex:1 1 auto;")
                .append("min-width:0;")
                .append("}")
                .append(".header-meta{")
                .append("font-size:13px;")
                .append("color:#555;")
                .append("text-align:right;")
                .append("flex:0 0 auto;")
                .append("max-width:100%;")
                .append("word-break:break-word;")
                .append("}")
                .append("h2{")
                .append("font-size:15px;")
                .append("font-weight:600;")
                .append("background:#dce6f1;")
                .append("color:#1a365d;")
                .append("padding:9px 14px;")
                .append("margin-top:24px;")
                .append("margin-bottom:10px;")
                .append("border-left:4px solid #003366;")
                .append("border-radius:2px")
                .append("}")
                .append("table{")
                .append("border-collapse:collapse;")
                .append("width:100%;")
                .append("table-layout:fixed;")
                .append("margin-bottom:20px;")
                .append("font-size:13px")
                .append("}")
                .append("th,td{")
                .append("border:1px solid #d0d7de;")
                .append("padding:9px 12px;")
                .append("vertical-align:middle;")
                .append("word-break:break-word")
                .append("}")
                .append("th{")
                .append("background:#eaf2f8;")
                .append("color:#2d3748;")
                .append("font-weight:600;")
                .append("font-size:12px;")
                .append("letter-spacing:0.3px;")
                .append("text-align:center")
                .append("}")
                .append(".text-left{text-align:left}")
                .append(".text-center{text-align:center}")
                .append(".client-col{")
                .append("font-weight:600;")
                .append("color:#1a202c")
                .append("}")
                .append(".success,.completed{")
                .append("background:#e2f0d9;")
                .append("color:#006100;")
                .append("font-weight:bold;")
                .append("text-align:center;")
                .append("white-space:nowrap")
                .append("}")
                .append(".failed{")
                .append("background:#f4cccc;")
                .append("color:#9c0006;")
                .append("font-weight:bold;")
                .append("text-align:center;")
                .append("white-space:nowrap")
                .append("}")
                .append(".other{")
                .append("background:#fff2cc;")
                .append("color:#7f6000;")
                .append("font-weight:bold;")
                .append("text-align:center;")
                .append("white-space:nowrap")
                .append("}")
                .append(".na{")
                .append("background:#e8eaed;")
                .append("color:#5f6368;")
                .append("font-weight:bold;")
                .append("text-align:center;")
                .append("white-space:nowrap")
                .append("}")
                .append(".reason-text{")
                .append("color:#9c0006;")
                .append("word-break:break-word;")
                .append("line-height:1.4;")
                .append("font-size:12px")
                .append("}")
                .append(".failure-text{")
                .append("word-break:break-word;")
                .append("line-height:1.4")
                .append("}")
                .append(".side-by-side-row{")
                .append("display:flex;")
                .append("gap:20px;")
                .append("align-items:flex-start;")
                .append("width:100%;")
                .append("margin-bottom:10px")
                .append("}")
                .append(".side-by-side-col{")
                .append("flex:1 1 0;")
                .append("min-width:0;")
                .append("width:50%")
                .append("}")
                .append(".side-by-side-col section{")
                .append("width:100%;")
                .append("}")
                .append("@media (max-width:850px){")
                .append(".side-by-side-row{")
                .append("flex-direction:column;")
                .append("gap:0")
                .append("}")
                .append(".side-by-side-col{")
                .append("width:100%")
                .append("}")
                .append("}")
                .append("</style></head><body>")
                .append("<div class='report-box'>")
                .append("<div class='header-row'>")
                .append("<h1 class='header-title'>EncorePay Job Monitoring Report</h1>")
                .append("<div class='header-meta'>")
                .append("<strong>Generated:</strong> ")
                .append(timestamp)
                .append(buildArtifactLink())
                .append("</div>")
                .append("</div>");
    }

    private static String buildSummary(int totalClients, int monitored, long successful, long failed, int unmonitored) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='summary-box' style='margin:20px 0; padding:16px; background:#f8f9fa; border:1px solid #d0d7de; border-radius:6px;'>")
                .append("<strong>Summary</strong><br>")
                .append("Total Clients: ").append(totalClients).append(" | ")
                .append("Monitored: ").append(monitored).append(" | ")
                .append("Successful: ").append(successful).append(" | ")
                .append("Failed: ").append(failed);
        if (unmonitored > 0) {
            sb.append(" | Unmonitored: ").append(unmonitored);
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String buildArtifactLink() {
        String server = System.getenv("GITHUB_SERVER_URL");
        String repository = System.getenv("GITHUB_REPOSITORY");
        String runId = System.getenv("GITHUB_RUN_ID");

        if (server == null || server.isBlank()
                || repository == null || repository.isBlank()
                || runId == null || runId.isBlank()) {
            return "";
        }

        String url = server.trim() + "/" + repository.trim() + "/actions/runs/" + runId.trim() + "#artifacts";
        return " <a href='" + url + "' target='_blank' style='color:#003366; text-decoration:underline; font-size:12px;'>[Download Report Artifacts]</a>";
    }

    private static void appendPostRows(StringBuilder html, String title, List<JobStatus> statuses) {
        appendTable(html, title, statuses, true);
    }

    private static void appendSimpleRows(StringBuilder html, String title, List<JobStatus> statuses) {
        appendTable(html, title, statuses, false);
    }

    private static void appendTable(
            StringBuilder html,
            String title,
            List<JobStatus> statuses,
            boolean includeCounts) {

        List<String> reasons = new ArrayList<>();
        for (JobStatus status : statuses) {
            reasons.add(failureReason(status));
        }

        boolean hasFailureReason = reasons.stream().anyMatch(r -> !r.isBlank());

        int[] widths = includeCounts
                ? (hasFailureReason ? WIDTHS_COUNTS_REASON : WIDTHS_COUNTS_ONLY)
                : (hasFailureReason ? WIDTHS_REASON_ONLY : WIDTHS_PLAIN);

        html.append("<section>")
                .append("<h2>")
                .append(escape(title))
                .append("</h2>")
                .append("<table><colgroup>");

        for (int width : widths) {
            html.append("<col style='width:").append(width).append("%'>");
        }

        html.append("</colgroup><thead><tr>")
                .append("<th class='text-left'>Client</th>")
                .append("<th class='text-center'>Status</th>");

        if (includeCounts) {
            html.append("<th class='text-center'>Failed</th>")
                    .append("<th class='text-center'>Pending to Be Posted</th>");
        }

        html.append("<th class='text-center'>Date &amp; Time</th>");

        if (hasFailureReason) {
            html.append("<th class='text-left'>Failure Reason</th>");
        }

        html.append("</tr></thead><tbody>");

        for (int i = 0; i < statuses.size(); i++) {
            JobStatus status = statuses.get(i);

            html.append("<tr>")
                    .append("<td class='text-left client-col'>")
                    .append(escape(status.getClientName()))
                    .append("</td>")
                    .append("<td class='").append(statusClass(status.getStatus())).append("'>")
                    .append(escape(status.getStatus()))
                    .append("</td>");

            if (includeCounts) {
                html.append("<td class='text-center'>")
                        .append(status.getFailedCount())
                        .append("</td>")
                        .append("<td class='text-center'>")
                        .append(status.getPendingCount())
                        .append("</td>");
            }

            html.append("<td class='text-center'>")
                    .append(escape(status.getDateTime()))
                    .append("</td>");

            if (hasFailureReason) {
                html.append("<td class='text-left reason-text'>")
                        .append(escape(reasons.get(i)))
                        .append("</td>");
            }

            html.append("</tr>");
        }

        html.append("</tbody></table></section>");
    }

    private static String failureReason(JobStatus status) {
        List<String> receiptReasons = status.getFailureReasons() == null
                ? List.of()
                : status.getFailureReasons();

        String receiptReasonText = String.join("; ", receiptReasons);
        String jobReason = status.getJobFailureReason() == null
                ? ""
                : status.getJobFailureReason().trim();

        String validation = status.getValidationMessage() == null
                ? ""
                : status.getValidationMessage().trim();

        if (!validation.isBlank() && !jobReason.isBlank()) {
            jobReason = jobReason + " | " + validation;
        } else if (!validation.isBlank()) {
            jobReason = validation;
        }

        if (!receiptReasonText.isBlank() && !jobReason.isBlank()) {
            return receiptReasonText + " | " + jobReason;
        }

        if (!receiptReasonText.isBlank()) {
            return receiptReasonText;
        }

        return jobReason;
    }

    private static void appendFailure(StringBuilder html, String client, String detail) {
        String cleanDetail = cleanReasonForDisplay(detail);
        html.append("<section>")
                .append("<h2>Client Monitoring Error</h2>")
                .append("<table><thead><tr>")
                .append("<th>Client</th>")
                .append("<th>Status</th>")
                .append("<th>Failure</th>")
                .append("</tr></thead><tbody><tr>")
                .append("<td class='client-col'>").append(escape(client)).append("</td>")
                .append("<td class='na'>N/A</td>")
                .append("<td class='failure-text'>").append(escape(cleanDetail)).append("</td>")
                .append("</tr></tbody></table>")
                .append("</section>");
    }

    private static Map<String, List<JobStatus>> groupByClient(List<JobStatus> statuses) {
        Map<String, List<JobStatus>> grouped = new LinkedHashMap<>();

        if (statuses == null) {
            return grouped;
        }

        for (JobStatus status : statuses) {
            if (status == null) {
                continue;
            }

            String client = status.getClientName() == null || status.getClientName().isBlank()
                    ? "Unknown Client"
                    : status.getClientName();

            grouped.computeIfAbsent(client, key -> new ArrayList<>()).add(status);
        }

        return grouped;
    }

    private static JobStatus find(List<JobStatus> statuses, String name) {
        JobStatus result = findOptional(statuses, name);

        if (result == null) {
            throw new IllegalStateException("Missing required job: " + name);
        }

        return result;
    }

    private static JobStatus findOptional(List<JobStatus> statuses, String name) {
        if (statuses == null) {
            return null;
        }

        return statuses.stream()
                .filter(x -> x != null && name.equalsIgnoreCase(x.getJobName()))
                .findFirst()
                .orElse(null);
    }

    private static String statusClass(String status) {
        if (status == null) {
            return "other";
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);

        if ("N/A".equals(normalized)) {
            return "na";
        }

        if (normalized.contains("SUCCESS")
                || normalized.contains("COMPLETED")
                || normalized.equals("SUCCEEDED")
                || normalized.equals("COMPLETED_SUCCESSFULLY")) {
            return "success";
        }

        if (normalized.contains("FAIL")) {
            return "failed";
        }

        return "other";
    }

    private static String cleanReasonForDisplay(String reason) {
        if (reason == null || reason.isBlank()) {
            return "No details available";
        }
        String clean = reason.replaceAll("\\s+", " ").trim();

        clean = clean.replaceAll("\\s*\\[step=[^\\]]*\\]", "");
        clean = clean.replaceAll("\\s*\\[url=[^\\]]*\\]", "");
        clean = clean.replaceAll("\\s*\\[title=[^\\]]*\\]", "");
        clean = clean.replaceAll("\\s*\\[readyState=[^\\]]*\\]", "");
        clean = clean.replaceAll("\\s*\\[screenshot=[^\\]]*\\]", "");
        clean = clean.trim();

        if (clean.length() > 180) {
            clean = clean.substring(0, 177) + "...";
        }
        return clean.isBlank() ? "No details available" : clean;
    }

    public static void cleanReportsDirectory() {
        try {
            File dir = new File(System.getProperty("user.dir"), "test-output/report");

            if (dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> {
                    String lower = name.toLowerCase();
                    return lower.endsWith(".html") || lower.endsWith(".xlsx");
                });

                if (files != null) {
                    for (File file : files) {
                        try {
                            file.delete();
                        } catch (Exception ignored) {

                        }
                    }
                }
            } else if (!dir.exists()) {
                dir.mkdirs();
            }
        } catch (Exception e) {
            System.out.println("Unable to clean reports directory: " + e.getMessage());
        }
    }

    private static String write(StringBuilder html, String prefix) {
        try {
            File dir = new File(System.getProperty("user.dir"), "test-output/report");

            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("Unable to create report directory.");
            }

            File[] oldReports = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".html"));
            if (oldReports != null) {
                for (File file : oldReports) {
                    try {
                        file.delete();
                    } catch (Exception ignored) {

                    }
                }
            }

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

    private static String escape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
    private static boolean isSuccessful(String status) {
        if (status == null) return false;
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return normalized.contains("SUCCESS")
                || normalized.contains("COMPLETED")
                || normalized.equals("SUCCEEDED")
                || normalized.equals("COMPLETED_SUCCESSFULLY");
    }

    private static boolean isFailed(String status) {
        return status != null && status.trim().toUpperCase(Locale.ROOT).contains("FAIL");
    }

}


