package com.encorepay.pages;
import org.openqa.selenium.interactions.Actions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.encorepay.models.JobStatus;
import com.encorepay.models.ReceiptCapture;
import com.encorepay.utilities.ConfigReader;

public class AdminJobsPage extends BasePage {

    private static final String JOB_POST_RECEIPTS = "Post Receipts Job";
    private static final String JOB_COLLECTION_ITEMS = "Encore Download Collection Items Job";
    private static final String JOB_UPCOMING_DEMAND = "Encore Up Coming Demands Job";

    private static final Map<String, String> JOB_GROUPS = Map.of(
        JOB_POST_RECEIPTS, "POST_RECEIPT",
        JOB_COLLECTION_ITEMS, "COLLECTION_ITEMS",
        JOB_UPCOMING_DEMAND, "UP_COMING_DEMAND"
    );

    private static final Map<String, List<String>> JOB_ALIASES = new LinkedHashMap<>();

    private static final By JOB_ROWS_FALLBACK = By.cssSelector("table.table-box tbody tr");

    static {
        JOB_ALIASES.put(JOB_POST_RECEIPTS, List.of(JOB_POST_RECEIPTS, "Post Receipt Job"));
        JOB_ALIASES.put(JOB_COLLECTION_ITEMS, List.of(
            JOB_COLLECTION_ITEMS, "Download Collection Items Job"));
        JOB_ALIASES.put(JOB_UPCOMING_DEMAND, List.of(
            JOB_UPCOMING_DEMAND, "Encore Up Coming Demand Job"));
    }

    private static final By JOB_ROWS = By.cssSelector(
            "app-job app-custom-table table.table-box tbody tr, app-job table.table-box tbody tr");

    private static final By ADMIN_MENU_TRIGGER = By.xpath(
            "//button[contains(@class,'menu-btn') and normalize-space()='Admin']"
                + " | //a[contains(@class,'menu-btn') and normalize-space()='Admin']"
                + " | //*[@role='button' and normalize-space()='Admin']");

    private static final By ADMIN_MENU_OVERLAY = By.xpath(
            "//div[contains(@class,'fixed') and contains(@class,'bg-black') and contains(@style,'z-index')]");

    private static final By ADMIN_MENU_PANEL = By.xpath(
            "//div[contains(@class,'bg-gray-100') and contains(@class,'overflow-auto')]");

    private static final By ADMIN_MENU_JOB_ITEM = By.xpath(
            "//div[contains(@class,'mega-menu-btn')]/button[normalize-space()='Job']"
                + " | //li//button[normalize-space()='Job']");

    private static final By JOBS_PAGE_ROOT = By.cssSelector("app-job");
    private static final By VIEW_ACTION = By.xpath(".//button[normalize-space()='View'] | .//a[normalize-space()='View']");
    private static final By RECEIPT_ACTION = By.xpath(".//button[normalize-space()='Receipt'] | .//a[normalize-space()='Receipt']");

    private static final By RECEIPT_SHOW_FILTER = By.xpath("//app-receipts//button[contains(normalize-space(),'Show Filter')]");
    private static final By RECEIPT_HIDE_FILTER = By.xpath("//app-receipts//button[contains(normalize-space(),'Hide Filter')]");
    private static final By RECEIPT_DATE = By.cssSelector("app-receipts input[name='receiptDate']");
    private static final By LMS_POSTING_STATUS = By.cssSelector("app-receipts select[name='lmsPostingStatus']");
    private static final By RECEIPT_SEARCH = By.xpath("//app-receipts//button[normalize-space()='Search']");
    private static final By RECEIPT_ROWS = By.cssSelector(
            "app-receipts app-custom-table table.table-box tbody tr, "
                    + "app-receipts app-custom-table table tbody tr, "
                    + "app-receipts table.table-box tbody tr");
    private static final By RECEIPT_PAGINATOR_RANGE = By.cssSelector(
            "app-receipts div.paginator-container .ct-range, app-receipts .ct-range, "
                    + "app-receipts mat-paginator .mat-mdc-paginator-range-label, "
                    + "app-receipts mat-paginator .mat-paginator-range-label");
    private static final By RECEIPT_FIRST_PAGE = By.cssSelector(
            "app-receipts div.paginator-container button[aria-label='First page'], "
                    + "app-receipts button[aria-label*='First page'], "
                    + "app-receipts button[aria-label*='First']");
    private static final By RECEIPT_NEXT_PAGE = By.cssSelector(
            "app-receipts div.paginator-container button[aria-label='Next page'], "
                    + "app-receipts button[aria-label*='Next page']");
    private static final By RECEIPT_PAGE_SIZE_SELECT = By.cssSelector(
            "app-receipts app-custom-table .ct-size select.ct-select, "
                    + "app-receipts app-custom-table .ct-select-wrap select.ct-select, "
                    + "app-receipts app-custom-table select.ct-select, "
                    + "app-receipts .ct-size select.ct-select, "
                    + "app-receipts .ct-select-wrap select.ct-select, "
                    + "app-receipts select[name='pageSize']");

    private static final By RECEIPT_PAGE_SIZE_SELECT_BY_LABEL = By.xpath(
            "//app-receipts//*[contains(concat(' ',normalize-space(@class),' '),' ct-size ') "
                    + "and contains(normalize-space(.),'Items per page')]//select"
                    + " | //app-receipts//*[contains(concat(' ',normalize-space(@class),' '),' ct-select-wrap ') "
                    + "and contains(normalize-space(.),'Items per page')]//select"
                    + " | //app-receipts//*[contains(normalize-space(.),'Items per page')]//select");

    private static final By RECEIPT_PAGE_SIZE_TRIGGER = By.xpath(
            "//app-receipts//*[contains(@class,'ct-pager') or contains(@class,'ct-size')]"
                    + "//*[@role='combobox' or self::mat-select or contains(@class,'ct-select')]"
                    + " | //app-receipts//*[@role='combobox'][contains(@aria-label,'page') or contains(@aria-label,'Page') or contains(@aria-label,'size') or contains(@aria-label,'Size')]" );

    private static final By JOB_PAGINATOR_RANGE = By.cssSelector(
            "app-job div.paginator-container .ct-range, app-job .ct-range, "
                    + "app-job mat-paginator .mat-mdc-paginator-range-label, "
                    + "app-job .mat-paginator-range-label");
    private static final By JOB_NEXT_PAGE = By.cssSelector(
            "app-job div.paginator-container button[aria-label='Next page'], "
                    + "app-job button[aria-label*='Next page']");
    private static final By JOB_FIRST_PAGE = By.cssSelector(
            "app-job div.paginator-container button[aria-label='First page'], "
                    + "app-job button[aria-label*='First page']");
    private static final By RECEIPT_ERROR_ICON = By.xpath(".//div[contains(@class,'material-symbols-rounded') and normalize-space()='error_outline']");
    private static final By MENU_BACKDROP = By.cssSelector(".cdk-overlay-backdrop, .cdk-overlay-dark-backdrop");
    private static final By FAILURE_MENU = By.cssSelector(".cdk-overlay-pane .mat-mdc-menu-panel, .cdk-overlay-pane .mat-menu-panel, .cdk-overlay-pane [role='menu']");
    private static final By OPEN_FAILURE_MENU_TRIGGER = By.cssSelector("app-receipts [aria-expanded='true'][aria-controls]");
    private static final By RECEIPTS_HEADING = By.xpath(
            "//app-receipts//h1[contains(normalize-space(),'Post-Receipts')] | //h1[contains(normalize-space(),'Post-Receipts')]");
    private static final By RECEIPT_CLOSE = By.xpath("//app-receipts//button[.//span[contains(@class,'material-symbols-rounded') and normalize-space()='close']]");

    private static final By JOB_DETAILS_ROOT = By.cssSelector("app-job-details");
    private static final By JOB_DETAIL_ROWS = By.cssSelector("app-job-details app-custom-table table.table-box tbody tr");
    private static final By JOB_DETAIL_VIEW = By.xpath(".//button[normalize-space()='View'] | .//a[normalize-space()='View']");
    private static final By JOB_DETAILS_CLOSE = By.xpath("//app-job-details//button[.//span[contains(@class,'material-symbols-rounded') and normalize-space()='close']]");
    private static final By EXECUTION_MODAL = By.xpath("//div[contains(@class,'modal-wrapper')][.//div[contains(@class,'list-label') and normalize-space()='Job Name']]");
    private static final By EXECUTION_MODAL_SCROLL = By.cssSelector("div.absolute.overflow-auto, div.overflow-auto.absolute, div.overflow-auto");
    private static final By EXECUTION_MODAL_CLOSE = By.xpath("//div[contains(@class,'modal-wrapper')]//button[.//span[contains(@class,'material-symbols-rounded') and normalize-space()='close']]");
    private static final By POSTING_LOGS_ACTION = By.xpath(
            "//app-job-details//button[normalize-space()='Posting Logs' or normalize-space()='PostingLogs']"
            + " | //app-job-details//a[normalize-space()='Posting Logs' or normalize-space()='PostingLogs']");
    private static final By POSTING_LOGS_MODAL = By.xpath("//div[contains(@class,'modal-wrapper')][.//span[contains(normalize-space(),'Receipt Posting Logs')] or .//h1[contains(normalize-space(),'Receipt Posting Logs')]]");
    private static final By POSTING_LOG_ROWS = By.cssSelector("app-receipt-posting-log app-custom-table table.table-box tbody tr");
    private static final By POSTING_LOG_NEXT_PAGE = By.cssSelector("app-receipt-posting-log div.paginator-container button[aria-label='Next page'], app-receipt-posting-log button[aria-label*='Next page']");
    private static final By POSTING_LOG_PAGE_SIZE_TRIGGER = By.cssSelector(
            "app-receipt-posting-log mat-paginator [role='combobox'], "
                    + "app-receipt-posting-log mat-paginator mat-select, "
                    + "app-receipt-posting-log mat-paginator .mat-mdc-paginator-page-size-select [role='combobox']");
    private static final By POSTING_LOG_PAGE_SIZE_OPTION_100 = By.xpath(
            "//div[@role='option'][normalize-space()='100' or .//*[normalize-space()='100']]"
                    + " | //mat-option[normalize-space()='100' or .//*[normalize-space()='100']]");

private static final Pattern PAGER_PATTERN = Pattern.compile(
            "(\\d+)\\s*(?:[-\\u2013\\u2014]\\s*(\\d+)\\s*)?of\\s*(?<total>\\d+)");
    private static final Pattern FULL_DATE_TIME = Pattern.compile("(?:\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{4}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})(?:\\s+|T)+\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*[APMapm]{2})?");
    private static final Pattern RECEIPT_POSTING_FAILURE = Pattern.compile(
            "(?i)Receipt Posting Failure:\\s*Total:\\s*(\\d+)\\s*,\\s*Success:\\s*(\\d+)\\s*,"
                    + "\\s*Partially Success:\\s*(\\d+)\\s*,\\s*Failed:\\s*(\\d+)");

    private static final int MAX_JOB_PAGES = 50;
    private static final int MAX_POSTING_LOG_PAGES = 1000;

    private static final int UNKNOWN_COUNT = -1;

    private final WebDriverWait jobsPageWait;

    public AdminJobsPage(WebDriver driver) {
        super(driver);
        this.jobsPageWait = wait;
    }

    public AdminJobsPage(WebDriver driver, ConfigReader config) {
        super(driver, config);
        this.jobsPageWait = wait;
    }

