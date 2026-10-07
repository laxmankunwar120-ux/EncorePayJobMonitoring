package com.encorepay.pages;
import org.openqa.selenium.interactions.Actions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.encorepay.models.JobStatus;
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

    /** Fallback for builds without the app-job wrapper; only used while the jobs route is open. */
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
/**
     * The Admin menu is opened by mouseover, not by clicking the trigger, so the trigger must be
     * hovered before the panel exists. This matches the layout template: the button carries
     * (mouseover)="openMenu(admin)" and the panel is rendered by *ngIf="show".
     */
    private static final By ADMIN_MENU_TRIGGER = By.xpath(
            "//button[contains(@class,'menu-btn') and normalize-space()='Admin']"
                + " | //a[contains(@class,'menu-btn') and normalize-space()='Admin']"
                + " | //*[@role='button' and normalize-space()='Admin']");

    /** Full-screen backdrop that only exists while a top-level menu is open. */
    private static final By ADMIN_MENU_OVERLAY = By.xpath(
            "//div[contains(@class,'fixed') and contains(@class,'bg-black') and contains(@style,'z-index')]");

    /** The panel itself. Both the backdrop and the panel close on click-outside and mouseleave. */
    private static final By ADMIN_MENU_PANEL = By.xpath(
            "//div[contains(@class,'bg-gray-100') and contains(@class,'overflow-auto')]");

    /** The Job entry, rendered inside the open panel. Its label is exactly "Job", not "Jobs". */
    private static final By ADMIN_MENU_JOB_ITEM = By.xpath(
            "//div[contains(@class,'mega-menu-btn')]/button[normalize-space()='Job']"
                + " | //li//button[normalize-space()='Job']");

    private static final By JOBS_PAGE_ROOT = By.cssSelector("app-job");
    private static final By VIEW_ACTION = By.xpath(".//button[normalize-space()='View'] | .//a[normalize-space()='View']");
    private static final By RECEIPT_ACTION = By.xpath(".//button[normalize-space()='Receipt'] | .//a[normalize-space()='Receipt']");

    private static final By RECEIPT_SHOW_FILTER = By.xpath("//app-receipts//button[contains(normalize-space(),'Show Filter')]");
    private static final By RECEIPT_HIDE_FILTER = By.xpath("//app-receipts//button[contains(normalize-space(),'Hide Filter')]");
    private static final By RECEIPT_DATE = By.cssSelector("app-receipts input[name='receiptDate']");
    private static final By LMS_POSTING_STATUS = By.xpath(
            "//select[@name='lmsPostingStatus']"
                + " | //select[@id='lmsPostingStatus']"
                + " | //*[contains(normalize-space(),'LMS Posting Status')]/following::select[1]"
                + " | //*[contains(normalize-space(),'LMS Posting')]/following-sibling::*//select"
                + " | //*[contains(normalize-space(),'LMS Posting')]/following-sibling::select");
    private static final By RECEIPT_SEARCH = By.xpath("//app-receipts//button[normalize-space()='Search']");
    private static final By RECEIPT_ROWS = By.cssSelector("app-receipts app-custom-table table.table-box tbody tr");
    private static final By RECEIPT_PAGINATOR_RANGE = By.cssSelector(
            "app-receipts div.paginator-container .ct-range, app-receipts .ct-range, "
                    + "app-receipts mat-paginator .mat-mdc-paginator-range-label, "
                    + "app-receipts mat-paginator .mat-paginator-range-label");
    private static final By RECEIPT_NEXT_PAGE = By.cssSelector(
            "app-receipts div.paginator-container button[aria-label='Next page'], "
                    + "app-receipts button[aria-label*='Next page']");
    private static final By RECEIPT_PAGE_SIZE_SELECT = By.cssSelector(
            "app-receipts mat-paginator .mat-mdc-paginator-page-size-select, "
                    + "app-receipts mat-paginator .mat-paginator-page-size-select");
    private static final By PAGINATOR_PAGE_SIZE_OPTIONS = By.cssSelector(
            ".cdk-overlay-pane mat-option, .cdk-overlay-pane .mat-mdc-option");
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
    private static final By FAILURE_MENU = By.cssSelector(".cdk-overlay-pane .mat-mdc-menu-panel, .cdk-overlay-pane .mat-menu-panel");
    private static final By FAILURE_REASON = By.cssSelector(
            ".cdk-overlay-pane .mat-mdc-menu-panel .text-red-500, "
                    + ".cdk-overlay-pane .mat-menu-panel .text-red-500");
    private static final By OPEN_RECEIPT_MENU_TRIGGER = By.cssSelector(
            "app-receipts [aria-haspopup='menu'][aria-expanded='true']");
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
    private static final By POSTING_LOGS_ACTION = By.xpath("//app-job-details//button[normalize-space()='PostingLogs']");
    private static final By POSTING_LOGS_MODAL = By.xpath("//div[contains(@class,'modal-wrapper')][.//span[contains(normalize-space(),'Receipt Posting Logs')] or .//h1[contains(normalize-space(),'Receipt Posting Logs')]]");
    private static final By POSTING_LOG_ROWS = By.cssSelector("app-receipt-posting-log app-custom-table table.table-box tbody tr");
    private static final By POSTING_LOG_NEXT_PAGE = By.cssSelector("app-receipt-posting-log div.paginator-container button[aria-label='Next page'], app-receipt-posting-log button[aria-label*='Next page']");
    private static final By POSTING_LOG_PAGE_SIZE_SELECT = By.cssSelector(
            "app-receipt-posting-log mat-paginator .mat-mdc-paginator-page-size-select, "
                    + "app-receipt-posting-log mat-paginator .mat-paginator-page-size-select");

    /**
 * The paginator label reads "N - M of T", but the range separator is rendered as an en dash, so a
 * hyphen and both dashes are accepted. Anchoring the pattern to the whole label keeps a page-count
 * phrase or a "(filtered from X)" suffix from being read as the record total.
 */
private static final Pattern PAGER_PATTERN = Pattern.compile(
            "(\\d+)\\s*(?:[-\\u2013\\u2014]\\s*(\\d+)\\s*)?of\\s*(?<total>\\d+)");
    private static final Pattern FULL_DATE_TIME = Pattern.compile("(?:\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{4}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})(?:\\s+|T)+\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*[APMapm]{2})?");
    private static final Pattern RECEIPT_POSTING_FAILURE = Pattern.compile(
            "(?i)Receipt\\s+Posting\\s+Failure:\\s*Total:\\s*(\\d+)\\s*,\\s*Success:\\s*(\\d+)\\s*,"
                    + "\\s*Partially\\s+Success:\\s*(\\d+)\\s*,\\s*Failed:\\s*(\\d+)");

    private final WebDriverWait jobsPageWait;

    public AdminJobsPage(WebDriver driver) {
        super(driver);
        this.jobsPageWait = wait;
    }

    public AdminJobsPage(WebDriver driver, ConfigReader config) {
        super(driver, config);
        this.jobsPageWait = wait;
    }

