package com.encorepay.utilities;

import com.encorepay.models.JobStatus;
import com.encorepay.models.ReportData;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public final class JobMonitoringHtmlReport {

    private static final String POST_RECEIPTS = "Post Receipts Job";
    private static final String COLLECTIONS = "Encore Download Collection Items Job";
    private static final String UPCOMING = "Encore Up Coming Demands Job";

    private JobMonitoringHtmlReport() {}

    public static String generate(List<JobStatus> statuses) {
        return generateCombined(statuses, List.of(), List.of());
    }

    public static String generateCombined(List<JobStatus> statuses, List<String> clientFailures, List<String> configuredClients) {
        ReportData data = ReportData.from(statuses, clientFailures, configuredClients);
        Map<String, Map<String, Integer>> reasonsByClient = data.getFailedReceiptReasonsByClient();
        StringBuilder html = new StringBuilder(24576);
        html.append(docHead());
        html.append(brandBar(data));
        html.append("<div class='content'>");
        html.append(postReceiptsSection(data, reasonsByClient));
        html.append(collectionSection(data));
        html.append(upcomingSection(data));
        html.append(attentionSection(data));
        html.append("</div>");
        html.append(footer());
        html.append("</div></body></html>");
        return write(html);
    }

    private static String brandBar(ReportData data) {
        return "<div class='brandbar'><div class='brand-title'>Job Status Report</div>"
                + "<div class='brand-meta'>Generated "
                + esc(data.getFormattedReportTime(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss")))
                + " &nbsp;&bull;&nbsp; Clients: " + data.getTotalClients()
                + " &nbsp;&bull;&nbsp; Monitored: " + data.getMonitoredClients()
                + "</div></div>";
    }

    private static String footer() {
        return "<div class='footer'>Full Report: Generated with this run.</div>";
    }

    private static String docHead() {
        StringBuilder css = new StringBuilder(4096);
        css.append("*{box-sizing:border-box}");
        css.append("body{font-family:'Segoe UI',Arial,Helvetica,sans-serif;margin:0;background:#eef1f5;color:#1a202c}");
        css.append(".report{max-width:100%;width:100%;margin:0;background:#fff;box-shadow:0 0 12px rgba(15,42,68,.08)}");
        css.append(".brandbar{background:#0f2a44;color:#fff;padding:6px 16px;display:flex;justify-content:space-between;align-items:baseline;flex-wrap:wrap}");
        css.append(".brand-title{font-size:15px;font-weight:700;letter-spacing:.3px}");
        css.append(".brand-meta{font-size:10.5px;opacity:.9}");
        css.append(".content{padding:3px 14px 1px}");
        css.append("section{margin:0 0 4px}");
        css.append("h2{font-size:11px;color:#0f2a44;margin:0 0 3px;padding:3px 9px;border-left:3px solid #1a56db;background:#eef3f9;letter-spacing:.4px;font-weight:700}");
        css.append("h2 .num{color:#1a56db;margin-right:6px;font-weight:800}");
        css.append("table{width:100%;max-width:100%;border-collapse:collapse;font-size:10.5px;table-layout:auto;line-height:1.12;margin:0}");
        css.append("th,td{border:1px solid #d7dee6;padding:2px 6px;text-align:left;white-space:nowrap;vertical-align:middle}");
        css.append("th{background:#eef3f9;color:#33475f;font-size:9.5px;text-transform:uppercase;letter-spacing:.3px;font-weight:700;padding:3px 6px;border-bottom:2px solid #c2d0e0}");
        css.append("tbody tr:hover td{background:#f0f6ff}");
        css.append("tbody tr:nth-child(even) td{background:#fafcfe}");
        css.append(".c{text-align:center}");
        css.append("td.reason{color:#c00000;font-weight:700;background:#ffe9e9;white-space:normal;max-width:340px;word-break:break-word;overflow-wrap:anywhere}");
        css.append("td.neg{color:#c00000;font-weight:700;text-align:center}");
        css.append("tr.total td{background:#e8eef6;font-weight:700;border-top:2px solid #0f2a44}");
        css.append(".st-success{color:#0b6b2f;font-weight:600}");
        css.append(".st-partial{color:#8a5a00;font-weight:600}");
        css.append(".st-failed{color:#c00000;font-weight:600}");
        css.append(".st-other,.st-na{color:#5f6b76}");
        css.append(".badge{display:inline-block;padding:0 7px;border-radius:999px;font-size:8px;line-height:1.3;font-weight:700;letter-spacing:.3px;white-space:nowrap;vertical-align:middle;box-sizing:border-box}");
        css.append(".bg-success{background:#def5e3;color:#0b6b2f;border:1px solid #9fd8ae}");
        css.append(".bg-partial{background:#fff4d6;color:#8a5a00;border:1px solid #ecd28a}");
        css.append(".bg-failed{background:#ffe1e1;color:#a11212;border:1px solid #f2a8a8}");
        css.append(".bg-other{background:#eef3f9;color:#33475f;border:1px solid #c9d6e4}");
        css.append(".bg-na{background:#eceff1;color:#5f6b76;border:1px solid #c9d1d8}");
        css.append(".footer{font-size:10px;color:#5f6b76;padding:3px 16px 5px;text-align:right}");
        css.append(".attn{border:1px solid #ecd28a;background:#fff9e9;border-radius:6px;padding:4px 10px;margin:0 0 4px}");
        css.append(".attn h2{border-left:3px solid #ecd28a;color:#8a5a00;margin:0 0 3px}");
        css.append(".attn table{font-size:12px;table-layout:auto}");
        css.append("@media print{body{background:#fff}.report{box-shadow:none}.brandbar{-webkit-print-color-adjust:exact;print-color-adjust:exact}section,.attn{break-inside:avoid}}");
        return "<!doctype html><html lang='en'><head><meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>EncorePay Job Monitoring</title>"
                + "<style>" + css + "</style></head><body><div class='report'>";
    }
    private static String postReceiptsSection(ReportData data, Map<String, Map<String, Integer>> reasonsByClient) {
        List<JobStatus> posts = data.getPostReceipts().stream().filter(j -> !j.isSynthetic()).toList();
        boolean showReason = hasReason(posts, reasonsByClient);
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<section><h2><span class='num'>1.</span>POST RECEIPTS JOB</h2>");
        sb.append("<table><thead><tr>");
        sb.append("<th>Client</th><th>Status</th><th class='c'>Failed</th><th class='c'>Pending</th><th>End Date/Time</th>");
        if (showReason) sb.append("<th>Failure Reason</th>");
        sb.append("</tr></thead><tbody>");
        int totalFailed = 0;
        int totalPending = 0;
        for (JobStatus job : posts) {
            int failed = job.getFailedCount() < 0 ? 0 : job.getFailedCount();
            int pending = job.getPendingCount() < 0 ? 0 : job.getPendingCount();
            totalFailed += failed;
            totalPending += pending;
            sb.append("<tr>");
            sb.append("<td>").append(esc(job.getClientName())).append("</td>");
            sb.append("<td>").append(statusBadge(job)).append("</td>");
            sb.append("<td class='c").append(failed > 0 ? " neg" : "").append("'>").append(failed).append("</td>");
            sb.append("<td class='c").append(pending > 0 ? " neg" : "").append("'>").append(pending).append("</td>");
            sb.append("<td>").append(esc(job.getDateTime())).append("</td>");
            if (showReason) {
                String reason = formatReasons(reasonsFor(reasonsByClient, job.getClientName()));
                if (reason.isEmpty()) {
                    sb.append("<td></td>");
                } else {
                    sb.append("<td class='reason'>").append(esc(reason)).append("</td>");
                }
            }
            sb.append("</tr>");
        }
        sb.append("<tr class='total'><td>TOTAL</td><td></td>");
        sb.append("<td class='c").append(totalFailed > 0 ? " neg" : "").append("'>").append(totalFailed).append("</td>");
        sb.append("<td class='c").append(totalPending > 0 ? " neg" : "").append("'>").append(totalPending).append("</td>");
        sb.append("<td></td>");
        if (showReason) sb.append("<td></td>");
        sb.append("</tr>");
        sb.append("</tbody></table></section>");
        return sb.toString();
    }

    private static String collectionSection(ReportData data) {
        List<JobStatus> jobs = data.getCollectionJobs().stream().filter(j -> !j.isSynthetic()).toList();
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<section><h2><span class='num'>2.</span>DOWNLOAD COLLECTION ITEMS JOB</h2>");
        boolean showReason = hasJobReason(jobs);
        sb.append("<table><thead><tr>");
        sb.append("<th>Client</th><th>Status</th><th>Start/End Date/Time</th>");
        if (showReason) sb.append("<th>Failure Reason</th>");
        sb.append("</tr></thead><tbody>");
        for (JobStatus job : jobs) {
            String reason = jobReason(job);
            sb.append("<tr>");
            sb.append("<td>").append(esc(job.getClientName())).append("</td>");
            sb.append("<td>").append(statusBadge(job)).append("</td>");
            sb.append("<td>").append(esc(job.getDateTime())).append("</td>");
            if (showReason) sb.append(reason.isEmpty() ? "<td></td>" : "<td class='reason'>" + esc(reason) + "</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table></section>");
        return sb.toString();
    }

    private static String upcomingSection(ReportData data) {
        List<JobStatus> jobs = data.getUpcomingJobs().stream().filter(j -> !j.isSynthetic()).toList();
        if (jobs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<section><h2><span class='num'>3.</span>UPCOMING DEMAND JOB</h2>");
        boolean showReason = hasJobReason(jobs);
        sb.append("<table><thead><tr>");
        sb.append("<th>Client</th><th>Status</th><th>Start/End Date/Time</th>");
        if (showReason) sb.append("<th>Failure Reason</th>");
        sb.append("</tr></thead><tbody>");
        for (JobStatus job : jobs) {
            String reason = jobReason(job);
            sb.append("<tr>");
            sb.append("<td>").append(esc(job.getClientName())).append("</td>");
            sb.append("<td>").append(statusBadge(job)).append("</td>");
            sb.append("<td>").append(esc(job.getDateTime())).append("</td>");
            if (showReason) sb.append(reason.isEmpty() ? "<td></td>" : "<td class='reason'>" + esc(reason) + "</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table></section>");
        return sb.toString();
    }
    private static String attentionSection(ReportData data) {
        List<ReportData.ClientException> exceptions = data.getClientExceptions().stream()
                .filter(e -> e.state != ReportData.ClientState.HEALTHY).toList();
        if (exceptions.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<div class='attn'><h2>Clients Requiring Attention</h2>");
        sb.append("<table><thead><tr><th>Client</th><th>State</th><th>Details</th></tr></thead><tbody>");
        for (ReportData.ClientException ex : exceptions) {
            String detail = ex.details.isEmpty() ? "" : String.join("; ", ex.details);
            sb.append("<tr><td>").append(esc(ex.client)).append("</td>");
            sb.append("<td>").append(esc(ex.state.name())).append("</td>");
            sb.append("<td class='reason'>").append(esc(detail)).append("</td></tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private static Map<String, Integer> reasonsFor(Map<String, Map<String, Integer>> reasonsByClient, String client) {
        if (client == null || reasonsByClient == null) return Map.of();
        return reasonsByClient.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(client))
                .map(Map.Entry::getValue)
                .findFirst().orElse(Map.of());
    }

    private static String formatReasons(Map<String, Integer> reasons) {
        if (reasons == null || reasons.isEmpty()) return "";
        return reasons.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .reduce((a, b) -> a + "; " + b).orElse("");
    }

    private static String statusBadge(JobStatus job) {
        return badge(job.getDisplayStatus());
    }

    private static String badge(String status) {
        return "<span class='badge " + badgeClass(status) + "'>" + esc(status == null || status.isBlank() ? "N/A" : status) + "</span>";
    }

    private static String badgeClass(String status) {
        if (status == null) return "bg-na";
        String v = status.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (v.equals("N/A") || v.isBlank()) return "bg-na";
        if (v.equals("PARTIALLY_SUCCESSFUL")) return "bg-partial";
        if (v.contains("SUCCESS") || v.contains("COMPLETED") || v.contains("SUCCEEDED")) return "bg-success";
        if (v.contains("FAIL")) return "bg-failed";
        return "bg-other";
    }

    private static boolean hasReason(List<JobStatus> posts, Map<String, Map<String, Integer>> reasonsByClient) {
        for (JobStatus job : posts) {
            if (!formatReasons(reasonsFor(reasonsByClient, job.getClientName())).isEmpty()) return true;
        }
        return false;
    }

    private static boolean hasJobReason(List<JobStatus> jobs) {
        for (JobStatus job : jobs) {
            if (!jobReason(job).isEmpty()) return true;
        }
        return false;
    }

    private static String jobReason(JobStatus job) {
        String status = job.getDisplayStatus();
        String v = status == null ? "" : status.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (v.contains("SUCCESS") && !v.contains("PARTIAL") && !v.contains("UNSUCCESS")) return "";
        String reason = job.getJobFailureReason();
        return reason == null ? "" : reason.trim();
    }

    private static String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String write(StringBuilder html) {
        try {
            File dir = new File(System.getProperty("user.dir"), "test-output/report");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Unable to create report directory.");
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd_HHmmss");
            fmt.setTimeZone(TimeZone.getTimeZone(new ConfigReader().getBusinessZone()));
            String stamp = fmt.format(new Date());
            File out = new File(dir, "EncorePay_Job_Monitoring_" + stamp + ".html");
            Files.writeString(out.toPath(), html.toString(), StandardCharsets.UTF_8);
            return out.getAbsolutePath();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate HTML report: " + e.getMessage(), e);
        }
    }

    public static void cleanReportsDirectory() {
        try {
            File dir = new File(System.getProperty("user.dir"), "test-output/report");
            if (dir.isDirectory()) {
                File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".html"));
                if (files != null) for (File f : files) f.delete();
            } else if (!dir.exists()) dir.mkdirs();
        } catch (Exception e) {
            System.out.println("Unable to clean reports directory: " + e.getMessage());
        }
    }
}
