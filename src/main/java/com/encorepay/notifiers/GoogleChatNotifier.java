package com.encorepay.notifiers;

import com.encorepay.models.JobStatus;
import com.encorepay.models.ReportData;
import com.encorepay.utilities.ConfigReader;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GoogleChatNotifier {

    private static final DateTimeFormatter REPORT_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    /** Right-aligned width of the Failed/Pending count columns. */
    private static final int COUNT_WIDTH = 7;

    private GoogleChatNotifier() {}

    public static void notify(
            List<JobStatus> statuses,
            String htmlReportPath) {
        notify(statuses, List.of(), List.of(), htmlReportPath);
    }

    public static void notify(
            List<JobStatus> statuses,
            List<String> clientFailures,
            List<String> configuredClients,
            String htmlReportPath) {

        String webhook = new ConfigReader().getGoogleChatWebhookUrl();

        if (webhook == null || webhook.isBlank()) {
            System.out.println("[WARN] Google Chat notification skipped.");
            return;
        }

        String message = buildMessage(statuses, clientFailures, configuredClients, htmlReportPath);

        try {
            GoogleChatApiNotifier.send(webhook, htmlReportPath, message);
        } catch (Exception e) {
            System.err.println("[ERROR] Google Chat notification failed: " + e.getMessage());
        }
    }

    public static String buildMessage(
            List<JobStatus> statuses,
            List<String> clientFailures,
            List<String> configuredClients,
            String htmlReportPath) {

        ReportData data = ReportData.from(statuses, clientFailures, configuredClients);

        String reportTime = data.getFormattedReportTime(REPORT_TIME);

        StringBuilder message = new StringBuilder(8000);

        message.append("*ENCOREPAY JOB MONITORING REPORT*").append('\n');
        message.append("Run Date : ").append(reportTime).append('\n');
        message.append("Clients  : ").append(data.getMonitoredClients())
                .append(" of ").append(data.getTotalClients()).append("\n\n");

        appendSummary(message, data);
        appendClientExceptions(message, data.getClientExceptions());

        appendJobSection(message, data.getPostReceipts(), true, "*1. POST RECEIPTS JOB*");
        appendJobSection(message, data.getCollectionJobs(), false, "*2. DOWNLOAD COLLECTION ITEMS JOB*");
        appendJobSection(message, data.getUpcomingJobs(), false, "*3. UPCOMING DEMAND JOB*");

        appendReceiptFailureSection(message, data.getFailedReceiptReasonsByClient(),
                data.getTotalFailedReceipts());

        appendReportLinks(message, htmlReportPath);

        return message.toString().trim();
    }
private static void appendSummary(StringBuilder message, ReportData data) {
    message.append("*EXECUTIVE SUMMARY*").append('\n').append('\n');

    List<JobStatus> allJobs = new ArrayList<>();
    allJobs.addAll(data.getPostReceipts());
    allJobs.addAll(data.getCollectionJobs());
    allJobs.addAll(data.getUpcomingJobs());

    int totalJobs = 0;
    int totalSuccessfulJobs = 0;
    int totalFailedJobs = 0;
    int totalPartialJobs = 0;
    int totalOtherJobs = 0;
    long overallFailedReceipts = 0;
    long overallPendingReceipts = 0;

    for (JobStatus job : allJobs) {
        if (job == null || job.isSynthetic()) {
            continue;
        }

        totalJobs++;

        String normalizedStatus = safe(job.getDisplayStatus())
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[\\s-]+", "_");

        if (normalizedStatus.contains("PARTIAL")
                && normalizedStatus.contains("SUCC")) {
            totalPartialJobs++;
        } else if (normalizedStatus.equals("SUCCESS")
                || normalizedStatus.equals("SUCCESSFUL")
                || normalizedStatus.equals("COMPLETED")
                || normalizedStatus.equals("SUCCEEDED")) {
            totalSuccessfulJobs++;
        } else if (normalizedStatus.equals("FAILED")
                || normalizedStatus.equals("FAILURE")) {
            totalFailedJobs++;
        } else {
            totalOtherJobs++;
        }

        if (job.getFailedCount() >= 0) {
            overallFailedReceipts += job.getFailedCount();
        }

        if (job.getPendingCount() >= 0) {
            overallPendingReceipts += job.getPendingCount();
        }
    }

    message.append("*OVERALL JOB SUMMARY*").append('\n');
    message.append("Total Jobs                 : ")
            .append(totalJobs).append('\n');
    message.append("Successful Jobs            : ")
            .append(totalSuccessfulJobs).append('\n');
    message.append("Partially Successful Jobs  : ")
            .append(totalPartialJobs).append('\n');
    message.append("Failed Jobs                : ")
            .append(totalFailedJobs).append('\n');
    message.append("Other / Unavailable Status : ")
            .append(totalOtherJobs).append('\n');
    message.append("Total Failed Receipts      : ")
            .append(overallFailedReceipts).append('\n');
    message.append("Total Pending Receipts     : ")
            .append(overallPendingReceipts).append("\n\n");

    Map<String, List<JobStatus>> jobsByClient = new java.util.LinkedHashMap<>();
    for (String configuredClient : data.getConfiguredClients()) {
        String client = safe(configuredClient);
        if (!client.isBlank()) {
            jobsByClient.putIfAbsent(client, new ArrayList<>());
        }
    }

    for (JobStatus job : allJobs) {
        if (job == null || job.isSynthetic()) {
            continue;
        }
        String client = safe(job.getClientName());
        if (client.isBlank()) {
            client = "UNKNOWN CLIENT";
        }
        jobsByClient.computeIfAbsent(client, key -> new ArrayList<>()).add(job);
    }

    int clientNumber = 1;

    for (Map.Entry<String, List<JobStatus>> entry : jobsByClient.entrySet()) {
        String clientName = entry.getKey();
        List<JobStatus> clientJobs = entry.getValue();

        int successfulJobs = 0;
        int failedJobs = 0;
        int partialJobs = 0;
        int attentionJobs = 0;
        long failedReceipts = 0;
        long pendingReceipts = 0;

        for (JobStatus job : clientJobs) {
            String status = safe(job.getDisplayStatus()).toUpperCase(Locale.ENGLISH);

            switch (status) {
                case "SUCCESS":
                case "SUCCESSFUL":
                case "COMPLETED":
                    successfulJobs++;
                    break;
                case "FAILED":
                    failedJobs++;
                    break;
                case "PARTIAL":
                case "PARTIAL_SUCCESS":
                case "PARTIAL SUCCESS":
                case "PARTIALLY_SUCCESSFUL":
                case "PARTIALLY SUCCESSFUL":
                    partialJobs++;
                    break;
                case "ATTENTION":
                case "REQUIRES ATTENTION":
                    attentionJobs++;
                    break;
                default:
                    break;
            }

            if (job.getFailedCount() >= 0) {
                failedReceipts += job.getFailedCount();
            }

            if (job.getPendingCount() >= 0) {
                pendingReceipts += job.getPendingCount();
            }
        }

        message.append("*")
                .append(clientNumber++)
                .append(". ")
                .append(clientName)
                .append("*\n");

        message.append("Total Jobs              : ")
                .append(clientJobs.size())
                .append('\n');

        message.append("Successful Jobs         : ")
                .append(successfulJobs)
                .append('\n');

        message.append("Failed Jobs             : ")
                .append(failedJobs)
                .append('\n');

        if (partialJobs > 0) {
            message.append("Partial Success Jobs    : ")
                    .append(partialJobs)
                    .append('\n');
        }

        if (attentionJobs > 0) {
            message.append("Jobs Requiring Attention: ")
                    .append(attentionJobs)
                    .append('\n');
        }

        message.append("Total Failed Receipts   : ")
                .append(failedReceipts)
                .append('\n');

        message.append("Total Pending Receipts  : ")
                .append(pendingReceipts)
                .append('\n');

        message.append('\n');
    }
}
private static void appendClientExceptions(
        StringBuilder message,
        List<ReportData.ClientException> exceptions) {

    if (exceptions == null || exceptions.isEmpty()) {
        return;
    }

    message.append("*CLIENTS REQUIRING ATTENTION*").append('\n');

    for (ReportData.ClientException ex : exceptions) {
        message.append("*")
               .append(safe(ex.client))
               .append("* — ")
               .append(clientStateLabel(ex.state));

        if (ex.details != null && !ex.details.isEmpty()) {
            String reason = safe(ex.details.get(0));
            message.append(" | 🔴 *")
                   .append(reason)
                   .append("*");

            if (ex.details.size() > 1) {
                message.append(" | +")
                       .append(ex.details.size() - 1)
                       .append(" more note(s)");
            }
        }

        message.append('\n');
    }

    message.append('\n');
}
   

    private static String clientStateLabel(ReportData.ClientState state) {
        return switch (state) {
            case FAILED -> "FAILED";
            case NOT_RUN -> "NOT RUN / NOT CONFIGURED";
            case ATTENTION -> "ATTENTION";
            case HEALTHY -> "HEALTHY";
        };
    }

    private static void appendJobSection(
            StringBuilder message,
            List<JobStatus> jobs,
            boolean includeCounts,
            String title) {

        if (jobs.isEmpty()) return;

        message.append(title).append('\n');

        // Google Chat renders normal text in a proportional font, so spaces
        // cannot be relied on for table alignment. Keep the table itself in a
        // monospace code block and cap widths so long values never make the
        // report unnecessarily wide.
        final int clientWidth = 18;
        final int statusWidth = 16;
        final int failedWidth = 7;
        final int pendingWidth = 7;
        final int dateWidth = 19;

        if (includeCounts) {
            String header = row(
                    "Client", clientWidth,
                    "Status", statusWidth,
                    "Failed", failedWidth,
                    "Pending", pendingWidth,
                    "End Date/Time", dateWidth);
            String separator = "-".repeat(header.length());

            message.append("```").append('\n')
                    .append(header).append('\n')
                    .append(separator).append('\n');

            for (JobStatus job : jobs) {
                message.append(row(
                        displayValue(job.getClientName(), clientWidth),
                        clientWidth,
                        displayValue(job.getDisplayStatus(), statusWidth),
                        statusWidth,
                        countLabel(job.getFailedCount()),
                        failedWidth,
                        countLabel(job.getPendingCount()),
                        pendingWidth,
                        compactDateTime(job.getDateTime()),
                        dateWidth))
                        .append('\n');
            }

            long totalFailed = jobs.stream()
                    .mapToLong(job -> Math.max(0, job.getFailedCount())).sum();
            long totalPending = jobs.stream()
                    .mapToLong(job -> Math.max(0, job.getPendingCount())).sum();

            message.append(separator).append('\n')
                    .append(row("TOTAL", clientWidth,
                            "", statusWidth,
                            String.valueOf(totalFailed), failedWidth,
                            String.valueOf(totalPending), pendingWidth,
                            "", dateWidth))
                    .append('\n')
                    .append("```").append('\n');
        } else {
            String header = row(
                    "Client", clientWidth,
                    "Status", statusWidth,
                    "Start/End Date/Time", dateWidth);
            String separator = "-".repeat(header.length());

            message.append("```").append('\n')
                    .append(header).append('\n')
                    .append(separator).append('\n');

            for (JobStatus job : jobs) {
                message.append(row(
                        displayValue(job.getClientName(), clientWidth),
                        clientWidth,
                        displayValue(job.getDisplayStatus(), statusWidth),
                        statusWidth,
                        compactDateTime(job.getDateTime()),
                        dateWidth))
                        .append('\n');
            }

            message.append(separator).append('\n')
                    .append("```").append('\n');
        }

        message.append('\n');
    }

    private static String row(String value1, int width1,
                              String value2, int width2,
                              String value3, int width3,
                              String value4, int width4,
                              String value5, int width5) {
        return padRight(displayValue(value1, width1), width1) + "  "
                + padRight(displayValue(value2, width2), width2) + "  "
                + padLeft(displayValue(value3, width3), width3) + "  "
                + padLeft(displayValue(value4, width4), width4) + "  "
                + padRight(displayValue(value5, width5), width5);
    }

    private static String row(String value1, int width1,
                              String value2, int width2,
                              String value3, int width3) {
        return padRight(displayValue(value1, width1), width1) + "  "
                + padRight(displayValue(value2, width2), width2) + "  "
                + padRight(displayValue(value3, width3), width3);
    }

    private static String displayValue(String value, int maxWidth) {
        String clean = safe(value);
        if (clean.isBlank()) return "";
        if (clean.length() <= maxWidth) return clean;
        if (maxWidth <= 3) return clean.substring(0, maxWidth);
        return clean.substring(0, maxWidth - 3) + "...";
    }

    private static void appendReceiptFailureSection(
            StringBuilder message,
            Map<String, Map<String, Integer>> reasonsByClient,
            int totalFailedReceipts) {

        if (totalFailedReceipts <= 0 && reasonsByClient.isEmpty()) return;

        message.append("*FAILED RECEIPT REASONS*").append('\n');

        if (reasonsByClient.isEmpty()) {
            message.append("No receipt-level failure reasons were available for the ")
                    .append(totalFailedReceipts)
                    .append(" failed receipt(s).").append('\n');
            message.append("```").append('\n')
                    .append("Reason Status          Count").append('\n')
                    .append("---------------------- -----").append('\n')
                    .append("REASON_NOT_CAPTURED    ")
                    .append(totalFailedReceipts)
                    .append('\n')
                    .append("```").append('\n').append('\n');
            return;
        }

        for (Map.Entry<String, Map<String, Integer>> entry : reasonsByClient.entrySet()) {
            String client = safe(entry.getKey());
            Map<String, Integer> reasons = entry.getValue();

            List<Map.Entry<String, Integer>> sorted = reasons.entrySet().stream()
                    .filter(e -> e.getKey() != null && e.getValue() != null && e.getValue() > 0)
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
                    .toList();

            if (sorted.isEmpty()) continue;

            int clientTotal = sorted.stream().mapToInt(Map.Entry::getValue).sum();
            message.append("*").append(client).append("*")
                    .append(" (").append(clientTotal).append(")").append('\n');

            final int numberWidth = Math.max(2, String.valueOf(sorted.size()).length());
            final int reasonWidth = 50;
            final int countWidth = Math.max(5, sorted.stream()
                    .mapToInt(e -> String.valueOf(e.getValue()).length())
                    .max().orElse(1));

            String header = padRight("#", numberWidth) + "  "
                    + padRight("Failure Reason", reasonWidth) + "  "
                    + padLeft("Count", countWidth);
            String separator = "-".repeat(header.length());

            message.append("```").append('\n')
                    .append(header).append('\n')
                    .append(separator).append('\n');

            for (int i = 0; i < sorted.size(); i++) {
                Map.Entry<String, Integer> reason = sorted.get(i);
                String reasonText = displayValue(reason.getKey(), reasonWidth);

                message.append(padRight(String.valueOf(i + 1), numberWidth)).append("  ")
                        .append(padRight(reasonText, reasonWidth)).append("  ")
                        .append(padLeft(String.valueOf(reason.getValue()), countWidth))
                        .append('\n');
            }

            message.append("```").append('\n');
        }

        message.append("Total Failed Receipts : ")
                .append(Math.max(0, totalFailedReceipts))
                .append("\n\n");
    }

    private static void appendReportLinks(StringBuilder message, String htmlReportPath) {
        String reportUrl = safe(System.getenv("REPORT_URL"));
        String server = safe(System.getenv("GITHUB_SERVER_URL"));
        String repository = safe(System.getenv("GITHUB_REPOSITORY"));
        String runId = safe(System.getenv("GITHUB_RUN_ID"));

        if (!reportUrl.isBlank()) {
            message.append("Full Report: ").append(reportUrl).append('\n');
            return;
        }
        if (!server.isBlank() && !repository.isBlank() && !runId.isBlank()) {
            message.append("GitHub Run: ")
                    .append(server).append('/').append(repository)
                    .append("/actions/runs/").append(runId).append("#artifacts\n");
            return;
        }
        if (!safe(htmlReportPath).isBlank()) {
            message.append("Full Report: Generated with this run.\n");
        }
    }


    private static String padRight(String value, int width) {
        StringBuilder padded = new StringBuilder(value);
        while (padded.length() < width) {
            padded.append(' ');
        }
        return padded.toString();
    }

    private static String padLeft(String value, int width) {
        StringBuilder padded = new StringBuilder(value);
        while (padded.length() < width) {
            padded.insert(0, ' ');
        }
        return padded.toString();
    }

    private static String countLabel(int count) {
        return count < 0 ? "N/A" : String.valueOf(count);
    }

    private static String compactDateTime(String value) {
        String clean = safe(value);
        if (clean.isBlank()) return "N/A";

        // Keep only the time when the captured value contains a full date/time.
        // This keeps Google Chat tables narrow without losing the execution time.
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d{1,2}:\\d{2}(?::\\d{2})?\\s+(?:AM|PM))$", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(clean);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return clean;
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