/**
     * Follows the flow a user performs: hover Admin, wait for the submenu, click Job inside it,
     * then confirm the Admin Jobs page actually rendered. Clicking the Admin trigger directly
     * cannot work, because the trigger only opens the menu on mouseover.
     */
    public void navigateToAdminJobs() {
        if (isJobsPageLoaded()) return;
        awaitAppBootstrap();
        hoverAdminMenu();
        clickJobInAdminMenu();
        verifyAdminJobsPageRendered();
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


        try {
            action.markStep("navigate to Admin Jobs");
            ensureJobsPage();
            action.markStep("Post Receipts Job");
            results.add(monitorPostReceiptJob(clientName));
        } catch (Exception e) {
            System.out.println("[WARN] Post Receipts Job capture error for " + clientName + ": " + e.getMessage());
            results.add(failedJobPlaceholder(clientName, JOB_POST_RECEIPTS, e));
        }


        try {
            action.markStep("Encore Download Collection Items Job");
            ensureJobsPage();
            results.add(monitorExecutionJob(JOB_COLLECTION_ITEMS, clientName));
        } catch (Exception e) {
            System.out.println("[WARN] Download Collection Items Job capture error for " + clientName + ": " + e.getMessage());
            results.add(failedJobPlaceholder(clientName, JOB_COLLECTION_ITEMS, e));
        }


        try {
            action.markStep("Encore Up Coming Demands Job");
            ensureJobsPage();
            if (findUpcomingDemandRow() != null) {
                results.add(monitorExecutionJob(JOB_UPCOMING_DEMAND, clientName));
            } else {
                System.out.println(" Skip Encore Up Coming Demands Job is not configured for client " + clientName + ".");
            }
        } catch (Exception e) {
            System.out.println("[WARN] Upcoming Demands Job capture error for " + clientName + ": " + e.getMessage());
            results.add(failedJobPlaceholder(clientName, JOB_UPCOMING_DEMAND, e));
        }

        action.markStep("client complete");
        return results;
    }

    /**
     * A job that could not be captured is reported as FAILED with no date, because there is no
     * execution to read a date from. Stamping the current time here would present a locally
     * generated timestamp as the job's End Date/Time.
     */
    private JobStatus failedJobPlaceholder(String clientName, String jobName, Exception cause) {
        JobStatus failed = new JobStatus();
        failed.setClientName(clientName);
        failed.setJobName(jobName);
        failed.setStatus("FAILED");
        failed.setDateTime("NOT CAPTURED");
        failed.setJobFailureReason("Capture failed at step '" + action.currentStep() + "': "
                + cause.getClass().getSimpleName() + ": " + safeText(cause));
        return failed;
    }

    private String safeText(Throwable cause) {
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) return "no further detail";
        String cleaned = message.replaceAll("\\s+", " ").trim();
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

        WebElement jobRow = requireJobRow(JOB_POST_RECEIPTS);
        WebElement receiptButton = visibleInside(jobRow, RECEIPT_ACTION);
        if (receiptButton == null) {
            throw new IllegalStateException("Receipt action was not found for Post Receipts Job.");
        }

        clickAndWait(receiptButton);
        waitForReceiptPage();
        ensureReceiptFiltersVisible();

        ReceiptCapture failed = captureReceiptStatus("FAILED", false);
        ReceiptCapture pending = captureReceiptStatus("PENDING", false);

        status.setFailedCount(failed.totalCount);
        status.setPendingCount(pending.totalCount);
        List<String> notes = new ArrayList<>(failed.problems);
        notes.addAll(pending.problems);
        if (failed.totalCount > 0 && failed.reasons.isEmpty() && notes.isEmpty()) {
            notes.add("Failed receipts were found but no failure reason could be read");
        }
        if (!notes.isEmpty()) {
            status.setJobFailureReason("Receipt capture incomplete: " + String.join("; ", notes));
        }

        closeReceiptPageUsingUi();
        ensureJobsPage();
        captureLatestExecutionFromJobsList(JOB_POST_RECEIPTS, status);

        if (status.getFailedCount() > 0 && status.getFailureReasons().isEmpty()) {
            captureReceiptFailureReasonsFallback(status);
        }

        return status;
    }

    private void captureReceiptFailureReasonsFallback(JobStatus status) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                ensureJobsPage();
                WebElement jobRow = requireJobRow(JOB_POST_RECEIPTS);
            WebElement receiptButton = visibleInside(jobRow, RECEIPT_ACTION);
            if (receiptButton == null) {
                status.setJobFailureReason("Failed receipts were found, but the Receipt action was unavailable for fallback reason capture.");
                return;
            }

            clickAndWait(receiptButton);
            waitForReceiptPage();
            ensureReceiptFiltersVisible();

            ReceiptCapture failed = captureReceiptStatus("FAILED", true);
            failed.reasons.forEach(status::addFailureReason);

            if (!failed.problems.isEmpty()) {
                String existing = status.getJobFailureReason();
                String note = String.join("; ", failed.problems);
                status.setJobFailureReason(existing == null || existing.isBlank()
                        ? "Receipt failure reason fallback incomplete: " + note
                        : existing + "; Receipt failure reason fallback incomplete: " + note);
            }

            if (status.getFailureReasons().isEmpty() && status.getFailedCount() > 0) {
                status.setJobFailureReason("Failed receipts were found, but no receipt-level failure reason could be captured.");
            }
            } catch (Exception e) {
                String note = "Fallback reason capture attempt " + attempt + " failed: "
                        + e.getClass().getSimpleName() + ": " + safeText(e);
                System.out.println("[WARN] " + status.getClientName() + " :: " + note);

                if (attempt == 3) {
                    status.setJobFailureReason("Failed receipts were found, but fallback reason capture failed after 3 attempts: "
                            + e.getClass().getSimpleName() + ": " + safeText(e));
                } else {
                    try {
                        closeReceiptPageUsingUi();
                    } catch (Exception ignored) {
                    }
                    try {
                        ensureJobsPage();
                    } catch (Exception ignored) {
                    }
                    pause(1000L * attempt);
                }
            } finally {
                try {
                    closeReceiptPageUsingUi();
                } catch (Exception ignored) {
                    try {
                        ensureJobsPage();
                    } catch (Exception ignoredAgain) {
                    }
                }
            }

            if (!status.getFailureReasons().isEmpty()) {
                return;
            }
        }
    }

    private JobStatus monitorExecutionJob(String jobName, String clientName) {
        requireActiveSession(jobName);

        JobStatus status = new JobStatus();
        status.setJobName(jobName);
        status.setClientName(clientName);
        captureLatestExecutionFromJobsList(jobName, status);
        return status;
    }

    private ReceiptCapture captureReceiptStatus(String postingStatus, boolean inspectReasons) {
        ensureReceiptPage();
        ensureReceiptFiltersVisible();
        selectReceiptDateToday();

        ReceiptCapture capture = new ReceiptCapture();

        if (!selectPostingStatus(postingStatus)) {
            capture.problems.add("LMS Posting Status '" + postingStatus
                    + "' is not offered for this client, so its count could not be captured");
            return capture;
        }

        configureReceiptPageSizeForScan(capture);
        searchReceipts(postingStatus, capture);
        capture.totalCount = readReceiptTotalCount(capture);

        if (inspectReasons && capture.totalCount > 0) {
            ReasonScan scan = readUniqueFailureReasons();
            capture.reasons.addAll(scan.reasons);
            capture.problems.addAll(scan.problems);
        }

        return capture;
    }

    /**
     * Sets the receipt date with a scripted value write. The field is a native
     * <input type="date"> bound with ngModel, whose value cannot be typed reliably because
     * keystrokes must match the browser's locale format. The events dispatched here are what
     * update the Angular model, and the applied value is then verified against the query
     * parameter the app itself writes when it searches.
     */
    private String selectReceiptDateToday() {
        WebElement date = wait.until(ExpectedConditions.visibilityOfElementLocated(RECEIPT_DATE));
        // "Today" is a business-day question, so it is asked in the business zone rather than
        // the machine's zone; a runner in UTC would otherwise pick yesterday late in the evening.
        String today = LocalDate.now(config.getBusinessZone()).toString();
        ((JavascriptExecutor) driver).executeScript(
            "arguments[0].value=arguments[1];" +
            "arguments[0].dispatchEvent(new Event('input',{bubbles:true}));" +
            "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));",
            date,
            today
        );
        wait.until(d -> today.equals(date.getAttribute("value")));
        return today;
    }

    private boolean selectPostingStatus(String status) {
        final int maxAttempts = 4;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                WebElement selectElement = findVisibleLmsPostingStatusSelect();
                if (selectElement == null) {
                    if (attempt == maxAttempts) {
                        return false;
                    }
                    pause(250);
                    continue;
                }

                Select select = new Select(selectElement);
                String targetValue = null;
                String targetText = null;

                for (WebElement option : select.getOptions()) {
                    String optionText = option.getText().trim();
                    String optionValue = option.getAttribute("value");
                    if (status.equalsIgnoreCase(optionText)
                            || (optionValue != null && status.equalsIgnoreCase(optionValue))) {
                        targetValue = optionValue;
                        targetText = optionText;
                        break;
                    }
                }

                if (targetText == null) {
                    return false;
                }

                try {
                    selectElement.click();
                    select.selectByVisibleText(targetText);
                } catch (Exception ignored) {
                }

                try {
                    ((JavascriptExecutor) driver).executeScript(
                        "var sel=arguments[0], val=arguments[1];" +
                        "if(val!==null){sel.value=val;}" +
                        "sel.dispatchEvent(new Event('input',{bubbles:true}));" +
                        "sel.dispatchEvent(new Event('change',{bubbles:true}));" +
                        "sel.dispatchEvent(new Event('blur',{bubbles:true}));",
                        selectElement,
                        targetValue
                    );
                } catch (Exception ignored) {
                }

                final String expectedText = targetText;
                final String expectedValue = targetValue;

                boolean selected = wait.until(d -> {
                    try {
                        WebElement current = findVisibleLmsPostingStatusSelect();
                        if (current == null) {
                            return false;
                        }
                        WebElement selectedOption = new Select(current).getFirstSelectedOption();
                        String currentText = selectedOption.getText().trim();
                        String currentValue = selectedOption.getAttribute("value");
                        return expectedText.equalsIgnoreCase(currentText)
                                || (expectedValue != null && expectedValue.equals(currentValue));
                    } catch (StaleElementReferenceException | org.openqa.selenium.NoSuchElementException e) {
                        return false;
                    }
                });

                if (selected) {
                    return true;
                }
            } catch (StaleElementReferenceException | org.openqa.selenium.NoSuchElementException ignored) {
            } catch (Exception e) {
                if (attempt == maxAttempts) {
                    throw new IllegalStateException(
                        "Unable to select LMS Posting Status '" + status + "': " + safeText(e), e);
                }
            }

            if (attempt < maxAttempts) {
                pause(300);
            }
        }

        return false;
    }

    private void pause(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private WebElement findVisibleLmsPostingStatusSelect() {
        List<WebElement> candidates = driver.findElements(LMS_POSTING_STATUS);
        for (WebElement candidate : candidates) {
            try {
                if (candidate.isDisplayed() && candidate.isEnabled()) {
                    return candidate;
                }
            } catch (StaleElementReferenceException ignored) {
            }
        }
        return null;
    }

    private void searchReceipts(String expectedStatus, ReceiptCapture capture) {
        WebElement search = wait.until(
            ExpectedConditions.elementToBeClickable(RECEIPT_SEARCH)
        );

        String rangeBefore = readPaginatorRange();
        String signatureBefore = receiptResultsSignature();

        clickAndWait(search);

        waitForState("the '" + expectedStatus + "' receipt search to be applied", d -> {
            WebElement selectElement = findVisibleLmsPostingStatusSelect();
            if (selectElement == null) {
                return false;
            }

            try {
                String selectedStatus = new Select(selectElement)
                    .getFirstSelectedOption()
                    .getText()
                    .trim();

                if (!expectedStatus.equalsIgnoreCase(selectedStatus)) {
                    return false;
                }

                String appliedStatus = queryParam("lmspostingstatus");
                if (!appliedStatus.isBlank()) {
                    return expectedStatus.equalsIgnoreCase(appliedStatus);
                }

                String rangeAfter = readPaginatorRange();
                String signatureAfter = receiptResultsSignature();
                return isReceiptEmpty()
                        || (!rangeAfter.isBlank() && !rangeAfter.equals(rangeBefore))
                        || (!signatureAfter.isBlank() && !signatureAfter.equals(signatureBefore));
            } catch (Exception e) {
                return false;
            }
        });

        waitForReceiptResults();
        waitForStableReceiptPaginator();

        String expectedDate = LocalDate.now(config.getBusinessZone()).toString();
        WebElement dateElement = visibleElement(RECEIPT_DATE);
        if (dateElement == null || !expectedDate.equals(dateElement.getAttribute("value"))) {
            capture.problems.add("Receipt date field is not set to " + expectedDate);
        }

        String appliedDate = queryParam("receiptdate");
        if (!appliedDate.isBlank() && !appliedDate.equalsIgnoreCase(expectedDate)) {
            capture.problems.add("Receipt date filter was applied as '" + appliedDate
                    + "' instead of " + expectedDate);
        }

        String appliedStatus = queryParam("lmspostingstatus");
        if (!appliedStatus.isBlank() && !appliedStatus.equalsIgnoreCase(expectedStatus)) {
            capture.problems.add("Posting status filter was applied as '" + appliedStatus
                    + "' instead of " + expectedStatus);
        }
    }

    /** Reads a query parameter from the current URL without executing script. */
    private String queryParam(String name) {
        String url = driver.getCurrentUrl();
        int start = url.indexOf('?');
        if (start < 0) return "";

        for (String pair : url.substring(start + 1).split("[&;]")) {
            int equals = pair.indexOf('=');
            if (equals <= 0) continue;
            if (pair.substring(0, equals).equalsIgnoreCase(name)) {
                try {
                    return java.net.URLDecoder.decode(pair.substring(equals + 1),
                        StandardCharsets.UTF_8).trim();
                } catch (Exception e) {
                    return pair.substring(equals + 1).trim();
                }
            }
        }
        return "";
    }

    /**
     * The total is the last number of the paginator label, which the app renders as
     * "N - M of T" or "0 of 0". Anchoring the pattern keeps a page-count phrase or a
     * "(filtered from X)" suffix from being read as the record total.
     */
    private int readReceiptTotalCount(ReceiptCapture capture) {
        waitForStableReceiptPaginator();

        String range = readPaginatorRange();
        Matcher matcher = PAGER_PATTERN.matcher(range);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group("total"));
        }

        capture.problems.add("Receipt paginator label '" + clean(range)
                + "' could not be read, so the filtered count could not be verified");
        return visibleReceiptRows().size();
    }

    private void waitForStableReceiptPaginator() {
        final String[] last = {""};
        final int[] stableReads = {0};

        waitForState("the receipt paginator total to stabilize", d -> {
            String current = readPaginatorRange();
            if (current.isBlank()) {
                stableReads[0] = 0;
                last[0] = "";
                return false;
            }

            if (current.equals(last[0])) {
                stableReads[0]++;
            } else {
                last[0] = current;
                stableReads[0] = 1;
            }

            return stableReads[0] >= 3;
        });
    }
    private ReasonScan readUniqueFailureReasons() {
        List<String> reasons = new ArrayList<>();
        Set<String> problems = new LinkedHashSet<>();
        Set<String> processedReceiptKeys = new LinkedHashSet<>();

        configureReceiptPageSizeForScan(null);
        waitForStableReceiptPaginator();

        ReceiptPageState firstState = readReceiptPageState();
        if (firstState == null) {
            problems.add("FAILED receipt paginator range could not be interpreted");
            return new ReasonScan(reasons, problems);
        }

        if (firstState.total == 0) {
            return new ReasonScan(reasons, problems);
        }

        int pageSize = firstState.pageSize();
        int maxPages = pageSize > 0
                ? Math.max(3, Math.min(10000, (int) Math.ceil(firstState.total / (double) pageSize) + 3))
                : 10000;

        int pageGuard = 0;
        int capturedReasons = 0;
        int expectedTotal = firstState.total;

        while (pageGuard++ < maxPages) {
            ReceiptPageState page = readReceiptPageState();
            if (page == null) {
                problems.add("FAILED receipt paginator state could not be read on page " + pageGuard);
                break;
            }

            if (page.total != expectedTotal) {
                expectedTotal = page.total;
            }

            if (page.total == 0) {
                break;
            }

            waitForReceiptResults();
            List<WebElement> rows = visibleReceiptRows();

            if (rows.isEmpty()) {
                problems.add("FAILED receipts were reported by the paginator but no receipt rows rendered "
                        + "(range: '" + page.range + "')");
                break;
            }

            for (int index = 0; index < rows.size(); index++) {
                List<WebElement> currentRows = visibleReceiptRows();
                if (index >= currentRows.size()) {
                    problems.add("FAILED receipt row " + (page.start + index)
                            + " disappeared before its failure reason could be read");
                    continue;
                }

                WebElement currentRow = currentRows.get(index);
                String receiptKey = receiptRowKey(currentRow, index, page);

                if (!processedReceiptKeys.add(receiptKey)) {
                    continue;
                }

                String reason = readFailureReasonForReceipt(receiptKey, index, problems);
                if (!reason.isBlank()) {
                    reasons.add(reason);
                    capturedReasons++;
                }
            }

            ReceiptPageState afterPage = readReceiptPageState();
            if (afterPage != null && afterPage.end >= afterPage.total && afterPage.total > 0) {
                break;
            }

            WebElement next = visibleElement(RECEIPT_NEXT_PAGE);
            if (next == null || !next.isEnabled()) {
                if (afterPage != null && afterPage.end < afterPage.total) {
                    problems.add("FAILED receipt pagination ended early at range '" + afterPage.range + "'");
                }
                break;
            }

            int beforeStart = afterPage == null ? page.start : afterPage.start;
            String beforeSignature = receiptResultsSignature();

            try {
                clickAndWait(next);
                waitForState("the FAILED receipt page to advance", d -> {
                    ReceiptPageState current = readReceiptPageState();
                    if (current == null) return false;
                    if (current.start > beforeStart) {
                        return !visibleReceiptRows().isEmpty() || current.total == 0;
                    }
                    return !receiptResultsSignature().isBlank()
                            && !receiptResultsSignature().equals(beforeSignature)
                            && (current.start != beforeStart || current.end >= current.total);
                });
                waitForReceiptResults();
            } catch (RuntimeException e) {
                problems.add("Receipt paginator stopped advancing after range '" + page.range + "': "
                        + e.getClass().getSimpleName());
                break;
            }
        }

        if (pageGuard >= maxPages) {
            ReceiptPageState finalState = readReceiptPageState();
            if (finalState != null && finalState.end < finalState.total) {
                problems.add("FAILED receipt pagination guard reached before all records were scanned: "
                        + finalState.range);
            }
        }

        if (expectedTotal > 0 && capturedReasons < expectedTotal) {
            problems.add("FAILED receipt reason capture incomplete: filtered failed receipts = "
                    + expectedTotal + ", failure reasons captured = " + capturedReasons);
        }

        return new ReasonScan(reasons, problems);
    }

    private String readFailureReasonForReceipt(
            String receiptKey,
            int rowIndex,
            Set<String> problems) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                if (isFailureReasonMenuOpen()) {
                    closeFailureReasonMenu(rowIndex);
                }

                List<WebElement> rows = visibleReceiptRows();
                WebElement row = findReceiptRowByKey(rows, receiptKey, rowIndex);
                if (row == null) {
                    if (attempt == 3) {
                        problems.add("FAILED receipt '" + receiptKey
                                + "' was not present when its failure reason was read");
                    }
                    continue;
                }

                WebElement icon = visibleInside(row, RECEIPT_ERROR_ICON);
                if (icon == null) {
                    problems.add("FAILED receipt '" + receiptKey + "' has no error icon to open");
                    return "";
                }

                scrollIntoView(icon);

                try {
                    jsClick(icon);
                } catch (Exception e) {
                    new Actions(driver).moveToElement(icon).click().perform();
                }

                String reason = readFailureReason();

                if (!reason.isBlank()) {
                    if (closeFailureReasonMenu(rowIndex)) {
                        return reason;
                    }

                    if (attempt == 3) {
                        problems.add("FAILED receipt '" + receiptKey
                                + "' reason was read, but its error menu could not be closed");
                        return "";
                    }
                } else if (attempt == 3) {
                    problems.add("FAILED receipt '" + receiptKey
                            + "' opened no failure reason after 3 attempts");
                }
            } catch (StaleElementReferenceException e) {
                if (attempt == 3) {
                    problems.add("FAILED receipt '" + receiptKey
                            + "' became stale during failure reason capture");
                }
            } catch (Exception e) {
                if (attempt == 3) {
                    problems.add("FAILED receipt '" + receiptKey
                            + "' failure reason capture failed: "
                            + e.getClass().getSimpleName());
                }
            } finally {
                if (isFailureReasonMenuOpen()) {
                    closeFailureReasonMenu(rowIndex);
                }
            }

            if (attempt < 3) {
                pause(100L * attempt);
            }
        }

        return "";
    }

    private WebElement findReceiptRowByKey(
            List<WebElement> rows,
            String receiptKey,
            int fallbackIndex) {
        for (WebElement row : rows) {
            try {
                if (receiptKey.equals(receiptRowKey(row, -1, readReceiptPageState()))) {
                    return row;
                }
            } catch (Exception ignored) {
            }
        }
        return fallbackIndex >= 0 && fallbackIndex < rows.size() ? rows.get(fallbackIndex) : null;
    }

    private String receiptRowKey(WebElement row, int index, ReceiptPageState page) {
        List<WebElement> cells = row.findElements(By.xpath("./td"));
        Map<String, Integer> headers = headerIndexesFromReceiptTable(row);
        String receiptNumber = cell(cells, headers, "receipt no", "receipt number", "receipt");
        String accountId = cell(cells, headers, "account no", "account id", "account");
        if (!receiptNumber.isBlank()) {
            return "RECEIPT:" + receiptNumber + "|ACCOUNT:" + accountId;
        }

        String rowText = clean(row.getText());
        if (!rowText.isBlank()) {
            return "ROW:" + rowText;
        }

        String range = page == null ? "" : page.range;
        return "ROW:" + range + "#" + Math.max(index, 0);
    }

    private Map<String, Integer> headerIndexesFromReceiptTable(WebElement row) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        try {
            WebElement table = row.findElement(By.xpath("./ancestor::table[1]"));
            List<WebElement> headers = table.findElements(By.xpath(".//thead//th"));
            for (int i = 0; i < headers.size(); i++) {
                String header = canonical(headers.get(i).getText());
                if (!header.isBlank()) {
                    indexes.putIfAbsent(header, i);
                }
            }
        } catch (Exception ignored) {
        }
        return indexes;
    }

    private ReceiptPageState readReceiptPageState() {
        String range = readPaginatorRange();
        Matcher matcher = PAGER_PATTERN.matcher(range);
        if (!matcher.matches()) {
            return null;
        }

        int start = Integer.parseInt(matcher.group(1));
        int end = matcher.group(2) == null
                ? start
                : Integer.parseInt(matcher.group(2));
        int total = Integer.parseInt(matcher.group("total"));
        return new ReceiptPageState(start, end, total, range);
    }

    private void configureReceiptPageSizeForScan(ReceiptCapture capture) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                WebElement selector = visibleElement(RECEIPT_PAGE_SIZE_SELECT);
                if (selector == null) {
                    return;
                }

                String current = clean(selector.getText());
                if ("100".equals(current)) {
                    return;
                }

                clickAndWait(selector);

                WebElement option = shortWait.until(d -> {
                    for (WebElement candidate : d.findElements(PAGINATOR_PAGE_SIZE_OPTIONS)) {
                        try {
                            if (isDisplayed(candidate) && "100".equals(clean(candidate.getText()))) {
                                return candidate;
                            }
                        } catch (StaleElementReferenceException ignored) {
                        }
                    }
                    return null;
                });

                if (option == null) {
                    throw new IllegalStateException("100 page-size option was not rendered");
                }

                try {
                    option.click();
                } catch (Exception e) {
                    jsClick(option);
                }

                waitForState("the receipt paginator page size to become 100",
                        d -> {
                            WebElement currentSelector = visibleElement(RECEIPT_PAGE_SIZE_SELECT);
                            return currentSelector != null && "100".equals(clean(currentSelector.getText()));
                        });
                return;
            } catch (StaleElementReferenceException | org.openqa.selenium.NoSuchElementException e) {
            } catch (Exception e) {
                if (attempt == 3 && capture != null) {
                    capture.problems.add("Receipt paginator page size could not be set to 100; continuing with paginated capture");
                }
            }

            if (attempt < 3) {
                pause(300L * attempt);
            }
        }
    }

    /** Reasons collected from the error menus, plus non-fatal capture problems. */
    private static final class ReasonScan {
        final List<String> reasons;
        final Set<String> problems;

        ReasonScan(List<String> reasons, Set<String> problems) {
            this.reasons = reasons;
            this.problems = problems;
        }
    }

    private String readFailureReason() {
        try {
            return clean(new WebDriverWait(driver, java.time.Duration.ofSeconds(2))
                    .until(d -> {
                        for (WebElement reason : d.findElements(FAILURE_REASON)) {
                            try {
                                if (!isDisplayed(reason)) continue;
                                String text = clean(reason.getText());
                                if (!text.isBlank()) return text;
                            } catch (StaleElementReferenceException ignored) {
                            }
                        }
                        return null;
                    }));
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * Closes the Material menu and confirms it is gone. Returns false when the menu survives
     * every safe dismissal, which the caller must treat as a data-integrity problem rather than
     * a warning, because a still-open menu makes the next row read return the previous reason.
     */
    private boolean closeFailureReasonMenu(int rowIndex) {
        if (!isFailureReasonMenuOpen()) {
            return true;
        }

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                ((JavascriptExecutor) driver).executeScript(
                        "var b=document.body;"
                                + "if(b){"
                                + " ['mousedown','mouseup','click'].forEach(function(t){"
                                + " b.dispatchEvent(new MouseEvent(t,{view:window,bubbles:true,cancelable:true,button:0}));"
                                + " });"
                                + "}"
                                + "var e=document.documentElement;"
                                + "if(e){e.dispatchEvent(new MouseEvent('click',{view:window,bubbles:true,cancelable:true,button:0}));}"
                );
            } catch (Exception ignored) {
            }

            if (waitForMenuClosedFast()) {
                return true;
            }

            try {
                driver.switchTo().activeElement().sendKeys(Keys.ESCAPE);
            } catch (Exception ignored) {
            }

            if (waitForMenuClosedFast()) {
                return true;
            }

            try {
                WebElement openTrigger = visibleElement(OPEN_RECEIPT_MENU_TRIGGER);
                if (openTrigger != null) {
                    jsClick(openTrigger);
                }
            } catch (Exception ignored) {
            }

            if (waitForMenuClosedFast()) {
                return true;
            }

            try {
                List<WebElement> rows = visibleReceiptRows();
                if (rowIndex >= 0 && rowIndex < rows.size()) {
                    WebElement icon = visibleInside(rows.get(rowIndex), RECEIPT_ERROR_ICON);
                    if (icon != null) {
                        jsClick(icon);
                    }
                }
            } catch (Exception ignored) {
            }

            if (waitForMenuClosedFast()) {
                return true;
            }
        }

        return !isFailureReasonMenuOpen();
    }

    private boolean waitForMenuClosedFast() {
        try {
            new WebDriverWait(driver, java.time.Duration.ofMillis(900))
                    .until(d -> d.findElements(FAILURE_MENU).stream().noneMatch(this::isDisplayed));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean isFailureReasonMenuOpen() {
        try {
            return driver.findElements(FAILURE_MENU).stream().anyMatch(this::isDisplayed);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean waitForMenuClosed() {
        try {
            shortWait.until(d -> d.findElements(FAILURE_MENU).stream().noneMatch(this::isDisplayed));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }


    private void captureLatestExecutionFromJobsList(String jobName, JobStatus status) {
        ensureJobsPage();
        WebElement jobRow = requireJobRow(jobName);
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
        scrollExecutionModal(modal);

        String executionStatus = waitForModalField(modal, "Status");
        String endDate = waitForModalField(modal, "End Date");
        String endTime = waitForModalField(modal, "End Time");
        String dateTime = combineDateTime(endDate, endTime);

        if (dateTime.isBlank()) {
            String startDate = waitForModalField(modal, "Start Date");
            String startTime = waitForModalField(modal, "Start Time");
            dateTime = combineDateTime(startDate, startTime);
        }
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
            executionStatus = "No Status";
        }
        if (dateTime.isBlank()) {
            dateTime = "NOT CAPTURED";
        }

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
            status.setJobFailureReason(jobFailureReason);
        } else if (isFailedStatus(executionStatus) && jobFailureReason.isBlank()) {
            System.out.println("[WARN] Job status is FAILED but Reason could not be captured for " + jobName + ".");
        }

        closeExecutionModalUsingUi();

        boolean postingLogsRequired = JOB_POST_RECEIPTS.equalsIgnoreCase(jobName)
                && (receiptPostingFailure || partialReceiptOutcome);

        if (postingLogsRequired) {
            status.clearFailureReasons();
            capturePostingLogFailureReasons(jobName, status);
        }

        closeJobDetailsUsingUi();
        waitForJobsPage();
    }

    private boolean isFailedStatus(String status) {
        return status != null && status.trim().toUpperCase(Locale.ROOT).contains("FAIL");
    }

    private boolean isPartialSuccessStatus(String status) {
        return status != null && status.trim().equalsIgnoreCase("PARTIALLY_SUCCESSFUL");
    }

    private boolean normalizeReceiptPostingSummary(String reason, JobStatus status) {
        String text = clean(reason);
        String normalized = text.toUpperCase(Locale.ROOT);

        boolean summaryDetected = normalized.contains("RECEIPT POSTING FAILURE")
                || (normalized.contains("TOTAL") && normalized.contains("SUCCESS")
                    && normalized.contains("FAILED") && normalized.contains("PARTIAL"));

        if (!summaryDetected) {
            return false;
        }

        Matcher matcher = RECEIPT_POSTING_FAILURE.matcher(text);
        if (matcher.find()) {
            int total = Integer.parseInt(matcher.group(1));
            int success = Integer.parseInt(matcher.group(2));
            int partial = Integer.parseInt(matcher.group(3));
            int failed = Integer.parseInt(matcher.group(4));

            if (failed > status.getFailedCount()) {
                status.setFailedCount(failed);
            }

            System.out.println("[RECEIPT OUTCOME] Receipt Posting Failure summary detected: "
                    + "Total=" + total + ", Success=" + success
                    + ", Partially Success=" + partial + ", Failed=" + failed
                    + ". Checking PostingLogs for receipt-level failure reasons.");
            return failed > 0;
        }

        boolean failedReceiptHint = normalized.contains("FAILED")
                || normalized.contains("PARTIAL")
                || status.getFailedCount() > 0;

        if (failedReceiptHint) {
            System.out.println("[RECEIPT OUTCOME] Receipt posting failure summary detected in variant format. Checking PostingLogs.");
        }

        return failedReceiptHint; 
    }

    private void capturePostingLogFailureReasons(String jobName, JobStatus status) {
        if (!JOB_POST_RECEIPTS.equalsIgnoreCase(jobName)) {
            return;
        }

        PostingLogScan bestScan = null;
        String lastFailure = "";

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                PostingLogScan scan = capturePostingLogFailureReasonsOnce(status);

                if (bestScan == null || scan.reasons.size() > bestScan.reasons.size()) {
                    bestScan = scan;
                }

                if (scan.reasons.size() >= status.getFailedCount()
                        || scan.problems.isEmpty()) {
                    break;
                }

                lastFailure = String.join("; ", scan.problems);
            } catch (Exception e) {
                lastFailure = e.getClass().getSimpleName() + ": " + safeText(e);
            } finally {
                closePostingLogsModal();
            }

            if (attempt < 3) {
                try {
                    ensureJobDetailsPageAfterPostingLogRetry();
                } catch (Exception ignored) {
                }
                pause(500L * attempt);
            }
        }

        if (bestScan == null) {
            status.setJobFailureReason(
                    "Receipt Posting Failure detected, but PostingLogs could not be captured after 3 attempts"
                            + (lastFailure.isBlank() ? "." : ": " + lastFailure));
            return;
        }

        for (String reason : bestScan.reasons) {
            status.addFailureReason(reason);
        }

        int expected = status.getFailedCount();
        int captured = bestScan.reasons.size();

        if (captured == 0) {
            status.setJobFailureReason(
                    "Receipt Posting Failure detected, but no receipt-level failure reason was available in PostingLogs."
                            + (bestScan.problems.isEmpty() ? "" : " " + String.join(" ", bestScan.problems)));
            return;
        }

        if (expected > 0 && captured < expected) {
            status.setJobFailureReason(
                    "Receipt Posting Failure detected; PostingLogs captured " + captured
                            + " of " + expected + " failure reasons."
                            + (bestScan.problems.isEmpty() ? "" : " " + String.join(" ", bestScan.problems)));
            return;
        }

        status.setJobFailureReason(
                "Receipt Posting Failure detected; PostingLogs captured "
                        + captured + " receipt failure reasons.");
    }

    private void ensureJobDetailsPageAfterPostingLogRetry() {
        waitForJobDetailsPage(JOB_POST_RECEIPTS);
        waitForState("the PostingLogs action after retry recovery",
                d -> visibleElement(POSTING_LOGS_ACTION) != null);
    }

    private PostingLogScan capturePostingLogFailureReasonsOnce(JobStatus status) {
        WebElement postingLogs = visibleElement(POSTING_LOGS_ACTION);
        if (postingLogs == null) {
            throw new IllegalStateException("PostingLogs action was not available.");
        }

        scrollIntoView(postingLogs);
        clickAndWait(postingLogs);
        waitForPostingLogsModal();
        configurePostingLogPageSizeForScan();

        ReceiptPageState page = readPostingLogPageState();
        if (page == null) {
            throw new IllegalStateException("PostingLogs paginator range could not be interpreted.");
        }

        PostingLogScan scan = new PostingLogScan();
        if (page.total == 0) {
            return scan;
        }

        int pageSize = page.pageSize();
        int maxPages = pageSize > 0
                ? Math.max(3, Math.min(10000, (int) Math.ceil(page.total / (double) pageSize) + 3))
                : 10000;
        int expectedTotal = page.total;
        Set<String> processedKeys = new LinkedHashSet<>();

        for (int pageGuard = 1; pageGuard <= maxPages; pageGuard++) {
            if (!isPostingLogsModalOpen()) {
                throw new IllegalStateException("PostingLogs modal closed before all receipt records were captured.");
            }

            page = readPostingLogPageState();
            if (page == null) {
                scan.problems.add("PostingLogs paginator state could not be read on page " + pageGuard);
                break;
            }

            expectedTotal = page.total;

            List<WebElement> rows = visiblePostingLogRows();
            if (rows.isEmpty()) {
                if (page.total == 0) break;
                scan.problems.add("PostingLogs reported " + page.total
                        + " records but no rows rendered at range '" + page.range + "'");
                break;
            }

            for (int index = 0; index < rows.size(); index++) {
                for (int rowAttempt = 1; rowAttempt <= 3; rowAttempt++) {
                    try {
                        List<WebElement> currentRows = visiblePostingLogRows();
                        if (index >= currentRows.size()) {
                            if (rowAttempt == 3) {
                                scan.problems.add("PostingLogs row " + (page.start + index)
                                        + " disappeared before it could be read");
                            }
                            continue;
                        }

                        WebElement row = currentRows.get(index);
                        String rowKey = postingLogRowKey(row, index, page);

                        List<WebElement> cells = row.findElements(By.xpath("./td"));
                        if (cells.isEmpty()) break;

                        Map<String, Integer> headers = headerIndexesFromPostingLogTable(row);
                        String receiptStatus = cell(cells, headers,
                                "status", "receipt status", "posting status");
                        String reason = cell(cells, headers,
                                "lms posting failure reason", "failure reason", "reason",
                                "error message", "error");
                        String failureCode = cell(cells, headers,
                                "failure code", "error code", "code");

                        if (receiptStatus.isBlank() && cells.size() > 5) {
                            receiptStatus = clean(cells.get(5).getText());
                        }
                        if (reason.isBlank() && cells.size() > 6) {
                            reason = clean(cells.get(6).getText());
                        }
                        if (failureCode.isBlank() && cells.size() > 7) {
                            failureCode = clean(cells.get(7).getText());
                        }

                        String statusText = receiptStatus.toUpperCase(Locale.ROOT);
                        boolean failedRecord = statusText.contains("FAIL")
                                || statusText.contains("PARTIAL");

                        if (!failedRecord && receiptStatus.isBlank()) {
                            failedRecord = !reason.isBlank() || !failureCode.isBlank();
                        }

                        if (!failedRecord) {
                            processedKeys.add(rowKey);
                            break;
                        }

                        scan.failedRecords++;
                        processedKeys.add(rowKey);

                        if (!reason.isBlank()) {
                            scan.reasons.add(
                                    failureCode.isBlank()
                                            ? reason
                                            : "[" + failureCode + "] " + reason);
                        }
                        break;
                    } catch (StaleElementReferenceException e) {
                        if (rowAttempt == 3) {
                            scan.problems.add("PostingLogs row " + (page.start + index)
                                    + " remained stale after 3 attempts");
                        }
                    }
                }
            }

            if (page.end >= page.total || page.total == 0) {
                break;
            }

            WebElement next = visibleElement(POSTING_LOG_NEXT_PAGE);
            if (next == null || !next.isEnabled()) {
                scan.problems.add("PostingLogs pagination ended early at range '" + page.range + "'");
                break;
            }

            int beforeStart = page.start;
            String beforeSignature = postingLogRowsSignature();

            clickAndWait(next);

            try {
                waitForState("PostingLogs to advance to the next page", d -> {
                    ReceiptPageState current = readPostingLogPageState();
                    if (current == null) return false;
                    return current.start > beforeStart
                            && (!visiblePostingLogRows().isEmpty() || current.total == 0);
                });
                waitForPostingLogRows();
            } catch (RuntimeException e) {
                scan.problems.add("PostingLogs paginator stopped advancing after range '"
                        + page.range + "': " + e.getClass().getSimpleName());
                break;
            }

            if (postingLogRowsSignature().equals(beforeSignature)
                    && readPostingLogPageState() != null
                    && readPostingLogPageState().start == beforeStart) {
                scan.problems.add("PostingLogs page content did not change after Next page.");
                break;
            }
        }

        if (expectedTotal > 0 && scan.failedRecords < expectedTotal) {
            scan.problems.add("PostingLogs failed records captured = "
                    + scan.failedRecords + ", paginator total = " + expectedTotal);
        }

        if (scan.reasons.size() < status.getFailedCount()) {
            scan.problems.add("PostingLogs failure reasons captured = "
                    + scan.reasons.size() + ", expected = " + status.getFailedCount());
        }

        return scan;
    }

    private void configurePostingLogPageSizeForScan() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                WebElement selector = visibleElement(POSTING_LOG_PAGE_SIZE_SELECT);
                if (selector == null) return;

                if ("100".equals(clean(selector.getText()))) {
                    return;
                }

                clickAndWait(selector);

                WebElement option = shortWait.until(d -> {
                    for (WebElement candidate : d.findElements(PAGINATOR_PAGE_SIZE_OPTIONS)) {
                        try {
                            if (isDisplayed(candidate) && "100".equals(clean(candidate.getText()))) {
                                return candidate;
                            }
                        } catch (StaleElementReferenceException ignored) {
                        }
                    }
                    return null;
                });

                if (option == null) {
                    throw new IllegalStateException("100 page-size option was not rendered in PostingLogs.");
                }

                try {
                    option.click();
                } catch (Exception e) {
                    jsClick(option);
                }

                waitForState("the PostingLogs paginator page size to become 100",
                        d -> {
                            WebElement current = visibleElement(POSTING_LOG_PAGE_SIZE_SELECT);
                            return current != null && "100".equals(clean(current.getText()));
                        });
                return;
            } catch (Exception e) {
                if (attempt == 3) {
                    System.out.println("[WARN] PostingLogs page size could not be set to 100; continuing with paginated capture.");
                }
                if (attempt < 3) pause(300L * attempt);
            }
        }
    }

    private ReceiptPageState readPostingLogPageState() {
        String range = readPostingLogRange();
        Matcher matcher = PAGER_PATTERN.matcher(range);
        if (!matcher.matches()) {
            return null;
        }

        int start = Integer.parseInt(matcher.group(1));
        int end = matcher.group(2) == null
                ? start
                : Integer.parseInt(matcher.group(2));
        int total = Integer.parseInt(matcher.group("total"));
        return new ReceiptPageState(start, end, total, range);
    }

    private String postingLogRowKey(WebElement row, int index, ReceiptPageState page) {
        List<WebElement> cells = row.findElements(By.xpath("./td"));
        Map<String, Integer> headers = headerIndexesFromPostingLogTable(row);
        String receiptNumber = cell(cells, headers, "receipt no", "receipt number", "receipt", "receipt id");
        String accountId = cell(cells, headers, "account id", "account");
        if (!receiptNumber.isBlank()) {
            return "RECEIPT-LOG:" + receiptNumber + "|ACCOUNT:" + accountId;
        }

        String rowText = clean(row.getText());
        if (!rowText.isBlank()) {
            return "ROW-LOG:" + rowText;
        }

        return "ROW-LOG:" + (page == null ? "" : page.range) + "#" + Math.max(index, 0);
    }

    private static final class PostingLogScan {
        final List<String> reasons = new ArrayList<>();
        final Set<String> problems = new LinkedHashSet<>();
        int failedRecords;
    }

    private boolean isPostingLogsModalOpen() {
        return driver.findElements(POSTING_LOGS_MODAL).stream().anyMatch(this::isDisplayed);
    }

    private void waitForPostingLogsModal() {
        shortWait.until(d -> isPostingLogsModalOpen());
        waitForPostingLogRows();
    }

    private void waitForPostingLogRows() {
        shortWait.until(d -> !visiblePostingLogRows().isEmpty() || readPostingLogRange().matches(".*\\bof\\s+\\d+.*"));
    }

    private List<WebElement> visiblePostingLogRows() {
        List<WebElement> result = new ArrayList<>();
        WebElement modal = visibleElement(POSTING_LOGS_MODAL);
        if (modal == null) {
            return result;
        }

        for (WebElement row : modal.findElements(POSTING_LOG_ROWS)) {
            try {
                if (isDisplayed(row) && !clean(row.getText()).isBlank()) {
                    result.add(row);
                }
            } catch (StaleElementReferenceException ignored) {
            }
        }
        return result;
    }

    private Map<String, Integer> headerIndexesFromPostingLogTable(WebElement row) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        try {
            WebElement table = row.findElement(By.xpath("./ancestor::table[1]"));
            List<WebElement> headers = table.findElements(By.xpath(".//thead//th"));
            for (int i = 0; i < headers.size(); i++) {
                String header = canonical(headers.get(i).getText());
                if (!header.isBlank()) {
                    indexes.putIfAbsent(header, i);
                }
            }
        } catch (Exception ignored) {
        }
        return indexes;
    }

    private String postingLogRowsSignature() {
        StringBuilder signature = new StringBuilder();
        for (WebElement row : visiblePostingLogRows()) {
            try {
                signature.append(clean(row.getText())).append("||");
            } catch (Exception ignored) {
            }
        }
        return signature.toString();
    }

    private String readPostingLogRange() {
        try {
            WebElement modal = visibleElement(POSTING_LOGS_MODAL);
            if (modal == null) return "";

            WebElement range = visibleInside(modal, By.cssSelector(
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
                    + "partially failed|processing|running|in progress|aborted|cancelled|canceled|skipped)$");

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

    private void scrollExecutionModal(WebElement modal) {
        WebElement container = null;
        for (WebElement candidate : modal.findElements(EXECUTION_MODAL_SCROLL)) {
            try {
                if (isDisplayed(candidate)) {
                    Long scrollHeight = ((Number)((JavascriptExecutor) driver).executeScript("return arguments[0].scrollHeight;", candidate)).longValue();
                    Long clientHeight = ((Number)((JavascriptExecutor) driver).executeScript("return arguments[0].clientHeight;", candidate)).longValue();
                    if (scrollHeight > clientHeight) {
                        container = candidate;
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (container == null) {
            container = modal;
        }

        try {

            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollTo({ top: arguments[0].scrollHeight, behavior: 'smooth' }); arguments[0].scrollTop = arguments[0].scrollHeight;",
                container
            );
            awaitUiStability();


            for (WebElement target : modal.findElements(By.xpath(".//div[contains(@class,'list-label') and (normalize-space()='Status' or normalize-space()='Start Date' or normalize-space()='Start Time' or normalize-space()='End Date' or normalize-space()='End Time' or normalize-space()='Reason')]"))) {
                try {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', behavior:'smooth'});", target);
                } catch (Exception ignored) {}
            }
            awaitUiStability();


            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollTo({ top: 0, behavior: 'smooth' }); arguments[0].scrollTop = 0;",
                container
            );
            awaitUiStability();
         }
         catch (Exception ignored) {
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
        awaitAppBootstrap();

        jobsPageWait.until(d -> {
            requireLiveSession(d);

            String url = d.getCurrentUrl().toLowerCase(Locale.ROOT);
            if (!url.contains("/admin/job")
                || url.contains("/postreceipts")
                || url.contains("/details/")) {
                return false;
            }

            return !visibleJobRows().isEmpty() && findJobRowOptional(JOB_POST_RECEIPTS) != null;
        });
    }

/** Step 1 and 2: hover the Admin trigger, then wait for the submenu to be rendered. */
    private void hoverAdminMenu() {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                action.waitForOverlayToClear();
                WebElement admin = waitForNavigation("the Admin menu trigger",
                        d -> visibleElement(ADMIN_MENU_TRIGGER));

                // moveToElement is required: the trigger opens the menu on mouseover, so a
                // plain click would do nothing at all.
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

    /** Step 3 and 4: find Job only once the submenu exists, then click it. */
    private void clickJobInAdminMenu() {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                WebElement job = waitForNavigation("the Job item inside the Admin submenu",
                        d -> visibleElement(ADMIN_MENU_JOB_ITEM));

                // The panel closes on mouseleave, so the pointer is moved onto the item before
                // clicking instead of letting the click jump in from outside the panel.
                new Actions(driver).moveToElement(job).perform();
                waitForNavigation("the Job item to become clickable",
                        d -> isDisplayed(ADMIN_MENU_JOB_ITEM) && isElementEnabled(ADMIN_MENU_JOB_ITEM));

                // Re-found immediately before the click because Angular re-creates the panel
                // on every open, which leaves earlier references stale.
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

    /** Step 5: confirm the Admin Jobs page rendered, using its own UI rather than the URL. */
    private void verifyAdminJobsPageRendered() {
        waitForNavigation("the Admin Jobs page to render its jobs table",
                d -> isAdminJobsPageRendered());
    }

    private boolean isAdminSubmenuVisible() {
        return isDisplayed(ADMIN_MENU_OVERLAY) && isDisplayed(ADMIN_MENU_PANEL);
    }

    /**
     * UI evidence that the Jobs page is really loaded: the app-job component is on screen and
     * its table has rows. The URL is deliberately not part of this, because the route can change
     * before the component has rendered.
     */
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

    /**
     * A navigation wait that explains itself. The stock message names only the page class, so a
     * failure here records the URL, which menu pieces were visible, and a screenshot instead.
     */
private <T> T waitForNavigation(String description, Function<? super WebDriver, T> condition) {
        try {
            return jobsPageWait.until(condition);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                describeNavigationFailure("Timed out waiting for " + description + "."), e);
        }
    }

    /**
     * A wait that names what it was waiting for. Selenium's own timeout message identifies only
     * the lambda, which is why a receipt-capture failure could not previously be traced to a step.
     */
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
        java.util.Set<String> handles;
        try {
            handles = d.getWindowHandles();
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                "Browser session ended before the Admin Jobs page finished loading.", e);
        }
        if (handles.isEmpty()) {
            throw new IllegalStateException(
                "Browser window was closed before the Admin Jobs page finished loading.");
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

    /**
     * Accepts only a finished result set: rows on screen, or an explicit empty result from the
     * paginator. A rendered paginator on its own is not enough, because it is still the previous
     * page's value while the next request is in flight.
     */
    private void waitForReceiptResults() {
        waitForState("receipt results to render rows or an explicit empty result",
                d -> !visibleReceiptRows().isEmpty() || isReceiptEmpty());
    }

    /**
     * The custom table renders no empty-state markup, so emptiness is read from the paginator
     * itself: its label is exactly "0 of 0" when there are no records, otherwise "N - M of T".
     * Text scanning is deliberately not used, because "no data" can appear anywhere in the panel.
     * A blank label is not treated as empty, so a paginator that has not rendered yet cannot be
     * mistaken for a result set that has finished loading.
     */
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

                // A row is usable with text or the error icon; cells may not have rendered.
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

        // Fallback for builds without the app-job wrapper; never mixes in other grids.
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

    /**
     * The grid is resolver-filled, so rows arrive in bursts. Wait for the row count to settle
     * before any lookup may treat a job as unconfigured.
     */
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
            row = findJobRowAcrossPages(jobName);
        }
        if (row == null) {
            throw new IllegalStateException(
                "Job row not found for '" + jobName + "'. Jobs on page: " + visibleJobNames());
        }
        return row;
    }

    /** Finds a job row on any grid page, restoring the starting page afterwards. */
    private WebElement findJobRowAcrossPages(String jobName) {
        WebElement row = findJobRowOptional(jobName);
        if (row != null) {
            return row;
        }

        String startRange = readJobPaginatorRange();
        Set<String> visited = new LinkedHashSet<>();
        visited.add(startRange);

        try {
            while (visited.size() <= 50) {
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

    /**
     * Matches a job row in two passes. The exact canonical match on the job-name cell runs first
     * so a row for another job can never win on a substring, and the looser row-text match is only
     * a fallback for layouts that do not put the name in its own cell.
     */
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

    /** Normalises case, spacing and punctuation so job name variants match. */
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

    private WebElement visibleElement(By locator) {
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

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String cell(List<WebElement> cells, Map<String, Integer> headers, String... names) {
        if (cells == null || cells.isEmpty() || names == null) return "";

        for (String name : names) {
            if (name == null || name.isBlank()) continue;

            Integer index = headers == null ? null : headers.get(canonical(name));
            if (index == null && headers != null) {
                String target = canonical(name);
                for (Map.Entry<String, Integer> entry : headers.entrySet()) {
                    if (canonical(entry.getKey()).equals(target)) {
                        index = entry.getValue();
                        break;
                    }
                }
            }

            if (index != null && index >= 0 && index < cells.size()) {
                try {
                    String value = clean(cells.get(index).getText());
                    if (!value.isBlank()) return value;
                } catch (StaleElementReferenceException ignored) {
                }
            }
        }

        return "";
    }

/**
     * Named for what it does. The old signature took a millisecond value that was ignored, which
     * hid the fact that these points waited on UI stability rather than on a fixed delay.
     */
    private void awaitUiStability() {
        waitForUiStable();
    }

    private String receiptResultsSignature() {
        StringBuilder signature = new StringBuilder();

        for (WebElement row : visibleReceiptRows()) {
            signature.append(clean(row.getText()))
                     .append("||");
        }

        return signature.toString();
    }

    private static final String[] MONTH_ABBREVIATIONS = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    /** Fixed format so generated rows match the timestamps read from the application. */

    private static final class ReceiptPageState {
        final int start;
        final int end;
        final int total;
        final String range;

        ReceiptPageState(int start, int end, int total, String range) {
            this.start = start;
            this.end = end;
            this.total = total;
            this.range = range;
        }

        int pageSize() {
            return end >= start && end > 0 ? end - start + 1 : 0;
        }
    }

    private static final class ReceiptCapture {
        int totalCount;
        List<String> reasons = new ArrayList<>();
        Set<String> problems = new LinkedHashSet<>();
    }
}
