package com.encorepay.models;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

import com.encorepay.utilities.JobMonitoringHtmlReport;

public class PostReceiptsReconciliationTest {

    private static JobStatus post(String client, String status, int failed, int pending) {
        JobStatus job = new JobStatus();
        job.setClientName(client);
        job.setJobName("Post Receipts Job");
        job.setStatus(status);
        job.setDateTime("08 Oct 2026, 10:00 AM");
        job.setFailedCount(failed);
        job.setPendingCount(pending);
        return job;
    }

    @Test
    public void fullySuccessfulPostReceiptsHasZeroCountsAndNoReasons() {
        JobStatus post = post("CONATUS", "SUCCESS", 0, 0);
        ReportData data = ReportData.from(List.of(post), List.of(), List.of("CONATUS"));
        assertEquals(data.getTotalFailedReceipts(), 0);
        assertEquals(data.getTotalPendingReceipts(), 0);
        assertEquals(data.getClientHealth().get("CONATUS").state, ReportData.ClientState.HEALTHY);
        assertEquals(data.getSuccessfulJobs(), 1);
    }

    @Test
    public void partialSuccessWithMultipleFailedReceiptsCountsExactlyOnce() {
        JobStatus post = post("CONATUS", "PARTIALLY_SUCCESSFUL", 3, 1);
        post.addFailureReason("Connection refused", 2);
        post.addFailureReason("500 Internal Server Error", 1);
        ReportData data = ReportData.from(List.of(post), List.of(), List.of("CONATUS"));
        assertEquals(data.getPartialJobs(), 1);
        assertEquals(data.getFailedJobs(), 0);
        assertEquals(data.getTotalFailedReceipts(), 3);
        Map<String, Integer> reasons = data.getFailedReceiptReasonsByClient().get("CONATUS");
        assertEquals(reasons.values().stream().mapToInt(Integer::intValue).sum(), 3);
    }

    @Test
    public void multipleDistinctReasonsArePreserved() {
        JobStatus post = post("CONATUS", "PARTIALLY_SUCCESSFUL", 3, 0);
        post.addFailureReason("Connection refused", 1);
        post.addFailureReason("500 Internal Server Error", 1);
        post.addFailureReason("Value Date cannot be after transaction date", 1);
        ReportData data = ReportData.from(List.of(post), List.of(), List.of("CONATUS"));
        assertEquals(data.getFailedReceiptReasonsByClient().get("CONATUS").size(), 3);
    }

    @Test
    public void repeatedIdenticalReasonsAggregateCounts() {
        JobStatus post = post("CONATUS", "PARTIALLY_SUCCESSFUL", 4, 0);
        post.addFailureReason("Connection refused", 2);
        post.addFailureReason("Connection refused", 2);
        ReportData data = ReportData.from(List.of(post), List.of(), List.of("CONATUS"));
        assertEquals(data.getFailedReceiptReasonsByClient().get("CONATUS")
                .get("Connection refused").intValue(), 4);
    }

    @Test
    public void missingReasonsAreExposedAsReasonNotCaptured() {
        JobStatus post = post("CONATUS", "PARTIALLY_SUCCESSFUL", 3, 0);
        post.addFailureReason("Connection refused", 1);
        ReportData data = ReportData.from(List.of(post), List.of(), List.of("CONATUS"));
        Map<String, Integer> reasons = data.getFailedReceiptReasonsByClient().get("CONATUS");
        assertEquals(reasons.get("REASON_NOT_CAPTURED").intValue(), 2);
        assertEquals(reasons.values().stream().mapToInt(Integer::intValue).sum(), 3);
    }

    @Test
    public void capturedUnrecognizableReasonIsNeverMislabeledAsGap() {
        JobStatus post = post("CONATUS", "PARTIALLY_SUCCESSFUL", 3, 0);
        post.addFailureReason("2026-10-10", 1);
        post.addFailureReason("Transaction 12345678 rejected 2026-10-10", 2);
        ReportData data = ReportData.from(List.of(post), List.of(), List.of("CONATUS"));
        Map<String, Integer> reasons = data.getFailedReceiptReasonsByClient().get("CONATUS");

        assertEquals(reasons.values().stream().mapToInt(Integer::intValue).sum(), 3,
                "captured reasons must sum to the failed count");
        assertFalse(reasons.containsKey("REASON_NOT_CAPTURED"),
                "a captured reason must never be mislabeled as REASON_NOT_CAPTURED");
    }

