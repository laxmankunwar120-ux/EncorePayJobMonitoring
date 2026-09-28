package com.encorepay.utilities;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.encorepay.models.JobStatus;

public final class JobMonitoringHtmlReport {

    private JobMonitoringHtmlReport() {}

    public static String generate(List<JobStatus> statuses) {
        JobStatus post = find(statuses, "Post Receipts Job");
        JobStatus collection = find(statuses, "Encore Download Collection Items Job");
        JobStatus upcoming = findOptional(statuses, "Encore Up Coming Demands Job");

        StringBuilder html = baseHtml();

        appendPostRows(html, "1. Post Receipt Job", List.of(post));

        if (upcoming != null) {
            html.append("<div class='side-by-side-row'>");
            html.append("<div class='side-by-side-col'>");
            appendSimpleRows(html, "2. Download Collection Item Job", List.of(collection));
            html.append("</div>");
            html.append("<div class='side-by-side-col'>");
            appendSimpleRows(html, "3. Upcoming Demand Job", List.of(upcoming));
            html.append("</div>");
            html.append("</div>");
        } else {
            appendSimpleRows(html, "2. Download Collection Item Job", List.of(collection));
        }

        html.append("</div></body></html>");
        return write(html, "EncorePay_Job_Monitoring_");
    }

    public static String generateCombined(List<JobStatus> statuses, List<String> failures) {
        StringBuilder html = baseHtml();
        Map<String, List<JobStatus>> grouped = groupByClient(statuses);

        List<JobStatus> posts = new ArrayList<>();
        List<JobStatus> collections = new ArrayList<>();
        List<JobStatus> upcomings = new ArrayList<>();

        for (List<JobStatus> clientStatuses : grouped.values()) {
            JobStatus post = findOptional(clientStatuses, "Post Receipts Job");
            JobStatus collection = findOptional(
                    clientStatuses,
                    "Encore Download Collection Items Job"
            );
            JobStatus upcoming = findOptional(
                    clientStatuses,
                    "Encore Up Coming Demands Job"
            );

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
            appendPostRows(
                    html,
                    "1. Post Receipt Job",
                    posts
            );
        }

        if (!collections.isEmpty() && !upcomings.isEmpty()) {
            html.append("<div class='side-by-side-row'>");
            html.append("<div class='side-by-side-col'>");

            appendSimpleRows(
                    html,
                    "2. Download Collection Item Job",
                    collections
            );

            html.append("</div>");
            html.append("<div class='side-by-side-col'>");

            appendSimpleRows(
                    html,
                    "3. Upcoming Demand Job",
                    upcomings
            );

            html.append("</div>");
            html.append("</div>");

        } else if (!collections.isEmpty()) {

            appendSimpleRows(
                    html,
                    "2. Download Collection Item Job",
                    collections
            );

        } else if (!upcomings.isEmpty()) {

            appendSimpleRows(
                    html,
                    "3. Upcoming Demand Job",
                    upcomings
            );
        }

        for (Map.Entry<String, List<JobStatus>> entry : grouped.entrySet()) {
            String client = entry.getKey();
            List<JobStatus> clientStatuses = entry.getValue();
            List<String> missing = new ArrayList<>();

            if (findOptional(
                    clientStatuses,
                    "Post Receipts Job"
            ) == null) {
                missing.add("Post Receipts Job");
            }

            if (findOptional(
                    clientStatuses,
                    "Encore Download Collection Items Job"
            ) == null) {
                missing.add(
                        "Encore Download Collection Items Job"
                );
            }

            if (findOptional(
                    clientStatuses,
                    "Encore Up Coming Demands Job"
            ) == null) {
                missing.add(
                        "Encore Up Coming Demands Job"
                );
            }

            if (!missing.isEmpty()) {
                appendFailure(
                        html,
                        client,
                        "Missing monitoring data: "
                                + String.join(", ", missing)
                );
            }
        }

        if (failures != null && !failures.isEmpty()) {
            html.append(
                    "<section>"
                            + "<h2>Client Run Failures</h2>"
                            + "<table>"
                            + "<thead>"
                            + "<tr>"
                            + "<th>Client</th>"
                            + "<th>Status</th>"
                            + "<th>Failure</th>"
                            + "</tr>"
                            + "</thead>"
                            + "<tbody>"
            );

            for (String failure : failures) {
                int separator = failure.indexOf(" :: ");

                String client =
                        separator > 0
                                ? failure.substring(0, separator)
                                : "Unknown Client";

                String detail =
                        separator > 0
                                ? failure.substring(separator + 4).trim()
                                : failure;

                html.append("<tr>")
                        .append("<td class='client-col'>")
                        .append(escape(client))
                        .append("</td>")
                        .append("<td class='failed'>FAILED</td>")
                        .append("<td class='failure-text'>")
                        .append(escape(detail))
                        .append("</td>")
                        .append("</tr>");
            }

            html.append("</tbody></table></section>");
        }

        html.append("</div></body></html>");

        return write(
                html,
                "EncorePay_Multi_Client_Job_Monitoring_"
        );
    }

    private static StringBuilder baseHtml() {
        String timestamp =
                new SimpleDateFormat(
                        "dd-MMM-yyyy HH:mm:ss"
                ).format(new Date());

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
                .append("</div>")
                .append("</div>");
    }

