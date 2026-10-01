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

    private static final int MAX_REASON_LENGTH = 600;
    private static final int MAX_CLIENT_LENGTH = 24;   // caps column growth as client count scales up
    private static final int MAX_STATUS_LENGTH = 14;
    private static final int MAX_DATETIME_LENGTH = 18;

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private GoogleChatNotifier() {
    }

    public static void notify(List<JobStatus> statuses, String htmlReportPath) {
        String webhook = new ConfigReader().getGoogleChatWebhookUrl();

        if (webhook == null || webhook.isBlank()) {
            // Not configured is a deliberate choice, not a failure, so nothing is raised.
            System.out.println("[WARN] Google Chat notification skipped.");
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("text", buildMessage(statuses));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhook))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> response =
                    HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("[INFO] Google Chat notification sent successfully.");
                return;
            }

            // A rejected webhook means the report never reached the team, which is a real
            // failure of this run rather than a detail to log and move past.
            throw new IllegalStateException("Google Chat returned HTTP " + response.statusCode()
                    + ": " + abbreviate(response.body(), 300));
        } catch (Exception e) {
            System.out.println("[FAIL] Google Chat notification failed: " + e.getMessage());
            throw e instanceof RuntimeException runtime
                ? runtime
                : new IllegalStateException("Google Chat notification failed: " + e.getMessage(), e);
        }
    }

    static String buildMessage(List<JobStatus> statuses) {
        List<JobStatus> jobs = statuses == null
                ? List.of()
                : statuses.stream().filter(s -> s != null).toList();

        int clients = (int) jobs.stream()
                .map(s -> safe(s.getClientName()))
                .filter(s -> !s.isBlank())
                .distinct()
                .count();

        long successful = jobs.stream().filter(GoogleChatNotifier::isSuccessful).count();
        long failed = jobs.stream().filter(GoogleChatNotifier::isFailed).count();

        StringBuilder message = new StringBuilder();

        message.append("ENCOREPAY JOB MONITORING REPORT\n")
                .append("Run Date : ")
                .append(LocalDateTime.now().format(REPORT_TIME))
                .append("\nClients  : ")
                .append(clients)
                .append("\n\n");

        appendSummary(message, clients, successful, failed);
        appendPostReceipts(message, jobs);
        appendJob(message, jobs, COLLECTIONS, "2. DOWNLOAD COLLECTION ITEMS JOB");
        appendJob(message, jobs, UPCOMING, "3. UPCOMING DEMAND JOB");
        appendTechnicalErrors(message, jobs);

        message.append("Automated report generated by EncorePay Job Monitoring System.");

        return message.toString();
    }

    private static void appendSummary(StringBuilder message, int clients,
                                      long successful, long failed) {
        message.append("SUMMARY\n```\n")
                .append(String.format("%-24s : %d\n", "Total Clients", clients))
                .append(String.format("%-24s : %d\n", "Successful Jobs", successful))
                .append(String.format("%-24s : %d\n", "Failed Jobs", failed))
                .append("```\n\n");
    }


    private enum Align { LEFT, RIGHT }

    /** Renders a heading + fenced table whose column widths are derived from the actual
     *  header and row content, so header/rows can never drift out of alignment. */
    private static void appendTable(StringBuilder message, String heading,
                                     String[] headers, Align[] aligns, List<String[]> rows) {
        if (rows.isEmpty()) {
            return;
        }

        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            widths[i] = headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }

        if (heading != null) {
            message.append(heading).append("\n");
        }
        message.append("```\n");
        message.append(formatRow(headers, widths, aligns)).append("\n");

        int totalWidth = -2; // no trailing separator after the last column
        for (int w : widths) {
            totalWidth += w + 2;
        }
        message.append("-".repeat(Math.max(1, totalWidth))).append("\n");

        for (String[] row : rows) {
            message.append(formatRow(row, widths, aligns)).append("\n");
        }
        message.append("```\n\n");
    }

    private static String formatRow(String[] cols, int[] widths, Align[] aligns) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            String cell = aligns[i] == Align.RIGHT
                    ? padLeft(cols[i], widths[i])
                    : padRight(cols[i], widths[i]);
            sb.append(cell);
            if (i < cols.length - 1) {
                sb.append("  "); // two-space gutter between every column, always
            }
        }
        return sb.toString();
    }

    private static String padRight(String value, int width) {
        return value.length() >= width ? value : value + " ".repeat(width - value.length());
    }

    private static String padLeft(String value, int width) {
        return value.length() >= width ? value : " ".repeat(width - value.length()) + value;
    }


    private static void appendPostReceipts(StringBuilder message, List<JobStatus> jobs) {
        List<JobStatus> records = jobs.stream()
                .filter(s -> POST_RECEIPTS.equalsIgnoreCase(s.getJobName()))
                .toList();

        if (records.isEmpty()) {
            return;
        }

        String[] headers = {"Client", "Status", "Failed", "Pending", "End Date/Time"};
        Align[] aligns = {Align.LEFT, Align.LEFT, Align.RIGHT, Align.RIGHT, Align.LEFT};

        List<String[]> rows = new ArrayList<>();
        for (JobStatus status : records) {
            rows.add(new String[] {
                    abbreviate(safe(status.getClientName()), MAX_CLIENT_LENGTH),
                    displayStatus(status.getStatus()),
                    String.valueOf(status.getFailedCount()),
                    String.valueOf(status.getPendingCount()),
                    abbreviate(formatDateTime(status.getDateTime()), MAX_DATETIME_LENGTH)
            });
        }

        appendTable(message, "1. POST RECEIPTS JOB", headers, aligns, rows);
        appendReceiptReasons(message, records);
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

        message.append("FAILED RECEIPT REASONS\n");

        for (Map.Entry<String, Set<String>> entry : reasonsByClient.entrySet()) {
            message.append(entry.getKey())
                    .append(" (")
                    .append(entry.getValue().size())
                    .append(entry.getValue().size() == 1
                            ? " unique reason)\n"
                            : " unique reasons)\n");

            int number = 1;
            for (String reason : entry.getValue()) {
                message.append("  ")
                        .append(number++)
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

        String[] headers = {"Client", "Status", "Start/End Date/Time"};
        Align[] aligns = {Align.LEFT, Align.LEFT, Align.LEFT};

        List<String[]> rows = new ArrayList<>();
        for (JobStatus status : records) {
            rows.add(new String[] {
                    abbreviate(safe(status.getClientName()), MAX_CLIENT_LENGTH),
                    displayStatus(status.getStatus()),
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

        message.append("TECHNICAL ERRORS\n");

        for (JobStatus status : errors) {
            String reason = safe(status.getJobFailureReason());
            String http = extractHttpError(reason);
            String api = extractApiName(reason);

            message.append("Client : ").append(safe(status.getClientName())).append("\n")
                    .append("Job    : ").append(safe(status.getJobName())).append("\n");

            if (!http.isBlank()) {
                message.append("Error  : ").append(http).append("\n");
            }

            if (!api.isBlank()) {
                message.append("API    : ").append(api).append("\n");
            }

            if (http.isBlank() && api.isBlank() && !reason.isBlank()) {
                message.append("Reason : ")
                        .append(abbreviate(cleanReason(reason), MAX_REASON_LENGTH))
                        .append("\n");
            }

            message.append("\n");
        }
    }

    private static boolean hasTechnicalFailure(JobStatus status) {
        return status != null
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
