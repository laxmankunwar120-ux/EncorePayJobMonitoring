package com.encorepay.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JobStatus {

    private String jobName;
    private String clientName;
    private String status;
    private int failedCount;
    private int pendingCount;
    private String dateTime;
    private final List<String> failureReasons = new ArrayList<>();
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
    public List<String> getFailureReasons() { return Collections.unmodifiableList(failureReasons); }

    public String getJobFailureReason() { return jobFailureReason; }

    public void setJobFailureReason(String jobFailureReason) {
        this.jobFailureReason = jobFailureReason == null ? null : jobFailureReason.trim();
    }

    public void addFailureReason(String reason) {
        if (reason == null || reason.isBlank()) return;
        String clean = reason.trim();
        if (failureReasons.stream().noneMatch(x -> x.equalsIgnoreCase(clean))) {
            failureReasons.add(clean);
        }
    }

    public String getValidationMessage() { return validationMessage; }
    public void setValidationMessage(String validationMessage) { this.validationMessage = validationMessage; }

    public String getJobStatus() { return status; }
    public String getEndDateTime() { return dateTime; }
}