    public void navigateToAdminJobs() {
        if (isJobsPageLoaded()) return;

        validateSessionAndWindow();
        if (!isAuthenticatedApplicationVisible()) {
            throw new IllegalStateException("Authenticated application shell is not available before opening Admin Jobs.");
        }

        RuntimeException directFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                navigateToAdminJobsRouteDirectly();
                verifyAdminJobsPageRendered();
                return;
            } catch (RuntimeException e) {
                directFailure = e;
                logWarn("Direct Admin Jobs navigation attempt " + attempt + "/2 failed: " + safeText(e));
                if (attempt < 2) {
                    try {
                        Thread.sleep(400L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        try {
            hoverAdminMenu();
            clickJobInAdminMenu();
            verifyAdminJobsPageRendered();
            return;
        } catch (RuntimeException menuFailure) {
            throw new IllegalStateException(
                    "Admin Jobs page could not be reached after direct route and UI-menu recovery attempts.",
                    menuFailure != null ? menuFailure : directFailure);
        }
    }

    private void navigateToAdminJobsRouteDirectly() {
        String configured = config.getURL();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("Application URL is not configured.");
        }
        String base = configured.trim();
        int hash = base.indexOf('#');
        if (hash >= 0) {
            base = base.substring(0, hash);
        }
        if (!base.endsWith("/")) {
            base += "/";
        }
        driver.get(base + "#/admin/job");
    }

    public boolean isJobsPageLoaded() {
        try {
            String url = driver.getCurrentUrl().toLowerCase(Locale.ROOT);
            return url.contains("/admin/job") && !url.contains("/postreceipts") && !url.contains("/details/") && !visibleJobRows().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public List<JobStatus> monitorAllConfiguredJobs() {
        String clientName = config.getClientName();
        if (clientName == null || clientName.isBlank()) {
            throw new IllegalStateException("Client name could not be resolved from the application URL.");
        }

        List<JobStatus> results = new ArrayList<>();

        action.markStep("navigate to Admin Jobs");
        ensureJobsPage();

        results.add(captureJobWithRetry(clientName, JOB_POST_RECEIPTS,
                () -> monitorPostReceiptJob(clientName)));

        results.add(captureJobWithRetry(clientName, JOB_COLLECTION_ITEMS,
                () -> monitorExecutionJob(JOB_COLLECTION_ITEMS, clientName)));

        action.markStep("Encore Up Coming Demands Job");
        try {
            ensureJobsPage();
            if (findUpcomingDemandRow() != null) {
                results.add(captureJobWithRetry(clientName, JOB_UPCOMING_DEMAND,
                        () -> monitorExecutionJob(JOB_UPCOMING_DEMAND, clientName)));
            } else {
                JobStatus unavailable = new JobStatus();
                unavailable.setClientName(clientName);
                unavailable.setJobName(JOB_UPCOMING_DEMAND);
                unavailable.setStatus("N/A");
                unavailable.setDateTime("N/A");
                unavailable.setJobFailureReason("Upcoming Demand Job not available");
                unavailable.setSynthetic(true);
                results.add(unavailable);
                System.out.println("[INFO] Upcoming Demand Job is not configured for " + clientName + ".");
            }
        } catch (Exception e) {
            System.out.println("[WARN] Upcoming Demands Job capture error for " + clientName + ": " + e.getMessage());
            results.add(unavailableJobPlaceholder(clientName, JOB_UPCOMING_DEMAND, e));
        }

        action.markStep("client complete");
        return results;
    }

    @FunctionalInterface
    private interface JobCaptureOperation {
        JobStatus capture();
    }

    private JobStatus captureJobWithRetry(String clientName, String jobName, JobCaptureOperation operation) {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                action.markStep(jobName + " attempt " + attempt);
                ensureJobsPage();
                JobStatus captured = operation.capture();
                if (captured == null) {
                    throw new IllegalStateException(jobName + " returned no monitoring result.");
                }
                validateCapturedJob(clientName, jobName, captured);
                return captured;
            } catch (RuntimeException e) {
                lastFailure = e;
                System.out.println("[WARN] " + jobName + " capture attempt " + attempt + "/2 failed for "
                        + clientName + ": " + safeText(e));
                if (attempt < 2) {
                    try {
                        recoverToJobsPage();
                        ensureJobsPage();
                    } catch (RuntimeException recoveryError) {
                        lastFailure = recoveryError;
                        System.out.println("[WARN] Jobs-page recovery before " + jobName
                                + " retry failed: " + safeText(recoveryError));
                    }
                }
            }
        }

        return unavailableJobPlaceholder(clientName, jobName, lastFailure);
    }

    private void validateCapturedJob(String clientName, String jobName, JobStatus status) {
        if (!clientName.equalsIgnoreCase(status.getClientName())) {
            throw new IllegalStateException("Captured " + jobName + " for unexpected client: "
                    + status.getClientName());
        }
        if (status.getStatus() == null || status.getStatus().isBlank()) {
            throw new IllegalStateException("Captured " + jobName + " without an execution status.");
        }
        if (status.getDateTime() == null || status.getDateTime().isBlank()) {
            throw new IllegalStateException("Captured " + jobName + " without an execution timestamp.");
        }
    }

    private JobStatus unavailableJobPlaceholder(String clientName, String jobName, Exception cause) {
        JobStatus unavailable = new JobStatus();
        unavailable.setClientName(clientName);
        unavailable.setJobName(jobName);
        unavailable.setStatus("N/A");
        unavailable.setDateTime("N/A");

        String diagnostic = safeText(cause);
        String reason;
        if (diagnostic.toLowerCase(Locale.ROOT).contains("server is down")
                || diagnostic.toLowerCase(Locale.ROOT).contains("login could not be completed")) {
            reason = "Server is down";
        } else if (diagnostic.toLowerCase(Locale.ROOT).contains("admin jobs page")) {
            reason = "Job monitoring page could not be loaded after recovery attempts";
        } else {
            reason = "Job execution data could not be captured after recovery attempts";
        }
        unavailable.setJobFailureReason(reason);
        unavailable.setSynthetic(true);
        return unavailable;
    }

    private String safeText(Throwable cause) {
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) return "no further detail";
        String cleaned = message.replaceAll("\\s+", " ").trim();

        int conditionIndex = cleaned.indexOf("Expected condition failed: waiting for");
        if (conditionIndex >= 0) {
            String prefix = cleaned.substring(0, conditionIndex).trim();
            cleaned = prefix.isBlank()
                    ? "Timed out waiting for the UI to reach the expected state"
                    : prefix + " (timed out waiting for the UI to reach the expected state)";
        }

        return cleaned.length() > 300 ? cleaned.substring(0, 297) + "..." : cleaned;
    }

    private void requireActiveSession(String jobName) {
        try {
            requireLiveSession(driver);
        } catch (IllegalStateException e) {
            throw new IllegalStateException(
                    "Browser session was lost before " + jobName + " could be monitored: " + e.getMessage(), e);
        }
    }

    private JobStatus monitorPostReceiptJob(String clientName) {
        requireActiveSession(JOB_POST_RECEIPTS);

        JobStatus status = new JobStatus();
        status.setJobName(JOB_POST_RECEIPTS);
        status.setClientName(clientName);

        ReceiptCapture pending = new ReceiptCapture();
        ReceiptCapture failed = new ReceiptCapture();

        try {
            WebElement jobRow = requireJobRow(JOB_POST_RECEIPTS);
            WebElement receiptButton = visibleInside(jobRow, RECEIPT_ACTION);
            if (receiptButton == null) {
                throw new IllegalStateException("Receipt action was not found for Post Receipts Job.");
            }

            clickAndWait(receiptButton);
            waitForReceiptPage();
            ensureReceiptFiltersVisible();

            String todayDate = selectReceiptDateToday();

            pending = captureReceiptsForStatusWithRetry("PENDING", todayDate, 3);

            failed = captureReceiptsForStatusWithRetry("FAILED", todayDate, 3);

            status.setPendingCount(pending.totalCount);
            status.setFailedCount(failed.totalCount);

            if (failed.totalCount > 0) {
                try {
                    int preferredPageSize = findPreferredReceiptPageSize(failed.totalCount);
                    if (preferredPageSize > 0) {
                        int currentPageSize = readCurrentReceiptPageSize();
                        if (currentPageSize <= 0 || currentPageSize < preferredPageSize) {
                            log("[FAILED] Total count: " + failed.totalCount
                                    + ", using discovered page size " + preferredPageSize
                                    + " (current=" + currentPageSize + ")");
                            if (!setReceiptPageSizeAndVerify(preferredPageSize)) {
                                log("[FAILED] Could not apply optimized page size "
                                        + preferredPageSize + "; continuing with active paginator size "
                                        + readCurrentReceiptPageSize());
                            }
                        }
                    }

                    goToFirstReceiptPage();
                    waitForFailedReceiptResultsReady();

                    Map<String, Integer> reasonCounts = new LinkedHashMap<>();
                    RuntimeException firstCaptureFailure = null;
                    try {
                        reasonCounts.putAll(readFailedReasonsFromUiBackingData(failed.totalCount));
                    } catch (RuntimeException first) {
                        firstCaptureFailure = first;
                        log("[FAILED] First UI-backed reason extraction attempt failed: " + safeText(first));
                    }

                    int captured = totalCapturedReasonCount(reasonCounts);
                    if (captured != failed.totalCount) {
                        log("[FAILED] UI-backed capture returned " + captured + "/" + failed.totalCount
                                + "; refreshing FAILED results in-place");
                        try {
                            if (refreshFailedReceiptResultsInPlace(todayDate)) {
                                reasonCounts.clear();
                                try {
                                    reasonCounts.putAll(readFailedReasonsFromUiBackingData(failed.totalCount));
                                    captured = totalCapturedReasonCount(reasonCounts);
                                } catch (RuntimeException second) {
                                    log("[FAILED] Second UI-backed reason extraction attempt failed: " + safeText(second));
                                }
                            }
                        } catch (RuntimeException recoveryFailure) {
                            log("[FAILED] In-place FAILED refresh failed; leaving receipt page open for final UI fallback: "
                                    + safeText(recoveryFailure));
                        }
                    }

                    if (captured != failed.totalCount) {
                        try {
                            Map<String, Integer> menuCounts = readAllFailureReasonsWithCounts(failed.totalCount);
                            reasonCounts.clear();
                            reasonCounts.putAll(menuCounts);
                            captured = totalCapturedReasonCount(reasonCounts);
                        } catch (RuntimeException fallbackFailure) {
                            String root = firstCaptureFailure == null
                                    ? safeText(fallbackFailure)
                                    : safeText(firstCaptureFailure) + "; fallback: " + safeText(fallbackFailure);
                            throw new IllegalStateException("FAILED receipt reason capture could not be completed in the open FAILED receipt UI: " + root, fallbackFailure);
                        }
                    }

                    failed.reasonCounts.clear();
                    failed.reasonCounts.putAll(reasonCounts);
                    if (captured != failed.totalCount) {
                        throw new IllegalStateException("Receipt failure reason count mismatch: captured "
                                + captured + " of " + failed.totalCount + " FAILED receipts");
                    }
                } catch (Exception e) {
                    failed.problems.add(
                            "Receipt-page failure-reason scan failed: " + safeText(e)
                            + "; execution PostingLogs will be used as fallback/cross-check.");
                    log("[FAILED] Receipt reason scan failed: " + safeText(e));
                }
            } else if (failed.totalCount == UNKNOWN_COUNT) {
                failed.problems.add(
                        "Cannot collect receipt-page failure reasons because FAILED count is unknown.");
            }

            failed.reasonCounts.forEach(status::addFailureReason);

            List<String> notes = new ArrayList<>(failed.problems);
            notes.addAll(pending.problems);
            if (failed.totalCount > 0 && totalCapturedReasonCount(failed.reasonCounts) == 0) {
                notes.add("Failed receipts were found, but no receipt-level failure reasons were captured from the FAILED receipt UI.");
            }
            if (!notes.isEmpty()) {
                status.setJobFailureReason(
                        "Receipt capture incomplete: " + String.join("; ", new LinkedHashSet<>(notes)));
            }

            if (failed.totalCount >= 0) {
                int capturedReasons = totalCapturedReasonCount(failed.reasonCounts);
                if (capturedReasons != failed.totalCount) {
                    String reconciliation = "Receipt failure reason reconciliation failed: captured "
                            + capturedReasons + " of " + failed.totalCount + " FAILED receipts.";
                    String existingValidation = status.getValidationMessage();
                    status.setValidationMessage(
                            existingValidation == null || existingValidation.isBlank()
                                    ? reconciliation
                                    : existingValidation + "; " + reconciliation);
                }
            }
        } catch (Exception e) {
            String captureProblem = "Receipt monitoring flow failed: " + safeText(e);
            status.setJobFailureReason(appendProblem(status.getJobFailureReason(), captureProblem));
            log("[POST RECEIPTS] Non-fatal capture failure: " + safeText(e));
        } finally {
        }

        try {
            ensureJobsPage();
            captureLatestExecutionFromJobsList(JOB_POST_RECEIPTS, status);
        } catch (Exception e) {
            String executionProblem =
                    "Job execution details could not be captured: " + safeText(e);
            status.setJobFailureReason(appendProblem(status.getJobFailureReason(), executionProblem));

            if (status.getStatus() == null || status.getStatus().isBlank()) {
                status.setStatus("N/A");
            }
            if (status.getDateTime() == null || status.getDateTime().isBlank()) {
                status.setDateTime("N/A");
            }
        }

        return status;
    }

    private String appendProblem(String existing, String problem) {
        String cleanProblem = clean(problem);
        if (cleanProblem.isBlank()) {
            return existing;
        }
        if (existing == null || existing.isBlank()) {
            return cleanProblem;
        }
        return existing + "; " + cleanProblem;
    }

    private JobStatus monitorExecutionJob(String jobName, String clientName) {
        requireActiveSession(jobName);

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            JobStatus status = new JobStatus();
            status.setJobName(jobName);
            status.setClientName(clientName);
            try {
                ensureJobsPage();
                captureLatestExecutionFromJobsList(jobName, status);
                validateCapturedExecution(status, jobName);
                return status;
            } catch (RuntimeException e) {
                lastFailure = e;
                logWarn("Execution capture " + jobName + " attempt " + attempt + "/2 failed: " + safeText(e));
                if (attempt < 2) {
                    try {
                        recoverToJobsPage();
                        awaitUiStability();
                    } catch (Exception recoveryError) {
                        logWarn("Execution capture recovery failed before retry: " + safeText(recoveryError));
                    }
                }
            }
        }
        throw new IllegalStateException("Unable to capture a trustworthy execution result for "
                + jobName + " after 2 attempts: " + safeText(lastFailure), lastFailure);
    }

    private void validateCapturedExecution(JobStatus status, String jobName) {
        String raw = status.getRawStatus();
        if (raw.isBlank()) {
            throw new IllegalStateException("Execution record was not captured for " + jobName + ".");
        }
        if ("NOT CAPTURED".equalsIgnoreCase(clean(status.getDateTime()))) {
            logWarn("Execution date/time was unavailable for " + jobName
                    + "; preserving the captured application status instead of inventing a timestamp.");
        }
    }

