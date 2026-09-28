package com.encorepay.pages;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.encorepay.models.JobStatus;
import com.encorepay.utilities.ConfigReader;

public class AdminJobsPage extends BasePage {

    public static final List<String> MONITORED_JOB_NAMES = List.of(
        "Post Receipts Job",
        "Encore Download Collection Items Job",
        "Encore Up Coming Demands Job"
    );

    private static final Map<String, String> JOB_GROUPS = Map.of(
        "Post Receipts Job", "POST_RECEIPT",
        "Encore Download Collection Items Job", "COLLECTION_ITEMS",
        "Encore Up Coming Demands Job", "UP_COMING_DEMAND"
    );

    private static final Map<String, List<String>> JOB_ALIASES = new LinkedHashMap<>();

    static {
        JOB_ALIASES.put("Post Receipts Job", List.of("Post Receipts Job", "Post Receipt Job"));
        JOB_ALIASES.put("Encore Download Collection Items Job", List.of(
            "Encore Download Collection Items Job", "Download Collection Items Job"));
        JOB_ALIASES.put("Encore Up Coming Demands Job", List.of(
            "Encore Up Coming Demands Job", "Encore Upcoming Demands Job", "Upcoming Demands Job"));
    }

    private static final By JOB_ROWS = By.cssSelector(
        "app-job table.table-box tbody tr, table.table-box tbody tr");
    private static final By VIEW_ACTION = By.xpath(".//button[normalize-space()='View'] | .//a[normalize-space()='View']");
    private static final By RECEIPT_ACTION = By.xpath(".//button[normalize-space()='Receipt'] | .//a[normalize-space()='Receipt']");

    private static final By RECEIPT_SHOW_FILTER = By.xpath("//app-receipts//button[contains(normalize-space(),'Show Filter')]");
    private static final By RECEIPT_HIDE_FILTER = By.xpath("//app-receipts//button[contains(normalize-space(),'Hide Filter')]");
    private static final By RECEIPT_DATE = By.cssSelector("app-receipts input[name='receiptDate']");
    private static final By LMS_POSTING_STATUS = By.cssSelector("app-receipts select[name='lmsPostingStatus']");
    private static final By RECEIPT_SEARCH = By.xpath("//app-receipts//button[normalize-space()='Search']");
    private static final By RECEIPT_ROWS = By.cssSelector("app-receipts app-custom-table table.table-box tbody tr");
    private static final By RECEIPT_PAGINATOR_RANGE = By.cssSelector("app-receipts mat-paginator .mat-mdc-paginator-range-label, app-receipts mat-paginator .mat-paginator-range-label");
    private static final By RECEIPT_NEXT_PAGE = By.cssSelector("app-receipts mat-paginator button[aria-label='Next page'], app-receipts mat-paginator button[aria-label*='Next page']");
    private static final By RECEIPT_ERROR_ICON = By.xpath(".//div[contains(@class,'material-symbols-rounded') and normalize-space()='error_outline']");
    private static final By FAILURE_MENU = By.cssSelector(".cdk-overlay-pane .mat-mdc-menu-panel, .cdk-overlay-pane .mat-menu-panel");
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

    public AdminJobsPage(WebDriver driver) {
        super(driver);
    }

    public AdminJobsPage(WebDriver driver, ConfigReader config) {
        super(driver, config);
    }

    public void navigateToAdminJobs() {
        openJobsRouteDirectly();
        waitForJobsPage();
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
            failedPost.setJobName("Post Receipts Job");
            failedPost.setStatus("FAILED");
            failedPost.setDateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm:ss a")));
            failedPost.setJobFailureReason("Network/Page error: " + e.getMessage());
            results.add(failedPost);
        }


        try {
            ensureJobsPage();
            results.add(monitorExecutionJob("Encore Download Collection Items Job", clientName));
        } catch (Exception e) {
            System.out.println("[WARN] Download Collection Items Job capture error for " + clientName + ": " + e.getMessage());
            JobStatus failedCol = new JobStatus();
            failedCol.setClientName(clientName);
            failedCol.setJobName("Encore Download Collection Items Job");
            failedCol.setStatus("FAILED");
            failedCol.setDateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm:ss a")));
            failedCol.setJobFailureReason("Network/Page error: " + e.getMessage());
            results.add(failedCol);
        }


        try {
            ensureJobsPage();
            if (findJobRowOptional("Encore Up Coming Demands Job") != null) {
                results.add(monitorExecutionJob("Encore Up Coming Demands Job", clientName));
            } else {
                System.out.println(" Skip Encore Up Coming Demands Job is not configured for client " + clientName + ".");
            }
        } catch (Exception e) {
            System.out.println("[WARN] Upcoming Demands Job capture error for " + clientName + ": " + e.getMessage());
            JobStatus failedUp = new JobStatus();
            failedUp.setClientName(clientName);
            failedUp.setJobName("Encore Up Coming Demands Job");
            failedUp.setStatus("FAILED");
            failedUp.setDateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm:ss a")));
            failedUp.setJobFailureReason("Network/Page error: " + e.getMessage());
            results.add(failedUp);
        }

