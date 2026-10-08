package com.encorepay.models;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

public class JobStatusReportDataTest {

    @Test
    public void jobStatusClassifiesTerminalAndMissingStates() {
        JobStatus success = job("SUCCESS");
        JobStatus failed = job("FAILED");
        JobStatus partial = job("PARTIALLY_SUCCESSFUL");
        JobStatus notRun = job("N/A");
        JobStatus unresolved = job("RUNNING");

        assertTrue(success.isSuccessful());
        assertFalse(success.isFailed());

        assertTrue(failed.isFailed());
        assertTrue(failed.requiresAttention());

        assertTrue(partial.isPartialSuccess());
        assertTrue(partial.requiresAttention());

        assertTrue(notRun.isNotRun());
        assertTrue(notRun.requiresAttention());

        assertTrue(unresolved.isUnresolvedStatus());
        assertTrue(unresolved.requiresAttention());
    }

    @Test
    public void receiptFailureReasonsMustExactlyReconcile() {
        JobStatus post = job("SUCCESS");
        post.setJobName("Post Receipts Job");
        post.setFailedCount(3);

        post.addFailureReason("500 Internal Server Error", 2);
        assertEquals(post.getTotalFailureReasonCount(), 2);
        assertFalse(post.isFailureReasonCountReconciled());

        post.addFailureReason("Connection refused", 1);
        assertEquals(post.getTotalFailureReasonCount(), 3);
        assertTrue(post.isFailureReasonCountReconciled());

        JobStatus noFailures = job("SUCCESS");
        noFailures.setJobName("Post Receipts Job");
        noFailures.setFailedCount(0);
        assertTrue(noFailures.isFailureReasonCountReconciled());

        JobStatus unknown = job("SUCCESS");
        unknown.setJobName("Post Receipts Job");
        unknown.setFailedCount(JobStatus.UNKNOWN_COUNT);
        assertFalse(unknown.isFailureReasonCountReconciled());

        JobStatus otherJob = job("SUCCESS");
        otherJob.setJobName("Encore Download Collection Items Job");
        assertTrue(otherJob.isFailureReasonCountReconciled());
    }

    @Test
    public void reportDataDoesNotInferJobFailureFromFailedReceiptCount() {
        JobStatus post = job("SUCCESS");
        post.setJobName("Post Receipts Job");
        post.setFailedCount(2);
        post.setPendingCount(1);
        post.addFailureReason("500 Internal Server Error", 2);

        ReportData data = ReportData.from(
                List.of(post),
                List.of(),
                List.of("SARVAGRAM"));

        assertEquals(data.getSuccessfulJobs(), 1);
        assertEquals(data.getFailedJobs(), 0);
        assertEquals(data.getTotalFailedReceipts(), 2);
        assertEquals(data.getTotalPendingReceipts(), 1);

        Map<String, Map<String, Integer>> reasons = data.getFailedReceiptReasonsByClient();
        assertEquals(reasons.get("SARVAGRAM").get("500 Internal Server Error").intValue(), 2);
    }

    @Test
    public void reportDataMarksReceiptValidationGapAsAttention() {
        JobStatus post = job("SUCCESS");
        post.setJobName("Post Receipts Job");
        post.setFailedCount(4);
        post.addFailureReason("Connection refused", 3);
        post.setValidationMessage("Receipt failure reason reconciliation failed: captured 3 of 4 FAILED receipts.");

        ReportData data = ReportData.from(
                List.of(post),
                List.of(),
                List.of("CONATUS"));

        ReportData.ClientHealth health = data.getClientHealth().get("CONATUS");
        assertEquals(health.state, ReportData.ClientState.ATTENTION);
        assertTrue(health.details.stream().anyMatch(x -> x.contains("Validation:")));
        assertEquals(
                data.getFailedReceiptReasonsByClient().get("CONATUS").get("REASON_NOT_CAPTURED").intValue(),
                1);
    }

    @Test
    public void clientLevelLoginTimeoutIsNotReportedAsJobFailure() {
        JobStatus syntheticPost = job("N/A");
        syntheticPost.setJobName("Post Receipts Job");
        syntheticPost.setSynthetic(true);

        ReportData data = ReportData.from(
                List.of(syntheticPost),
                List.of("CONATUS :: Login timeout"),
                List.of("CONATUS"));

        assertEquals(data.getFailedJobs(), 0);
        assertEquals(data.getMonitoredClients(), 0);
        assertEquals(data.getClientHealth().get("CONATUS").state, ReportData.ClientState.ATTENTION);
        assertTrue(data.getClientExceptions().stream()
                .anyMatch(x -> x.client.equals("CONATUS")
                        && x.details.stream().anyMatch(d -> d.contains("Login timeout"))));
    }

    private static JobStatus job(String status) {
        JobStatus job = new JobStatus();
        job.setClientName("SARVAGRAM");
        job.setStatus(status);
        job.setDateTime("08 Oct 2026, 10:00 AM");
        return job;
    }
}