    private ReceiptCapture captureReceiptsForStatusWithRetry(String targetStatus, String expectedDate, int maxAttempts) {
        ReceiptCapture last = new ReceiptCapture();
        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
            try {
                if (attempt > 1) {
                    ensureReceiptPage();
                    ensureReceiptFiltersVisible();
                    if (isFailureReasonMenuOpen()) closeFailureReasonMenu(-1);
                }
                ReceiptCapture current = captureReceiptsForStatus(targetStatus, expectedDate);
                if (current.totalCount != UNKNOWN_COUNT && current.problems.isEmpty()) {
                    return current;
                }
                if (current.totalCount != UNKNOWN_COUNT) {
                    log("[" + targetStatus + "] Attempt " + attempt + " reached a paginator total but had validation notes; retrying in place");
                }
                last = current;
                log("[" + targetStatus + "] Attempt " + attempt + "/" + maxAttempts
                        + " did not produce a usable paginator total; retrying in place");
            } catch (RuntimeException e) {
                last.problems.add(targetStatus + " receipt capture attempt " + attempt
                        + " failed: " + safeText(e));
                log("[" + targetStatus + "] Attempt " + attempt + "/" + maxAttempts
                        + " failed; keeping receipt page open for recovery: " + safeText(e));
            }
            if (attempt < maxAttempts) {
                try {
                    closeFailureReasonMenu(-1);
                    Thread.sleep(150);
                } catch (Exception ignored) {
                }
            }
        }
        return last;
    }

    private ReceiptCapture captureReceiptsForStatus(String targetStatus, String expectedDate) {
        validateSessionAndWindow();
        ensureReceiptPage();
        ensureReceiptFiltersVisible();

        ReceiptCapture capture = new ReceiptCapture();

        WebElement date = findDateInput();
        if (date != null) {
            String currentValue = clean(date.getAttribute("value"));
            if (!expectedDate.equals(currentValue)) {
                ((JavascriptExecutor) driver).executeScript(
                    "var el = arguments[0], v = arguments[1];" +
                    "el.value = v;" +
                    "el.dispatchEvent(new Event('input', { bubbles: true }));" +
                    "el.dispatchEvent(new Event('change', { bubbles: true }));" +
                    "el.dispatchEvent(new Event('blur', { bubbles: true }));" +
                    "el.focus();",
                    date, expectedDate);
            }
        }

        if (!selectPostingStatus(targetStatus)) {
            capture.problems.add("LMS Posting Status '" + targetStatus + "' could not be selected.");
            return capture;
        }

        searchReceipts(targetStatus, capture);
        if ("FAILED".equalsIgnoreCase(targetStatus)) {
            waitForFailedReceiptResultsReady();
        } else {
            waitForReceiptResults();
        }

        capture.totalCount = readReceiptTotalCount(capture);

        if (capture.totalCount == UNKNOWN_COUNT) {
            capture.problems.add(targetStatus + " receipt paginator total could not be read");
        } else {
            log("[" + targetStatus + "] Total count from paginator: " + capture.totalCount);
        }

        return capture;
    }
    private int findPreferredReceiptPageSize(int totalCount) {
        List<Integer> available = findAvailableReceiptPageSizes();

        if (available.isEmpty()) {
            available = readReceiptPageSizesFromExpandControl();
        }

        if (available.isEmpty()) {
            return readCurrentReceiptPageSize();
        }

        return available.get(available.size() - 1);
    }

    private List<Integer> readReceiptPageSizesFromExpandControl() {
        Set<Integer> sizes = new java.util.TreeSet<>();
        String currentSize = String.valueOf(readCurrentReceiptPageSize());
        WebElement trigger = findReceiptPageSizeTrigger(currentSize);
        if (trigger == null) {
            return new ArrayList<>();
        }

        try {
            scrollIntoView(trigger);
            fastClick(trigger);

            By optionLocator = By.xpath("//div[@role='option'] | //mat-option");
            List<WebElement> options = new WebDriverWait(driver, Duration.ofSeconds(2)).until(d -> {
                List<WebElement> found = new ArrayList<>();
                for (WebElement candidate : d.findElements(optionLocator)) {
                    if (isDisplayed(candidate)) {
                        found.add(candidate);
                    }
                }
                return found;
            });

            for (WebElement option : options) {
                String text = clean(option.getText());
                try {
                    int size = Integer.parseInt(text);
                    if (size > 0) {
                        sizes.add(size);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ignored) {
        } finally {
            safeEscape();
        }

        return new ArrayList<>(sizes);
    }
 

    private List<Integer> findAvailableReceiptPageSizes() {
        Set<Integer> sizes = new java.util.TreeSet<>();
        for (WebElement selectElement : driver.findElements(RECEIPT_PAGE_SIZE_SELECT)) {
            try {
                if (!isDisplayed(selectElement) || !selectElement.isEnabled()) continue;
                for (WebElement option : new Select(selectElement).getOptions()) {
                    String value = clean(option.getAttribute("value"));
                    String text = clean(option.getText());
                    try {
                        int size = Integer.parseInt(!value.isBlank() ? value : text);
                        if (size > 0) sizes.add(size);
                    } catch (NumberFormatException ignored) { }
                }
            } catch (Exception ignored) { }
        }
        return new ArrayList<>(sizes);
    }

    private int readCurrentReceiptPageSize() {
        String querySize = queryParam("size");
        try {
            if (!querySize.isBlank()) return Integer.parseInt(querySize);
        } catch (NumberFormatException ignored) { }

        for (WebElement selectElement : driver.findElements(RECEIPT_PAGE_SIZE_SELECT)) {
            try {
                if (!isDisplayed(selectElement)) continue;
                WebElement selected = new Select(selectElement).getFirstSelectedOption();
                String value = clean(selected.getAttribute("value"));
                String text = clean(selected.getText());
                try {
                    if (!value.isBlank()) return Integer.parseInt(value);
                    if (!text.isBlank()) return Integer.parseInt(text);
                } catch (NumberFormatException ignored) { }
            } catch (Exception ignored) { }
        }
        return UNKNOWN_COUNT;
    }

    private boolean setReceiptPageSizeAndVerify(int desiredSize) {
        log("[PAGE SIZE] Attempting to set page size to " + desiredSize);
        validateSessionAndWindow();
        ensureReceiptPage();
        ensureReceiptFiltersVisible();

        String expected = String.valueOf(desiredSize);

        if (expected.equals(queryParam("size"))) {
            log("[PAGE SIZE] Already set to " + desiredSize + " via query param");
            return true;
        }

        boolean applied = false;

        WebElement target = findReceiptPageSizeSelect(expected);
        if (target != null) {
            scrollIntoView(target);
            if (applyPageSizeSelect(target, expected)
                    && waitUntilReceiptPageSizeIsApplied(desiredSize)) {
                applied = true;
            }
        }

        if (!applied) {
            WebElement trigger = findReceiptPageSizeTrigger(expected);
            if (trigger != null) {
                try {
                    scrollIntoView(trigger);
                    fastClick(trigger);

                    By optionLocator = By.xpath(
                            "//div[@role='option'][normalize-space()='" + expected + "']"
                                    + " | //mat-option[normalize-space()='" + expected + "']");

                    WebElement option = new WebDriverWait(driver, Duration.ofSeconds(2)).until(d -> {
                        for (WebElement candidate : d.findElements(optionLocator)) {
                            if (isDisplayed(candidate) && expected.equals(clean(candidate.getText()))) {
                                return candidate;
                            }
                        }
                        return null;
                    });

                    fastClick(option);
                    applied = waitUntilReceiptPageSizeIsApplied(desiredSize);
                } catch (Exception ignored) {
                    safeEscape();
                }
            }
        }

        if (!applied) {
            if (applyPageSizeViaJavaScript(expected)) {
                applied = waitUntilReceiptPageSizeIsApplied(desiredSize);
            }
        }

        if (!applied) {
            applied = expected.equals(queryParam("size"));
        }

        if (!applied) {
            String range = readPaginatorRange();
            Matcher matcher = PAGER_PATTERN.matcher(range);
            if (matcher.matches()) {
                int start = Integer.parseInt(matcher.group(1));
                int end = matcher.group(2) == null ? start : Integer.parseInt(matcher.group(2));
                int total = Integer.parseInt(matcher.group("total"));
                int expectedVisible = Math.min(desiredSize, total);
                int actualVisible = Math.max(0, end - start + 1);
                if (start == 1 && actualVisible == expectedVisible
                        ) {
                    applied = true;
                }
            }
        }

        if (!applied) {
            log("[PAGE SIZE] Could not verify page size " + desiredSize + " was applied. Current query param size: " + queryParam("size") + ", paginator: " + readPaginatorRange());
        } else {
            log("[PAGE SIZE] Successfully set page size to " + desiredSize);
        }

        return applied;
    }

    private boolean applyPageSizeSelect(WebElement element, String expected) {
        try {
            element.click();
            By optionLocator = By.xpath(".//option[@value='" + expected + "']");
            WebElement option = element.findElement(optionLocator);
            if (option != null && isDisplayed(option)) {
                option.click();
                ((JavascriptExecutor) driver).executeScript(
                    "var el = arguments[0];" +
                    "el.dispatchEvent(new Event('change', { bubbles: true }));" +
                    "el.dispatchEvent(new Event('input', { bubbles: true }));" +
                    "el.dispatchEvent(new Event('blur', { bubbles: true }));",
                    element);
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                if (isSelectedPageSize(element, expected)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean applyPageSizeViaJavaScript(String expected) {
        try {
            Boolean result = (Boolean) ((JavascriptExecutor) driver).executeScript(
                "var expected = arguments[0];"
                + "var selects = document.querySelectorAll('app-receipts select, app-receipts app-custom-table select, app-receipts .ct-select-wrap select, app-receipts select[name=\"pageSize\"]');"
                + "for (var s = 0; s < selects.length; s++) {"
                + "  var sel = selects[s];"
                + "  if (!sel.offsetParent && sel !== document.body) continue;"
                + "  for (var o = 0; o < sel.options.length; o++) {"
                + "    var opt = sel.options[o];"
                + "    if (opt.value === expected || opt.text.trim() === expected) {"
                + "      var setter = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value').set;"
                + "      setter.call(sel, opt.value);"
                + "      sel.selectedIndex = o;"
                + "      sel.dispatchEvent(new Event('input', { bubbles: true }));"
                + "      sel.dispatchEvent(new Event('change', { bubbles: true }));"
                + "      sel.dispatchEvent(new Event('blur', { bubbles: true }));"
                + "      return true;"
                + "    }"
                + "  }"
                + "}"
                + "return false;",
                expected);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log("[PAGE SIZE] JavaScript page size control failed: " + safeText(e));
            return false;
        }
    }

    private void waitForFailedReceiptResultsReady() {
        waitForState("FAILED receipt results to become the active UI dataset", d -> {
            String status = readSelectedPostingStatus();
            String urlStatus = queryParam("lmsPostingStatus");
            if (!"FAILED".equalsIgnoreCase(status) || !"FAILED".equalsIgnoreCase(urlStatus)) {
                return false;
            }
            String range = readPaginatorRange();
            if (range.isBlank()) return false;
            return !visibleReceiptRows().isEmpty() || isReceiptEmpty();
        });
    }

    private boolean refreshFailedReceiptResultsInPlace(String expectedDate) {
        if (!expectedDate.equals(readDateFieldValue())) return false;
        if (!"FAILED".equalsIgnoreCase(readSelectedPostingStatus())) return false;
        waitForFailedReceiptResultsReady();
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> readFailedReasonsFromUiBackingData(int expectedTotal) {
        if (expectedTotal <= 0) return new LinkedHashMap<>();

        int pageSize = readCurrentUiReceiptPageSizeFromRangeOrQuery();
        if (pageSize <= 0) {
            throw new IllegalStateException("FAILED receipt UI page size could not be determined");
        }

        int pages = (expectedTotal + pageSize - 1) / pageSize;
        Map<String, Integer> counts = new LinkedHashMap<>();
        Set<String> receiptIds = new LinkedHashSet<>();

        for (int page = 0; page < pages; page++) {
            List<Object> records = null;
            RuntimeException lastPageFailure = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    Object raw = fetchFailedReceiptPageFromCurrentUiQuery(page, pageSize);
                    if (!(raw instanceof List)) {
                        throw new IllegalStateException("FAILED receipt UI backing endpoint returned an unexpected payload for page " + page);
                    }
                    records = (List<Object>) raw;
                    if (!records.isEmpty()) break;
                    lastPageFailure = new IllegalStateException(
                            "FAILED receipt UI backing endpoint returned an empty page " + page
                            + " before the UI paginator total of " + expectedTotal + " was exhausted");
                } catch (RuntimeException e) {
                    lastPageFailure = e;
                }
                try { Thread.sleep(250L * attempt); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while retrying FAILED receipt backing page " + page, interrupted);
                }
            }
            if (records == null || records.isEmpty()) {
                throw lastPageFailure == null
                        ? new IllegalStateException("FAILED receipt UI backing endpoint returned no records for page " + page)
                        : lastPageFailure;
            }

            for (Object record : records) {
                if (!(record instanceof Map)) {
                    throw new IllegalStateException("FAILED receipt UI backing endpoint returned a non-object receipt record");
                }
                Map<String, Object> receipt = (Map<String, Object>) record;
                String id = clean(String.valueOf(receipt.getOrDefault("id", "")));
                String receiptNum = clean(String.valueOf(receipt.getOrDefault("receiptNum", "")));
                String account = clean(String.valueOf(receipt.getOrDefault("lmsAccountId", receipt.getOrDefault("accountId", ""))));
                String identity = !id.isBlank() ? "id:" + id : "receipt:" + receiptNum + "|account:" + account;

                if (!receiptIds.add(identity)) {
                    throw new IllegalStateException("Duplicate FAILED receipt encountered while paging backing data: " + identity);
                }

                Object rawReason = receipt.get("lmsErrorCode");
                if (rawReason == null) rawReason = receipt.get("lmsError");
                if (rawReason == null) rawReason = receipt.get("errorCode");
                String reason = trimReason(String.valueOf(rawReason == null ? "" : rawReason));
                if (reason.isBlank()) {
                    throw new IllegalStateException("FAILED receipt " + identity + " has a blank lmsErrorCode in the same data rendered by the receipt UI");
                }
                counts.merge(reason, 1, Integer::sum);
            }

            if (receiptIds.size() >= expectedTotal) break;
        }

        int captured = totalCapturedReasonCount(counts);
        if (captured != expectedTotal) {
            throw new IllegalStateException("Backing data captured " + captured + " FAILED receipts, expected " + expectedTotal);
        }
        log("[FAILED] Captured " + captured + "/" + expectedTotal
                + " failure reasons from the exact API dataset rendered by the FAILED receipt UI");
        return counts;
    }

    private int readCurrentUiReceiptPageSizeFromRangeOrQuery() {
        int current = readCurrentReceiptPageSize();
        if (current > 0) return current;

        String range = readPaginatorRange();
        Matcher matcher = PAGER_PATTERN.matcher(range);
        if (matcher.matches()) {
            int start = Integer.parseInt(matcher.group(1));
            int end = matcher.group(2) == null ? start : Integer.parseInt(matcher.group(2));
            return Math.max(1, end - start + 1);
        }
        return 0;
    }

    private Object fetchFailedReceiptPageFromCurrentUiQuery(int pageIndex, int pageSize) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                Object result = ((JavascriptExecutor) driver).executeAsyncScript(
                    "var done = arguments[arguments.length - 1];"
                    + "try {"
                    + "  var p = new URLSearchParams(window.location.search);"
                    + "  p.set('page', String(arguments[0]));"
                    + "  p.set('size', String(arguments[1]));"
                    + "  p.set('lmsPostingStatus', 'FAILED');"
                    + "  var base = document.baseURI || window.location.href;"
                    + "  var url = new URL('api/receipts', base);"
                    + "  url.search = p.toString();"
                    + "  fetch(url.toString(), { credentials: 'same-origin', cache: 'no-store', headers: { 'Accept': 'application/json' } })"
                    + "    .then(function(r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })"
                    + "    .then(function(body) { done(body); })"
                    + "    .catch(function(e) { done({__error: String(e)}); });"
                    + "} catch (e) { done({__error: String(e)}); }",
                    pageIndex, pageSize);

                if (result instanceof List && !((List<?>) result).isEmpty()) return result;

                if (result instanceof Map<?, ?> map) {
                    Object error = map.get("__error");
                    if (error != null) throw new IllegalStateException(String.valueOf(error));
                    for (String key : List.of("content", "body", "data", "items")) {
                        Object value = map.get(key);
                        if (value instanceof List && !((List<?>) value).isEmpty()) return value;
                    }
                }
                throw new IllegalStateException("FAILED receipt API returned an empty page " + pageIndex
                        + " while the UI still expects FAILED receipts");
            } catch (RuntimeException e) {
                last = e;
                if (attempt < 5) {
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while retrying FAILED receipt API page " + pageIndex, interrupted);
                    }
                }
            }
        }
        throw last == null
                ? new IllegalStateException("Could not read FAILED receipt data from the UI-backed /api/receipts request")
                : last;
    }

    private Map<String, Integer> readAllFailureReasonsWithCounts(int expectedTotal) {
        Map<String, Integer> totalReasonCounts = new LinkedHashMap<>();
        Set<String> capturedReceiptSignatures = new LinkedHashSet<>();
        String lastRange = "";
        int pageTransitions = 0;

        while (true) {
            validateSessionAndWindow();
            if (expectedTotal > 0 && pageTransitions > expectedTotal + 5) {
                throw new IllegalStateException("FAILED receipt pagination exceeded a dynamic safety bound after "
                        + pageTransitions + " page transitions; expected " + expectedTotal + " receipts");
            }
            waitForReceiptResults();

            String pageMarker = readPaginatorRange();
            int rowCount = visibleReceiptRows().size();

            if (rowCount == 0) {
                if (isReceiptEmpty()) {
                    break;
                }
                throw new IllegalStateException("FAILED receipt page '" + pageMarker
                        + "' has no rendered rows; refusing to declare capture complete");
            }

            int capturedBeforePage = totalCapturedReasonCount(totalReasonCounts);
            Set<String> pageProcessed = new LinkedHashSet<>();
            int noProgressPasses = 0;

            while (true) {
                List<WebElement> rows = visibleReceiptRows();
                boolean foundUncaptured = false;

                for (int index = 0; index < rows.size(); index++) {
                    validateSessionAndWindow();
                    rows = visibleReceiptRows();
                    if (index >= rows.size()) break;

                    WebElement row = rows.get(index);
                    String signature = buildRowSignature(pageMarker, row, index);
                    if (capturedReceiptSignatures.contains(signature) || pageProcessed.contains(signature)) {
                        continue;
                    }

                    foundUncaptured = true;
                    String reason = captureSingleFailureReason(index, pageMarker);
                    String key = normalizeReasonKey(reason);
                    totalReasonCounts.merge(key, 1, Integer::sum);
                    capturedReceiptSignatures.add(signature);
                    pageProcessed.add(signature);

                    if (!closeFailureReasonMenu(index)) {
                        throw new IllegalStateException("Failure reason menu remained open after receipt "
                                + signature + "; refusing to continue to the next receipt");
                    }
                }

                if (!foundUncaptured) {
                    break;
                }

                int capturedNow = totalCapturedReasonCount(totalReasonCounts);
                if (capturedNow == capturedBeforePage) {
                    noProgressPasses++;
                    if (noProgressPasses >= 3) {
                        throw new IllegalStateException("FAILED receipt page '" + pageMarker
                                + "' made no capture progress after repeated DOM refreshes; refusing to skip receipts");
                    }
                } else {
                    noProgressPasses = 0;
                }
                awaitUiStability();
            }

            int capturedAfterPage = totalCapturedReasonCount(totalReasonCounts);
            if (capturedAfterPage == capturedBeforePage && rowCount > 0) {
                throw new IllegalStateException("FAILED receipt page '" + pageMarker
                        + "' produced no new receipt captures; refusing to advance");
            }

            if (expectedTotal > 0 && capturedAfterPage >= expectedTotal) {
                log("[FAILED] Captured every FAILED receipt: " + capturedAfterPage + "/" + expectedTotal
                        + " across " + (pageTransitions + 1) + " page(s)");
                return totalReasonCounts;
            }

            if (expectedTotal > 0 && capturedAfterPage < expectedTotal
                    && expandReceiptPageToLargestSize()) {
                lastRange = "";
                continue;
            }

            WebElement next = visibleElement(RECEIPT_NEXT_PAGE);
            if (next == null || !next.isEnabled()) {
                throw new IllegalStateException("FAILED receipt paginator ended at '" + pageMarker
                        + "' after " + capturedAfterPage + " captured receipt reason(s), but expected "
                        + expectedTotal + ". No receipts may be left uncaptured.");
            }

            if (!closeFailureReasonMenu(-1)) {
                throw new IllegalStateException("Failure reason menu remained open before pagination from '"
                        + pageMarker + "'");
            }

            String before = pageMarker;
            clickAndWait(next);
            wait.until(d -> {
                String after = readPaginatorRange();
                return !after.isBlank() && !after.equals(before) && !visibleReceiptRows().isEmpty();
            });

            String after = readPaginatorRange();
            if (after.equals(lastRange)) {
                throw new IllegalStateException("FAILED receipt paginator repeated range '" + after
                        + "'; refusing to loop or skip receipts");
            }
            lastRange = after;
            pageTransitions++;
        }

        int captured = totalCapturedReasonCount(totalReasonCounts);
        if (expectedTotal > 0 && captured != expectedTotal) {
            throw new IllegalStateException("FAILED receipt capture ended with " + captured
                    + " captured receipt reasons, expected " + expectedTotal);
        }
        return totalReasonCounts;
    }

    private static final int[] STANDARD_PAGE_SIZES = {100, 50, 25, 10};

    private boolean expandReceiptPageToLargestSize() {
        int current = readCurrentReceiptPageSize();
        int preferred = findPreferredReceiptPageSize(UNKNOWN_COUNT);

        List<Integer> candidates = new ArrayList<>();
        if (preferred > 0) {
            candidates.add(preferred);
        }
        for (int standard : STANDARD_PAGE_SIZES) {
            if (!candidates.contains(standard)) {
                candidates.add(standard);
            }
        }

        for (int candidate : candidates) {
            if (current > 0 && current >= candidate) {
                continue; // already at or above this size
            }
            log("[EXPAND] Attempting page size " + candidate
                    + " (current=" + current + ", discoveredPreferred=" + preferred + ")");
            if (setReceiptPageSizeAndVerify(candidate)) {
                log("[EXPAND] Successfully expanded receipt page to " + candidate);
                return true;
            }
            log("[EXPAND] Page size " + candidate + " could not be applied; trying next candidate");
        }

        log("[EXPAND] No larger page size could be applied; falling back to Next Page pagination");
        return false;
    }

    private String captureSingleFailureReason(int rowIndex, String pageMarker) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                if (!closeFailureReasonMenu(rowIndex)) {
                    throw new IllegalStateException("Previous failure-reason menu could not be closed");
                }
                List<WebElement> rows = visibleReceiptRows();
                if (rowIndex >= rows.size()) {
                    throw new IllegalStateException("Receipt row index " + rowIndex + " disappeared");
                }
                WebElement row = rows.get(rowIndex);
                WebElement icon = visibleInside(row, RECEIPT_ERROR_ICON);
                if (icon == null) {
                    throw new IllegalStateException("Receipt row " + (rowIndex + 1) + " has no failure icon");
                }
                scrollIntoViewSmooth(icon);
                fastClick(icon);
                wait.until(d -> isFailureReasonMenuOpen());
                String reason = trimReason(readFailureReason());
                if (reason.isBlank()) {
                    throw new IllegalStateException("Failure reason menu opened with blank text");
                }
                if (!closeFailureReasonMenu(rowIndex)) {
                    throw new IllegalStateException("Failure reason menu could not be closed");
                }
                return reason;
            } catch (RuntimeException e) {
                last = e;
                closeFailureReasonMenu(rowIndex);
                try { Thread.sleep(40); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw new IllegalStateException("Could not capture FAILED receipt row " + (rowIndex + 1)
                + " on page '" + pageMarker + "' after 3 attempts", last);
    }

    private String normalizeReasonKey(String reason) {
        String clean = trimReason(reason);
        return clean;
    }

    private int totalCapturedReasonCount(Map<String, Integer> reasonCounts) {
        int total = 0;
        for (Integer count : reasonCounts.values()) {
            if (count != null) total += count;
        }
        return total;
    }

    private String selectReceiptDateToday() {
        String isoDate = LocalDate.now(config.getBusinessZone())
                .format(DateTimeFormatter.ISO_LOCAL_DATE); // YYYY-MM-DD

        WebElement date = findDateInput();
        if (date == null) {
            throw new IllegalStateException("Receipt Date input not found");
        }

        ((JavascriptExecutor) driver).executeScript(
            "var el = arguments[0], v = arguments[1];" +
            "el.value = v;" +
            "el.dispatchEvent(new Event('input', { bubbles: true }));" +
            "el.dispatchEvent(new Event('change', { bubbles: true }));" +
            "el.dispatchEvent(new Event('blur', { bubbles: true }));" +
            "el.focus();",
            date, isoDate);

        for (int i = 0; i < 5; i++) {
            String current = clean(date.getAttribute("value"));
            if (isoDate.equals(current)) {
                return isoDate;
            }
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }

        throw new IllegalStateException("Receipt Date could not be set to " + isoDate
                + ". Current field value: " + clean(date.getAttribute("value")));
    }

    private boolean applyDateValue(String displayDate) {
        WebElement date = findDateInput();
        if (date == null) return false;
        try {
            ((JavascriptExecutor) driver).executeScript(
                "var el=arguments[0], v=arguments[1];" +
                "el.value=v;" +
                "el.dispatchEvent(new Event('input',{bubbles:true}));" +
                "el.dispatchEvent(new Event('change',{bubbles:true}));" +
                "el.dispatchEvent(new Event('blur',{bubbles:true}));" +
                "if(el.focus)el.focus();",
                date, displayDate);
            return true;
        } catch (Exception e) {
            log("[DATE] JS date apply failed: " + safeText(e));
            return false;
        }
    }

    private boolean verifyDateValue(String displayDate, int polls) {
        for (int i = 0; i < polls; i++) {
            if (displayDate.equals(readDateFieldValue())) return true;
            try { Thread.sleep(30); } catch (InterruptedException ignored) {}
        }
        return false;
    }

    private boolean selectPostingStatus(String status) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                WebElement selectElement = new WebDriverWait(driver, Duration.ofSeconds(3))
                        .until(ExpectedConditions.elementToBeClickable(LMS_POSTING_STATUS));
                
                Boolean success = (Boolean) ((JavascriptExecutor) driver).executeScript(
                    "var s = arguments[0], v = arguments[1];" +
                    "for (var i = 0; i < s.options.length; i++) {" +
                    "  var o = s.options[i];" +
                    "  if (o.text.trim() === v || o.value === v) {" +
                    "    var setter = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value').set;" +
                    "    setter.call(s, o.value);" +
                    "    s.selectedIndex = i;" +
                    "    s.dispatchEvent(new Event('input', { bubbles: true }));" +
                    "    s.dispatchEvent(new Event('change', { bubbles: true }));" +
                    "    s.dispatchEvent(new Event('blur', { bubbles: true }));" +
                    "    return true;" +
                    "  }" +
                    "}" +
                    "return false;",
                    selectElement, status);

                if (Boolean.TRUE.equals(success)) {
                    String selected = clean(new Select(selectElement).getFirstSelectedOption().getText());
                    if (status.equalsIgnoreCase(selected)) {
                        return true;
                    }
                }

                Select select = new Select(selectElement);
                for (WebElement option : select.getOptions()) {
                    String text = clean(option.getText());
                    String value = clean(option.getAttribute("value"));
                    if (status.equalsIgnoreCase(text) || status.equalsIgnoreCase(value)) {
                        if (status.equalsIgnoreCase(text)) select.selectByVisibleText(option.getText());
                        else select.selectByValue(option.getAttribute("value"));
                        
                        ((JavascriptExecutor) driver).executeScript(
                            "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));" +
                            "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));" +
                            "arguments[0].dispatchEvent(new Event('blur', { bubbles: true }));",
                            selectElement);
                        
                        String selected = clean(select.getFirstSelectedOption().getText());
                        if (status.equalsIgnoreCase(selected)) return true;
                    }
                }
            } catch (Exception e) {
                log("[POSTING STATUS] Attempt " + attempt + " failed: " + safeText(e));
            }
        }
        return false;
    }

    private WebElement findReceiptPageSizeSelect(String expected) {
        for (WebElement candidate : driver.findElements(RECEIPT_PAGE_SIZE_SELECT)) {
            try {
                if (!isDisplayed(candidate) || !candidate.isEnabled()) continue;
                Select select = new Select(candidate);
                for (WebElement option : select.getOptions()) {
                    if (expected.equals(clean(option.getText()))
                            || expected.equals(clean(option.getAttribute("value")))) {
                        return candidate;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean isSelectedPageSize(WebElement element, String expected) {
        try {
            WebElement selected = new Select(element).getFirstSelectedOption();
            return expected.equals(clean(selected.getText()))
                    || expected.equals(clean(selected.getAttribute("value")));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean waitUntilReceiptPageSizeIsApplied(int desiredSize) {
        String expected = String.valueOf(desiredSize);
        long deadline = System.currentTimeMillis() + 15000; // Increased to 15 seconds for data reload

        while (System.currentTimeMillis() < deadline) {
            if (expected.equals(queryParam("size"))) {
                if (!visibleReceiptRows().isEmpty() || isReceiptEmpty()) {
                    return true;
                }
            }

            String range = readPaginatorRange();
            Matcher matcher = PAGER_PATTERN.matcher(range);

            if (matcher.matches()) {
                int start = Integer.parseInt(matcher.group(1));
                int end = matcher.group(2) == null ? start : Integer.parseInt(matcher.group(2));
                int total = Integer.parseInt(matcher.group("total"));
                int expectedVisible = Math.min(desiredSize, total);
                int actualVisible = Math.max(0, end - start + 1);

                if (start == 1 && actualVisible == expectedVisible
                        ) {
                    if (!visibleReceiptRows().isEmpty() || isReceiptEmpty()) {
                        return true;
                    }
                }
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return expected.equals(queryParam("size")) && (!visibleReceiptRows().isEmpty() || isReceiptEmpty());
    }

    private WebElement findReceiptPageSizeTrigger(String expected) {
        for (WebElement candidate : driver.findElements(RECEIPT_PAGE_SIZE_TRIGGER)) {
            try {
                if (!isDisplayed(candidate) || !candidate.isEnabled()) continue;
                String text = clean(candidate.getText());
                String value = clean(candidate.getAttribute("value"));
                String aria = clean(candidate.getAttribute("aria-label"));
                if (expected.equals(text) || expected.equals(value)
                        || aria.toLowerCase(Locale.ROOT).contains("page")
                        || aria.toLowerCase(Locale.ROOT).contains("size")) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }

        for (WebElement candidate : driver.findElements(By.xpath(
                "//app-receipts//*[contains(concat(' ',normalize-space(@class),' '),' ct-size ') "
                        + "and contains(normalize-space(.),'Items per page')]//*[self::button or self::select or @role='combobox' or contains(@class,'ct-select')]") )) {
            try {
                if (isDisplayed(candidate) && candidate.isEnabled()) return candidate;
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private void goToFirstReceiptPage() {
        try {
            WebElement first = visibleElement(RECEIPT_FIRST_PAGE);
            if (first == null || !first.isEnabled()) return;

            String before = readPaginatorRange();
            clickAndWait(first);

            long deadline = System.currentTimeMillis() + 800;
            while (System.currentTimeMillis() < deadline) {
                String after = readPaginatorRange();
                if (after.startsWith("1") || (!after.isBlank() && !after.equals(before))) return;
                awaitUiStability();
            }
        } catch (Exception ignored) {
        }
    }

    private boolean ensurePostingStatusStillSelected(String expectedStatus) {
        try {
            WebElement selectElement = driver.findElement(LMS_POSTING_STATUS);
            String selected = clean(new Select(selectElement).getFirstSelectedOption().getText());
            return expectedStatus.equalsIgnoreCase(selected);
        } catch (Exception e) {
            return false;
        }
    }

    private String readSelectedPostingStatus() {
        try {
            WebElement selectElement = driver.findElement(LMS_POSTING_STATUS);
            WebElement selected = new Select(selectElement).getFirstSelectedOption();
            if (selected != null) {
                String text = clean(selected.getText());
                if (!text.isBlank()) return text;
                String value = clean(selected.getAttribute("value"));
                if (!value.isBlank()) return value;
            }
        } catch (Exception ignored) {}
        return "";
    }
private void searchReceipts(String expectedStatus, ReceiptCapture capture) {
        validateSessionAndWindow();
        ensureReceiptPage();
        ensureReceiptFiltersVisible();

        if (isFailureReasonMenuOpen() && !closeFailureReasonMenu(-1)) {
            throw new IllegalStateException("Cannot start " + expectedStatus
                    + " receipt search while a failure-reason menu is still open");
        }

        String expectedDate = LocalDate.now(config.getBusinessZone())
                .format(DateTimeFormatter.ISO_LOCAL_DATE);

        String currentDate = readDateFieldValue();
        if (!expectedDate.equals(currentDate)) {
            WebElement date = findDateInput();
            if (date == null) {
                capture.problems.add("Receipt Date field was not found before searching.");
                return;
            }
            ((JavascriptExecutor) driver).executeScript(
                    "var el = arguments[0], v = arguments[1];"
                            + "var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;"
                            + "setter.call(el, v);"
                            + "el.dispatchEvent(new Event('input', { bubbles: true }));"
                            + "el.dispatchEvent(new Event('change', { bubbles: true }));"
                            + "el.dispatchEvent(new Event('blur', { bubbles: true }));",
                    date, expectedDate);
            if (!verifyDateValue(expectedDate, 10)) {
                capture.problems.add("Receipt Date could not be set to " + expectedDate + " before searching.");
                return;
            }
        }

        if (!selectPostingStatus(expectedStatus)) {
            capture.problems.add("LMS Posting Status '" + expectedStatus + "' could not be selected.");
            return;
        }

        String selectedBeforeSearch = readSelectedPostingStatus();
        if (!expectedStatus.equalsIgnoreCase(selectedBeforeSearch)) {
            capture.problems.add("LMS Posting Status was not applied. Expected "
                    + expectedStatus + ", but found " + selectedBeforeSearch);
            return;
        }

        WebElement search = wait.until(ExpectedConditions.elementToBeClickable(RECEIPT_SEARCH));
        scrollIntoViewSmooth(search);
        try {
            search.click();
        } catch (Exception e) {
            jsClick(search);
        }
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].blur();", search);
        } catch (Exception ignored) { }

        try {
            wait.until(d -> {
                String urlStatus = queryParam("lmsPostingStatus");
                String urlDate = queryParam("receiptDate");
                String page = queryParam("page");
                return expectedStatus.equalsIgnoreCase(urlStatus)
                        && (urlDate.isBlank() || expectedDate.equals(urlDate))
                        && (page.isBlank() || "0".equals(page))
                        && (isReceiptEmpty() || !visibleReceiptRows().isEmpty() || !readPaginatorRange().isBlank());
            });
            waitForReceiptResults();
        } catch (RuntimeException e) {
            capture.problems.add("Receipt Search did not finish loading " + expectedStatus
                    + " results: " + safeText(e));
            return;
        }

        String finalStatus = readSelectedPostingStatus();
        String finalDate = readDateFieldValue();
        String finalUrlStatus = queryParam("lmsPostingStatus");
        String finalRange = readPaginatorRange();

        if (!expectedStatus.equalsIgnoreCase(finalStatus)
                || !expectedStatus.equalsIgnoreCase(finalUrlStatus)) {
            capture.problems.add("Status changed after search: expected " + expectedStatus
                    + ", selected='" + finalStatus + "', url='" + finalUrlStatus + "'");
        }
        if (!expectedDate.equals(finalDate)) {
            capture.problems.add("Date changed after search: expected " + expectedDate
                    + ", got " + finalDate);
        }

        log("[SEARCH] " + expectedStatus + " loaded with date " + finalDate
                + "; paginator='" + finalRange + "', rows=" + visibleReceiptRows().size());
    }

    private String queryParam(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }

        String url;
        try {
            url = driver.getCurrentUrl();
        } catch (Exception e) {
            return "";
        }
        if (url == null || url.isBlank()) {
            return "";
        }

        int questionMark = url.indexOf('?');
        if (questionMark < 0 || questionMark == url.length() - 1) {
            return "";
        }

        int fragment = url.indexOf('#', questionMark + 1);
        String query = fragment >= 0
                ? url.substring(questionMark + 1, fragment)
                : url.substring(questionMark + 1);

        for (String pair : query.split("&|;")) {
            if (pair.isBlank()) {
                continue;
            }

            int equals = pair.indexOf('=');
            String rawKey = equals >= 0 ? pair.substring(0, equals) : pair;
            if (!java.net.URLDecoder.decode(rawKey, StandardCharsets.UTF_8)
                    .equalsIgnoreCase(name)) {
                continue;
            }

            if (equals < 0 || equals == pair.length() - 1) {
                return "";
            }

            String rawValue = pair.substring(equals + 1);
            try {
                return java.net.URLDecoder.decode(rawValue, StandardCharsets.UTF_8).trim();
            } catch (IllegalArgumentException e) {
                return rawValue.trim();
            }
        }

        return "";
    }

    private int readReceiptTotalCount(ReceiptCapture capture) {
        String range = readPaginatorRange();
        Matcher matcher = PAGER_PATTERN.matcher(range);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group("total"));
        }

        capture.problems.add("Receipt paginator label '" + clean(range)
                + "' could not be read, so the total count is unknown");
        return UNKNOWN_COUNT;
    }

    private int parseTotalFromRange(String range) {
        if (range == null || range.isBlank()) {
            return UNKNOWN_COUNT;
        }
        Matcher matcher = PAGER_PATTERN.matcher(clean(range));
        if (matcher.matches()) {
            try {
                return Integer.parseInt(matcher.group("total"));
            } catch (NumberFormatException ignored) {
            }
        }
        return UNKNOWN_COUNT;
    }

    private String buildRowSignature(String pageMarker, WebElement row, int index) {
        String position = globalRowPosition(pageMarker, index);
        try {
            String text = clean(row.getText());
            if (text.isBlank()) {
                return "empty-row-" + position;
            }
            return position + "|" + text.hashCode();
        } catch (Exception e) {
            return "error-row-" + position;
        }
    }

    private String globalRowPosition(String pageMarker, int index) {
        Matcher matcher = PAGER_PATTERN.matcher(clean(pageMarker));
        if (matcher.matches()) {
            try {
                int start = Integer.parseInt(matcher.group(1));
                return String.valueOf(start + index);
            } catch (NumberFormatException ignored) {
            }
        }
        return pageMarker + "|" + index;
    }

    private String readFailureReason() {
        try {
            return clean(new WebDriverWait(driver, Duration.ofMillis(600))
                    .pollingEvery(Duration.ofMillis(25))
                    .until(d -> {
                List<WebElement> menus = d.findElements(FAILURE_MENU);
                for (int i = menus.size() - 1; i >= 0; i--) {
                    WebElement menu = menus.get(i);
                    if (!isDisplayed(menu)) continue;
                    String text = clean(menu.getText());
                    if (!text.isBlank()) return text;
                }
                return null;
            }));
        } catch (Exception e) {
            return "";
        }
    }

    private void scrollIntoViewSmooth(WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block:'center',inline:'nearest'});", element);
        } catch (Exception ignored) { }
    }

    private void fastClick(WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        } catch (Exception e) {
            try { element.click(); } catch (Exception ignored) { }
        }
    }

    private boolean closeFailureReasonMenu(int rowIndex) {
        if (!isFailureReasonMenuOpen()) return true;

        try {
            for (WebElement trigger : driver.findElements(OPEN_FAILURE_MENU_TRIGGER)) {
                if (!isDisplayed(trigger)) continue;
                try {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", trigger);
                } catch (Exception ignored) {
                    try { trigger.click(); } catch (Exception ignoredAgain) { }
                }
                if (waitForFailureReasonMenuToClose(500)) return true;
            }
        } catch (Exception ignored) { }

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                new Actions(driver).sendKeys(Keys.ESCAPE).perform();
            } catch (Exception ignored) {
                try { driver.switchTo().activeElement().sendKeys(Keys.ESCAPE); }
                catch (Exception ignoredAgain) { }
            }
            if (waitForFailureReasonMenuToClose(500)) return true;
        }

        return !isFailureReasonMenuOpen();
    }

    private boolean waitForFailureReasonMenuToClose(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (!isFailureReasonMenuOpen()) return true;
            try { Thread.sleep(25); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !isFailureReasonMenuOpen();
    }

    private boolean isFailureReasonMenuOpen() {
        try {
            return driver.findElements(FAILURE_MENU).stream().anyMatch(this::isDisplayed);
        } catch (Exception e) {
            return false;
        }
    }

    private void captureLatestExecutionFromJobsList(String jobName, JobStatus status) {
        validateSessionAndWindow();
        ensureJobsPage();
        WebElement jobRow = requireJobRow(jobName);

        RuntimeException lastCaptureError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                captureExecutionDetailsFromModal(jobName, jobRow, status);
                return;
            } catch (RuntimeException e) {
                lastCaptureError = e;
                log("[WARN] Execution details capture attempt " + attempt + "/2 failed for "
                        + jobName + ": " + safeText(e));

                try {
                    try {
                        closeExecutionModalUsingUi();
                    } catch (RuntimeException ignored) {
                    }
                    try {
                        closeJobDetailsUsingUi();
                    } catch (RuntimeException ignored) {
                    }
                    ensureJobsPage();
                    jobRow = requireJobRow(jobName);
                } catch (RuntimeException recoveryError) {
                    log("[WARN] UI recovery before the execution-capture retry failed: "
                            + safeText(recoveryError));
                }
            }
        }

        if (readLatestExecutionFromJobRow(jobRow, status)) {
            status.setJobFailureReason(appendProblem(status.getJobFailureReason(),
                    "Execution details were read from the jobs list because the execution "
                            + "details view could not be captured: " + safeText(lastCaptureError)));
            return;
        }

        throw new IllegalStateException("Job execution details could not be captured for "
                + jobName + " after the execution details view and jobs-list fallbacks: "
                + safeText(lastCaptureError));
    }

    private void captureExecutionDetailsFromModal(String jobName, WebElement jobRow, JobStatus status) {
        scrollToBottomAround(jobRow);

        WebElement jobView = visibleInside(jobRow, VIEW_ACTION);
        if (jobView == null) throw new IllegalStateException("View action was not found for " + jobName + ".");

        clickAndWait(jobView);
        waitForJobDetailsPage(jobName);

        WebElement latestExecutionRow = findLatestExecutionRow();
        if (latestExecutionRow == null) {
            throw new IllegalStateException("No execution record was found for " + jobName + ".");
        }

        WebElement executionView = visibleInside(latestExecutionRow, JOB_DETAIL_VIEW);
        if (executionView == null) {
            throw new IllegalStateException("Latest execution View was not found for " + jobName + ".");
        }

        scrollIntoView(executionView);
        clickAndWait(executionView);

        WebElement modal = waitForExecutionModal();
        scrollExecutionModalToBottom(modal);

        String executionStatus = waitForModalField(modal, "Status");
        String endDate = waitForModalField(modal, "End Date");
        String endTime = waitForModalField(modal, "End Time");
        String startDate = waitForModalField(modal, "Start Date");
        String startTime = waitForModalField(modal, "Start Time");

        String endDateTime = combineDateTime(endDate, endTime);
        String startDateTime = combineDateTime(startDate, startTime);
        String dateTime = executionStatus.isBlank()
                ? (!startDateTime.isBlank() ? startDateTime : endDateTime)
                : (!endDateTime.isBlank() ? endDateTime : startDateTime);
        if (dateTime.isBlank()) {
            String modalText = clean(modal.getText());
            dateTime = findDateTime(modalText);
        }
        if (dateTime.isBlank()) {
            dateTime = findDateTime(clean(latestExecutionRow.getText()));
        }

        if (executionStatus.isBlank()) {
            executionStatus = readStatusFromModalText(modal.getText());
        }

        if (executionStatus.isBlank()) {
            executionStatus = "N/A";
        }
        if (dateTime.isBlank()) {
            dateTime = "NOT CAPTURED";
        }

        validateExecutionData(executionStatus, dateTime, jobName, status);

        status.setStatus(executionStatus);
        status.setDateTime(dateTime);

        boolean partialReceiptOutcome = isPartialSuccessStatus(executionStatus);
        boolean inspectReason = JOB_POST_RECEIPTS.equalsIgnoreCase(jobName)
                || isFailedStatus(executionStatus)
                || partialReceiptOutcome;

        String jobFailureReason = "";
        if (inspectReason) {
            jobFailureReason = waitForModalField(modal, "Reason");
            if (jobFailureReason.isBlank()) {
                jobFailureReason = readReasonFromModalText(modal.getText());
            }
        }

        boolean receiptPostingFailure = normalizeReceiptPostingSummary(jobFailureReason, status);

        if (!jobFailureReason.isBlank() && isFailedStatus(executionStatus) && !receiptPostingFailure) {
            status.setJobFailureReason(trimReason(jobFailureReason));
        } else if (isFailedStatus(executionStatus) && jobFailureReason.isBlank()) {
            status.setJobFailureReason("Execution status is FAILED, but the job execution Reason field was blank or unavailable in the UI.");
            System.out.println("[WARN] Job status is FAILED but Reason could not be captured for " + jobName + ".");
        }

        boolean partialPostingFailure = JOB_POST_RECEIPTS.equalsIgnoreCase(jobName)
                && partialReceiptOutcome;
        boolean shouldCapturePostingLogs = partialPostingFailure
                || receiptPostingFailure && status.getFailedCount() > 0
                    && totalFailureReasonCount(status) < status.getFailedCount();

        if (shouldCapturePostingLogs) {
            log("[POSTING LOGS] Capture requested: status=" + executionStatus
                    + ", failedCount=" + status.getFailedCount()
                    + ", expectedFailureReasons=" + (receiptPostingFailure ? status.getFailedCount() : "status-triggered"));
            if (partialPostingFailure) {
                Map<String, Integer> previouslyCapturedReasons =
                        new LinkedHashMap<>(status.getFailureReasonCounts());
                status.clearFailureReasons();
                capturePostingLogFailureReasons(jobName, status);
                if (totalFailureReasonCount(status) == 0 && !previouslyCapturedReasons.isEmpty()) {
                    previouslyCapturedReasons.forEach(status::addFailureReason);
                }
            } else {
                capturePostingLogFailureReasons(jobName, status);
            }

            if (status.getFailedCount() > 0
                    && totalFailureReasonCount(status) < status.getFailedCount()) {
                String gapMessage = "Receipt UI/PostingLogs capture incomplete: captured "
                        + totalFailureReasonCount(status) + " of " + status.getFailedCount()
                        + " FAILED receipts.";
                String existing = status.getValidationMessage();
                status.setValidationMessage(existing == null || existing.isBlank()
                        ? gapMessage
                        : existing + "; " + gapMessage);
            }
        }

        closeExecutionModalUsingUi();
        closeJobDetailsUsingUi();
        waitForJobsPage();
    }

    private boolean readLatestExecutionFromJobRow(WebElement jobRow, JobStatus status) {
        if (jobRow == null) {
            return false;
        }

        try {
            StringBuilder rowText = new StringBuilder();
            String rowStatus = null;

            for (WebElement cell : jobRow.findElements(By.xpath("./td"))) {
                if (!isDisplayed(cell)) continue;
                String text = clean(cell.getText());
                if (text.isBlank()) continue;

                if (rowText.length() > 0) {
                    rowText.append(' ');
                }
                rowText.append(text);

                if (rowStatus == null && STATUS_LINE.matcher(text).matches() && isPlausibleExecutionStatus(text)) {
                    rowStatus = text;
                }
            }

            if (rowText.isEmpty()) {
                return false;
            }

            String rowDateTime = findDateTime(rowText.toString());
            boolean captured = false;

            if (rowStatus != null && (status.getStatus() == null || status.getStatus().isBlank())) {
                status.setStatus(rowStatus);
                captured = true;
            } else if (rowStatus == null && (status.getStatus() == null || status.getStatus().isBlank())) {
                status.setStatus("N/A");
                captured = true;
            }
            if (!rowDateTime.isBlank() && (status.getDateTime() == null || status.getDateTime().isBlank())) {
                status.setDateTime(rowDateTime);
                captured = true;
            }

            return captured;
        } catch (RuntimeException e) {
            log("[WARN] Jobs-list execution fallback could not be read: " + safeText(e));
            return false;
        }
    }

    private boolean isPlausibleExecutionStatus(String value) {
        String normalized = clean(value).toUpperCase(Locale.ROOT).replace(' ', '_');
        if (normalized.isBlank()) return false;
        return normalized.contains("SUCCESS")
                || normalized.contains("COMPLETED")
                || normalized.equals("SUCCEEDED")
                || normalized.contains("FAIL")
                || normalized.contains("PARTIAL")
                || normalized.equals("SCHEDULED")
                || normalized.equals("EXECUTION")
                || normalized.equals("RUNNING")
                || normalized.equals("PAUSED")
                || normalized.equals("BLOCKED")
                || normalized.equals("CANCELLED");
    }

    private void validateExecutionData(String executionStatus, String dateTime, String jobName, JobStatus status) {
        if (executionStatus == null || executionStatus.isBlank() || "No Status".equals(executionStatus)) {
            status.setValidationMessage("Execution status could not be determined for " + jobName);
        }
        if ("NOT CAPTURED".equals(dateTime)) {
            String existing = status.getValidationMessage();
            String msg = "Execution end date/time could not be captured for " + jobName;
            status.setValidationMessage((existing == null ? "" : existing + "; ") + msg);
        }
        if (status.getClientName() == null || status.getClientName().isBlank()) {
            throw new IllegalStateException("Client name is missing for job " + jobName);
        }
    }

    private boolean isFailedStatus(String status) {
        return status != null && status.trim().toUpperCase(Locale.ROOT).contains("FAIL");
    }

    private boolean isPartialSuccessStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().replaceAll("[\\s-]+", "_").toUpperCase(Locale.ROOT);
        return normalized.contains("PARTIALLY_SUCCESSFUL")
                || normalized.contains("PARTIAL_SUCCESS");
    }

    private boolean normalizeReceiptPostingSummary(String reason, JobStatus status) {
        String text = clean(reason);
        Matcher matcher = RECEIPT_POSTING_FAILURE.matcher(text);
        if (!matcher.matches()) {
            return false;
        }

        int total = Integer.parseInt(matcher.group(1));
        int success = Integer.parseInt(matcher.group(2));
        int partial = Integer.parseInt(matcher.group(3));
        int failed = Integer.parseInt(matcher.group(4));

        if (isPartialSuccessStatus(status.getStatus())) {
            status.setFailedCount(failed);
            status.setValidationMessage(removeReceiptCountMismatch(status.getValidationMessage()));
            log("[RECEIPT RECONCILIATION] PARTIALLY_SUCCESSFUL execution reports "
                    + failed + " failed receipt(s); using this count to validate PostingLogs capture.");
        } else if (status.getFailedCount() == UNKNOWN_COUNT) {
            status.setFailedCount(failed);
            status.setValidationMessage(
                "FAILED receipt search count was unavailable; using the job execution summary count of "
                + failed + " as the fallback."
            );
        } else if (failed != status.getFailedCount()) {
            String mismatch = "Receipt count mismatch: receipt API reports " + status.getFailedCount()
                    + " FAILED record(s), while the job execution summary reports " + failed
                    + " FAILED record(s). These counts may cover different receipt populations; "
                    + "the receipt UI/API count is retained as authoritative.";
            status.setValidationMessage(mismatch);
            log("[RECEIPT RECONCILIATION] " + status.getClientName() + ": " + mismatch);
        }

        System.out.println("[RECEIPT OUTCOME] Receipt Posting Failure summary detected: "
                + "Total=" + total + ", Success=" + success
                + ", Partially Success=" + partial + ", Failed=" + failed
                + ". Receipt UI remains authoritative for receipt counts and reasons.");

        return failed > 0;
    }

    private int totalFailureReasonCount(JobStatus status) {
        int total = 0;
        for (Integer count : status.getFailureReasonCounts().values()) {
            if (count != null) total += count;
        }
        return total;
    }

    private WebElement findPostingLogsAction() {
        By actionLocator = By.xpath(
                "//*[self::button or self::a or @role='button']"
                + "[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'posting log')]");
        List<WebElement> actions = driver.findElements(actionLocator);
        for (WebElement action : actions) {
            try {
                if (isDisplayed(action) && action.isEnabled()) {
                    return action;
                }
            } catch (StaleElementReferenceException ignored) {
            }
        }
        return null;
    }

    private void capturePostingLogFailureReasons(String jobName, JobStatus status) {
        if (!JOB_POST_RECEIPTS.equalsIgnoreCase(jobName)) {
            return;
        }

        try {
            WebElement postingLogs = findPostingLogsAction();
            if (postingLogs == null) {
                status.setJobFailureReason(appendProblem(status.getJobFailureReason(),
                        "Receipt Posting Failure detected, but the Posting Logs action was not found in job details."));
                return;
            }

            scrollIntoView(postingLogs);
            try {
                clickAndWait(postingLogs);
            } catch (RuntimeException clickFailure) {
                WebElement refreshedAction = findPostingLogsAction();
                if (refreshedAction == null) {
                    throw new IllegalStateException("Posting Logs action disappeared before it could be clicked.", clickFailure);
                }
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", refreshedAction);
            }
            log("[POSTING LOGS] Opened Posting Logs for " + status.getStatus()
                    + " Post Receipts execution.");
            WebElement modal = wait.until(d -> {
                List<WebElement> modals = d.findElements(POSTING_LOGS_MODAL);
                for (int i = modals.size() - 1; i >= 0; i--) {
                    if (isDisplayed(modals.get(i))) return modals.get(i);
                }
                return null;
            });

            setPostingLogPageSizeTo100();

            Map<String, Integer> reasonCounts = new LinkedHashMap<>();
            Set<String> pages = new LinkedHashSet<>();
            Set<String> seenReceiptNumbers = new LinkedHashSet<>();
            Set<String> seenFallbackRows = new LinkedHashSet<>();
            int pageCount = 0;
            int expectedTotal = readPostingLogTotalCount();
            int pageSize = readCurrentPostingLogPageSize();
            int calculatedMaxPages = expectedTotal > 0 && pageSize > 0
                    ? (int) Math.ceil((double) expectedTotal / pageSize) + 2
                    : MAX_POSTING_LOG_PAGES;
            int maxPages = Math.min(MAX_POSTING_LOG_PAGES, Math.max(1, calculatedMaxPages));

            while (true) {
                validateSessionAndWindow();

                if (pageCount >= maxPages) {
                    status.setJobFailureReason(appendProblem(status.getJobFailureReason(),
                            "PostingLogs pagination exceeded safety limit (" + maxPages
                                    + " pages; total=" + expectedTotal + ", pageSize=" + pageSize + ")"));
                    break;
                }

                String range = readPostingLogRange();
                if (!range.isBlank() && !pages.add(range)) {
                    break;
                }

                List<WebElement> rows = driver.findElements(POSTING_LOG_ROWS);
                boolean foundReason = false;

                for (WebElement row : rows) {
                    if (!isDisplayed(row)) continue;
                    List<WebElement> cells = row.findElements(By.xpath("./td"));
                    if (cells.size() < 8) continue;

                    String receiptNumber = clean(cells.get(0).getText());
                    String receiptStatus = clean(cells.get(5).getText());
                    String reason = clean(cells.get(6).getText());
                    String failureCode = clean(cells.get(7).getText());
                    boolean partialExecution = isPartialSuccessStatus(status.getStatus());
                    String normalizedStatus = receiptStatus.toUpperCase(Locale.ROOT);
                    boolean failedStatus = normalizedStatus.contains("FAIL");
                    boolean successStatus = normalizedStatus.contains("SUCCESS")
                            || normalizedStatus.contains("COMPLETED");
                    boolean hasFailureDetails = !reason.isBlank() || !failureCode.isBlank();

                    boolean captureFailure = failedStatus
                            || partialExecution && receiptStatus.isBlank() && hasFailureDetails
                            || !partialExecution && !reason.isBlank()
                                    && (receiptStatus.isBlank() || !successStatus || !failureCode.isBlank());
                    if (!captureFailure) continue;

                    String fallbackSignature = buildRowSignature("", row, 0);
                    if (!receiptNumber.isBlank()) {
                        if (!seenReceiptNumbers.add(receiptNumber)) continue;
                    } else if (!seenFallbackRows.add(fallbackSignature)) {
                        continue;
                    }

                    String normalizedReason = trimReason(
                            reason.isBlank()
                                    ? failureCode.isBlank()
                                            ? "Failure reason unavailable in Posting Logs"
                                            : "Failure Code: " + failureCode
                                    : failureCode.isBlank()
                                            ? reason
                                            : reason + " | Failure Code: " + failureCode);
                    reasonCounts.merge(normalizedReason, 1, Integer::sum);
                    foundReason = true;
                }

                if (!foundReason && rows.isEmpty()) {
                    status.setJobFailureReason(appendProblem(status.getJobFailureReason(),
                            "Receipt Posting Failure detected, but PostingLogs returned no receipt records."));
                }

                pageCount++;

                WebElement next = visibleElement(POSTING_LOG_NEXT_PAGE);
                if (next == null || !next.isEnabled()) {
                    break;
                }

                String before = range;
                clickAndWait(next);
                try {
                    wait.until(d -> {
                        String after = readPostingLogRange();
                        return !after.isBlank() && !after.equals(before);
                    });
                } catch (RuntimeException e) {
                    status.setJobFailureReason(appendProblem(status.getJobFailureReason(),
                            "PostingLogs pagination stopped before the next page was confirmed after range "
                                    + before + ". Captured failure reasons may be incomplete."));
                    break;
                }
            }

            log("Scanned " + pageCount + " posting log page(s), found " + reasonCounts.size() + " unique failure reason(s)");

            if (!reasonCounts.isEmpty()) {
                for (Map.Entry<String, Integer> entry : reasonCounts.entrySet()) {
                    status.addFailureReason(entry.getKey(), entry.getValue());
                }

                int postingLogFailures = reasonCounts.values().stream()
                        .mapToInt(Integer::intValue)
                        .sum();
                if (status.getFailedCount() == 0
                        && isPartialSuccessStatus(status.getStatus())
                        && postingLogFailures > 0) {
                    status.setFailedCount(postingLogFailures);
                    status.setValidationMessage(removeReceiptCountMismatch(status.getValidationMessage()));
                }
            }

            if (reasonCounts.isEmpty()) {
                status.setJobFailureReason(appendProblem(status.getJobFailureReason(),
                        "Receipt Posting Failure detected, but no receipt-level failure reason was available in PostingLogs."));
            }

            closePostingLogsModal();
        } catch (Exception e) {
            status.setJobFailureReason(appendProblem(status.getJobFailureReason(),
                    "Receipt Posting Failure detected, but PostingLogs could not be captured: "
                            + e.getClass().getSimpleName() + ": " + safeText(e)));
            closePostingLogsModal();
        }
    }

    private String removeReceiptCountMismatch(String validation) {
        if (validation == null || validation.isBlank()) {
            return "";
        }

        List<String> remaining = new ArrayList<>();
        for (String item : validation.split(";\\s*")) {
            String value = clean(item);
            if (!value.isBlank()
                    && !value.toLowerCase(Locale.ROOT).startsWith("receipt count mismatch:")) {
                remaining.add(value);
            }
        }
        return String.join("; ", remaining);
    }

    private int readPostingLogTotalCount() {
        String range = readPostingLogRange();
        Matcher matcher = PAGER_PATTERN.matcher(range);
        if (!matcher.matches()) return UNKNOWN_COUNT;
        try {
            return Integer.parseInt(matcher.group("total"));
        } catch (RuntimeException e) {
            return UNKNOWN_COUNT;
        }
    }

    private int readCurrentPostingLogPageSize() {
        String range = readPostingLogRange();
        Matcher matcher = PAGER_PATTERN.matcher(range);
        if (!matcher.matches()) return UNKNOWN_COUNT;
        try {
            int start = Integer.parseInt(matcher.group(1));
            int end = matcher.group(2) == null ? start : Integer.parseInt(matcher.group(2));
            return Math.max(1, end - start + 1);
        } catch (RuntimeException e) {
            return UNKNOWN_COUNT;
        }
    }

    private boolean setPostingLogPageSizeTo100() {
        final int desiredSize = 100;

        try {
            for (WebElement trigger : driver.findElements(POSTING_LOG_PAGE_SIZE_TRIGGER)) {
                try {
                    if (!isDisplayed(trigger) || !trigger.isEnabled()) continue;

                    scrollIntoView(trigger);
                    fastClick(trigger);

                    WebElement option = new WebDriverWait(driver, Duration.ofSeconds(3)).until(d -> {
                        for (WebElement candidate : d.findElements(POSTING_LOG_PAGE_SIZE_OPTION_100)) {
                            if (isDisplayed(candidate) && "100".equals(clean(candidate.getText()))) {
                                return candidate;
                            }
                        }
                        return null;
                    });

                    fastClick(option);
                    if (waitUntilPostingLogPageSizeIsApplied(desiredSize)) {
                        log("[POSTING LOGS] Page size set to 100 via Angular Material paginator.");
                        return true;
                    }
                } catch (Exception ignored) {
                    safeEscape();
                }
            }
        } catch (Exception e) {
            log("[POSTING LOGS] Material paginator page-size change failed: " + safeText(e));
        }

        try {
            By selectLocator = By.cssSelector(
                    "app-receipt-posting-log mat-paginator select, "
                            + "app-receipt-posting-log app-custom-table select[name='pageSize'], "
                            + "app-receipt-posting-log app-custom-table select.ct-select");
            for (WebElement selectElement : driver.findElements(selectLocator)) {
                try {
                    if (!isDisplayed(selectElement) || !selectElement.isEnabled()) continue;
                    Select select = new Select(selectElement);
                    boolean has100 = select.getOptions().stream().anyMatch(option ->
                            "100".equals(clean(option.getText()))
                                    || "100".equals(clean(option.getAttribute("value"))));
                    if (!has100) continue;

                    try {
                        select.selectByVisibleText("100");
                    } catch (Exception e) {
                        select.selectByValue("100");
                    }
                    ((JavascriptExecutor) driver).executeScript(
                            "arguments[0].dispatchEvent(new Event('input',{bubbles:true}));"
                                    + "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));"
                                    + "arguments[0].dispatchEvent(new Event('blur',{bubbles:true}));",
                            selectElement);

                    if (waitUntilPostingLogPageSizeIsApplied(desiredSize)) {
                        log("[POSTING LOGS] Page size set to 100 via native select.");
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            log("[POSTING LOGS] Native page-size fallback failed: " + safeText(e));
        }

        log("[POSTING LOGS] Could not verify page size 100; paginator will be traversed safely until the last page.");
        return false;
    }

    private boolean waitUntilPostingLogPageSizeIsApplied(int desiredSize) {
        long deadline = System.currentTimeMillis() + 5000L;

        while (System.currentTimeMillis() < deadline) {
            String range = readPostingLogRange();
            Matcher matcher = PAGER_PATTERN.matcher(range);

            if (matcher.matches()) {
                int start = Integer.parseInt(matcher.group(1));
                int end = matcher.group(2) == null ? start : Integer.parseInt(matcher.group(2));
                int total = Integer.parseInt(matcher.group("total"));
                int expectedVisible = Math.min(desiredSize, total);
                int actualVisible = Math.max(0, end - start + 1);

                if (start == 1 && actualVisible == expectedVisible
                        ) {
                    return true;
                }
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    private String readPostingLogRange() {
        try {
            WebElement range = visibleElement(By.cssSelector(
                    "app-receipt-posting-log div.paginator-container .ct-range, "
                    + "app-receipt-posting-log .ct-range, "
                    + "app-receipt-posting-log mat-paginator .mat-mdc-paginator-range-label, "
                    + "app-receipt-posting-log mat-paginator .mat-paginator-range-label"));
            return range == null ? "" : clean(range.getText());
        } catch (Exception e) {
            return "";
        }
    }

    private void closePostingLogsModal() {
        try {
            List<WebElement> modals = driver.findElements(POSTING_LOGS_MODAL);
            for (int i = modals.size() - 1; i >= 0; i--) {
                WebElement modal = modals.get(i);
                if (!isDisplayed(modal)) continue;

                WebElement close = visibleInside(modal, By.xpath(".//button[.//span[contains(@class,'material-symbols-rounded') and normalize-space()='close']]"));
                if (close != null) {
                    try {
                        clickAndWait(close);
                    } catch (Exception e) {
                        jsClick(close);
                    }
                }
                break;
            }

            wait.until(d -> d.findElements(POSTING_LOGS_MODAL).stream().noneMatch(this::isDisplayed));
        } catch (Exception e) {
            try {
                driver.switchTo().activeElement().sendKeys(Keys.ESCAPE);
                awaitUiStability();
            } catch (Exception ignored) {
            }
        }
    }

    private static final Pattern STATUS_LINE = Pattern.compile(
            "(?i)^(success(?:ful(?:ly)?)?|completed(?: successfully)?|succeeded|failed|failure|"
                    + "partially[_ ]successful|partial[_ ]success|partially failed|processing|running|"
                    + "in progress|aborted|cancelled|canceled|skipped)$");

    private String readStatusFromModalText(String modalText) {
        if (modalText == null || modalText.isBlank()) return "";

        for (String line : modalText.replace("\r", "").split("\n")) {
            String value = clean(line);
            if (value.isBlank() || "Status".equalsIgnoreCase(value)) continue;
            if (STATUS_LINE.matcher(value).matches()) return value;
        }
        return "";
    }

    private String readReasonFromModalText(String modalText) {
        if (modalText == null || modalText.isBlank()) return "";

        String normalized = modalText.replace("\r", "");
        String[] lines = normalized.split("\n");
        boolean reasonStarted = false;
        StringBuilder reason = new StringBuilder();
        for (String line : lines) {
            String cleanLine = clean(line);
            if (cleanLine.isBlank()) continue;
            if (!reasonStarted) {
                if ("Reason".equalsIgnoreCase(cleanLine)) {
                    reasonStarted = true;
                }
                continue;
            }
            if ("Status".equalsIgnoreCase(cleanLine) || "Start Date".equalsIgnoreCase(cleanLine)
                || "Start Time".equalsIgnoreCase(cleanLine) || "End Date".equalsIgnoreCase(cleanLine)
                || "End Time".equalsIgnoreCase(cleanLine) || "Duration".equalsIgnoreCase(cleanLine)
                || "Time Duration".equalsIgnoreCase(cleanLine) || "Next Fire Time".equalsIgnoreCase(cleanLine)
                || "Previous Fire Time".equalsIgnoreCase(cleanLine) || "Value Date".equalsIgnoreCase(cleanLine)) {
                break;
            }
            if (reason.length() > 0) reason.append(" ");
            reason.append(cleanLine);
        }
        return clean(reason.toString());
    }

    private WebElement findLatestExecutionRow() {
        wait.until(d -> !d.findElements(JOB_DETAIL_ROWS).isEmpty());
        for (WebElement row : driver.findElements(JOB_DETAIL_ROWS)) {
            try {
                if (!isDisplayed(row)) continue;
                if (visibleInside(row, JOB_DETAIL_VIEW) != null && row.findElements(By.xpath("./td")).size() >= 7) {
                    return row;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private WebElement waitForExecutionModal() {
        return wait.until(d -> {
            List<WebElement> modals = d.findElements(EXECUTION_MODAL);
            for (int i = modals.size() - 1; i >= 0; i--) {
                if (isDisplayed(modals.get(i))) return modals.get(i);
            }
            return null;
        });
    }

    private void scrollExecutionModalToBottom(WebElement modal) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object scrolled = js.executeScript(
                    "const root = arguments[0];"
                    + "const reason = [...root.querySelectorAll('.list-label')]"
                    + ".find(el => el.textContent.trim() === 'Reason');"
                    + "if (!reason) return false;"
                    + "const scroller = root.querySelector('.w-full.absolute.overflow-auto');"
                    + "if (scroller) {"
                    + "const target = reason.getBoundingClientRect();"
                    + "const viewport = scroller.getBoundingClientRect();"
                    + "scroller.scrollTop = Math.max(0, Math.min(scroller.scrollHeight - scroller.clientHeight,"
                    + "scroller.scrollTop + target.top - viewport.top - 24));"
                    + "return true;"
                    + "}"
                    + "let node = reason.parentElement;"
                    + "while (node && node !== root) {"
                    + "const style = getComputedStyle(node);"
                    + "if (node.scrollHeight > node.clientHeight + 2 && /(auto|scroll|overlay)/.test(style.overflowY)) {"
                    + "const target = reason.getBoundingClientRect();"
                    + "const viewport = node.getBoundingClientRect();"
                    + "node.scrollTop = Math.max(0, Math.min(node.scrollHeight - node.clientHeight,"
                    + "node.scrollTop + target.top - viewport.top - 24));"
                    + "return true;"
                    + "}"
                    + "node = node.parentElement;"
                    + "}"
                    + "return false;",
                    modal);
            if (Boolean.TRUE.equals(scrolled)) {
                log("[EXECUTION MODAL] Scrolled the client job-details panel to the Reason field.");
            } else {
                log("[WARN] Could not find the client job-details scroll container or Reason field.");
            }
        } catch (Exception e) {
            log("[WARN] Could not scroll the job details panel to the Reason field: " + safeText(e));
        }
    }

    private String waitForModalField(WebElement modal, String label) {
        if (!hasModalField(modal, label)) {
            return "";
        }
        try {
            return clean(shortWait.until(d -> {
                String value = readModalField(modal, label);
                return value.isBlank() ? null : value;
            }));
        } catch (Exception ignored) {
            return readModalField(modal, label);
        }
    }

    private boolean hasModalField(WebElement modal, String label) {
        String literal = xpathLiteral(label);
        try {
            WebElement el = modal.findElement(By.xpath(
                    ".//div[contains(@class,'list-label') and normalize-space()=" + literal + "]"));
            return isDisplayed(el);
        } catch (Exception e) {
            return false;
        }
    }

    private String readModalField(WebElement modal, String label) {
        String literal = xpathLiteral(label);
        try {
            WebElement row = modal.findElement(By.xpath(".//div[contains(@class,'list')][.//div[contains(@class,'list-label') and normalize-space()=" + literal + "]]"));
            WebElement value = row.findElement(By.xpath(".//div[contains(@class,'list-content')][1]"));
            String text = clean(value.getText());
            if (!text.isBlank() && !text.equalsIgnoreCase(label)) return text;
        } catch (Exception ignored) {
        }

        try {
            WebElement labelEl = modal.findElement(By.xpath(".//div[contains(@class,'list-label') and normalize-space()=" + literal + "]"));
            WebElement parent = labelEl.findElement(By.xpath("./parent::div"));
            String text = clean(parent.getText());
            if (!text.equalsIgnoreCase(label)) return clean(text.replaceFirst("(?i)^" + Pattern.quote(label) + "\\s*", ""));
        } catch (Exception ignored) {
        }
        return "";
    }

    private void closeExecutionModalUsingUi() {
        try {
            if (isExecutionModalClosed()) return;

            List<WebElement> modals = driver.findElements(EXECUTION_MODAL);
            for (int i = modals.size() - 1; i >= 0; i--) {
                WebElement modal = modals.get(i);
                if (isDisplayed(modal)) {
                    List<WebElement> closeBtns = modal.findElements(
                        By.xpath(".//button[.//span[contains(@class,'material-symbols-rounded') and normalize-space()='close']]")
                    );
                    for (WebElement btn : closeBtns) {
                        if (isDisplayed(btn)) {
                            try {
                                clickAndWait(btn);
                            } catch (Exception e) {
                                jsClick(btn);
                            }
                            break;
                        }
                    }
                }
            }

            try {
                wait.until(d -> isExecutionModalClosed());
            } catch (RuntimeException ignored) {
            }

            if (!isExecutionModalClosed()) {
                try {
                    driver.switchTo().activeElement().sendKeys(Keys.ESCAPE);
                    awaitUiStability();
                } catch (Exception ignored) {}
            }

            if (!isExecutionModalClosed()) {
                for (WebElement btn : driver.findElements(By.xpath("//mat-dialog-container//button[contains(@class,'close') or .//span[normalize-space()='close']] | //div[contains(@class,'cdk-overlay-backdrop')]"))) {
                    try {
                        if (isDisplayed(btn)) {
                            jsClick(btn);
                            awaitUiStability();
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (!isExecutionModalClosed()) {
                action.waitForOverlayToClear();
            }
        } catch (Exception e) {
            System.out.println("[WARN] Non-critical execution modal close note: " + e.getMessage());
        }
    }

    private boolean isExecutionModalClosed() {
        return driver.findElements(EXECUTION_MODAL).stream().noneMatch(this::isDisplayed);
    }

    private void closeJobDetailsUsingUi() {
        try {
            WebElement close = visibleElement(JOB_DETAILS_CLOSE);
            if (close != null) {
                try {
                    clickAndWait(close);
                } catch (Exception e) {
                    jsClick(close);
                }
            } else {
                recoverToJobsPage();
            }
            waitForJobsPage();
        } catch (Exception e) {
            System.out.println("[WARN] Job Details close button fallback triggered: " + e.getMessage());
            recoverToJobsPage();
        }
    }

    private void closeReceiptPageUsingUi() {
        try {
            WebElement close = visibleElement(RECEIPT_CLOSE);
            if (close != null) {
                try {
                    clickAndWait(close);
                } catch (Exception e) {
                    jsClick(close);
                }
            } else {
                recoverToJobsPage();
            }
            waitForJobsPage();
        } catch (Exception e) {
            System.out.println("[WARN] Post-receipts close button fallback triggered: " + e.getMessage());
            recoverToJobsPage();
        }
    }

    private void returnToJobsListUsingUi() {
        String url = driver.getCurrentUrl().toLowerCase(Locale.ROOT);
        if (url.contains("/postreceipts")) closeReceiptPageUsingUi();
        else if (url.contains("/admin/job/details/")) {
            closeExecutionModalUsingUi();
            closeJobDetailsUsingUi();
        }
        else waitForJobsPage();
    }

    private void recoverToJobsPage() {
        try {
            String url = driver.getCurrentUrl().toLowerCase(Locale.ROOT);
            if (url.contains("/postreceipts")) {
                try { closeReceiptPageUsingUi(); return; } catch (Exception ignored) {}
            }
            if (url.contains("/admin/job/details/")) {
                try { closeExecutionModalUsingUi(); } catch (Exception ignored) {}
                try { closeJobDetailsUsingUi(); return; } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {
        }
        navigateToAdminJobs();
    }

    private void ensureJobsPage() {
        validateSessionAndWindow();
        if (isJobsPageLoaded()) return;
        recoverToJobsPage();
        if (isJobsPageLoaded()) return;

        navigateToAdminJobs();

        if (!isJobsPageLoaded()) {
            throw new IllegalStateException(
                "Admin Jobs page is not available. URL: " + driver.getCurrentUrl()
                    + " Jobs on page: " + visibleJobNames());
        }
    }

    private void waitForJobsPage() {
        validateSessionAndWindow();

        if (!isJobsPageLoaded()) {
            navigateToAdminJobs();
        }

        jobsPageWait.until(d -> {
            requireLiveSession(d);

            String url = d.getCurrentUrl().toLowerCase(Locale.ROOT);
            if (!url.contains("/admin/job")
                || url.contains("/postreceipts")
                || url.contains("/details/")) {
                return false;
            }

            return isDisplayed(JOBS_PAGE_ROOT)
                    && !visibleJobRows().isEmpty()
                    && findJobRowOptional(JOB_POST_RECEIPTS) != null;
        });
    }

    private void hoverAdminMenu() {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                action.waitForOverlayToClear();
                WebElement admin = waitForNavigation("the Admin menu trigger",
                        d -> visibleElement(ADMIN_MENU_TRIGGER));

                new Actions(driver).moveToElement(admin).perform();

                waitForNavigation("the Admin submenu to become visible", d -> isAdminSubmenuVisible());
                return;
            } catch (RuntimeException e) {
                lastFailure = e;
                System.out.println("[WARN] Admin menu hover attempt " + attempt + " failed: " + e.getMessage());
            }
        }

        throw new IllegalStateException(
                describeNavigationFailure("Admin submenu could not be opened by hovering Admin."),
                lastFailure);
    }

    private void clickJobInAdminMenu() {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                WebElement job = waitForNavigation("the Job item inside the Admin submenu",
                        d -> visibleElement(ADMIN_MENU_JOB_ITEM));

                new Actions(driver).moveToElement(job).perform();
                waitForNavigation("the Job item to become clickable",
                        d -> isDisplayed(ADMIN_MENU_JOB_ITEM) && isElementEnabled(ADMIN_MENU_JOB_ITEM));

                visibleElement(ADMIN_MENU_JOB_ITEM).click();
                return;
            } catch (StaleElementReferenceException e) {
                lastFailure = e;
                System.out.println("[WARN] Job item went stale, reopening the Admin submenu.");
                hoverAdminMenu();
            } catch (RuntimeException e) {
                lastFailure = e;
                System.out.println("[WARN] Job click attempt " + attempt + " failed: " + e.getMessage());
                if (!isAdminSubmenuVisible()) hoverAdminMenu();
            }
        }

        throw new IllegalStateException(
                describeNavigationFailure("Job could not be clicked inside the Admin submenu."),
                lastFailure);
    }

    private void verifyAdminJobsPageRendered() {
        waitForNavigation("the Admin Jobs page to render its jobs table",
                d -> isAdminJobsPageRendered());
    }

    private boolean isAdminSubmenuVisible() {
        return isDisplayed(ADMIN_MENU_OVERLAY) && isDisplayed(ADMIN_MENU_PANEL);
    }

    private boolean isAdminJobsPageRendered() {
        return isDisplayed(JOBS_PAGE_ROOT) && !visibleJobRows().isEmpty();
    }

    private boolean isElementEnabled(By locator) {
        WebElement element = visibleElement(locator);
        if (element == null) return false;
        try {
            return element.isEnabled();
        } catch (StaleElementReferenceException e) {
            return false;
        }
    }

private <T> T waitForNavigation(String description, Function<? super WebDriver, T> condition) {
        try {
            return jobsPageWait.until(condition);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                describeNavigationFailure("Timed out waiting for " + description + "."), e);
        }
    }

    private void waitForState(String description, java.util.function.Predicate<WebDriver> condition) {
        waitForNavigation(description, d -> condition.test(d) ? Boolean.TRUE : null);
    }

    private String describeNavigationFailure(String reason) {
        action.captureStep("Admin Jobs navigation failure");

        String url;
        try {
            url = driver.getCurrentUrl();
        } catch (RuntimeException e) {
            url = "<unavailable>";
        }

        return reason
                + " URL: " + url
                + " | Admin trigger visible: " + isDisplayed(ADMIN_MENU_TRIGGER)
                + " | Admin submenu visible: " + isAdminSubmenuVisible()
                + " | Job item visible: " + isDisplayed(ADMIN_MENU_JOB_ITEM)
                + " | Jobs page rendered: " + isAdminJobsPageRendered();
    }

    private void requireLiveSession(WebDriver d) {
        if (d instanceof BasePage) {
            ((BasePage) d).validateSessionAndWindow();
        } else {
            try {
                java.util.Set<String> handles = d.getWindowHandles();
                if (handles.isEmpty()) {
                    throw new IllegalStateException("Browser window was closed before the Admin Jobs page finished loading.");
                }
            } catch (RuntimeException e) {
                throw new IllegalStateException("Browser session ended before the Admin Jobs page finished loading.", e);
            }
        }
    }

private void waitForJobDetailsPage(String jobName) {
        String group = JOB_GROUPS.get(jobName);
        waitForState("the " + jobName + " details route", d -> {
                String url = d.getCurrentUrl().toLowerCase(Locale.ROOT);
                return group != null && url.contains("/admin/job/details/" + group.toLowerCase(Locale.ROOT));
            });
        waitForState("the " + jobName + " details component",
                d -> d.findElements(JOB_DETAILS_ROOT).stream().anyMatch(this::isDisplayed));
        waitForState("at least one execution row on " + jobName,
                d -> d.findElements(JOB_DETAIL_ROWS).stream().anyMatch(this::isDisplayed));
    }

    private void waitForReceiptPage() {
        wait.until(d -> d.getCurrentUrl().toLowerCase(Locale.ROOT).contains("/admin/job/postreceipts"));
        wait.until(d -> isDisplayed(RECEIPT_SHOW_FILTER) || isDisplayed(RECEIPT_HIDE_FILTER)
                || !visibleReceiptRows().isEmpty() || !readPaginatorRange().isBlank());
    }

    private void ensureReceiptPage() {
        validateSessionAndWindow();
        if (!driver.getCurrentUrl().toLowerCase(Locale.ROOT).contains("/admin/job/postreceipts")) {
            throw new IllegalStateException("Post Receipts page is not open.");
        }
    }

    private void ensureReceiptFiltersVisible() {
        wait.until(d -> isDisplayed(RECEIPT_SHOW_FILTER) || isDisplayed(RECEIPT_HIDE_FILTER));
        if (!isDisplayed(RECEIPT_DATE) || !isDisplayed(LMS_POSTING_STATUS) || !isDisplayed(RECEIPT_SEARCH)) {
            WebElement show = visibleElement(RECEIPT_SHOW_FILTER);
            if (show == null) throw new IllegalStateException("Show Filter button was not found.");
            clickAndWait(show);
        }
        wait.until(d -> isDisplayed(RECEIPT_DATE) && isDisplayed(LMS_POSTING_STATUS) && isDisplayed(RECEIPT_SEARCH));
    }

    private void waitForReceiptResults() {
        WebDriverWait receiptRenderWait = new WebDriverWait(driver, Duration.ofSeconds(45));
        receiptRenderWait.until(d -> {
            if (isReceiptEmpty()) return true;

            String range = readPaginatorRange();
            if (range.isBlank()) return false;

            Matcher matcher = PAGER_PATTERN.matcher(range);
            if (!matcher.matches()) {
                return !visibleReceiptRows().isEmpty();
            }

            int start = Integer.parseInt(matcher.group(1));
            int end = matcher.group(2) == null ? start : Integer.parseInt(matcher.group(2));
            int expectedRows = Math.max(1, end - start + 1);
            int actualRows = visibleReceiptRows().size();

            return actualRows >= expectedRows;
        });
    }

    private boolean isReceiptEmpty() {
        String range = readPaginatorRange();
        if (range.isBlank()) return false;
        Matcher matcher = PAGER_PATTERN.matcher(range);
        return matcher.matches() && Integer.parseInt(matcher.group("total")) == 0;
    }

    private String readPaginatorRange() {
        for (WebElement e : driver.findElements(RECEIPT_PAGINATOR_RANGE)) {
            try {
                if (isDisplayed(e)) return clean(e.getText());
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private List<WebElement> visibleReceiptRows() {
        List<WebElement> result = new ArrayList<>();
        for (WebElement row : driver.findElements(RECEIPT_ROWS)) {
            try {
                if (!isDisplayed(row)) continue;

                String text = clean(row.getText());
                boolean hasText = !text.isBlank();
                boolean hasErrorIcon = !row.findElements(RECEIPT_ERROR_ICON).isEmpty();

                if (hasText || hasErrorIcon) {
                    result.add(row);
                }
            } catch (StaleElementReferenceException ignored) {
            }
        }
        return result;
    }

    private List<WebElement> visibleJobRows() {
        List<WebElement> result = collectDisplayedRows(JOB_ROWS);

        if (result.isEmpty()) {
            result = collectDisplayedRows(JOB_ROWS_FALLBACK);
        }
        return result;
    }

    private List<WebElement> collectDisplayedRows(By locator) {
        List<WebElement> result = new ArrayList<>();
        for (WebElement row : driver.findElements(locator)) {
            try {
                if (isDisplayed(row) && !clean(row.getText()).isBlank()) result.add(row);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private List<WebElement> waitForSettledJobRows() {
        final int[] previousCount = { -1 };
        final int[] stablePasses = { 0 };
        return wait.until(d -> {
            int count = visibleJobRows().size();

            if (count > 0 && count == previousCount[0]) {
                if (++stablePasses[0] >= 3) {
                    return visibleJobRows();
                }
            } else {
                stablePasses[0] = 0;
            }

            previousCount[0] = count;
            return null;
        });
    }

    private WebElement findUpcomingDemandRow() {
        for (int attempt = 0; attempt < 2; attempt++) {
            waitForSettledJobRows();

            scrollPageToBottom();

            WebElement row = findJobRowOptional(JOB_UPCOMING_DEMAND);
            if (row == null) {
                row = findJobRowAcrossPages(JOB_UPCOMING_DEMAND);
            }
            if (row != null) {
                return row;
            }

            if (attempt == 0) {
                System.out.println(
                        " Encore Up Coming Demands Job row not found on first scan; reloading the jobs grid.");
                try {
                    recoverToJobsPage();
                } catch (RuntimeException e) {
                    System.out.println("[WARN] Could not reload the jobs grid: " + e.getMessage());
                }
            }
        }

        System.out.println(" Jobs rendered for this client: " + visibleJobNames());
        return null;
    }

    private WebElement requireJobRow(String jobName) {
        WebElement row = waitForNavigation("the '" + jobName + "' row on the jobs list",
                d -> findJobRowOptional(jobName));
        if (row == null) {
            scrollPageToBottom();
            row = findJobRowAcrossPages(jobName);
        }
        if (row == null) {
            throw new IllegalStateException(
                "Job row not found for '" + jobName + "'. Jobs on page: " + visibleJobNames());
        }
        return row;
    }

    private WebElement findJobRowAcrossPages(String jobName) {
        WebElement row = findJobRowOptional(jobName);
        if (row != null) {
            return row;
        }

        validateSessionAndWindow();

        scrollPageToBottom();

        String startRange = readJobPaginatorRange();
        Set<String> visited = new LinkedHashSet<>();
        visited.add(startRange);

        try {
            while (visited.size() <= 50) {
                validateSessionAndWindow();

                WebElement next = visibleElement(JOB_NEXT_PAGE);
                if (next == null || !next.isEnabled()) {
                    break;
                }

                String before = readJobPaginatorRange();
                clickAndWait(next);

                try {
                    wait.until(d -> {
                        String after = readJobPaginatorRange();
                        return !after.isBlank() && !after.equals(before);
                    });
                } catch (RuntimeException e) {
                    System.out.println("[WARN] Admin Jobs paginator did not advance; stopping job scan.");
                    break;
                }

                String marker = readJobPaginatorRange();
                if (!visited.add(marker)) {
                    break;
                }

                row = findJobRowOptional(jobName);
                if (row != null) {
                    return row;
                }
            }
        } catch (RuntimeException e) {
            System.out.println("[WARN] Admin Jobs pagination scan failed: " + e.getMessage());
        } finally {
            restoreJobPage(startRange, visited.size());
        }

        return null;
    }

    private void restoreJobPage(String startRange, int visitedPages) {
        for (int guard = 0; guard <= visitedPages; guard++) {
            if (startRange.equalsIgnoreCase(readJobPaginatorRange())) {
                return;
            }
            WebElement first = visibleElement(JOB_FIRST_PAGE);
            if (first == null || !first.isEnabled()) {
                return;
            }
            try {
                clickAndWait(first);
            } catch (RuntimeException e) {
                System.out.println("[WARN] Could not restore the Admin Jobs first page: " + e.getMessage());
                return;
            }
        }
    }

    private String readJobPaginatorRange() {
        for (WebElement e : driver.findElements(JOB_PAGINATOR_RANGE)) {
            try {
                if (isDisplayed(e)) return clean(e.getText());
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private WebElement findJobRowOptional(String jobName) {
        List<String> canonicalAliases = canonicalAliasesOf(jobName);
        List<WebElement> scopedRows = displayedRows(JOB_ROWS);
        if (scopedRows.isEmpty()) {
            scopedRows = displayedRows(JOB_ROWS_FALLBACK);
        }

        for (WebElement row : scopedRows) {
            String nameCell = canonical(jobNameCellText(row));
            if (!nameCell.isEmpty() && canonicalAliases.contains(nameCell)) return row;
        }

        for (WebElement row : scopedRows) {
            if (containsAlias(canonicalAliases, canonical(jobNameCellText(row)))) return row;
            if (containsAlias(canonicalAliases, canonical(row.getText()))) return row;
        }

        return null;
    }

    private List<String> canonicalAliasesOf(String jobName) {
        List<String> canonicalAliases = new ArrayList<>();
        for (String alias : JOB_ALIASES.getOrDefault(jobName, List.of(jobName))) {
            canonicalAliases.add(canonical(alias));
        }
        return canonicalAliases;
    }

    private List<WebElement> displayedRows(By locator) {
        List<WebElement> rows = new ArrayList<>();
        for (WebElement row : driver.findElements(locator)) {
            try {
                if (isDisplayed(row)) rows.add(row);
            } catch (StaleElementReferenceException ignored) {
            }
        }
        return rows;
    }

    private boolean containsAlias(List<String> canonicalAliases, String haystack) {
        if (haystack.isEmpty()) return false;
        for (String alias : canonicalAliases) {
            if (!alias.isEmpty() && haystack.contains(alias)) return true;
        }
        return false;
    }

    private String jobNameCellText(WebElement row) {
        for (WebElement cell : row.findElements(By.xpath("./td"))) {
            try {
                if (!isDisplayed(cell)) continue;
                String text = clean(cell.getText());
                if (!text.isBlank()) return text;
            } catch (StaleElementReferenceException ignored) {
            }
        }
        return "";
    }

    private static String canonical(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String visibleJobNames() {
        List<String> names = new ArrayList<>();
        for (WebElement row : visibleJobRows()) {
            try {
                String text = clean(row.getText());
                if (!text.isBlank()) names.add(text);
            } catch (StaleElementReferenceException ignored) {
            }
        }
        return names.isEmpty() ? "(none rendered)" : String.join(" | ", names);
    }

    private WebElement visibleInside(WebElement parent, By locator) {
        if (parent == null) return null;
        for (WebElement element : parent.findElements(locator)) {
            if (isDisplayed(element)) return element;
        }
        return null;
    }

    private void safeEscape() {
        try {
            driver.switchTo().activeElement().sendKeys(Keys.ESCAPE);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected WebElement visibleElement(By locator) {
        for (WebElement element : driver.findElements(locator)) {
            if (isDisplayed(element)) return element;
        }
        return null;
    }

    private void clickAndWait(WebElement element) {
        scrollIntoView(element);
        try {
            wait.until(ExpectedConditions.elementToBeClickable(element)).click();
        } catch (Exception e) {
            jsClick(element);
        }
    }

    private String combineDateTime(String date, String time) {
        date = clean(date);
        time = clean(time);
        if (date.isBlank() && time.isBlank()) return "";
        if (!date.isBlank() && (date.matches(".*\\d{1,2}:\\d{2}.*"))) return date;
        if (!date.isBlank() && !time.isBlank()) return clean(date + " " + time);
        return date.isBlank() ? time : date;
    }

    private String findDateTime(String text) {
        if (text == null || text.isBlank()) return "";
        Matcher matcher = FULL_DATE_TIME.matcher(text);
        return matcher.find() ? clean(matcher.group()) : "";
    }

    private String xpathLiteral(String value) {
        if (!value.contains("'")) return "'" + value + "'";
        if (!value.contains("\"")) return "\"" + value + "\"";
        return "concat('" + value.replace("'", "',\"'\",'") + "')";
    }

    private String readDateFieldValue() {
        WebElement input = findDateInput();
        if (input == null) return "";
        try {
            String value = input.getAttribute("value");
            if (value != null && !value.isBlank()) return clean(value);
        } catch (StaleElementReferenceException e) {
            try {
                WebElement refreshed = findDateInput();
                if (refreshed != null) {
                    String value = refreshed.getAttribute("value");
                    return value == null ? "" : clean(value);
                }
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private WebElement findDateInput() {
        try {
            List<WebElement> elements = driver.findElements(RECEIPT_DATE);
            for (WebElement element : elements) {
                if (isDisplayed(element) && element.isEnabled()) return element;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String trimReason(String reason) {
        if (reason == null) return "";
        String clean = clean(reason);
        if (clean.length() <= 180) return clean;
        return clean.substring(0, 177) + "...";
    }

    private void awaitUiStability() {
        waitForUiStable();
    }
}

