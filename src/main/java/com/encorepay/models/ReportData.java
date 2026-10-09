package com.encorepay.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ReportData {

    private static final String POST_RECEIPTS = "Post Receipts Job";
    private static final String COLLECTIONS = "Encore Download Collection Items Job";
    private static final String UPCOMING = "Encore Up Coming Demands Job";

    private final List<JobStatus> statuses;
    private final List<String> clientFailures;
    private final List<String> configuredClients;
    private final LocalDateTime reportTime;

    private int totalClients;
    private int monitoredClients;
    private long successfulJobs;
    private long failedJobs;
    private long partialJobs;
    private long attentionJobs;
    private int totalFailedReceipts;
    private int totalPendingReceipts;
    private final Map<String, ClientHealth> clientHealth = new LinkedHashMap<>();
    private final List<JobStatus> postReceipts = new ArrayList<>();
    private final List<JobStatus> collectionJobs = new ArrayList<>();
    private final List<JobStatus> upcomingJobs = new ArrayList<>();
    private final Map<String, Map<String, Integer>> failedReceiptReasonsByClient = new LinkedHashMap<>();

    private ReportData(List<JobStatus> statuses, List<String> clientFailures, List<String> configuredClients) {
        this.statuses = statuses == null ? List.of() : statuses.stream().filter(s -> s != null).toList();
        this.clientFailures = clientFailures == null ? List.of() : clientFailures;
        this.configuredClients = configuredClients == null ? List.of() : configuredClients.stream()
                .filter(c -> c != null && !c.isBlank()).distinct().toList();
        this.reportTime = LocalDateTime.now();
        compute();
    }

    public static ReportData from(List<JobStatus> statuses, List<String> clientFailures, List<String> configuredClients) {
        return new ReportData(statuses, clientFailures, configuredClients);
    }

    private void compute() {
        List<String> configured = new ArrayList<>(configuredClients);
        if (configured.isEmpty()) {
            configured = statuses.stream()
                    .map(JobStatus::getClientName)
                    .filter(c -> c != null && !c.isBlank())
                    .distinct()
                    .toList();
        }
        totalClients = configured.size();

        successfulJobs = statuses.stream().filter(job -> !job.isSynthetic()).filter(JobStatus::isSuccessful).count();
        failedJobs = statuses.stream().filter(job -> !job.isSynthetic()).filter(JobStatus::isFailed).count();
        partialJobs = statuses.stream().filter(job -> !job.isSynthetic()).filter(JobStatus::isPartialSuccess).count();
        // Count only actual unresolved execution outcomes. Plain N/A is not a
        // business failure; it is a missing/optional execution status.
        attentionJobs = statuses.stream()
                .filter(job -> !job.isSynthetic())
                .filter(job -> job.isPartialSuccess() || job.isUnresolvedStatus())
                .count();

        totalFailedReceipts = statuses.stream()
                .filter(s -> !s.isSynthetic())
                .filter(s -> POST_RECEIPTS.equalsIgnoreCase(safe(s.getJobName())))
                .mapToInt(JobStatus::getFailedCount)
                .filter(v -> v >= 0)
                .sum();

        totalPendingReceipts = statuses.stream()
                .filter(s -> !s.isSynthetic())
                .filter(s -> POST_RECEIPTS.equalsIgnoreCase(safe(s.getJobName())))
                .mapToInt(JobStatus::getPendingCount)
                .filter(v -> v >= 0)
                .sum();

        buildClientHealth(configured);
        monitoredClients = (int) statuses.stream()
                .filter(job -> !job.isSynthetic())
                .map(JobStatus::getClientName)
                .map(ReportData::safe)
                .filter(client -> !client.isBlank())
                .distinct()
                .count();

        for (JobStatus job : statuses) {
            String jobName = safe(job.getJobName());
            if (POST_RECEIPTS.equalsIgnoreCase(jobName)) {
                postReceipts.add(job);
            } else if (COLLECTIONS.equalsIgnoreCase(jobName)) {
                collectionJobs.add(job);
            } else if (UPCOMING.equalsIgnoreCase(jobName)) {
                upcomingJobs.add(job);
            }
        }

        buildFailedReceiptReasonsByClient();
    }

    private void buildClientHealth(List<String> configured) {
        for (String client : configured) {
            clientHealth.put(client, new ClientHealth(client));
        }

        for (JobStatus job : statuses) {
            String client = safe(job.getClientName());
            if (client.isBlank()) continue;

            // Synthetic JobStatus records are client-level fallback placeholders
            // created when login/application monitoring fails before a real job
            // was inspected. They must never be presented as if the failure
            // happened inside that job (for example: "Post Receipts: Server is down").
            // The real client-level failure is carried separately in clientFailures.
            if (job.isSynthetic()) {
                continue;
            }

            ClientHealth health = clientHealth.computeIfAbsent(client, ClientHealth::new);

            // Client state must reflect the real application job outcome.
            // N/A / NOT_CAPTURED / NO_STATUS means the optional execution
            // status was unavailable; it is not itself a client failure.
            // Explicit FAILED remains FAILED, while PARTIAL remains an
            // attention-worthy real job outcome. Explicit client-level
            // monitoring failures are handled separately below.
            if (job.isFailed()) {
                health.state = ClientState.FAILED;
            } else if ((job.isPartialSuccess() || job.isUnresolvedStatus())
                    && health.state != ClientState.FAILED) {
                health.state = ClientState.ATTENTION;
            }

            String reason = safe(job.getJobFailureReason());
            if (job.isNotRun() && !reason.isBlank() && health.state != ClientState.FAILED) {
                // An optional job that is genuinely not configured/available is
                // not a client failure. The report still shows N/A and its reason.
                boolean optionalUnavailable = UPCOMING.equalsIgnoreCase(safe(job.getJobName()))
                        && reason.equalsIgnoreCase("Upcoming Demand Job not available");
                if (!optionalUnavailable) {
                    // N/A with an explicit capture/automation error is a real
                    // monitoring problem; plain N/A without an error is not.
                    health.state = ClientState.ATTENTION;
                }
            }
            if (!reason.isBlank() && !job.isSuccessful()) {
                String concise = conciseMonitoringReason(reason);
                if (!concise.isBlank()) {
                    String detail = jobLabel(job.getJobName()) + ": " + concise;
                    addOnce(health.details, detail);
                }
            }

            String validation = safe(job.getValidationMessage());
            if (!validation.isBlank()) {
                if (health.state != ClientState.FAILED
                        && !isUnscopedReceiptCountMismatch(validation)) {
                    health.state = ClientState.ATTENTION;
                }
                String detail = "Validation: " + truncate(validation, 140);
                addOnce(health.details, detail);
            }
        }

        if (clientFailures != null) {
            for (String failure : clientFailures) {
                String value = safe(failure);
                if (value.isBlank()) continue;

                String client = extractFailureClient(value);
                if (client.isBlank()) continue;

                ClientHealth health = clientHealth.computeIfAbsent(client, ClientHealth::new);
                if (health.state != ClientState.FAILED) {
                    health.state = ClientState.ATTENTION;
                }

                String detail = extractClientFailureMessage(value);
                if (!detail.isBlank() && !containsDetailIgnoreCase(health.details, detail)) {
                    addOnce(health.details, detail);
                }
            }
        }

        for (ClientHealth health : clientHealth.values()) {
            List<JobStatus> clientJobs = statuses.stream()
                    .filter(job -> health.client.equalsIgnoreCase(safe(job.getClientName())))
                    .toList();

            // NOT_RUN is reserved for clients that produced no job entries at
            // all. A client whose jobs were attempted but whose execution
            // status could not be captured (N/A with a capture note) was still
            // monitored: the job exists and its receipt counts are real data.
            // Such clients remain monitored; unavailable execution status is
            // represented by the job itself and does not poison client health.
            if (clientJobs.isEmpty()) {
                health.state = ClientState.NOT_RUN;
            }
        }
    }

    private void buildFailedReceiptReasonsByClient() {
        for (JobStatus job : postReceipts) {
            int failed = job.getFailedCount();
            if (failed <= 0) continue;

            String client = safe(job.getClientName());
            if (client.isBlank()) client = "Unknown Client";

            Map<String, Integer> target =
                    failedReceiptReasonsByClient.computeIfAbsent(client, k -> new LinkedHashMap<>());

            Map<String, Integer> grouped = groupReceiptReasons(job);
            int captured = 0;

            for (Map.Entry<String, Integer> entry : grouped.entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0) continue;
                target.merge(entry.getKey(), entry.getValue(), Integer::sum);
                captured += entry.getValue();
            }

            // Reporting must never make a failed receipt disappear. If the
            // captured reason counts do not reconcile with the job's failed
            // count, expose the exact gap instead of silently dropping it.
            int missing = failed - captured;
            if (missing > 0) {
                target.merge("REASON_NOT_CAPTURED", missing, Integer::sum);
            }
        }
    }

    private Map<String, Integer> groupReceiptReasons(JobStatus job) {
        Map<String, Integer> grouped = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : job.getFailureReasonCounts().entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) continue;
            String normalized = normalizeReceiptReason(entry.getKey());
            if (normalized.isBlank()) continue;
            grouped.merge(normalized, entry.getValue(), Integer::sum);
        }
        return grouped;
    }

    private String normalizeReceiptReason(String reason) {
        String clean = safe(reason);
        if (clean.isBlank()) return "";

        clean = clean.replaceAll("https?://\\S+", "");
        clean = clean.replaceAll("\\b\\d{4}-\\d{2}-\\d{2}\\b", "");
        clean = clean.replaceAll("\\s+for\\s+[A-Za-z0-9_-]{6,}(?=\\s|$)", "");
        clean = clean.replaceAll("\\s+[A-Za-z]{0,6}[0-9]{6,}(?=\\s|$)", "");
        clean = clean.replaceAll("\\s+", " ").trim();

        String lower = clean.toLowerCase(Locale.ROOT);

        if (lower.contains("500 internal server error")) return "500 Internal Server Error";
        if (lower.contains("value date") && lower.contains("cannot be after transaction date"))
            return "Value Date cannot be after transaction date";
        if (lower.contains("preclose account") && lower.contains("instead of current payment"))
            return "Preclose account instead of current payment";
        if (lower.contains("timeoutexception")) return "Transaction timeout";
        if (lower.contains("connection refused")) return "Connection refused";
        if (lower.contains("service unavailable")) return "Service temporarily unavailable";

        return clean;
    }

    public List<ClientException> getClientExceptions() {
        return clientHealth.values().stream()
                .filter(h -> h.state != ClientState.HEALTHY)
                .map(h -> new ClientException(h.client, h.state, h.details))
                .toList();
    }

    public String getFormattedReportTime(DateTimeFormatter formatter) {
        return reportTime.format(formatter);
    }

    // Getters
    public int getTotalClients() { return totalClients; }
    public int getMonitoredClients() { return monitoredClients; }
    public long getSuccessfulJobs() { return successfulJobs; }
    public long getFailedJobs() { return failedJobs; }
    public long getPartialJobs() { return partialJobs; }
    public long getAttentionJobs() { return attentionJobs; }
    public int getTotalFailedReceipts() { return totalFailedReceipts; }
    public int getTotalPendingReceipts() { return totalPendingReceipts; }
    public List<JobStatus> getPostReceipts() { return Collections.unmodifiableList(postReceipts); }
    public List<JobStatus> getCollectionJobs() { return Collections.unmodifiableList(collectionJobs); }
    public List<JobStatus> getUpcomingJobs() { return Collections.unmodifiableList(upcomingJobs); }
    public Map<String, Map<String, Integer>> getFailedReceiptReasonsByClient() {
        return Collections.unmodifiableMap(failedReceiptReasonsByClient);
    }
    public Map<String, ClientHealth> getClientHealth() { return Collections.unmodifiableMap(clientHealth); }
    public List<JobStatus> getAllStatuses() { return Collections.unmodifiableList(statuses); }
    public List<String> getConfiguredClients() { return Collections.unmodifiableList(configuredClients); }

    // Helper classes
    public enum ClientState { HEALTHY, FAILED, NOT_RUN, ATTENTION }

    public static final class ClientHealth {
        final String client;
        ClientState state = ClientState.HEALTHY;
        final List<String> details = new ArrayList<>();

        ClientHealth(String client) { this.client = client; }
    }

    public static final class ClientException {
        public final String client;
        public final ClientState state;
        public final List<String> details;

        ClientException(String client, ClientState state, List<String> details) {
            this.client = client;
            this.state = state;
            this.details = details;
        }
    }

    // Static helpers (match existing logic exactly)
    private static String safe(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String jobLabel(String jobName) {
        if (POST_RECEIPTS.equalsIgnoreCase(safe(jobName))) return "Post Receipts";
        if (COLLECTIONS.equalsIgnoreCase(safe(jobName))) return "Download Collection Items";
        if (UPCOMING.equalsIgnoreCase(safe(jobName))) return "Upcoming Demand";
        return safe(jobName);
    }

    private static boolean isUnscopedReceiptCountMismatch(String validation) {
        return safe(validation).toLowerCase(Locale.ROOT).startsWith("receipt count mismatch:");
    }

    private static String conciseMonitoringReason(String reason) {
        String clean = safe(reason);
        if (clean.isBlank()) return "";
        String lower = clean.toLowerCase(Locale.ROOT);

        if (lower.contains("execution status was not captured")
                || lower.contains("status was not captured for")
                || lower.contains("status could not be determined")) {
            return "Status not available";
        }

        return truncate(clean, 90);
    }

    private static String truncate(String value, int maxLength) {
        String clean = safe(value);
        if (clean.length() <= maxLength) return clean;
        return clean.substring(0, Math.max(1, maxLength - 1)).trim() + "...";
    }

    private static String extractFailureClient(String failure) {
        int sep = failure.indexOf(" :: ");
        return sep > 0 ? safe(failure.substring(0, sep)) : "";
    }

    private static String extractClientFailureMessage(String failure) {
        int sep = failure.indexOf(" :: ");
        if (sep <= 0) return truncate(failure, 140);
        String error = safe(failure.substring(sep + 4));
        int stepIdx = error.indexOf(" [step=");
        if (stepIdx > 0) error = error.substring(0, stepIdx);
        String lower = error.toLowerCase(Locale.ROOT);
        if (lower.contains("server is down") || lower.contains("server_down")
                || lower.contains("404 not found") || lower.contains("502 bad gateway")
                || lower.contains("503 service unavailable") || lower.contains("504 gateway timeout")) {
            return "Server is down";
        }
        if (lower.contains("login_timeout") || lower.contains("login timed out")) return "Login timeout";
        if (lower.contains("application_not_ready") || lower.contains("application not ready")) return "Application not ready";
        if (lower.contains("browser_failure") || lower.contains("browser/session failure")) return "Browser/session failure";
        if (lower.contains("403") || lower.contains("forbidden")) return "Application access rejected (403)";
        if (lower.contains("timeout")) return "Connection timeout";
        if (lower.contains("connection")) return "Connection failed";
        return truncate(error, 140);
    }

    private static boolean containsDetailIgnoreCase(List<String> details, String value) {
        String needle = safe(value).toLowerCase(Locale.ROOT);
        if (needle.isBlank()) return false;
        for (String detail : details) {
            if (safe(detail).toLowerCase(Locale.ROOT).contains(needle)) return true;
        }
        return false;
    }

    private static void addOnce(List<String> list, String value) {
        if (value != null && !value.isBlank() && !list.contains(value)) list.add(value);
    }
}