    @Test
    public void gapMarkerIsNeverNormalizedAway() {
        JobStatus post = post("CONATUS", "SUCCESS", 2, 0);
        post.addFailureReason("REASON_NOT_CAPTURED", 2);
        ReportData data = ReportData.from(List.of(post), List.of(), List.of("CONATUS"));
        assertEquals(data.getFailedReceiptReasonsByClient().get("CONATUS")
                .get("REASON_NOT_CAPTURED").intValue(), 2);
    }

    @Test
    public void multiClientResultsStayIsolated() {
        JobStatus a = post("CONATUS", "PARTIALLY_SUCCESSFUL", 2, 0);
        a.addFailureReason("Connection refused", 2);
        JobStatus b = post("SARVAGRAM", "SUCCESS", 0, 5);
        ReportData data = ReportData.from(List.of(a, b), List.of(), List.of("CONATUS", "SARVAGRAM"));
        assertEquals(data.getTotalFailedReceipts(), 2);
        assertEquals(data.getTotalPendingReceipts(), 5);
        assertTrue(data.getFailedReceiptReasonsByClient().containsKey("CONATUS"));
        assertFalse(data.getFailedReceiptReasonsByClient().containsKey("SARVAGRAM"));
    }

    @Test
    public void clientNameNormalizationIsCaseInsensitive() {
        JobStatus job = post("conatus", "PARTIALLY_SUCCESSFUL", 1, 0);
        job.addFailureReason("Connection refused", 1);
        ReportData data = ReportData.from(List.of(job), List.of(), List.of("CONATUS"));
        assertEquals(data.getClientHealth().get("CONATUS").state, ReportData.ClientState.ATTENTION);
        assertTrue(data.getFailedReceiptReasonsByClient().containsKey("CONATUS"));
    }

    @Test
    public void loginFailureProducesAttentionWithoutFalseReceiptCounts() {
        JobStatus synthetic = post("CONATUS", "N/A", JobStatus.UNKNOWN_COUNT, JobStatus.UNKNOWN_COUNT);
        synthetic.setSynthetic(true);
        ReportData data = ReportData.from(List.of(synthetic),
                List.of("CONATUS :: Login timeout"), List.of("CONATUS"));
        assertEquals(data.getFailedJobs(), 0);
        assertEquals(data.getTotalFailedReceipts(), 0);
        assertEquals(data.getClientHealth().get("CONATUS").state, ReportData.ClientState.ATTENTION);
    }

    @Test
    public void healthStatesAreCorrectAcrossClients() {
        JobStatus healthy = post("A", "SUCCESS", 0, 0);
        JobStatus attention = post("B", "PARTIALLY_SUCCESSFUL", 1, 0);
        attention.addFailureReason("Connection refused", 1);
        ReportData data = ReportData.from(List.of(healthy, attention), List.of(), List.of("A", "B", "C"));
        assertEquals(data.getClientHealth().get("A").state, ReportData.ClientState.HEALTHY);
        assertEquals(data.getClientHealth().get("B").state, ReportData.ClientState.ATTENTION);
        assertEquals(data.getClientHealth().get("C").state, ReportData.ClientState.NOT_RUN);
    }

    @Test
    public void executiveSummaryReconcilesWithDetailTable() {
        JobStatus a = post("CONATUS", "PARTIALLY_SUCCESSFUL", 2, 1);
        a.addFailureReason("Connection refused", 2);
        JobStatus b = post("SARVAGRAM", "SUCCESS", 1, 0);
        b.addFailureReason("500 Internal Server Error", 1);
        ReportData data = ReportData.from(List.of(a, b), List.of(), List.of("CONATUS", "SARVAGRAM"));
        int detailSum = data.getPostReceipts().stream()
                .filter(s -> s.getFailedCount() >= 0).mapToInt(JobStatus::getFailedCount).sum();
        assertEquals(data.getTotalFailedReceipts(), detailSum);
    }

