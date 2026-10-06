package com.encorepay.notifiers;

import com.encorepay.models.JobStatus;
import com.encorepay.utilities.ConfigReader;
import com.google.gson.JsonObject;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GoogleChatNotifier {

    private static final String POST_RECEIPTS = "Post Receipts Job";
    private static final String COLLECTIONS = "Encore Download Collection Items Job";
    private static final String UPCOMING = "Encore Up Coming Demands Job";

    private static final DateTimeFormatter REPORT_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);

    private static final DateTimeFormatter[] INPUT_FORMATS = {
            DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm:ss a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH)
    };

    private static final Pattern HTTP_ERROR =
            Pattern.compile("(?i)(\\d{3})\\s+([A-Za-z][A-Za-z ]{2,50})");

    private static final Pattern API =
            Pattern.compile("(?i)(?:from\\s+GET\\s+|GET\\s+)([^\\s\\]]+)");

    private static final Pattern TIMEOUT_ERROR =
            Pattern.compile("(?i)TimeoutException.*?(?:\\(tried for ([^)]+)\\))?");

private static final int MAX_REASON_LENGTH = 600;
    private static final int MAX_CLIENT_LENGTH = 20;
    private static final int MAX_STATUS_LENGTH = 12;
    private static final int MAX_DATETIME_LENGTH = 16;

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
            // Not configured is a deliberate choice, not a failure, so nothing is raised.
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

        int clients = configuredClients == null || configuredClients.isEmpty()
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

        long successful = jobs.stream().filter(GoogleChatNotifier::isSuccessful).count();
        long failed = jobs.stream().filter(GoogleChatNotifier::isFailed).count()
                + countClientFailures(clientFailures);

        StringBuilder message = new StringBuilder();

        message.append("📊 *ENCOREPAY JOB MONITORING REPORT*\n")
                .append("🕐 Run: ")
                .append(LocalDateTime.now(new ConfigReader().getBusinessZone()).format(REPORT_TIME))
                .append("\n👥 Clients: ")
                .append(clients)
                .append("\n\n");

        appendSummary(message, clients, successful, failed);
        appendPostReceipts(message, jobs);
        appendJob(message, jobs, COLLECTIONS, "2. DOWNLOAD COLLECTION ITEMS JOB");
        appendJob(message, jobs, UPCOMING, "3. UPCOMING DEMAND JOB");
        appendTechnicalErrors(message, jobs);
        appendClientFailures(message, clientFailures);
        appendReportLink(message);

        message.append("Automated report generated by EncorePay Job Monitoring System.");

        return message.toString();
    }

