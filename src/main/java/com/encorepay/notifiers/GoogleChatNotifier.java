package com.encorepay.notifiers;

import com.encorepay.models.JobStatus;
import com.encorepay.utilities.ConfigReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GoogleChatNotifier {

    private static final String POST_RECEIPTS = "Post Receipts Job";
    private static final String COLLECTIONS = "Encore Download Collection Items Job";
    private static final String UPCOMING = "Encore Up Coming Demands Job";

    private static final DateTimeFormatter REPORT_TIME =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a", Locale.ENGLISH);

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
                : statuses.stream()
                        .filter(s -> s != null)
                        .toList();

        List<String> clients = resolveClients(jobs, configuredClients);
        Set<String> monitoredClients = resolveMonitoredClients(jobs);

        long successfulJobs = jobs.stream()
                .filter(s -> isSuccessful(s.getStatus()))
                .count();

        long failedJobs = jobs.stream()
                .filter(s -> isFailed(s.getStatus()) || isPartial(s.getStatus()))
                .count();

        long noStatusJobs = jobs.stream()
                .filter(s -> isNoStatus(s.getStatus()))
                .count();

        int monitoringFailed = 0;
        for (String client : clients) {
            if (!monitoredClients.contains(client)) {
                monitoringFailed++;
            }
        }

        int monitored = Math.max(0, clients.size() - monitoringFailed);

        StringBuilder message = new StringBuilder();

        message.append("*ENCOREPAY JOB MONITORING REPORT*\\n")
                .append("Generated: ")
                .append(LocalDateTime.now(new ConfigReader().getBusinessZone()).format(REPORT_TIME))
                .append("\\n\\n");

        message.append("*Summary*\\n")
                .append("Total Clients: ").append(clients.size())
                .append(" | Monitored: ").append(monitored)
                .append(" | Successful: ").append(successfulJobs)
                .append(" | Failed: ").append(failedJobs)
                .append(" | No Status: ").append(noStatusJobs)
                .append(" | Monitoring Failed: ").append(monitoringFailed)
                .append("\\n\\n");

        appendPostReceiptSection(message, clients, jobs);
        appendFailureReasonsSection(message, clients, jobs);
        appendSimpleJobSection(message, "*2. DOWNLOAD COLLECTION ITEM JOB*", clients, jobs, COLLECTIONS, false);
        appendSimpleJobSection(message, "*3. UPCOMING DEMAND JOB*", clients, jobs, UPCOMING, true);
        appendAutomationIssues(message, clients, jobs, clientFailures, monitoredClients);
        appendReportStatus(message, jobs, clientFailures, monitoringFailed, noStatusJobs);

        return message.toString().trim();
    }

    private static Set<String> resolveMonitoredClients(List<JobStatus> jobs) {
        Set<String> monitored = new LinkedHashSet<>();

        for (JobStatus job : jobs) {
            if (job == null) continue;

            String client = clean(job.getClientName());
            String jobName = clean(job.getJobName());

            if (!client.isBlank()
                    && (POST_RECEIPTS.equalsIgnoreCase(jobName)
                    || COLLECTIONS.equalsIgnoreCase(jobName))) {
                monitored.add(client);
            }
        }

        return monitored;
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

    private static void appendPostReceiptSection(
            StringBuilder message,
            List<String> clients,
            List<JobStatus> jobs) {

        message.append("*1. POST RECEIPT JOB*\\n");
        message.append("```\\n");
        message.append(String.format(
                "%-17s %-12s %9s %9s  %-20s%n",
                "Client", "Status", "Failed", "Pending", "Date & Time"));
        message.append("---------------------------------------------------------------\\n");

        for (String client : clients) {
            JobStatus status = findJob(jobs, client, POST_RECEIPTS);

            if (status == null) {
                message.append(String.format(
                        "%-17s %-12s %9s %9s  %-20s%n",
                        abbreviate(client, 17), "NO STATUS", "-", "-", "-"));
                continue;
            }

            String value = displayStatus(status.getStatus());
            String dateTime = formatDateTime(status.getDateTime());

            String failed = isNoStatus(status.getStatus())
                    ? "-"
                    : String.valueOf(status.getFailedCount());

            String pending = isNoStatus(status.getStatus())
                    ? "-"
                    : String.valueOf(status.getPendingCount());

            if (dateTime.isBlank() || "NOT CAPTURED".equalsIgnoreCase(dateTime)) {
                dateTime = "-";
            }

            message.append(String.format(
                    "%-17s %-12s %9s %9s  %-20s%n",
                    abbreviate(client, 17),
                    abbreviate(value, 12),
                    failed,
                    pending,
                    abbreviate(dateTime, 20)));
        }

        message.append("```\\n");
    }

    private static void appendFailureReasonsSection(
            StringBuilder message,
            List<String> clients,
            List<JobStatus> jobs) {

        List<String> lines = new ArrayList<>();

        for (String client : clients) {
            JobStatus post = findJob(jobs, client, POST_RECEIPTS);

            if (post == null || post.getFailedCount() <= 0) {
                continue;
            }

            List<String> reasons = post.getFailureReasons() == null
                    ? List.of()
                    : post.getFailureReasons();

            if (!reasons.isEmpty()) {
                for (String reason : new LinkedHashSet<>(reasons)) {
                    String value = abbreviate(cleanReason(reason), MAX_REASON_LENGTH);
                    if (!value.isBlank()) {
                        lines.add("- " + client + ": " + value);
                    }
                }
            } else {
                String fallback = cleanReason(post.getJobFailureReason());
                if (fallback.isBlank()) {
                    fallback = "no receipt-level reason captured";
                }
                lines.add("- " + client + ": " + abbreviate(fallback, MAX_REASON_LENGTH));
            }
        }

        if (lines.isEmpty()) return;

        message.append("\\n*Failure Reasons*\\n");
        for (String line : lines) {
            message.append(line).append("\\n");
        }
    }

    private static void appendSimpleJobSection(
            StringBuilder message,
            String title,
            List<String> clients,
            List<JobStatus> jobs,
            String jobName,
            boolean optional) {

        message.append("\\n").append(title).append("\\n");
        message.append("```\\n");
        message.append(String.format(
                "%-17s  %-12s    %-20s%n",
                "Client", "Status", "Date & Time"));
        message.append("--------------------------------------------------------\\n");

        for (String client : clients) {
            JobStatus status = findJob(jobs, client, jobName);

            if (status == null && optional) {
                message.append(String.format(
                        "%-17s  %-12s    %-20s%n",
                        abbreviate(client, 17),
                        "N/A",
                        "Not configured"));
                continue;
            }

            if (status == null) {
                message.append(String.format(
                        "%-17s  %-12s    %-20s%n",
                        abbreviate(client, 17),
                        "NO STATUS",
                        "-"));
                continue;
            }

            String value = displayStatus(status.getStatus());
            String dateTime = formatDateTime(status.getDateTime());

            if (dateTime.isBlank() || "NOT CAPTURED".equalsIgnoreCase(dateTime)) {
                dateTime = "-";
            }

            message.append(String.format(
                    "%-17s  %-12s    %-20s%n",
                    abbreviate(client, 17),
                    abbreviate(value, 12),
                    abbreviate(dateTime, 20)));
        }

        message.append("```\\n");
    }

    private static JobStatus findJob(List<JobStatus> jobs, String client, String jobName) {
        return jobs.stream()
                .filter(s -> client.equalsIgnoreCase(safe(s.getClientName())))
                .filter(s -> jobName.equalsIgnoreCase(safe(s.getJobName())))
                .findFirst()
                .orElse(null);
    }

    private static void appendAutomationIssues(
            StringBuilder message,
            List<String> clients,
            List<JobStatus> jobs,
            List<String> clientFailures,
            Set<String> monitoredClients) {

        Set<String> issues = new LinkedHashSet<>();

        for (String client : clients) {
            if (!monitoredClients.contains(client)) {
                String failure = failureForClient(client, clientFailures);
                if (failure.isBlank()) {
                    failure = "No monitoring result was produced for this client.";
                }
                issues.add(client + ": " + failure);
            }
        }

        for (JobStatus status : jobs) {
            if (status == null) continue;

            String reason = cleanReason(status.getJobFailureReason());
            if (reason.isBlank()) continue;

            if (isNoStatus(status.getStatus())
                    || isFailed(status.getStatus())
                    || isPartial(status.getStatus())) {

                if (POST_RECEIPTS.equalsIgnoreCase(clean(status.getJobName()))
                        && status.getFailedCount() > 0) {
                    continue;
                }

                issues.add(
                        clean(status.getClientName())
                                + " - "
                                + clean(status.getJobName())
                                + ": "
                                + reason);
            }
        }

        if (clientFailures != null) {
            for (String failure : clientFailures) {
                String value = cleanReason(failure);

                if (value.isBlank()) continue;

                String upper = value.toUpperCase(Locale.ROOT);
                if (upper.startsWith("REPORT GENERATION")
                        || upper.startsWith("NOTIFICATION")
                        || upper.startsWith("CLIENT COVERAGE")) {
                    continue;
                }

                String client = failureClient(value);
                if (!client.isBlank() && clients.contains(client) && !monitoredClients.contains(client)) {
                    continue;
                }

                issues.add(value);
            }
        }

        if (issues.isEmpty()) return;

        message.append("\\n*Automation Issues*\\n");
        for (String issue : issues) {
            message.append(issue).append("\\n");
        }
    }

    private static void appendReportStatus(
            StringBuilder message,
            List<JobStatus> jobs,
            List<String> clientFailures,
            int monitoringFailed,
            long noStatusJobs) {

        boolean actionRequired = monitoringFailed > 0 || noStatusJobs > 0;

        for (JobStatus status : jobs) {
            if (status != null
                    && (isFailed(status.getStatus()) || isPartial(status.getStatus()))) {
                actionRequired = true;
                break;
            }
        }

        if (!actionRequired && clientFailures != null) {
            for (String failure : clientFailures) {
                if (failure == null || failure.isBlank()) continue;

                String upper = failure.toUpperCase(Locale.ROOT);
                if (!upper.startsWith("REPORT GENERATION")
                        && !upper.startsWith("NOTIFICATION")) {
                    actionRequired = true;
                    break;
                }
            }
        }

        message.append("\\n*Report Status:* ")
                .append(actionRequired ? "ACTION REQUIRED" : "COMPLETED")
                .append("\\n");
    }

    private static String failureForClient(
            String client,
            List<String> clientFailures) {

        if (clientFailures == null) return "";

        for (String failure : clientFailures) {
            String value = cleanReason(failure);

            if (client.equalsIgnoreCase(failureClient(value))) {
                return failureDetail(value);
            }
        }

        return "";
    }

    private static String failureClient(String failure) {
        int separator = failure.indexOf(" :: ");

        return separator > 0
                ? clean(failure.substring(0, separator))
                : "";
    }

    private static String failureDetail(String failure) {
        int separator = failure.indexOf(" :: ");

        return separator > 0
                ? cleanReason(failure.substring(separator + 4))
                : cleanReason(failure);
    }

    private static boolean containsHardFailure(List<JobStatus> jobs, List<String> clientFailures) {
        if (clientFailures != null) {
            for (String failure : clientFailures) {
                if (failure == null || failure.isBlank()) continue;
                String normalized = failure.toUpperCase(Locale.ROOT);
                if (!normalized.startsWith("REPORT GENERATION")
                        && !normalized.startsWith("NOTIFICATION")) {
                    return true;
                }
            }
        }

        for (JobStatus status : jobs) {
            if (status == null) continue;
            String value = safe(status.getStatus()).toUpperCase(Locale.ROOT);
            if (value.contains("FAIL") && status.getFailedCount() <= 0) {
                return true;
            }
        }

        return false;
    }

    private static boolean isSuccessful(String status) {
        String value = clean(status).toUpperCase(Locale.ROOT);
        return value.contains("SUCCESS")
                || value.contains("COMPLETED")
                || value.equals("SUCCEEDED");
    }

    private static boolean isPartial(String status) {
        return clean(status).toUpperCase(Locale.ROOT).contains("PARTIAL");
    }

    private static boolean isFailed(String status) {
        return clean(status).toUpperCase(Locale.ROOT).contains("FAIL");
    }

    private static boolean isNoStatus(String status) {
        String value = clean(status).toUpperCase(Locale.ROOT);

        return value.isBlank()
                || value.contains("NO STATUS")
                || value.contains("NOT CAPTURED")
                || value.contains("NOT RUN")
                || value.contains("NOT EXECUTED");
    }

    private static String displayStatus(String status) {
        String value = clean(status);

        if (isNoStatus(value)) return "NO STATUS";
        if (isPartial(value)) return "PARTIAL";
        if (isFailed(value)) return "FAILED";

        if (isSuccessful(value)) {
            return value.toUpperCase(Locale.ROOT).contains("COMPLETED")
                    ? "COMPLETED"
                    : "SUCCESSFUL";
        }

        return value.isBlank() ? "NO STATUS" : value.toUpperCase(Locale.ROOT);
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
        return clean(reason)
                .replaceAll("(?i)Receipt capture incomplete:\\s*", "")
                .replaceAll("(?i)Receipt failure reason fallback incomplete:\\s*", "")
                .replaceAll("(?s)\\[[^\\]]*nested exception:", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String clean(String value) {
        return value == null
                ? ""
                : value.trim()
                        .replace("\n", " ")
                        .replace("\r", " ")
                        .replaceAll("\\s+", " ");
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
