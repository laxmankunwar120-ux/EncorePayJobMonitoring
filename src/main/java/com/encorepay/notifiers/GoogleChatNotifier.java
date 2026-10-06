package com.encorepay.notifiers;

import com.encorepay.models.JobStatus;
import com.encorepay.utilities.ConfigReader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class GoogleChatNotifier {

    private static final String POST_RECEIPTS = "Post Receipts Job";
    private static final String COLLECTIONS = "Encore Download Collection Items Job";
    private static final String UPCOMING = "Encore Up Coming Demands Job";

    private static final DateTimeFormatter REPORT_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private GoogleChatNotifier() {
    }

    public static void notify(List<JobStatus> statuses, String htmlReportPath) {
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
            e.printStackTrace();
        }
    }

    static String buildMessage(List<JobStatus> statuses) {
        return buildMessage(statuses, List.of(), List.of(), null);
    }

    static String buildMessage(
            List<JobStatus> statuses,
            List<String> clientFailures,
            List<String> configuredClients,
            String htmlReportPath) {
        List<JobStatus> jobs = statuses == null
                ? List.of()
                : statuses.stream().filter(s -> s != null).toList();

        int totalClients = configuredClients == null || configuredClients.isEmpty()
                ? (int) jobs.stream()
                    .map(s -> safe(s.getClientName()))
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .count()
                : (int) configuredClients.stream()
                    .map(GoogleChatNotifier::safe)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .count();

        int unmonitoredCount = countClientFailures(clientFailures);
        int monitoredClients = totalClients - unmonitoredCount;

        String reportTime = LocalDateTime.now(new ConfigReader().getBusinessZone()).format(REPORT_TIME);

        StringBuilder message = new StringBuilder();

        message.append("ENCOREPAY JOB MONITORING REPORT\n");
        message.append("Run Date : ").append(reportTime).append("\n");
        message.append("Clients  : ").append(totalClients).append("\n\n");

        message.append("SUMMARY\n");
        message.append("Total Clients            : ").append(totalClients).append("\n");

        long successfulJobs = jobs.stream().filter(j -> isSuccessful(j.getStatus())).count();
        long failedJobs = jobs.stream().filter(j -> isFailed(j.getStatus())).count();
        long partialSuccessJobs = jobs.stream().filter(j -> isPartialSuccess(j.getStatus())).count();
        long notRunJobs = jobs.stream().filter(j -> isNotRun(j.getStatus())).count();

        message.append("Successful Jobs          : ").append(successfulJobs).append("\n");
        if (failedJobs > 0) {
            message.append("Failed Jobs              : ").append(failedJobs).append("\n");
        }
        if (partialSuccessJobs > 0) {
            message.append("Partial Success Jobs     : ").append(partialSuccessJobs).append("\n");
        }
        if (notRunJobs > 0) {
            message.append("Not Run Jobs             : ").append(notRunJobs).append("\n");
        }

        message.append("\n");

        message.append(buildJobSection(jobs, POST_RECEIPTS, true));
        message.append(buildJobSection(jobs, COLLECTIONS, false));
        message.append(buildJobSection(jobs, UPCOMING, false));

        message.append(buildReceiptFailureSection(jobs));

        if (clientFailures != null && !clientFailures.isEmpty()) {
            message.append("CLIENT FAILURES\n");
            for (String failure : clientFailures) {
                if (failure != null && !failure.isBlank()) {
                    String cleanMsg = extractClientFailureMessage(failure);
                    message.append(cleanMsg).append("\n");
                }
            }
            message.append("\n");
        }

        appendReportLinks(message, htmlReportPath);

        return message.toString();
    }

    private static String buildJobSection(List<JobStatus> jobs, String jobName, boolean includeCounts) {
        List<JobStatus> jobList = jobs.stream()
                .filter(j -> jobName.equalsIgnoreCase(j.getJobName()))
                .toList();

        if (jobList.isEmpty()) return "";

        String title = switch (jobName) {
            case "Post Receipts Job" -> "1. POST RECEIPTS JOB";
            case "Encore Download Collection Items Job" -> "2. DOWNLOAD COLLECTION ITEMS JOB";
            case "Encore Up Coming Demands Job" -> "3. UPCOMING DEMAND JOB";
            default -> jobName.toUpperCase();
        };

        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\n");

        if (includeCounts) {
            sb.append(String.format("%-20s %-12s %-8s %-8s %s\n",
                    center("Client", 20), center("Status", 12), center("Failed", 8), center("Pending", 8), "End Date/Time"));
            sb.append("-".repeat(70)).append("\n");
        } else {
            sb.append(String.format("%-20s %-12s %s\n",
                    center("Client", 20), center("Status", 12), "Start/End Date/Time"));
            sb.append("-".repeat(60)).append("\n");
        }

        for (JobStatus job : jobList) {
            String client = safe(job.getClientName());
            if (client.length() > 18) client = client.substring(0, 17) + "…";

            String status = safe(job.getStatus());
            if (status.length() > 10) status = status.substring(0, 9) + "…";

            String dateTime = safe(job.getDateTime());

            if (includeCounts) {
                sb.append(String.format("%-20s %-12s %-8d %-8d %s\n",
                        client, status, job.getFailedCount(), job.getPendingCount(), dateTime));
            } else {
                sb.append(String.format("%-20s %-12s %s\n",
                        client, status, dateTime));
            }
        }

        sb.append("\n");
        return sb.toString();
    }

    private static String buildReceiptFailureSection(List<JobStatus> jobs) {
        Map<String, Map<String, Integer>> receiptFailuresByClient = buildReceiptFailureGroups(jobs);
        if (receiptFailuresByClient.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("RECEIPT FAILURE REASONS\n");

        for (Map.Entry<String, Map<String, Integer>> entry : receiptFailuresByClient.entrySet()) {
            String client = entry.getKey();
            Map<String, Integer> reasons = entry.getValue();
            int totalFailures = reasons.values().stream().mapToInt(Integer::intValue).sum();
            sb.append(client).append(" — ").append(totalFailures).append(" failures\n");
            for (Map.Entry<String, Integer> reasonEntry : reasons.entrySet()) {
                String reason = reasonEntry.getKey();
                if (reason.length() > 60) reason = reason.substring(0, 57) + "…";
                sb.append("  • ").append(reason).append(" — ").append(reasonEntry.getValue()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static Map<String, Map<String, Integer>> buildReceiptFailureGroups(List<JobStatus> jobs) {
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();
        for (JobStatus job : jobs) {
            if (!POST_RECEIPTS.equalsIgnoreCase(job.getJobName())) continue;
            if (job.getFailedCount() == 0) continue;

            String client = safe(job.getClientName());
            if (client.isBlank()) continue;

            Map<String, Integer> reasonCounts = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : job.getFailureReasonCounts().entrySet()) {
                String reason = entry.getKey();
                int count = entry.getValue();
                String displayReason = extractDisplayReason(reason);
                if (!displayReason.isBlank()) {
                    reasonCounts.merge(displayReason, count, Integer::sum);
                }
            }

            if (!reasonCounts.isEmpty()) {
                result.put(client, reasonCounts);
            }
        }
        return result;
    }

    private static String extractDisplayReason(String reason) {
        if (reason == null || reason.isBlank()) return "";
        String clean = reason.replaceAll("\\s+", " ").trim();
        int bracketStart = clean.indexOf('[');
        if (bracketStart >= 0) {
            int bracketEnd = clean.indexOf(']', bracketStart);
            if (bracketEnd > bracketStart) {
                return clean.substring(bracketEnd + 1).trim();
            }
        }
        return clean;
    }

    private static int countClientFailures(List<String> clientFailures) {
        if (clientFailures == null) return 0;
        return (int) clientFailures.stream()
                .filter(f -> f != null && !f.isBlank())
                .count();
    }

    private static String extractClientFailureMessage(String failure) {
        int separator = failure.indexOf(" :: ");
        if (separator <= 0) {
            return safe(failure);
        }
        String client = failure.substring(0, separator).trim();
        String errorDetail = failure.substring(separator + 4).trim();
        String coreMessage = extractCoreExceptionMessage(errorDetail);
        return client + " — " + coreMessage;
    }

    private static String extractCoreExceptionMessage(String errorDetail) {
        int bracketIndex = errorDetail.indexOf(" [step=");
        if (bracketIndex > 0) {
            errorDetail = errorDetail.substring(0, bracketIndex);
        }
        if (errorDetail.toLowerCase().contains("403") || errorDetail.toLowerCase().contains("forbidden")) {
            return "Application access rejected (403)";
        }
        if (errorDetail.toLowerCase().contains("timeout")) {
            return "Connection timeout";
        }
        if (errorDetail.toLowerCase().contains("connection")) {
            return "Connection failed";
        }
        return abbreviate(errorDetail, 120);
    }

    private static void appendReportLinks(StringBuilder message, String htmlReportPath) {
        String server = System.getenv("GITHUB_SERVER_URL");
        String repository = System.getenv("GITHUB_REPOSITORY");
        String runId = System.getenv("GITHUB_RUN_ID");
        String reportUrl = System.getenv("REPORT_URL");

        message.append("📁 REPORTS\n\n");

        if (reportUrl != null && !reportUrl.isBlank()) {
            message.append("📄 Direct Report: ").append(reportUrl.trim()).append("\n");
        }

        if (htmlReportPath != null && !htmlReportPath.isBlank()) {
            message.append("📄 HTML Report (local): ").append(htmlReportPath).append("\n");
        }

        if (server != null && !server.isBlank()
                && repository != null && !repository.isBlank()
                && runId != null && !runId.isBlank()) {
            String artifactUrl = server.trim() + "/" + repository.trim() + "/actions/runs/" + runId.trim() + "#artifacts";
            message.append("🔗 GitHub Execution: ").append(artifactUrl).append("\n");
        }
        message.append("\n");
    }

    private static boolean isSuccessful(JobStatus status) {
        return status != null && isSuccessful(status.getStatus());
    }

    private static boolean isSuccessful(String status) {
        String value = safe(status).toUpperCase(Locale.ROOT).replace(" ", "_");
        return value.contains("SUCCESS")
                || value.contains("COMPLETED")
                || value.equals("SUCCEEDED")
                || value.equals("PARTIALLY_SUCCESSFUL");
    }

    private static boolean isFailed(JobStatus status) {
        return status != null && isFailed(status.getStatus());
    }

    private static boolean isFailed(String status) {
        String value = safe(status).toUpperCase(Locale.ROOT).replace(" ", "_");
        return value.contains("FAIL") && !value.contains("PARTIAL");
    }

    private static boolean isNotRun(JobStatus status) {
        return status != null && isNotRun(status.getStatus());
    }

    private static boolean isNotRun(String status) {
        String value = safe(status).toUpperCase(Locale.ROOT);
        return value.equals("N/A") || value.equals("NOT CAPTURED") || value.isBlank();
    }

    private static boolean isPartialSuccess(String status) {
        String value = safe(status).toUpperCase(Locale.ROOT).replace(" ", "_");
        return value.equals("PARTIALLY_SUCCESSFUL");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim().replace("\n", " ").replace("\r", " ");
    }

    private static String abbreviate(String value, int maxLength) {
        String text = safe(value);
        if (text.length() <= maxLength) return text;
        return text.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private static String center(String text, int width) {
        String t = safe(text);
        if (t.length() >= width) return t;
        int leftPad = (width - t.length()) / 2;
        int rightPad = width - t.length() - leftPad;
        return " ".repeat(leftPad) + t + " ".repeat(rightPad);
    }

    private static class ClientSummary {
        int totalJobs = 0;
        int successful = 0;
        int failed = 0;
        int partialSuccess = 0;
        int notRun = 0;
    }
}