        return results;
    }

    private JobStatus monitorPostReceiptJob(String clientName) {
        JobStatus status = new JobStatus();
        status.setJobName("Post Receipts Job");
        status.setClientName(clientName);

        WebElement jobRow = requireJobRow("Post Receipts Job");
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

        if (failed.totalCount > 0 && failed.reasons.isEmpty()) {
            throw new IllegalStateException("Failed receipt reason could not be captured from the individual error icon.");
        }

        closeReceiptPageUsingUi();
        ensureJobsPage();
        captureLatestExecutionFromJobsList("Post Receipts Job", status);
        return status;
    }

    private JobStatus monitorExecutionJob(String jobName, String clientName) {
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
        selectPostingStatus(postingStatus);
        waitMillis(FILTER_PAUSE_MS);
        searchReceipts(postingStatus);

        ReceiptCapture capture = new ReceiptCapture();
        capture.totalCount = readReceiptTotalCount();

        if (inspectReasons && capture.totalCount > 0) {
            capture.reasons.addAll(readUniqueFailureReasons());
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

private void selectPostingStatus(String status) {
        WebElement selectElement = wait.until(
            ExpectedConditions.elementToBeClickable(LMS_POSTING_STATUS)
        );
        Select select = new Select(selectElement);

        try {
            select.selectByVisibleText(status);
        } catch (Exception e) {
            // Fallback: case-insensitive match against visible text
            boolean matched = false;
            for (WebElement option : select.getOptions()) {
                if (status.equalsIgnoreCase(option.getText().trim())) {
                    select.selectByVisibleText(option.getText().trim());
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                throw new IllegalStateException(
                    "Could not select LMS posting status '" + status + "'."
                );
            }
        }

        wait.until(d -> status.equalsIgnoreCase(
            new Select(d.findElement(LMS_POSTING_STATUS)).getFirstSelectedOption().getText().trim()
        ));
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
    private Set<String> readUniqueFailureReasons() {
        Set<String> reasons = new LinkedHashSet<>();
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

                throw new IllegalStateException(
                    "FAILED receipt results were not available."
                );
            }

            for (int index = 0; index < rowCount; index++) {
                // Re-fetch rows each iteration to avoid stale element references
                List<WebElement> currentRows = visibleReceiptRows();

                if (index >= currentRows.size()) {
                    throw new IllegalStateException(
                        "FAILED receipt row disappeared while reading failure reason."
                    );
                }

                WebElement row = currentRows.get(index);
                WebElement icon = visibleInside(row, RECEIPT_ERROR_ICON);

                if (icon == null) {
                    throw new IllegalStateException(
                        "FAILED receipt row " + (index + 1)
                            + " does not contain the error icon."
                    );
                }

                boolean reasonCaptured = false;
                String reason = "";
                int maxAttempts = 3;

                for (int attempt = 0; attempt < maxAttempts && !reasonCaptured; attempt++) {
                    scrollIntoView(icon);
                    clickAndWait(icon);

                    // Wait for the menu to appear, with a generous timeout
                    reason = readFailureReason();

                    if (!reason.isBlank()) {
                        reasons.add(reason);
                        reasonCaptured = true;
                    } else {
                        // Menu may not have opened yet - retry the click
                        if (attempt < maxAttempts - 1) {
                            waitMillis(500);
                            // Re-fetch the icon in case of stale element
                            List<WebElement> refreshedRows = visibleReceiptRows();
                            if (index < refreshedRows.size()) {
                                icon = visibleInside(refreshedRows.get(index), RECEIPT_ERROR_ICON);
                            }
                        }
                    }
                }

                if (!reasonCaptured) {
                    throw new IllegalStateException(
                        "FAILED receipt row " + (index + 1)
                            + " opened the error menu, but no failure reason was captured after "
                            + maxAttempts + " attempts."
                    );
                }

                closeFailureReasonMenu();

                // Wait for any DOM changes to settle before continuing
                waitMillis(UI_PAUSE_MS);
            }

            // Re-fetch next page button to avoid stale element
            WebElement next = visibleElement(RECEIPT_NEXT_PAGE);

            if (next == null || !next.isEnabled()) {
                break;
            }

            String before = readPaginatorRange();
            clickAndWait(next);

            wait.until(d -> {
                String after = readPaginatorRange();
                return !after.isBlank() && !after.equals(before);
            });

            waitForReceiptResults();
        }

        return reasons;
    }

    private String readFailureReason() {
        return clean(wait.until(d -> {
            List<WebElement> menus = d.findElements(FAILURE_MENU);

            for (int i = menus.size() - 1; i >= 0; i--) {
                WebElement menu = menus.get(i);
                if (!isDisplayed(menu)) continue;

                String text = clean(menu.getText());
                if (!text.isBlank()) return text;
            }

            return null;
        }));
    }
    private void closeFailureReasonMenu() {
        // Escape closes the menu
        try {
            driver.switchTo().activeElement().sendKeys(Keys.ESCAPE);
        } catch (Exception ignored) {
        }

        wait.until(d ->
            d.findElements(FAILURE_MENU).stream().noneMatch(this::isDisplayed)
        );
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
                // Row may have become stale, skip it
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
                try {
                    ((JavascriptExecutor) driver).executeScript(
                        "document.querySelectorAll('mat-dialog-container, .cdk-overlay-backdrop, .cdk-overlay-container .cdk-global-overlay-wrapper').forEach(e => e.remove());"
                    );
                    waitMillis(200);
                } catch (Exception ignored) {}
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
        openJobsRouteDirectly();
        waitForJobsPage();
    }

    private void ensureJobsPage() {
        if (!isJobsPageLoaded()) {
            recoverToJobsPage();
        }
        if (!isJobsPageLoaded()) throw new IllegalStateException("Admin Jobs page is not available.");
    }

    private void openJobsRouteDirectly() {
        String configuredUrl = config.getURL();
        int hash = configuredUrl.indexOf('#');
        String base = hash >= 0 ? configuredUrl.substring(0, hash) : configuredUrl;
        String jobsUrl = base + "#/admin/job";
        if (!driver.getCurrentUrl().equalsIgnoreCase(jobsUrl)) {
            driver.navigate().to(jobsUrl);
        }
        waitForPageLoad();
    }

    private void waitForJobsPage() {
        wait.until(d -> {
            String url = d.getCurrentUrl().toLowerCase(Locale.ROOT);
            return url.contains("/admin/job") && !url.contains("/postreceipts") && !url.contains("/details/");
        });
        wait.until(d -> !visibleJobRows().isEmpty());
        waitMillis(UI_PAUSE_MS);
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
        String body = clean(driver.findElement(By.tagName("body")).getText()).toLowerCase(Locale.ROOT);
        return body.contains("no record") || body.contains("no records") || body.contains("no data");
    }

    private String readPaginatorRange() {
        for (WebElement e : driver.findElements(RECEIPT_PAGINATOR_RANGE)) {
            try {
                if (isDisplayed(e)) return clean(e.getText());
            } catch (Exception ignored) {
                // Element may have become stale, skip it
            }
        }
        return "";
    }

    private List<WebElement> visibleReceiptRows() {
        List<WebElement> result = new ArrayList<>();
        for (WebElement row : driver.findElements(RECEIPT_ROWS)) {
            try {
                if (isDisplayed(row) && !clean(row.getText()).isBlank()) result.add(row);
            } catch (Exception ignored) {
                // Row may have become stale, skip it
            }
        }
        return result;
    }

    private List<WebElement> visibleJobRows() {
        List<WebElement> result = new ArrayList<>();
        for (WebElement row : driver.findElements(JOB_ROWS)) {
            try {
                if (isDisplayed(row) && !clean(row.getText()).isBlank()) result.add(row);
            } catch (Exception ignored) {
                // Row may have become stale, skip it
            }
        }
        return result;
    }

    private WebElement requireJobRow(String jobName) {
        WebElement row = findJobRowOptional(jobName);
        if (row == null) throw new IllegalStateException("Job row not found for " + jobName + ".");
        return row;
    }

    private WebElement findJobRowOptional(String jobName) {
        List<String> aliases = JOB_ALIASES.getOrDefault(jobName, List.of(jobName));
        long deadline = System.currentTimeMillis() + 2000L;
        do {
            for (WebElement row : driver.findElements(JOB_ROWS)) {
                try {
                    if (!isDisplayed(row)) continue;
                    String text = normalize(row.getText());
                    for (String alias : aliases) {
                        if (text.contains(normalize(alias))) return row;
                    }
                } catch (Exception ignored) {
                    // Row may have become stale, skip it
                }
            }
            waitMillis(100);
        } while (System.currentTimeMillis() < deadline);
        return null;
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

    private String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private void waitMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread interrupted while waiting for application state.", e);
        }
    }

    private String receiptResultsSignature() {
        StringBuilder signature = new StringBuilder();

        for (WebElement row : visibleReceiptRows()) {
            signature.append(clean(row.getText()))
                     .append("||");
        }

        return signature.toString();
    }

    private static class ReceiptCapture {
        int totalCount;
        Set<String> reasons = new LinkedHashSet<>();
    }
}
