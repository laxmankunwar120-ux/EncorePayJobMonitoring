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

        Map<String, ClientSummary> clientSummaries = buildClientSummaries(jobs);
        Map<String, Map<String, Integer>> receiptFailuresByClient = buildReceiptFailureGroups(jobs);

        long totalJobs = jobs.size();
        long successfulJobs = jobs.stream().filter(GoogleChatNotifier::isSuccessful).count();
        long failedJobs = jobs.stream().filter(GoogleChatNotifier::isFailed).count();
        long notRunJobs = jobs.stream().filter(GoogleChatNotifier::isNotRun).count();

        StringBuilder message = new StringBuilder();

        message.append("ENCOREPAY JOB MONITORING\n\n");
        message.append("📅 ").append(LocalDateTime.now(new ConfigReader().getBusinessZone()).format(REPORT_TIME)).append("\n");
        message.append("👥 Clients Monitored: ").append(monitoredClients).append("\n\n");

        message.append("📊 EXECUTION SUMMARY\n\n");
        message.append("✅ Successful Jobs : ").append(successfulJobs).append("\n");
        message.append("❌ Failed Jobs     : ").append(failedJobs).append("\n");
        message.append("⚠️ Not Run        : ").append(notRunJobs).append("\n");
        message.append("📋 Total Jobs     : ").append(totalJobs).append("\n\n");

        if (!clientSummaries.isEmpty()) {
            message.append("🏢 CLIENT STATUS\n\n");
            for (Map.Entry<String, ClientSummary> entry : clientSummaries.entrySet()) {
                ClientSummary summary = entry.getValue();
                String emoji = getClientEmoji(summary);
                message.append(emoji).append(" ").append(entry.getKey()).append("\n");
                message.append("   Jobs    : ").append(summary.totalJobs).append("\n");
                message.append("   Success : ").append(summary.successful).append("\n");
                message.append("   Failed  : ").append(summary.failed).append("\n");
                if (summary.notRun > 0) {
                    message.append("   Not Run : ").append(summary.notRun).append("\n");
                }
                message.append("\n");
            }
        }

        if (!receiptFailuresByClient.isEmpty()) {
            message.append("🔴 RECEIPT FAILURE REASONS\n\n");
            for (Map.Entry<String, Map<String, Integer>> entry : receiptFailuresByClient.entrySet()) {
                String client = entry.getKey();
                Map<String, Integer> reasons = entry.getValue();
                int totalFailures = reasons.values().stream().mapToInt(Integer::intValue).sum();
                message.append("🔴 ").append(client).append(" — ").append(totalFailures).append(" failures\n");
                for (Map.Entry<String, Integer> reasonEntry : reasons.entrySet()) {
                    message.append("• ").append(reasonEntry.getKey()).append(" — ").append(reasonEntry.getValue()).append("\n");
                }
                message.append("Total: ").append(totalFailures).append("\n\n");
            }
        }

        if (clientFailures != null && !clientFailures.isEmpty()) {
            message.append("⚠️ AUTOMATION / ACCESS ISSUES\n\n");
            for (String failure : clientFailures) {
                if (failure != null && !failure.isBlank()) {
                    String cleanMsg = extractClientFailureMessage(failure);
                    message.append("⚠️ ").append(cleanMsg).append("\n");
                }
            }
            message.append("\n");
        }

        appendReportLinks(message, htmlReportPath);

        String overallStatus = calculateOverallStatus(successfulJobs, failedJobs, notRunJobs, unmonitoredCount);
        message.append(overallStatus);

        return message.toString();
    }

    private static Map<String, ClientSummary> buildClientSummaries(List<JobStatus> jobs) {
        Map<String, ClientSummary> summaries = new LinkedHashMap<>();
        for (JobStatus job : jobs) {
            String client = safe(job.getClientName());
            if (client.isBlank()) continue;
            ClientSummary summary = summaries.computeIfAbsent(client, k -> new ClientSummary());
            summary.totalJobs++;
            if (isSuccessful(job.getStatus())) summary.successful++;
            else if (isFailed(job.getStatus())) summary.failed++;
            else if (isNotRun(job.getStatus())) summary.notRun++;
        }
        return summaries;
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

    private static String getClientEmoji(ClientSummary summary) {
        if (summary.failed > 0) return "🔴";
        if (summary.notRun > 0) return "🟡";
        return "🟢";
    }

    private static String calculateOverallStatus(long successful, long failed, long notRun, int unmonitored) {
        if (failed > 0) {
            return "🔴 STATUS: MONITORING COMPLETED — JOB FAILURES DETECTED";
        }
        if (notRun > 0) {
            return "🟡 STATUS: MONITORING COMPLETED — SOME JOBS NOT RUN";
        }
        if (unmonitored > 0) {
            return "🟡 STATUS: MONITORING COMPLETED — ATTENTION REQUIRED (Unmonitored: " + unmonitored + ")";
        }
        if (successful > 0) {
            return "🟢 STATUS: MONITORING COMPLETED — ALL JOBS SUCCESSFUL";
        }
        return "🔴 STATUS: MONITORING FAILED — NO RESULTS";
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
        String value = safe(status).toUpperCase(Locale.ROOT);
        return value.contains("SUCCESS")
                || value.contains("COMPLETED")
                || value.equals("SUCCEEDED");
    }

    private static boolean isFailed(JobStatus status) {
        return status != null && isFailed(status.getStatus());
    }

    private static boolean isFailed(String status) {
        return safe(status).toUpperCase(Locale.ROOT).contains("FAIL");
    }

    private static boolean isNotRun(JobStatus status) {
        return status != null && isNotRun(status.getStatus());
    }

    private static boolean isNotRun(String status) {
        String value = safe(status).toUpperCase(Locale.ROOT);
        return value.equals("N/A") || value.equals("NOT CAPTURED") || value.isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim().replace("\n", " ").replace("\r", " ");
    }

    private static String abbreviate(String value, int maxLength) {
        String text = safe(value);
        if (text.length() <= maxLength) return text;
        return text.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private static class ClientSummary {
        int totalJobs = 0;
        int successful = 0;
        int failed = 0;
        int notRun = 0;
    }
}