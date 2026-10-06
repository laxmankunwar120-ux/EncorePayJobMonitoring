package com.encorepay.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JobStatus {

    private static final int MAX_REASON_LENGTH = 180;

    private String jobName;
    private String clientName;
    private String status;
    private int failedCount;
    private int pendingCount;
    private String dateTime;
    private final Map<String, Integer> failureReasonCounts = new LinkedHashMap<>();
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

    
    public List<String> getFailureReasons() {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : failureReasonCounts.entrySet()) {
            String code = entry.getKey();
            int count = entry.getValue();
            String bracket = extractCode(code);
            if (bracket != null) {
                result.add(bracket + " (" + count + " receipts)");
            } else {
                result.add(trimReason(code) + " (" + count + " receipts)");
            }
        }
        return Collections.unmodifiableList(result);
    }

    public String getJobFailureReason() { return jobFailureReason; }

    public void clearFailureReasons() { failureReasonCounts.clear(); }

    public void setJobFailureReason(String jobFailureReason) {
        this.jobFailureReason = jobFailureReason == null ? null : trimReason(jobFailureReason);
    }

    
    public void addFailureReason(String reason) {
        if (reason == null || reason.isBlank()) return;
        String clean = trimReason(reason);
        String code = extractCode(clean);
        String key = (code != null && !code.isBlank()) ? code : clean;
        failureReasonCounts.merge(key, 1, Integer::sum);
    }

public String getValidationMessage() { return validationMessage; }
    public void setValidationMessage(String validationMessage) { this.validationMessage = validationMessage; }

    public Map<String, Integer> getFailureReasonCounts() {
        return Collections.unmodifiableMap(failureReasonCounts);
    }

    public String getJobStatus() { return status; }
    public String getEndDateTime() { return dateTime; }

    private static String trimReason(String text) {
        if (text == null) return null;
        String clean = text.replaceAll("\\s+", " ").trim();
        if (clean.length() <= MAX_REASON_LENGTH) return clean;
        return clean.substring(0, MAX_REASON_LENGTH - 3) + "...";
    }

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


