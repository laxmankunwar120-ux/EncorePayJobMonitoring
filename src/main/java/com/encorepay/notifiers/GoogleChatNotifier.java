package com.encorepay.notifiers;

import com.encorepay.models.JobStatus;
import com.encorepay.utilities.ConfigReader;
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
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

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
            try {
                java.nio.file.Path marker = java.nio.file.Path.of("test-output", "google-chat-sent.marker");
                java.nio.file.Files.createDirectories(marker.getParent());
                java.nio.file.Files.writeString(marker, LocalDateTime.now(new ConfigReader().getBusinessZone()).toString());
            } catch (Exception markerError) {
                System.err.println("[WARN] Google Chat success marker could not be written: " + markerError.getMessage());
            }
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

        List<String> clients = resolveClients(jobs, configuredClients);

        long successful = jobs.stream()
                .filter(s -> isSuccessful(s.getStatus()))
                .count();

        long partial = jobs.stream()
                .filter(s -> isPartial(s.getStatus()))
                .count();

        long failed = jobs.stream()
                .filter(s -> isFailed(s.getStatus()))
                .count();

        long notRun = jobs.stream()
                .filter(s -> isNotRun(s.getStatus()))
                .count();

        StringBuilder message = new StringBuilder();

        message.append("ENCOREPAY JOB MONITORING REPORT\n\n")
                .append("Run Date      : ")
                .append(LocalDateTime.now(new ConfigReader().getBusinessZone()).format(REPORT_TIME))
                .append("\n")
                .append("Total Clients : ")
                .append(clients.size())
                .append("\n\n");

        message.append("OVERALL SUMMARY\n\n")
                .append("Successful Jobs : ").append(successful).append("\n")
                .append("Partial Jobs    : ").append(partial).append("\n")
                .append("Failed Jobs     : ").append(failed).append("\n")
                .append("Not Run Jobs    : ").append(notRun).append("\n\n");

        message.append("CLIENT JOB STATUS\n\n");

        for (String client : clients) {
            appendClient(message, client, jobs);
        }

        appendTechnicalIssues(message, clientFailures, jobs);

        message.append("REPORT STATUS\n\nCOMPLETED");

        return message.toString();
    }

    private static List<String> resolveClients(List<JobStatus> jobs, List<String> configuredClients) {
        Set<String> names = new LinkedHashSet<>();

        if (configuredClients != null) {
            for (String client : configuredClients) {
                String value = safe(client);
                if (!value.isBlank()) {
                    names.add(value);
                }
            }
        }

        for (JobStatus job : jobs) {
            String value = safe(job.getClientName());
            if (!value.isBlank()) {
                names.add(value);
            }
        }

        return new ArrayList<>(names);
    }

    private static void appendClient(StringBuilder message, String client, List<JobStatus> jobs) {
        message.append(client).append("\n");

        JobStatus post = findJob(jobs, client, POST_RECEIPTS);
        JobStatus collections = findJob(jobs, client, COLLECTIONS);
        JobStatus upcoming = findJob(jobs, client, UPCOMING);

        appendJobLine(message, "Post Receipts", post);
        appendJobLine(message, "Collection Items", collections);
        appendJobLine(message, "Upcoming Demand", upcoming);

        if (post != null && post.getFailedCount() > 0) {
            message.append("  Failed Receipts  : ")
                    .append(post.getFailedCount())
                    .append("\n")
                    .append("  Pending Receipts : ")
                    .append(post.getPendingCount())
                    .append("\n");

            appendFailureDetails(message, post);
        }

        message.append("\n");
    }

    private static void appendJobLine(StringBuilder message, String label, JobStatus status) {
        message.append("  ")
                .append(String.format("%-17s", label + " :"));

        if (status == null) {
            message.append(" NOT RUN\n");
            return;
        }

        String value = displayStatus(status.getStatus());
        String dateTime = formatDateTime(status.getDateTime());

        if (isNotRun(status.getStatus()) || dateTime.isBlank() || "NOT CAPTURED".equalsIgnoreCase(dateTime)) {
            message.append(" ").append(value).append("\n");
            return;
        }

        message.append(" ")
                .append(value)
                .append(" (")
                .append(dateTime)
                .append(")\n");
    }

    private static void appendFailureDetails(StringBuilder message, JobStatus status) {
        List<String> reasons = status.getFailureReasons();

        if (reasons == null || reasons.isEmpty()) {
            message.append("  Failure Details   : Not captured\n");
            return;
        }

        message.append("  Failure Details\n");

        for (String reason : reasons) {
            message.append("    ")
                    .append(abbreviate(cleanReason(reason), MAX_REASON_LENGTH))
                    .append("\n");
        }
    }

    private static JobStatus findJob(List<JobStatus> jobs, String client, String jobName) {
        return jobs.stream()
                .filter(s -> client.equalsIgnoreCase(safe(s.getClientName())))
                .filter(s -> jobName.equalsIgnoreCase(safe(s.getJobName())))
                .findFirst()
                .orElse(null);
    }

    private static void appendTechnicalIssues(
            StringBuilder message,
            List<String> clientFailures,
            List<JobStatus> jobs) {

        List<String> issues = new ArrayList<>();

        if (clientFailures != null) {
            for (String failure : clientFailures) {
                if (failure != null && !failure.isBlank()) {
                    issues.add(cleanReason(failure));
                }
            }
        }

        for (JobStatus status : jobs) {
            String reason = safe(status.getJobFailureReason());
            if (reason.isBlank() || status.getFailedCount() > 0) {
                continue;
            }

            if (isFailed(status.getStatus()) || reason.toUpperCase(Locale.ROOT).contains("CAPTURE FAILED")) {
                issues.add(safe(status.getClientName()) + " - " + reason);
            }
        }

        if (issues.isEmpty()) {
            return;
        }

        message.append("AUTOMATION ISSUES\n\n");

        for (String issue : new LinkedHashSet<>(issues)) {
            message.append(issue).append("\n");
        }

        message.append("\n");
    }

    private static boolean isSuccessful(String status) {
        String value = safe(status).toUpperCase(Locale.ROOT);
        return value.contains("SUCCESS")
                || value.contains("COMPLETED")
                || value.equals("SUCCEEDED");
    }

    private static boolean isPartial(String status) {
        String value = safe(status).toUpperCase(Locale.ROOT);
        return value.contains("PARTIAL");
    }

    private static boolean isFailed(String status) {
        return safe(status).toUpperCase(Locale.ROOT).contains("FAIL");
    }

    private static boolean isNotRun(String status) {
        String value = safe(status).toUpperCase(Locale.ROOT);
        return value.contains("NOT RUN")
                || value.contains("NOT EXECUTED")
                || value.equals("N/A");
    }

    private static String displayStatus(String status) {
        String value = safe(status);

        if (isPartial(value)) {
            return "PARTIAL";
        }

        if (isFailed(value)) {
            return "FAILED";
        }

        if (isNotRun(value)) {
            return "NOT RUN";
        }

        if (isSuccessful(value)) {
            String upper = value.toUpperCase(Locale.ROOT);
            return upper.contains("COMPLETED") ? "COMPLETED" : "SUCCESSFUL";
        }

        return value.isBlank() ? "NOT CAPTURED" : value.toUpperCase(Locale.ROOT);
    }

    private static String formatDateTime(String value) {
        String text = safe(value);

        if (text.isBlank() || "NOT CAPTURED".equalsIgnoreCase(text)) {
            return "NOT CAPTURED";
        }

        for (DateTimeFormatter formatter : INPUT_FORMATS) {
            try {
                return LocalDateTime.parse(text, formatter)
                        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH));
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
