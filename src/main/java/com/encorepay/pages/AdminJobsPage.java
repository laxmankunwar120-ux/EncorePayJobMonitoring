package com.encorepay.pages;
import org.openqa.selenium.interactions.Actions;

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
    private static final By LMS_POSTING_STATUS = By.cssSelector("app-receipts select[name='lmsPostingStatus']");
    private static final By RECEIPT_SEARCH = By.xpath("//app-receipts//button[normalize-space()='Search']");
    private static final By RECEIPT_ROWS = By.cssSelector("app-receipts app-custom-table table.table-box tbody tr");
    private static final By RECEIPT_PAGINATOR_RANGE = By.cssSelector(
            "app-receipts div.paginator-container .ct-range, app-receipts .ct-range, "
                    + "app-receipts mat-paginator .mat-mdc-paginator-range-label, "
                    + "app-receipts mat-paginator .mat-paginator-range-label");
    private static final By RECEIPT_NEXT_PAGE = By.cssSelector(
            "app-receipts div.paginator-container button[aria-label='Next page'], "
                    + "app-receipts button[aria-label*='Next page']");
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
    private static final By RECEIPTS_PANEL = By.cssSelector("app-receipts div.list-panel, app-receipts div.list-page, app-receipts");
    private static final By MENU_BACKDROP = By.cssSelector(".cdk-overlay-backdrop, .cdk-overlay-dark-backdrop");
    private static final By FAILURE_MENU = By.cssSelector(".cdk-overlay-pane .mat-mdc-menu-panel, .cdk-overlay-pane .mat-menu-panel");
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

    private static final long UI_PAUSE_MS = 150L;
    private static final long FILTER_PAUSE_MS = 250L;
    private static final long SEARCH_PAUSE_MS = 300L;

    private static final Pattern PAGER_PATTERN = Pattern.compile("(?:of)\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FULL_DATE_TIME = Pattern.compile("(?:\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{4}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})(?:\\s+|T)+\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*[APMapm]{2})?");

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
            ensureJobsPage();
            results.add(monitorPostReceiptJob(clientName));
        } catch (Exception e) {
            System.out.println("[WARN] Post Receipts Job capture error for " + clientName + ": " + e.getMessage());
            JobStatus failedPost = new JobStatus();
            failedPost.setClientName(clientName);
            failedPost.setJobName(JOB_POST_RECEIPTS);
            failedPost.setStatus("FAILED");
            failedPost.setDateTime(runTimestamp());
            failedPost.setJobFailureReason("Network/Page error: " + e.getMessage());
            results.add(failedPost);
        }


        try {
            ensureJobsPage();
            results.add(monitorExecutionJob(JOB_COLLECTION_ITEMS, clientName));
        } catch (Exception e) {
            System.out.println("[WARN] Download Collection Items Job capture error for " + clientName + ": " + e.getMessage());
            JobStatus failedCol = new JobStatus();
            failedCol.setClientName(clientName);
            failedCol.setJobName(JOB_COLLECTION_ITEMS);
            failedCol.setStatus("FAILED");
            failedCol.setDateTime(runTimestamp());
            failedCol.setJobFailureReason("Network/Page error: " + e.getMessage());
            results.add(failedCol);
        }


        try {
            ensureJobsPage();
            if (findUpcomingDemandRow() != null) {
                results.add(monitorExecutionJob(JOB_UPCOMING_DEMAND, clientName));
            } else {
                System.out.println(" Skip Encore Up Coming Demands Job is not configured for client " + clientName + ".");
            }
        } catch (Exception e) {
            System.out.println("[WARN] Upcoming Demands Job capture error for " + clientName + ": " + e.getMessage());
            JobStatus failedUp = new JobStatus();
            failedUp.setClientName(clientName);
            failedUp.setJobName(JOB_UPCOMING_DEMAND);
            failedUp.setStatus("FAILED");
            failedUp.setDateTime(runTimestamp());
            failedUp.setJobFailureReason("Network/Page error: " + e.getMessage());
            results.add(failedUp);
        }

        return results;
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

        ReceiptCapture failed = captureReceiptStatus("FAILED", true);
        ReceiptCapture pending = captureReceiptStatus("PENDING", false);

        status.setFailedCount(failed.totalCount);
        status.setPendingCount(pending.totalCount);
        failed.reasons.forEach(status::addFailureReason);

        List<String> notes = new ArrayList<>(failed.problems);
        if (failed.totalCount > 0 && failed.reasons.isEmpty() && notes.isEmpty()) {
            notes.add("Failed receipts were found but no failure reason could be read");
        }
        if (!notes.isEmpty()) {
            status.setJobFailureReason("Failure reason capture incomplete: " + String.join("; ", notes));
        }

        closeReceiptPageUsingUi();
        ensureJobsPage();
        captureLatestExecutionFromJobsList(JOB_POST_RECEIPTS, status);
        return status;
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

        if (!selectPostingStatus(postingStatus)) {
            System.out.println(" LMS Posting Status '" + postingStatus
                    + "' is not offered for this client; reporting 0 for it.");
            return new ReceiptCapture();
        }

        waitMillis(FILTER_PAUSE_MS);
        searchReceipts(postingStatus);

        ReceiptCapture capture = new ReceiptCapture();
        capture.totalCount = readReceiptTotalCount();

        if (inspectReasons && capture.totalCount > 0) {
            ReasonScan scan = readUniqueFailureReasons();
            capture.reasons.addAll(scan.reasons);
            capture.problems.addAll(scan.problems);
        }

        return capture;
    }

    private void selectReceiptDateToday() {
        WebElement date = wait.until(ExpectedConditions.visibilityOfElementLocated(RECEIPT_DATE));
        String today = LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
            "arguments[0].value=arguments[1];" +
            "arguments[0].dispatchEvent(new Event('input',{bubbles:true}));" +
            "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));" +
            "arguments[0].blur();",
            date,
            today
        );
        wait.until(d -> today.equals(date.getAttribute("value")));
        waitMillis(UI_PAUSE_MS);
    }

    private boolean selectPostingStatus(String status) {
        WebElement selectElement = wait.until(
            ExpectedConditions.elementToBeClickable(LMS_POSTING_STATUS)
        );
        Select select = new Select(selectElement);

        boolean matched = false;
        try {
            select.selectByVisibleText(status);
            matched = true;
        } catch (Exception e) {
            for (WebElement option : select.getOptions()) {
                if (status.equalsIgnoreCase(option.getText().trim())) {
                    select.selectByVisibleText(option.getText().trim());
                    matched = true;
                    break;
                }
            }
        }

        if (!matched) {
            return false;
        }

        wait.until(d -> status.equalsIgnoreCase(
            new Select(d.findElement(LMS_POSTING_STATUS)).getFirstSelectedOption().getText().trim()
        ));
        return true;
    }

    private void searchReceipts(String expectedStatus) {
        WebElement search = wait.until(
            ExpectedConditions.elementToBeClickable(RECEIPT_SEARCH)
        );

        clickAndWait(search);

        wait.until(d -> {
            WebElement selectElement = d.findElement(LMS_POSTING_STATUS);

            String selectedStatus = new Select(selectElement)
                .getFirstSelectedOption()
                .getText()
                .trim();

            if (!expectedStatus.equalsIgnoreCase(selectedStatus)) {
                return false;
            }

            String currentUrl = d.getCurrentUrl().toLowerCase(Locale.ROOT);
            String expectedParam = "lmspostingstatus=" + expectedStatus.toLowerCase(Locale.ROOT);
            boolean requestCompleted = currentUrl.contains(expectedParam);
            boolean resultsLoaded = isReceiptEmpty()
                || !visibleReceiptRows().isEmpty()
                || !readPaginatorRange().isBlank();

            return requestCompleted && resultsLoaded;
        });

        waitMillis(SEARCH_PAUSE_MS);
    }

    private int readReceiptTotalCount() {
        String range = readPaginatorRange();
        Matcher matcher = PAGER_PATTERN.matcher(range);
        if (matcher.find()) return Integer.parseInt(matcher.group(1));
        return visibleReceiptRows().size();
    }
    private ReasonScan readUniqueFailureReasons() {
        Set<String> reasons = new LinkedHashSet<>();
        Set<String> problems = new LinkedHashSet<>();
        Set<String> pages = new LinkedHashSet<>();

        while (true) {
            String marker = readPaginatorRange();

            if (!marker.isBlank() && !pages.add(marker)) {
                break;
            }

            int rowCount = visibleReceiptRows().size();

            if (rowCount == 0) {
                if (isReceiptEmpty()) {
                    break;
                }

                waitForReceiptResults();
                rowCount = visibleReceiptRows().size();

                if (rowCount == 0) {
                    problems.add("FAILED receipts were reported by the paginator but no receipt "
                            + "rows rendered (range: '" + readPaginatorRange() + "')");
                    break;
                }
            }

            for (int index = 0; index < rowCount; index++) {
                // Re-fetch each iteration; cached row references go stale on re-render.
                List<WebElement> currentRows = visibleReceiptRows();

                if (index >= currentRows.size()) {
                    problems.add("FAILED receipt row " + (index + 1)
                            + " disappeared before its failure reason could be read");
                    break;
                }

                WebElement row = currentRows.get(index);
                WebElement icon = visibleInside(row, RECEIPT_ERROR_ICON);

                if (icon == null) {
                    problems.add("FAILED receipt row " + (index + 1)
                            + " has no error icon to open");
                    continue;
                }

                boolean reasonCaptured = false;
                String reason = "";
                int maxAttempts = 3;

                for (int attempt = 0; attempt < maxAttempts && !reasonCaptured; attempt++) {
                    scrollIntoView(icon);
                    clickAndWait(icon);

                    reason = readFailureReason();

                    if (!reason.isBlank()) {
                        reasons.add(reason);
                        reasonCaptured = true;
                    } else if (attempt < maxAttempts - 1) {
                        waitMillis(500);
                        List<WebElement> refreshedRows = visibleReceiptRows();
                        if (index < refreshedRows.size()) {
                            icon = visibleInside(refreshedRows.get(index), RECEIPT_ERROR_ICON);
                        }
                    }
                }

                if (!reasonCaptured) {
                    problems.add("FAILED receipt row " + (index + 1)
                            + " opened no failure reason after " + maxAttempts + " attempts");
                }

                closeFailureReasonMenu();

                waitMillis(UI_PAUSE_MS);
            }

            WebElement next = visibleElement(RECEIPT_NEXT_PAGE);

            if (next == null || !next.isEnabled()) {
                break;
            }

            String before = readPaginatorRange();
            clickAndWait(next);

            try {
                wait.until(d -> {
                    String after = readPaginatorRange();
                    return !after.isBlank() && !after.equals(before);
                });
            } catch (RuntimeException e) {
                problems.add("Receipt paginator stopped advancing at range '" + before + "'");
                break;
            }

            waitForReceiptResults();
        }

        return new ReasonScan(reasons, problems);
    }

    /** Reasons collected from the error menus, plus non-fatal capture problems. */
    private static final class ReasonScan {
        final Set<String> reasons;
        final Set<String> problems;

        ReasonScan(Set<String> reasons, Set<String> problems) {
            this.reasons = reasons;
            this.problems = problems;
        }
    }

    private String readFailureReason() {
        // shortWait keeps a menu that never opens from stalling the whole scan.
        try {
            return clean(shortWait.until(d -> {
                List<WebElement> menus = d.findElements(FAILURE_MENU);

                for (int i = menus.size() - 1; i >= 0; i--) {
                    WebElement menu = menus.get(i);
                    if (!isDisplayed(menu)) continue;

                    String text = clean(menu.getText());
                    if (!text.isBlank()) return text;
                }

                return null;
            }));
        } catch (RuntimeException e) {
            return "";
        }
    }

    private void closeFailureReasonMenu() {
        try {
            driver.switchTo().activeElement().sendKeys(Keys.ESCAPE);
        } catch (Exception ignored) {
        }

        // Never click <body>: its centre can hit a row action and navigate away.
        for (By safeTarget : List.of(MENU_BACKDROP, RECEIPTS_HEADING)) {
            try {
                WebElement target = visibleElement(safeTarget);
                if (target == null) continue;
                new Actions(driver).moveToElement(target).click().perform();
                if (waitForMenuClosed()) {
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        if (!waitForMenuClosed()) {
            System.out.println("[WARN] A failure reason menu stayed open; continuing the receipt scan anyway.");
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

        if (isFailedStatus(executionStatus)) {
            String jobFailureReason = waitForModalField(modal, "Reason");
            if (jobFailureReason.isBlank()) {
                jobFailureReason = readReasonFromModalText(modal.getText());
            }
            if (!jobFailureReason.isBlank()) {
                status.setJobFailureReason(jobFailureReason);
            } else {
                System.out.println("[WARN] Job status is FAILED but Reason could not be captured for " + jobName + ".");
            }
        }

        closeExecutionModalUsingUi();
        closeJobDetailsUsingUi();
        waitForJobsPage();
    }

    private boolean isFailedStatus(String status) {
        return status != null && status.trim().toUpperCase(Locale.ROOT).contains("FAIL");
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
            waitMillis(500);


            for (WebElement target : modal.findElements(By.xpath(".//div[contains(@class,'list-label') and (normalize-space()='Status' or normalize-space()='Start Date' or normalize-space()='Start Time' or normalize-space()='End Date' or normalize-space()='End Time' or normalize-space()='Reason')]"))) {
                try {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', behavior:'smooth'});", target);
                } catch (Exception ignored) {}
            }
            waitMillis(400);


            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollTo({ top: 0, behavior: 'smooth' }); arguments[0].scrollTop = 0;",
                container
            );
            waitMillis(400);
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
                    waitMillis(300);
                } catch (Exception ignored) {}
            }


            if (!isExecutionModalClosed()) {
                for (WebElement btn : driver.findElements(By.xpath("//mat-dialog-container//button[contains(@class,'close') or .//span[normalize-space()='close']] | //div[contains(@class,'cdk-overlay-backdrop')]"))) {
                    try {
                        if (isDisplayed(btn)) {
                            jsClick(btn);
                            waitMillis(200);
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
        wait.until(d -> {
                String url = d.getCurrentUrl().toLowerCase(Locale.ROOT);
                return group != null && url.contains("/admin/job/details/" + group.toLowerCase(Locale.ROOT));
            });
        wait.until(d -> d.findElements(JOB_DETAILS_ROOT).stream().anyMatch(this::isDisplayed));
        wait.until(d -> !d.findElements(JOB_DETAIL_ROWS).isEmpty());
        waitMillis(UI_PAUSE_MS);
    }

    private void waitForReceiptPage() {
        wait.until(d -> d.getCurrentUrl().toLowerCase(Locale.ROOT).contains("/admin/job/postreceipts"));
        wait.until(d -> isDisplayed(RECEIPT_SHOW_FILTER) || isDisplayed(RECEIPT_HIDE_FILTER) || !visibleReceiptRows().isEmpty());
        waitMillis(UI_PAUSE_MS);
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
        waitMillis(UI_PAUSE_MS);
    }

    private void waitForReceiptResults() {
        wait.until(d -> !visibleReceiptRows().isEmpty() || isReceiptEmpty() || !readPaginatorRange().isBlank());
        waitMillis(UI_PAUSE_MS);
    }

    private boolean isReceiptEmpty() {
        String range = readPaginatorRange().toLowerCase(Locale.ROOT);
        if (range.contains("of 0")) return true;

        // Scoped to the panel because <body> can match unrelated "No data" text.
        for (WebElement panel : driver.findElements(RECEIPTS_PANEL)) {
            try {
                if (!isDisplayed(panel)) continue;
                String text = clean(panel.getText()).toLowerCase(Locale.ROOT);
                if (text.contains("no record") || text.contains("no records") || text.contains("no data")) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
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
        WebElement row = wait.until(d -> findJobRowOptional(jobName));
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

    private WebElement findJobRowOptional(String jobName) {
        List<String> aliases = JOB_ALIASES.getOrDefault(jobName, List.of(jobName));
        List<String> canonicalAliases = new ArrayList<>();
        for (String alias : aliases) {
            canonicalAliases.add(canonical(alias));
        }

        for (WebElement row : driver.findElements(JOB_ROWS)) {
            try {
                if (!isDisplayed(row)) continue;

                if (matchesAlias(canonicalAliases, canonical(jobNameCellText(row)))) return row;
                if (matchesAlias(canonicalAliases, canonical(row.getText()))) return row;
            } catch (StaleElementReferenceException ignored) {
            }
        }

        for (WebElement row : driver.findElements(JOB_ROWS_FALLBACK)) {
            try {
                if (!isDisplayed(row)) continue;
                if (matchesAlias(canonicalAliases, canonical(row.getText()))) return row;
            } catch (StaleElementReferenceException ignored) {
            }
        }
        return null;
    }

    private boolean matchesAlias(List<String> canonicalAliases, String haystack) {
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
        waitMillis(UI_PAUSE_MS);
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

    private void waitMillis(long millis) {
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
    private static String runTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        int hour12 = now.getHour() % 12;
        if (hour12 == 0) {
            hour12 = 12;
        }
        return String.format(
                Locale.ROOT,
                "%02d %s %04d %02d:%02d:%02d %s",
                now.getDayOfMonth(),
                MONTH_ABBREVIATIONS[now.getMonthValue() - 1],
                now.getYear(),
                hour12,
                now.getMinute(),
                now.getSecond(),
                now.getHour() < 12 ? "AM" : "PM");
    }

    private static class ReceiptCapture {
        int totalCount;
        Set<String> reasons = new LinkedHashSet<>();
        Set<String> problems = new LinkedHashSet<>();
    }
}