    @Test
    public void otherJobTypesArePreserved() {
        JobStatus coll = new JobStatus();
        coll.setClientName("CONATUS");
        coll.setJobName("Encore Download Collection Items Job");
        coll.setStatus("SUCCESS");
        coll.setDateTime("08 Oct 2026, 10:00 AM");
        JobStatus job = post("CONATUS", "SUCCESS", 0, 0);
        ReportData data = ReportData.from(List.of(job, coll), List.of(), List.of("CONATUS"));
        assertEquals(data.getCollectionJobs().size(), 1);
        assertEquals(data.getPostReceipts().size(), 1);
    }

    @Test
    public void unconfiguredUpcomingDemandLeavesNoReportTrace() {

        JobStatus post = post("CONATUS", "SUCCESS", 0, 0);
        JobStatus coll = new JobStatus();
        coll.setClientName("CONATUS");
        coll.setJobName("Encore Download Collection Items Job");
        coll.setStatus("SUCCESS");
        coll.setDateTime("08 Oct 2026, 10:00 AM");
        ReportData data = ReportData.from(List.of(post, coll), List.of(), List.of("CONATUS"));
        assertTrue(data.getUpcomingJobs().isEmpty(), "no upcoming placeholder may be recorded");
        assertEquals(data.getClientHealth().get("CONATUS").state, ReportData.ClientState.HEALTHY);
        String path = JobMonitoringHtmlReport.generateCombined(
                List.of(post, coll), List.of(), List.of("CONATUS"));
        String html = readFile(path);
        assertFalse(html.contains("Upcoming Demand"), "report must skip unconfigured optional job");
        assertFalse(html.contains("not available"), "no placeholder text may leak into the report");
    }

    @Test
    public void htmlReportEscapesOutputAndShowsGapHonestly() {
        JobStatus job = post("CONATUS", "PARTIALLY_SUCCESSFUL", 2, 0);
        job.addFailureReason("Connection refused", 1);
        String path = JobMonitoringHtmlReport.generateCombined(
                List.of(job), List.of(), List.of("CONATUS"));
        String html = readFile(path);
        assertTrue(html.contains("REASON_NOT_CAPTURED"), "missing reasons must be disclosed");
        assertTrue(html.contains("PARTIALLY_SUCCESSFUL"), "partial status must be represented");
    }

    @Test
    public void concurrentRunsDoNotLeakAcrossReportDataInstances() throws Exception {
        JobStatus a = post("CONATUS", "PARTIALLY_SUCCESSFUL", 2, 0);
        a.addFailureReason("Connection refused", 2);
        JobStatus b = post("SARVAGRAM", "SUCCESS", 0, 0);
        java.util.concurrent.Callable<ReportData> t1 =
                () -> ReportData.from(List.of(a), List.of(), List.of("CONATUS"));
        java.util.concurrent.Callable<ReportData> t2 =
                () -> ReportData.from(List.of(b), List.of(), List.of("SARVAGRAM"));
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            ReportData d1 = pool.submit(t1).get(10, java.util.concurrent.TimeUnit.SECONDS);
            ReportData d2 = pool.submit(t2).get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(d1.getTotalFailedReceipts(), 2);
            assertEquals(d2.getTotalFailedReceipts(), 0);
            assertFalse(d2.getFailedReceiptReasonsByClient().containsKey("CONATUS"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void credentialsNeverAppearInReportOutput() {
        JobStatus job = post("CONATUS", "SUCCESS", 0, 0);
        job.setJobFailureReason("ok");
        String path = JobMonitoringHtmlReport.generateCombined(
                List.of(job), List.of(), List.of("CONATUS"));
        String html = readFile(path);
        assertFalse(html.toLowerCase().contains("password"));
    }

    private static String readFile(String path) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read generated report: " + path, e);
        }
    }
}
