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
    private static final By POSTING_LOGS_ACTION = By.xpath("//app-job-details//button[normalize-space()='PostingLogs']");
    private static final By POSTING_LOGS_MODAL = By.xpath("//div[contains(@class,'modal-wrapper')][.//span[contains(normalize-space(),'Receipt Posting Logs')] or .//h1[contains(normalize-space(),'Receipt Posting Logs')]]");
    private static final By POSTING_LOG_ROWS = By.cssSelector("app-receipt-posting-log app-custom-table table.table-box tbody tr, app-custom-table table.table-box tbody tr");
    private static final By POSTING_LOG_NEXT_PAGE = By.cssSelector("app-receipt-posting-log div.paginator-container button[aria-label='Next page'], app-receipt-posting-log button[aria-label*='Next page']");

    
private static final Pattern PAGER_PATTERN = Pattern.compile(
            "(\\d+)\\s*(?:[-\\u2013\\u2014]\\s*(\\d+)\\s*)?of\\s*(?<total>\\d+)");
    private static final Pattern FULL_DATE_TIME = Pattern.compile("(?:\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{4}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})(?:\\s+|T)+\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*[APMapm]{2})?");
    private static final Pattern RECEIPT_POSTING_FAILURE = Pattern.compile(
            "(?i)Receipt Posting Failure:\\s*Total:\\s*(\\d+)\\s*,\\s*Success:\\s*(\\d+)\\s*,"
                    + "\\s*Partially Success:\\s*(\\d+)\\s*,\\s*Failed:\\s*(\\d+)");

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
            results.add(unavailableJobPlaceholder(clientName, JOB_POST_RECEIPTS, e));
        }

        try {
            action.markStep("Encore Download Collection Items Job");
            ensureJobsPage();
            results.add(monitorExecutionJob(JOB_COLLECTION_ITEMS, clientName));
        } catch (Exception e) {
            System.out.println("[WARN] Download Collection Items Job capture error for " + clientName + ": " + e.getMessage());
            results.add(unavailableJobPlaceholder(clientName, JOB_COLLECTION_ITEMS, e));
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
            results.add(unavailableJobPlaceholder(clientName, JOB_UPCOMING_DEMAND, e));
        }

        action.markStep("client complete");
        return results;
    }

    
    private JobStatus unavailableJobPlaceholder(String clientName, String jobName, Exception cause) {
        JobStatus unavailable = new JobStatus();
        unavailable.setClientName(clientName);
        unavailable.setJobName(jobName);
        unavailable.setStatus("N/A");
        unavailable.setDateTime("N/A");
        unavailable.setJobFailureReason("Monitoring unavailable at step '" + action.currentStep() + "': "
                + cause.getClass().getSimpleName() + ": " + safeText(cause));
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

        ReceiptCapture failed = captureReceiptStatus("FAILED", true);
        ReceiptCapture pending = captureReceiptStatus("PENDING", false);

        status.setFailedCount(failed.totalCount);
        status.setPendingCount(pending.totalCount);
        failed.reasons.forEach(status::addFailureReason);

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
        validateSessionAndWindow();
        ensureReceiptPage();
        ensureReceiptFiltersVisible();
        selectReceiptDateToday();

        ReceiptCapture capture = new ReceiptCapture();

        if (!selectPostingStatus(postingStatus)) {
            capture.problems.add("LMS Posting Status '" + postingStatus
                    + "' is not offered for this client, so its count could not be captured");
            return capture;
        }

        searchReceipts(postingStatus, capture);

        capture.totalCount = readReceiptTotalCount(capture);

        validateReceiptCapture(capture, postingStatus);

        if (inspectReasons && capture.totalCount > 0) {
            ReasonScan scan = readUniqueFailureReasons();
            capture.reasons.addAll(scan.reasons);
            capture.problems.addAll(scan.problems);
        }

        return capture;
    }

    private void validateReceiptCapture(ReceiptCapture capture, String postingStatus) {
        if (capture.totalCount < 0) {
            capture.problems.add("Invalid " + postingStatus + " receipt count: " + capture.totalCount + " (must be non-negative)");
            capture.totalCount = 0;
        }
    }

    
    private String selectReceiptDateToday() {
        WebElement date = wait.until(ExpectedConditions.visibilityOfElementLocated(RECEIPT_DATE));

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

        waitForState("the '" + status + "' posting status option to be selected",
            d -> status.equalsIgnoreCase(
                new Select(d.findElement(LMS_POSTING_STATUS)).getFirstSelectedOption().getText().trim()
            ));
        return true;
    }

    private void searchReceipts(String expectedStatus, ReceiptCapture capture) {
        WebElement search = wait.until(
            ExpectedConditions.elementToBeClickable(RECEIPT_SEARCH)
        );

        String rangeBefore = readPaginatorRange();
        String signatureBefore = receiptResultsSignature();

        clickAndWait(search);

        waitForState("the '" + expectedStatus + "' receipt search to be applied", d -> {
            WebElement selectElement = d.findElement(LMS_POSTING_STATUS);

            String selectedStatus = new Select(selectElement)
                .getFirstSelectedOption()
                .getText()
                .trim();

            if (!expectedStatus.equalsIgnoreCase(selectedStatus)) {
                return false;
            }

            return d.getCurrentUrl().toLowerCase(Locale.ROOT)
                .contains("lmspostingstatus=" + expectedStatus.toLowerCase(Locale.ROOT));
        });

        boolean settled = wait.until(d ->
            !visibleReceiptRows().isEmpty() || isReceiptEmpty() || readPaginatorRange().isBlank());

        if (!settled) {
            capture.problems.add("The " + expectedStatus
                    + " receipt results did not finish rendering (paginator '"
                    + readPaginatorRange() + "')");
        }

        String expectedDate = LocalDate.now(config.getBusinessZone()).toString();
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

    
    private int readReceiptTotalCount(ReceiptCapture capture) {
        String range = readPaginatorRange();
        Matcher matcher = PAGER_PATTERN.matcher(range);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group("total"));
        }

        capture.problems.add("Receipt paginator label '" + clean(range)
                + "' could not be read, so the failed count covers the rows on screen only");
        return visibleReceiptRows().size();
    }
    private static final int MAX_RECEIPT_PAGES = 50;
    private static final int MAX_JOB_PAGES = 50;
    private static final int MAX_POSTING_LOG_PAGES = 50;

    private ReasonScan readUniqueFailureReasons() {
        Set<String> reasons = new LinkedHashSet<>();
        Set<String> problems = new LinkedHashSet<>();
        Set<String> pages = new LinkedHashSet<>();
        Set<String> seenRowSignatures = new LinkedHashSet<>();
        int pageCount = 0;

        while (true) {
            validateSessionAndWindow();

            if (pageCount >= MAX_RECEIPT_PAGES) {
                problems.add("Receipt pagination exceeded maximum page limit (" + MAX_RECEIPT_PAGES + "), stopping to prevent infinite loop");
                break;
            }

            String marker = readPaginatorRange();

            if (!marker.isBlank() && !pages.add(marker)) {
                problems.add("Receipt paginator returned duplicate range marker '" + marker + "', stopping to prevent infinite loop");
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
                validateSessionAndWindow();

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

                String rowSignature = buildRowSignature(row, index);
                if (!seenRowSignatures.add(rowSignature)) {
                    problems.add("Duplicate receipt row detected at index " + index + " (signature: " + rowSignature + "), skipping to prevent infinite loop");
                    continue;
                }

                boolean reasonCaptured = false;
                String reason = "";
                int maxAttempts = 3;

                for (int attempt = 0; attempt < maxAttempts && !reasonCaptured; attempt++) {
                    scrollIntoViewSmooth(icon);
                    fastClick(icon);

                    reason = readFailureReason();

                    if (!reason.isBlank()) {
                        reasons.add(trimReason(reason));
                        reasonCaptured = true;
                    } else if (attempt < maxAttempts - 1) {
                        awaitUiStability();
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

                if (!closeFailureReasonMenu(index)) {
                    problems.add("The failure reason menu for FAILED receipt row " + (index + 1)
                            + " could not be closed, so the remaining rows were not scanned");
                    break;
                }
            }

            pageCount++;

            WebElement next = visibleElement(RECEIPT_NEXT_PAGE);

            if (next == null || !next.isEnabled()) {
                break;
            }

            String before = readPaginatorRange();
            clickAndWait(next);

            try {

                wait.until(d -> {
                    String after = readPaginatorRange();
                    boolean moved = !after.isBlank() && !after.equals(before);
                    return moved && (!visibleReceiptRows().isEmpty() || isReceiptEmpty());
                });
            } catch (RuntimeException e) {
                problems.add("Receipt paginator stopped advancing at range '" + before + "'");
                break;
            }

            waitForReceiptResults();
        }

        log("Scanned " + pageCount + " receipt page(s), found " + reasons.size() + " unique failure reason(s)");
        return new ReasonScan(reasons, problems);
    }

    private String buildRowSignature(WebElement row, int index) {
        try {
            String text = clean(row.getText());
            if (text.isBlank()) {
                return "empty-row-" + index;
            }
            return String.valueOf(text.hashCode());
        } catch (Exception e) {
            return "error-row-" + index;
        }
    }

    
    private static final class ReasonScan {
        final Set<String> reasons;
        final Set<String> problems;

        ReasonScan(Set<String> reasons, Set<String> problems) {
            this.reasons = reasons;
            this.problems = problems;
        }
    }

    private String readFailureReason() {

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

    private void scrollIntoViewSmooth(WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center', inline:'nearest', behavior:'smooth'});", element);

            Thread.sleep(300);
        } catch (RuntimeException | InterruptedException ignored) {
        }
    }

    private void fastClick(WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        } catch (RuntimeException e) {
            try { element.click(); } catch (Exception ignored) { }
        }
    }

    
    private boolean closeFailureReasonMenu(int rowIndex) {
        for (int attempt = 0; attempt < 3 && isFailureReasonMenuOpen(); attempt++) {
            try {
                driver.switchTo().activeElement().sendKeys(Keys.ESCAPE);
            } catch (Exception ignored) {
            }
            if (waitForMenuClosed()) return true;

            for (By safeTarget : List.of(MENU_BACKDROP, RECEIPTS_HEADING)) {
                try {
                    WebElement target = visibleElement(safeTarget);
                    if (target == null) continue;
                    new Actions(driver).moveToElement(target).click().perform();
                    if (waitForMenuClosed()) return true;
                } catch (Exception ignored) {
                }
            }

            try {
                List<WebElement> rows = visibleReceiptRows();
                if (rowIndex < rows.size()) {
                    WebElement icon = visibleInside(rows.get(rowIndex), RECEIPT_ERROR_ICON);
                    if (icon != null) {
                        scrollIntoView(icon);
                        new Actions(driver).moveToElement(icon).click().perform();
                        if (waitForMenuClosed()) return true;
                    }
                }
            } catch (Exception ignored) {
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

    private boolean waitForMenuClosed() {
        try {
            shortWait.until(d -> d.findElements(FAILURE_MENU).stream().noneMatch(this::isDisplayed));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void captureLatestExecutionFromJobsList(String jobName, JobStatus status) {
        validateSessionAndWindow();
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
        scrollExecutionModalToBottom(modal);

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
            System.out.println("[WARN] Job status is FAILED but Reason could not be captured for " + jobName + ".");
        }

        closeExecutionModalUsingUi();

        if (receiptPostingFailure && status.getFailedCount() > 0) {
            status.clearFailureReasons();
            capturePostingLogFailureReasons(jobName, status);
        }

        closeJobDetailsUsingUi();
        waitForJobsPage();
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
        return status != null && status.trim().equalsIgnoreCase("PARTIALLY_SUCCESSFUL");
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

        if (failed != status.getFailedCount()) {
            status.setValidationMessage(
                "Receipt count mismatch: receipt API reports " + status.getFailedCount()
                + " FAILED record(s), while the job execution summary reports " + failed
                + " FAILED record(s). The receipt API count is retained as authoritative."
            );
        }

        System.out.println("[RECEIPT OUTCOME] Receipt Posting Failure summary detected: "
                + "Total=" + total + ", Success=" + success
                + ", Partially Success=" + partial + ", Failed=" + failed
                + ". Checking PostingLogs for receipt-level failure reasons.");

        return failed > 0;
    }

    private void capturePostingLogFailureReasons(String jobName, JobStatus status) {
        if (!JOB_POST_RECEIPTS.equalsIgnoreCase(jobName)) {
            return;
        }

        WebElement postingLogs = visibleElement(POSTING_LOGS_ACTION);
        if (postingLogs == null) {
            status.setJobFailureReason("Receipt Posting Failure detected, but PostingLogs action was not available.");
            return;
        }

        try {
            scrollIntoView(postingLogs);
            clickAndWait(postingLogs);
            WebElement modal = wait.until(d -> {
                List<WebElement> modals = d.findElements(POSTING_LOGS_MODAL);
                for (int i = modals.size() - 1; i >= 0; i--) {
                    if (isDisplayed(modals.get(i))) return modals.get(i);
                }
                return null;
            });

            Set<String> reasons = new LinkedHashSet<>();
            Set<String> pages = new LinkedHashSet<>();
            Set<String> seenRowSignatures = new LinkedHashSet<>();
            int pageCount = 0;

            while (true) {
                validateSessionAndWindow();

                if (pageCount >= MAX_POSTING_LOG_PAGES) {
                    status.setJobFailureReason("PostingLogs pagination exceeded maximum page limit (" + MAX_POSTING_LOG_PAGES + ")");
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
                    if (cells.size() < 7) continue;

                    String receiptStatus = clean(cells.get(5).getText());
                    String reason = clean(cells.get(6).getText());
                    String failureCode = cells.size() > 7 ? clean(cells.get(7).getText()) : "";

                    String rowSignature = buildRowSignature(row, 0);
                    if (!seenRowSignatures.add(rowSignature)) {
                        continue;
                    }

                    if (!reason.isBlank() && (receiptStatus.isBlank()
                            || receiptStatus.toUpperCase(Locale.ROOT).contains("FAIL")
                            || failureCode.length() > 0)) {
                        reasons.add(trimReason(failureCode.isBlank() ? reason : reason + " | Failure Code: " + failureCode));
                        foundReason = true;
                    }
                }

                if (!foundReason && rows.isEmpty()) {
                    status.setJobFailureReason("Receipt Posting Failure detected, but PostingLogs returned no receipt records.");
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
                    break;
                }
            }

            log("Scanned " + pageCount + " posting log page(s), found " + reasons.size() + " unique failure reason(s)");

            for (String reason : reasons) {
                status.addFailureReason(reason);
            }

            if (!reasons.isEmpty()) {
                status.setJobFailureReason(null);
            } else if (status.getJobFailureReason() == null || status.getJobFailureReason().isBlank()) {
                status.setJobFailureReason("Receipt Posting Failure detected, but no receipt-level failure reason was available in PostingLogs.");
            }

            closePostingLogsModal();
        } catch (Exception e) {
            status.setJobFailureReason("Receipt Posting Failure detected, but PostingLogs could not be captured: "
                    + e.getClass().getSimpleName() + ": " + safeText(e));
            closePostingLogsModal();
        }
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

    private void scrollExecutionModalToBottom(WebElement modal) {
        try {
            WebElement container = null;
            long largestScrollableArea = -1;

            List<WebElement> candidates = new ArrayList<>();
            candidates.add(modal);
            candidates.addAll(modal.findElements(By.cssSelector(".absolute.overflow-auto, .overflow-auto, [style*='overflow']")));

            for (WebElement candidate : candidates) {
                try {
                    if (!isDisplayed(candidate)) continue;
                    long scrollHeight = ((Number) ((JavascriptExecutor) driver).executeScript(
                            "return arguments[0].scrollHeight;", candidate)).longValue();
                    long clientHeight = ((Number) ((JavascriptExecutor) driver).executeScript(
                            "return arguments[0].clientHeight;", candidate)).longValue();
                    long scrollable = scrollHeight - clientHeight;
                    if (scrollable > largestScrollableArea) {
                        largestScrollableArea = scrollable;
                        container = candidate;
                    }
                } catch (Exception ignored) {
                }
            }

            if (container == null) container = modal;

            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollTo({top: arguments[0].scrollHeight, behavior: 'smooth'});", container);
            awaitUiStability();

            WebElement reason = null;
            List<WebElement> labels = modal.findElements(By.xpath(
                    ".//div[contains(@class,'list-label') and (normalize-space()='Reason' or "
                    + "normalize-space()='End Date' or normalize-space()='End Time')]"));
            for (WebElement label : labels) {
                try {
                    if ("Reason".equalsIgnoreCase(clean(label.getText()))) {
                        reason = label;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
            if (reason != null) {
                ((JavascriptExecutor) driver).executeScript(
                        "arguments[0].scrollIntoView({block:'center', inline:'nearest', behavior:'smooth'});", reason);
                awaitUiStability();
            }
        } catch (Exception e) {
            System.out.println("[WARN] Could not scroll execution View modal: " + safeText(e));
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
        waitForState("receipt results to render rows or an explicit empty result",
                d -> !visibleReceiptRows().isEmpty() || isReceiptEmpty());
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

    
    private WebElement findJobRowAcrossPages(String jobName) {
        WebElement row = findJobRowOptional(jobName);
        if (row != null) {
            return row;
        }

        validateSessionAndWindow();

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

    private String trimReason(String reason) {
        if (reason == null) return "";
        String clean = clean(reason);
        if (clean.length() <= 180) return clean;
        return clean.substring(0, 177) + "...";
    }

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

    

    private static class ReceiptCapture {
        int totalCount;
        Set<String> reasons = new LinkedHashSet<>();
        Set<String> problems = new LinkedHashSet<>();
    }
}