    private static final int[] WIDTHS_COUNTS_REASON = {20, 13, 9, 15, 16, 27};
    private static final int[] WIDTHS_COUNTS_ONLY = {28, 16, 12, 20, 24};
    private static final int[] WIDTHS_REASON_ONLY = {28, 16, 24, 32};
    private static final int[] WIDTHS_PLAIN = {38, 24, 38};

    private static void appendPostRows(
            StringBuilder html,
            String title,
            List<JobStatus> statuses) {

        appendTable(html, title, statuses, true);
    }

    private static void appendSimpleRows(
            StringBuilder html,
            String title,
            List<JobStatus> statuses) {

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

        boolean hasFailureReason =
                reasons.stream().anyMatch(r -> !r.isBlank());

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
                    .append("<td class='")
                    .append(statusClass(status.getStatus()))
                    .append("'>")
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

        String reason = status.getJobFailureReason();

        if (reason == null || reason.isBlank()) {
            reason =
                    status.getFailureReasons() == null
                            ? ""
                            : String.join(
                                    "; ",
                                    status.getFailureReasons()
                            );
        }

        return reason == null ? "" : reason;
    }

    private static void appendFailure(
            StringBuilder html,
            String client,
            String detail) {

        html.append(
                "<section>"
                        + "<h2>Client Monitoring Error</h2>"
                        + "<table>"
                        + "<thead><tr>"
                        + "<th>Client</th>"
                        + "<th>Status</th>"
                        + "<th>Failure</th>"
                        + "</tr></thead>"
                        + "<tbody><tr>"
                        + "<td class='client-col'>"
                        + escape(client)
                        + "</td>"
                        + "<td class='failed'>FAILED</td>"
                        + "<td class='failure-text'>"
                        + escape(detail)
                        + "</td>"
                        + "</tr></tbody>"
                        + "</table>"
                        + "</section>"
        );
    }

    private static Map<String, List<JobStatus>> groupByClient(
            List<JobStatus> statuses) {

        Map<String, List<JobStatus>> grouped =
                new LinkedHashMap<>();

        if (statuses == null) {
            return grouped;
        }

        for (JobStatus status : statuses) {

            if (status == null) {
                continue;
            }

            String client =
                    status.getClientName() == null
                            || status.getClientName().isBlank()
                            ? "Unknown Client"
                            : status.getClientName();

            grouped.computeIfAbsent(
                    client,
                    key -> new ArrayList<>()
            ).add(status);
        }

        return grouped;
    }

    private static JobStatus find(
            List<JobStatus> statuses,
            String name) {

        JobStatus result =
                findOptional(
                        statuses,
                        name
                );

        if (result == null) {
            throw new IllegalStateException(
                    "Missing required job: " + name
            );
        }

        return result;
    }

    private static JobStatus findOptional(
            List<JobStatus> statuses,
            String name) {

        if (statuses == null) {
            return null;
        }

        return statuses.stream()
                .filter(
                        x -> x != null
                                && name.equalsIgnoreCase(
                                        x.getJobName()
                                )
                )
                .findFirst()
                .orElse(null);
    }

    private static String statusClass(
            String status) {

        if (status == null) {
            return "other";
        }

        String normalized =
                status.trim().toUpperCase();

        if (normalized.contains("SUCCESS")
                || normalized.contains("COMPLETED")
                || normalized.equals("SUCCEEDED")
                || normalized.equals(
                        "COMPLETED_SUCCESSFULLY"
                )) {

            return "success";
        }

        if (normalized.contains("FAIL")) {
            return "failed";
        }

        return "other";
    }

    public static void cleanReportsDirectory() {

        try {

            File dir =
                    new File(
                            System.getProperty("user.dir"),
                            "test-output/report"
                    );

            if (dir.exists()
                    && dir.isDirectory()) {

                File[] files =
                        dir.listFiles(
                                (d, name) -> {

                                    String lower =
                                            name.toLowerCase();

                                    return lower.endsWith(".html")
                                            || lower.endsWith(".xlsx");
                                }
                        );

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

            System.out.println(
                    " Unable to clean reports directory: "
                            + e.getMessage()
            );
        }
    }

    private static String write(
            StringBuilder html,
            String prefix) {

        try {

            File dir =
                    new File(
                            System.getProperty("user.dir"),
                            "test-output/report"
                    );

            if (!dir.exists()
                    && !dir.mkdirs()) {

                throw new IllegalStateException(
                        "Unable to create report directory."
                );
            }

            File[] oldReports =
                    dir.listFiles(
                            (d, name) ->
                                    name.toLowerCase()
                                            .endsWith(".html")
                    );

            if (oldReports != null) {

                for (File file : oldReports) {

                    try {
                        file.delete();
                    } catch (Exception ignored) {
                    }
                }
            }

            String stamp =
                    new SimpleDateFormat(
                            "yyyy-MM-dd_HHmmss"
                    ).format(new Date());

            File output =
                    new File(
                            dir,
                            prefix + stamp + ".html"
                    );

            Files.writeString(
                    output.toPath(),
                    html.toString(),
                    StandardCharsets.UTF_8
            );

            return output.getAbsolutePath();

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Unable to generate HTML report: "
                            + e.getMessage(),
                    e
            );
        }
    }

    private static String escape(
            String value) {

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
}