private static void appendSummary(StringBuilder message, int clients,
                                       long successful, long failed) {
        message.append("*SUMMARY*\n")
                .append("📊 Total Clients: `").append(clients).append("`\n")
                .append("✅ Successful: `").append(successful).append("`\n")
                .append("❌ Failed: `").append(failed).append("`\n\n");
    }


    private enum Align { LEFT, RIGHT }

    /** Mobile-friendly line-based format (no code-block tables). */
    private static void appendTable(StringBuilder message, String heading,
                                     String[] headers, Align[] aligns, List<String[]> rows) {
        if (rows.isEmpty()) {
            return;
        }

        if (heading != null) {
            message.append("*").append(heading).append("*\n");
        }

        for (String[] row : rows) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < row.length; i++) {
                String label = headers[i];
                String value = row[i];
                if (i > 0) line.append("  •  ");
                line.append("`").append(label).append(":` ").append(value);
            }
            message.append(line).append("\n");
        }
        message.append("\n");
    }


    private static void appendPostReceipts(StringBuilder message, List<JobStatus> jobs) {
        List<JobStatus> records = jobs.stream()
                .filter(s -> POST_RECEIPTS.equalsIgnoreCase(s.getJobName()))
                .toList();

        if (records.isEmpty()) {
            return;
        }

        String[] headers = {"Client", "Status", "Failed", "Pending", "End Time"};
        Align[] aligns = {Align.LEFT, Align.LEFT, Align.RIGHT, Align.RIGHT, Align.LEFT};

        List<String[]> rows = new ArrayList<>();
        for (JobStatus status : records) {
            String statusEmoji = getStatusEmoji(status.getStatus());
            rows.add(new String[] {
                    abbreviate(safe(status.getClientName()), MAX_CLIENT_LENGTH),
                    statusEmoji + " " + displayStatus(status.getStatus()),
                    String.valueOf(status.getFailedCount()),
                    String.valueOf(status.getPendingCount()),
                    abbreviate(formatDateTime(status.getDateTime()), MAX_DATETIME_LENGTH)
            });
        }

        appendTable(message, "1. POST RECEIPTS JOB", headers, aligns, rows);
        appendReceiptReasons(message, records);

        for (JobStatus status : records) {
            String validation = safe(status.getValidationMessage());
            if (!validation.isBlank()) {
                message.append("⚠️ *VALIDATION* - ")
                        .append(safe(status.getClientName()))
                        .append(": ")
                        .append(abbreviate(validation, MAX_REASON_LENGTH))
                        .append("\n");
            }
        }
        message.append("\n");
    }

    private static String getStatusEmoji(String status) {
        if (status == null) return "❓";
        String s = status.toUpperCase(Locale.ROOT);
        if (s.contains("SUCCESS") || s.contains("COMPLETED") || s.equals("SUCCEEDED")) return "✅";
        if (s.contains("FAIL")) return "❌";
        if (s.contains("PARTIAL")) return "⚠️";
        if (s.contains("RUNNING") || s.contains("PROGRESS") || s.contains("EXECUTION")) return "🔄";
        if (s.equals("N/A")) return "⏭️";
        return "❓";
    }

    private static void appendReceiptReasons(StringBuilder message, List<JobStatus> records) {
        Map<String, Set<String>> reasonsByClient = new LinkedHashMap<>();

        for (JobStatus status : records) {
            // Only real failures can have real reasons - skip anything else up front.
            if (status.getFailureReasons() == null || status.getFailedCount() == 0) {
                continue;
            }

            Set<String> reasons = new LinkedHashSet<>();
            for (String reason : status.getFailureReasons()) {
                String cleaned = cleanReason(reason);
                if (!cleaned.isBlank()) {
                    reasons.add(cleaned);
                }
            }

            // Nothing survived cleaning -> don't create a client entry at all.
            if (reasons.isEmpty()) {
                continue;
            }

            reasonsByClient
                    .computeIfAbsent(safe(status.getClientName()), k -> new LinkedHashSet<>())
                    .addAll(reasons);
        }

        if (reasonsByClient.isEmpty()) {
            return;
        }

        message.append("📋 *FAILED RECEIPT REASONS*\n");

        for (Map.Entry<String, Set<String>> entry : reasonsByClient.entrySet()) {
            message.append("📍 *").append(entry.getKey()).append("* (")
                    .append(entry.getValue().size())
                    .append(entry.getValue().size() == 1
                            ? " reason)\n"
                            : " reasons)\n");

            int number = 1;
            for (String reason : entry.getValue()) {
                message.append("   ").append(number++)
                        .append(". ")
                        .append(abbreviate(reason, MAX_REASON_LENGTH))
                        .append("\n");
            }
            message.append("\n");
        }
    }


    private static void appendJob(StringBuilder message, List<JobStatus> jobs,
                                   String jobName, String heading) {
        List<JobStatus> records = jobs.stream()
                .filter(s -> jobName.equalsIgnoreCase(s.getJobName()))
                .toList();

        if (records.isEmpty()) {
            return;
        }

        String[] headers = {"Client", "Status", "End Time"};
        Align[] aligns = {Align.LEFT, Align.LEFT, Align.LEFT};

        List<String[]> rows = new ArrayList<>();
        for (JobStatus status : records) {
            String statusEmoji = getStatusEmoji(status.getStatus());
            rows.add(new String[] {
                    abbreviate(safe(status.getClientName()), MAX_CLIENT_LENGTH),
                    statusEmoji + " " + displayStatus(status.getStatus()),
                    abbreviate(formatDateTime(status.getDateTime()), MAX_DATETIME_LENGTH)
            });
        }

        appendTable(message, heading, headers, aligns, rows);
    }

    private static void appendTechnicalErrors(StringBuilder message, List<JobStatus> jobs) {
        List<JobStatus> errors = jobs.stream()
                .filter(GoogleChatNotifier::hasTechnicalFailure)
                .toList();

        if (errors.isEmpty()) {
            return;
        }

        message.append("🔧 *TECHNICAL ERRORS*\n");

        for (JobStatus status : errors) {
            String reason = safe(status.getJobFailureReason());
            String http = extractHttpError(reason);
            String api = extractApiName(reason);

            message.append("📍 *").append(safe(status.getClientName())).append("*\n")
                    .append("   Job: ").append(safe(status.getJobName())).append("\n");

            if (!http.isBlank()) {
                message.append("   🌐 Error: ").append(http).append("\n");
            }

            if (!api.isBlank()) {
                message.append("   🔗 API: ").append(api).append("\n");
            }

            if (http.isBlank() && api.isBlank() && !reason.isBlank()) {
                message.append("   📝 Reason: ")
                        .append(abbreviate(cleanReason(reason), MAX_REASON_LENGTH))
                        .append("\n");
            }

            String validation = safe(status.getValidationMessage());
            if (!validation.isBlank()) {
                message.append("   ⚠️ Validation: ")
                        .append(abbreviate(validation, MAX_REASON_LENGTH))
                        .append("\n");
            }

            message.append("\n");
        }
    }

    private static int countClientFailures(List<String> clientFailures) {
        if (clientFailures == null) {
            return 0;
        }

        return (int) clientFailures.stream()
                .filter(failure -> failure != null && !failure.isBlank())
                .count();
    }

    private static void appendClientFailures(StringBuilder message, List<String> clientFailures) {
        if (clientFailures == null || clientFailures.isEmpty()) {
            return;
        }

        message.append("🚫 *CLIENT ACCESS FAILURES*\n");

        for (String failure : clientFailures) {
            if (failure != null && !failure.isBlank()) {
                String cleanMsg = extractClientFailureMessage(failure);
                message.append("• ").append(cleanMsg).append("\n");
            }
        }

        message.append("\n");
    }

    private static String extractClientFailureMessage(String failure) {
        int separator = failure.indexOf(" :: ");
        if (separator <= 0) {
            return cleanReason(failure);
        }

        String client = failure.substring(0, separator).trim();
        String errorDetail = failure.substring(separator + 4).trim();

        String coreMessage = extractCoreExceptionMessage(errorDetail);
        coreMessage = cleanReason(coreMessage);
        String friendlyMessage = makeFriendlyMessage(coreMessage);

        return client + " : " + friendlyMessage;
    }

    private static String extractCoreExceptionMessage(String errorDetail) {
        int bracketIndex = errorDetail.indexOf(" [step=");
        if (bracketIndex > 0) {
            errorDetail = errorDetail.substring(0, bracketIndex);
        }

        Matcher timeoutMatcher = TIMEOUT_ERROR.matcher(errorDetail);
        if (timeoutMatcher.find()) {
            String triedFor = timeoutMatcher.group(1);
            if (triedFor != null && !triedFor.isBlank()) {
                return "Login timeout after " + triedFor;
            }
            return "Login timeout";
        }

        Matcher httpMatcher = HTTP_ERROR.matcher(errorDetail);
        if (httpMatcher.find()) {
            return httpMatcher.group(1) + " " + httpMatcher.group(2).trim();
        }

        return abbreviate(errorDetail, 200);
    }

    private static String makeFriendlyMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown error";
        }

        String lower = message.toLowerCase();

        if (lower.contains("503") || lower.contains("service unavailable")) {
            return "Service unavailable (503) - server temporarily down";
        }
        if (lower.contains("403") || lower.contains("forbidden")) {
            return "Access forbidden (403)";
        }
        if (lower.contains("404") || lower.contains("not found")) {
            return "Not found (404)";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "Connection timeout - server not responding";
        }
        if (lower.contains("connection refused") || lower.contains("connect")) {
            return "Connection refused - server unreachable";
        }
        if (lower.contains("ssl") || lower.contains("certificate") || lower.contains("handshake")) {
            return "SSL/TLS certificate error";
        }
        if (lower.contains("dns") || lower.contains("name resolution")) {
            return "DNS resolution failed";
        }

        return abbreviate(message, 180);
    }

    private static void appendReportLink(StringBuilder message) {
        String server = System.getenv("GITHUB_SERVER_URL");
        String repository = System.getenv("GITHUB_REPOSITORY");
        String runId = System.getenv("GITHUB_RUN_ID");

        if (server == null || server.isBlank()
                || repository == null || repository.isBlank()
                || runId == null || runId.isBlank()) {
            return;
        }

        message.append("GitHub Actions Run : ")
                .append(server.trim())
                .append("/")
                .append(repository.trim())
                .append("/actions/runs/")
                .append(runId.trim())
                .append("\n\n");
    }

    private static boolean hasTechnicalFailure(JobStatus status) {
        return status != null
                && !"N/A".equalsIgnoreCase(safe(status.getStatus()))
                && !safe(status.getJobFailureReason()).isBlank()
                && status.getFailedCount() == 0;
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

    private static String displayStatus(String status) {
        if (isSuccessful(status)) {
            return "SUCCESSFUL";
        }
        if (isFailed(status)) {
            return "FAILED";
        }
        if (isInProgress(status)) {
            return "NO STATUS";
        }
        return safe(status).isBlank()
                ? "NOT CAPTURED"
                : safe(status).toUpperCase(Locale.ROOT);
    }

    private static boolean isInProgress(String status) {
        String value = safe(status).toUpperCase(Locale.ROOT);
        return value.contains("IN PROGRESS")
            || value.contains("RUNNING")
            || value.contains("IN EXECUTION")
            || value.contains("NO STATUS");
    }

    private static String formatDateTime(String value) {
        String text = safe(value);

        if (text.isBlank()) {
            return "NOT CAPTURED";
        }

        for (DateTimeFormatter formatter : INPUT_FORMATS) {
            try {
                return LocalDateTime.parse(text, formatter).format(DISPLAY_TIME);
            } catch (Exception ignored) {
            }
        }

        return text;
    }

    private static String cleanReason(String reason) {
        return safe(reason)
                .replaceAll("(?s)\\[[^\\]]*nested exception:", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String extractHttpError(String reason) {
        Matcher matcher = HTTP_ERROR.matcher(safe(reason));
        return matcher.find()
                ? matcher.group(1) + " " + matcher.group(2).trim()
                : "";
    }

    private static String extractApiName(String reason) {
        Matcher matcher = API.matcher(safe(reason));

        if (!matcher.find()) {
            return "";
        }

        String endpoint = matcher.group(1);
        int query = endpoint.indexOf('?');

        if (query >= 0) {
            endpoint = endpoint.substring(0, query);
        }

        endpoint = endpoint.replaceAll("[\\])}>,.;]+$", "");
        int slash = endpoint.lastIndexOf('/');

        return slash >= 0 && slash < endpoint.length() - 1
                ? endpoint.substring(slash + 1)
                : endpoint;
    }

    private static String safe(String value) {
        return value == null
                ? ""
                : value.trim().replace("\n", " ").replace("\r", " ");
    }

    private static String abbreviate(String value, int maxLength) {
        String text = safe(value);

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }
}
