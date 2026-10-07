package com.encorepay.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JobStatus {

    private String jobName;
    private String clientName;
    private String status;
    private int failedCount;
    private int pendingCount;
    private String dateTime;
    private final Map<String, Integer> failureReasonCounts = new LinkedHashMap<>();
    private final Map<String, String> failureReasonDisplay = new LinkedHashMap<>();
    private String jobFailureReason;
    private String validationMessage;

    public JobStatus() {
    }

    public JobStatus(String jobName, String clientName, String status, int failedCount, int pendingCount, String dateTime) {
        this.jobName = jobName;
        this.clientName = clientName;
        this.status = status;
        this.failedCount = failedCount;
        this.pendingCount = pendingCount;
        this.dateTime = dateTime;
    }

    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
    public String getDateTime() { return dateTime; }
    public void setDateTime(String dateTime) { this.dateTime = dateTime; }

    /**
     * Returns each unique failure reason as a formatted string:
     * {@code [CODE] (N accounts)} or the full reason text when no bracketed code exists.
     */
    public List<String> getFailureReasons() {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : failureReasonCounts.entrySet()) {
            String display = failureReasonDisplay.getOrDefault(entry.getKey(), entry.getKey());
            result.add(display + " (" + entry.getValue() + " accounts)");
        }
        return Collections.unmodifiableList(result);
    }

    public String getJobFailureReason() { return jobFailureReason; }

    public void clearFailureReasons() {
        failureReasonCounts.clear();
        failureReasonDisplay.clear();
    }

    public void setJobFailureReason(String jobFailureReason) {
        this.jobFailureReason = jobFailureReason == null ? null : jobFailureReason.trim();
    }

    /**
     * Adds a failure reason, keyed by its bracketed error code when present,
     * otherwise by the trimmed reason text. Counts occurrences so that many
     * accounts sharing the same code collapse to a single entry.
     */
    public void addFailureReason(String reason) {
        if (reason == null || reason.isBlank()) return;

        String clean = reason.replaceAll("\\s+", " ").trim();
        String code = extractCode(clean);
        String key = code == null ? clean : code;

        failureReasonDisplay.putIfAbsent(key, clean);
        failureReasonCounts.merge(key, 1, Integer::sum);
    }

    public String getValidationMessage() { return validationMessage; }
    public void setValidationMessage(String validationMessage) { this.validationMessage = validationMessage; }

    public String getJobStatus() { return status; }
    public String getEndDateTime() { return dateTime; }

    private static String extractCode(String text) {
        if (text == null) return null;
        int start = text.indexOf('[');
        int end = text.indexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1).trim();
        }
        return null;
    }
}
