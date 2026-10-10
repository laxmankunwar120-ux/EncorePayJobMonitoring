package com.encorepay.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class JobStatus {

    public static final int UNKNOWN_COUNT = -1;
    private static final int MAX_REASON_LENGTH = 180;

    private String jobName;
    private String clientName;
    private String status;
    private int failedCount = UNKNOWN_COUNT;
    private int pendingCount = UNKNOWN_COUNT;
    private String dateTime;
    private final Map<String, Integer> failureReasonCounts = new LinkedHashMap<>();
    private String jobFailureReason;
    private String validationMessage;

    private boolean synthetic;

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

    public JobStatus copy() {
        JobStatus copy = new JobStatus();
        copy.jobName = this.jobName;
        copy.clientName = this.clientName;
        copy.status = this.status;
        copy.failedCount = this.failedCount;
        copy.pendingCount = this.pendingCount;
        copy.dateTime = this.dateTime;
        copy.jobFailureReason = this.jobFailureReason;
        copy.validationMessage = this.validationMessage;
        copy.synthetic = this.synthetic;
        copy.failureReasonCounts.putAll(this.failureReasonCounts);
        return copy;
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

    public List<String> getFailureReasons() {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : failureReasonCounts.entrySet()) {
            result.add(entry.getKey() + " (" + entry.getValue() + " receipts)");
        }
        return Collections.unmodifiableList(result);
    }

    public String getJobFailureReason() { return jobFailureReason; }

    public void clearFailureReasons() { failureReasonCounts.clear(); }

    public void setJobFailureReason(String jobFailureReason) {
        this.jobFailureReason = jobFailureReason == null ? null : trimReason(jobFailureReason);
    }

    public void addFailureReason(String reason) {
        addFailureReason(reason, 1);
    }

    public void addFailureReason(String reason, int count) {
        if (reason == null || reason.isBlank() || count <= 0) return;
        String clean = trimReason(reason);
        failureReasonCounts.merge(clean, count, Integer::sum);
    }

    public String getValidationMessage() { return validationMessage; }
    public void setValidationMessage(String validationMessage) { this.validationMessage = validationMessage; }

    public boolean isSynthetic() { return synthetic; }
    public void setSynthetic(boolean synthetic) { this.synthetic = synthetic; }

    public Map<String, Integer> getFailureReasonCounts() {
        return Collections.unmodifiableMap(failureReasonCounts);
    }

    public int getTotalFailureReasonCount() {
        int total = 0;
        for (Integer count : failureReasonCounts.values()) {
            if (count != null) total += count;
        }
        return total;
    }

    public boolean isFailureReasonCountReconciled() {
        if (!"Post Receipts Job".equalsIgnoreCase(clean(jobName))) return true;
        if (failedCount < 0) return false;
        return getTotalFailureReasonCount() == failedCount;
    }

    public String getRawStatus() {
        return status == null ? "" : status.trim();
    }

    public boolean isSuccessful() {
        String v = getRawStatus().toUpperCase(Locale.ROOT).replace(' ', '_');
        return !v.contains("UNSUCCESS")
                && (v.contains("SUCCESS") || v.contains("COMPLETED") || v.equals("SUCCEEDED"))
                && !v.contains("PARTIAL");
    }

    public boolean isFailed() {
        String v = getRawStatus().toUpperCase(Locale.ROOT).replace(' ', '_');
        return v.contains("FAIL") && !v.contains("PARTIAL");
    }

    public boolean isPartialSuccess() {
        String v = getRawStatus().toUpperCase(Locale.ROOT).replace(' ', '_');
        return v.equals("PARTIALLY_SUCCESSFUL");
    }

    public boolean isNotRun() {
        String v = getRawStatus().toUpperCase(Locale.ROOT).replace(' ', '_');
        return v.isBlank() || v.equals("N/A") || v.equals("NOT_CAPTURED") || v.equals("NO_STATUS");
    }

    public boolean requiresAttention() {
        return isFailed() || isPartialSuccess() || isNotRun() || isUnresolvedStatus();
    }

    public boolean isUnresolvedStatus() {
        String v = getRawStatus().toUpperCase(Locale.ROOT).replace(' ', '_');
        return !v.isBlank() && !isSuccessful() && !isFailed() && !isPartialSuccess() && !isNotRun();
    }

    public String getDisplayStatus() {
        String raw = getRawStatus();
        if (raw.isBlank()) return "N/A";
        return raw;
    }

    private static String trimReason(String text) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").trim();
        if (clean.length() <= MAX_REASON_LENGTH) return clean;
        return clean.substring(0, MAX_REASON_LENGTH - 3) + "...";
    }

    public static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    @Override
    public String toString() {
        return "JobStatus{jobName='" + jobName + "', clientName='" + clientName
                + "', status='" + status + "', failedCount=" + failedCount
                + ", pendingCount=" + pendingCount + ", dateTime='" + dateTime + "'}";
    }
}
