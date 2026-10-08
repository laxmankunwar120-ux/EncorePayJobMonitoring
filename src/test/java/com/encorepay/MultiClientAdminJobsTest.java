package com.encorepay;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.encorepay.actiondriver.ActionDriver;
import com.encorepay.base.WebDriverFactory;
import com.encorepay.models.ClientConfig;
import com.encorepay.models.JobStatus;
import com.encorepay.notifiers.EmailNotifier;
import com.encorepay.notifiers.GoogleChatNotifier;
import com.encorepay.pages.AdminJobsPage;
import com.encorepay.pages.LoginPage;
import com.encorepay.utilities.ConfigReader;
import com.encorepay.utilities.JobMonitoringHtmlReport;
import com.encorepay.utilities.ScreenshotUtil;

public class MultiClientAdminJobsTest {

    @BeforeSuite(alwaysRun = true)
    public void setupSuite() {
        cleanOldArtifacts();
    }

    private static void cleanOldArtifacts() {
        System.out.println("[INIT] Cleaning old reports and screenshots before run...");
        ScreenshotUtil.cleanScreenshotsDirectory();
        JobMonitoringHtmlReport.cleanReportsDirectory();
    }

    @Test(priority = 1, description = "Monitor all configured EncorePay clients sequentially")
    public void monitorAllConfiguredClients() {
        String runCorrelationId = UUID.randomUUID().toString().substring(0, 8);
        System.out.println("[RUN START] Correlation ID: " + runCorrelationId);

        cleanOldArtifacts();

        ConfigReader baseConfig = new ConfigReader();
        List<ClientConfig> clients = resolveClients(baseConfig);

        List<JobStatus> allStatuses = new ArrayList<>();
        List<String> clientFailures = new ArrayList<>();

        System.out.println("[MULTI-CLIENT] Total configured clients: " + clients.size());
        for (ClientConfig client : clients) {
            System.out.println("[CLIENT CONFIGURED] " + safeClientName(client) + " -> " + client.getUrl()
                    + " | sso=" + client.isSso()
                    + " | credentials=" + (hasCredentials(client) ? "present" : "MISSING"));
        }

        for (ClientConfig client : clients) {
            String clientName = safeClientName(client);
            System.out.println("[CLIENT START] " + clientName);

            ClientRunResult result = runClient(client, runCorrelationId);
            allStatuses.addAll(result.statuses);

            if (result.failureMessage != null && !result.failureMessage.isBlank()) {
                clientFailures.add(clientName + " :: " + result.failureMessage);
                System.out.println("[CLIENT FAILED] " + clientName + " :: " + result.failureMessage);
            } else {
                System.out.println("[CLIENT COMPLETE] " + clientName);
            }
        }

        if (allStatuses.isEmpty() && clientFailures.isEmpty()) {
            clientFailures.add("MULTI-CLIENT RUN :: No monitoring result was produced.");
        }

        Set<String> monitoredClients = new LinkedHashSet<>();
        for (JobStatus status : allStatuses) {
            if (status.getClientName() != null && !status.getClientName().isBlank()) {
                monitoredClients.add(status.getClientName());
            }
        }

        List<String> neverMonitored = new ArrayList<>();
        for (ClientConfig client : clients) {
            if (!monitoredClients.contains(safeClientName(client))) {
                neverMonitored.add(safeClientName(client));
            }
        }

        if (!neverMonitored.isEmpty()) {
            List<String> uncovered = new ArrayList<>();

            for (String clientName : neverMonitored) {
                boolean alreadyReported = clientFailures.stream()
                        .anyMatch(failure -> failure.startsWith(clientName + " :: "));
                if (!alreadyReported) {
                    uncovered.add(clientName);
                }
            }

            if (!uncovered.isEmpty()) {
                String message = "These configured clients produced no monitoring result: "
                        + String.join(", ", uncovered)
                        + ". Check their CLIENT_N_URL secret, or CLIENT_URLS if that is the source in use.";
                clientFailures.add("CLIENT COVERAGE :: " + message);
                System.out.println("[CLIENT COVERAGE] " + message);
            }
        }

        String htmlReportPath = null;
        try {
            List<String> configuredClientNames = clients.stream()
                    .map(this::safeClientName)
                    .toList();
            htmlReportPath = JobMonitoringHtmlReport.generateCombined(allStatuses, clientFailures, configuredClientNames);
        } catch (Exception e) {
            clientFailures.add("REPORT GENERATION :: " + safeMessage(e));
            System.out.println("[REPORT FAILED] " + safeMessage(e));
        }

        if (htmlReportPath != null) {
            final String reportPath = htmlReportPath;
            List<String> notificationClientNames = clients.stream()
                    .map(this::safeClientName)
                    .toList();
            notifySafely(clientFailures, "Google Chat", () -> GoogleChatNotifier.notify(allStatuses, clientFailures, notificationClientNames, reportPath));
            notifySafely(clientFailures, "Email", () -> EmailNotifier.notify(allStatuses, reportPath));
        } else {
            clientFailures.add("NOTIFICATION :: Skipped because no HTML report could be generated.");
        }

        boolean anyClientMonitored = !allStatuses.isEmpty();

        if (!anyClientMonitored) {
            Assert.fail("No clients could be monitored. All " + clients.size() + " configured clients failed: " + String.join(" | ", clientFailures));
        }

        if (!clientFailures.isEmpty()) {
            System.out.println("[MULTI-CLIENT] Partial success: " + monitoredClients.size() + " of " + clients.size() + " clients monitored. Failures: " + String.join(" | ", clientFailures));
        } else {
            System.out.println("[MULTI-CLIENT] All " + clients.size() + " configured clients completed successfully.");
        }
    }

    private void notifySafely(List<String> clientFailures, String label, Runnable notifier) {
        try {
            notifier.run();
        } catch (Exception e) {
            clientFailures.add(label.toUpperCase() + " NOTIFICATION :: " + safeMessage(e));
            System.out.println("[" + label.toUpperCase() + " FAILED] " + safeMessage(e));
        }
    }

    private List<ClientConfig> resolveClients(ConfigReader baseConfig) {
        List<ClientConfig> clients = baseConfig.getClients();
        Assert.assertFalse(clients.isEmpty(), "No EncorePay clients are configured.");

        String runMode = baseConfig.getProperty("runMode", "multiple");

        if ("single".equalsIgnoreCase(runMode)) {
            List<ClientConfig> first = List.of(clients.get(0));
            System.out.println("[RUN MODE] SINGLE - Running only client.1: " + safeClientName(first.get(0)));
            return first;
        }

        if ("multiple".equalsIgnoreCase(runMode)) {
            System.out.println("[RUN MODE] MULTIPLE - Running all configured clients: " + clients.size());
            return clients;
        }

        throw new IllegalArgumentException(
                "Invalid runMode: " + runMode + ". Allowed values are single or multiple.");
    }

    /**
     * Runs one client with a bounded fresh-browser recovery pass. A browser/session
     * can become poisoned even when the website itself is healthy; retrying the
     * same Selenium state is not a reliable recovery strategy. The first pass is
     * always the source of truth, and a second pass is used only when required
     * monitoring data is incomplete or the monitoring stage failed.
     */
    private ClientRunResult runClient(ClientConfig client, String runCorrelationId) {
        ClientRunResult best = null;

        for (int attempt = 1; attempt <= 2; attempt++) {
            ClientRunResult current = runClientAttempt(client, runCorrelationId, attempt);
            if (best == null || monitoringCompleteness(current.statuses) > monitoringCompleteness(best.statuses)) {
                best = current;
            }

            if (isCompleteRequiredMonitoring(current.statuses)) {
                return current;
            }

            // Never retry an explicitly rejected credential or an already-classified
            // two-attempt login outage. Those are deterministic business outcomes.
            if (current.failureMessage != null && isTerminalClientFailure(current.failureMessage)) {
                return current;
            }

            if (attempt < 2) {
                System.out.println("[CLIENT RECOVERY] " + safeClientName(client)
                        + " monitoring result is incomplete; starting a fresh browser/session for attempt 2/2.");
            }
        }

        return best == null
                ? new ClientRunResult(unavailableStatusesForClient(client, "Monitoring data could not be captured after recovery attempts."),
                        "Monitoring data could not be captured after recovery attempts.")
                : best;
    }

    private ClientRunResult runClientAttempt(ClientConfig client, String runCorrelationId, int attemptNumber) {
        WebDriver driver = null;
        ActionDriver action = null;
        LoginPage loginPage = null;
        List<JobStatus> statuses = new ArrayList<>();
        boolean loginSucceeded = false;
        String failureMessage = null;

        try {
            System.out.println("[CLIENT ATTEMPT] " + safeClientName(client) + " :: fresh browser attempt " + attemptNumber + "/2");
            ConfigReader clientConfig = new ConfigReader(client);
            driver = WebDriverFactory.create(clientConfig);
            WebDriverFactory.configure(driver, clientConfig);
            action = new ActionDriver(driver, clientConfig);

            System.out.println("[" + runCorrelationId + "][CLIENT URL] " + safeClientName(client) + " -> " + client.getUrl());
            action.markStep("open client application");
            openClientSafely(driver, client.getUrl(), clientConfig);

            action.markStep("authenticate");
            loginPage = new LoginPage(driver, clientConfig);
            loginPage.setCorrelationId(runCorrelationId + "-" + safeClientName(client).substring(0, Math.min(4, safeClientName(client).length())).toUpperCase());
            // LoginPage owns login readiness/retry handling. Do not run the generic
            // application bootstrap here; an unavailable client must reach the
            // two-attempt SERVER_DOWN policy instead of leaking a raw TimeoutException.
            if (client.isSso()) {
                loginPage.ssoLogin(client.getUsername(), client.getPassword());
            } else {
                loginPage.login(client.getUsername(), client.getPassword());
            }
            loginSucceeded = true;

            action.markStep("navigate to Admin Jobs");
            AdminJobsPage adminJobsPage = new AdminJobsPage(driver, clientConfig);
            adminJobsPage.setCorrelationId(runCorrelationId + "-" + safeClientName(client).substring(0, Math.min(4, safeClientName(client).length())).toUpperCase());
            adminJobsPage.navigateToAdminJobs();

            if (!adminJobsPage.isJobsPageLoaded()) {
                throw new IllegalStateException("Admin Jobs page did not load.");
            }

            action.markStep("monitor configured jobs");
            statuses = adminJobsPage.monitorAllConfiguredJobs();

            // Monitoring is read-only. If a transient page-load/session problem
            // caused all required jobs to become unavailable, recover the jobs
            // page and perform one bounded second pass instead of publishing
            // false N/A data from a single transient UI failure.
            if (requiredResultsUnavailable(statuses)) {
                System.out.println("[CLIENT RETRY] " + safeClientName(client)
                        + " produced no trustworthy required-job results; retrying monitoring once after UI recovery.");
                try {
                    adminJobsPage.navigateToAdminJobs();
                    statuses = adminJobsPage.monitorAllConfiguredJobs();
                } catch (Exception retryError) {
                    System.out.println("[CLIENT RETRY] " + safeClientName(client)
                            + " second monitoring pass failed: " + safeMessage(retryError));
                }
            }

            try {
                validateMonitoringData(statuses);
            } catch (Throwable validationError) {
                // Validation must never replace valid per-job UI data with client-wide N/A.
                // Keep the captured job statuses so the manager report remains truthful.
                failureMessage = "Monitoring validation warning: " + safeMessage(validationError);
                System.out.println("[" + runCorrelationId + "][CLIENT VALIDATION WARNING] "
                        + safeClientName(client) + " :: " + failureMessage);
            }

            // A monitoring pass that returns an N/A required job without a usable
            // timestamp is incomplete data, not a successful monitoring run. The
            // outer client recovery loop will start one fresh browser pass.
            if (!isCompleteRequiredMonitoring(statuses) && (failureMessage == null || failureMessage.isBlank())) {
                failureMessage = "Monitoring data could not be completely captured after UI recovery attempts.";
            }
        } catch (Throwable e) {

            String reason;
            if (!loginSucceeded && !isExplicitLoginRejection(e)
                    && (isServerDownLoginFailure(e) || isLoginStageFailure(action) || isApplicationUnavailable(driver))) {
                // Two complete login attempts without an explicit credential
                // rejection mean the application could not complete the login
                // transition. Keep the report business-facing and unambiguous.
                reason = "Server is down";
                System.out.println("[CLIENT SERVER DOWN] " + safeClientName(client)
                        + " :: login did not complete after 2 attempts.");
            } else {
                reason = action == null
                    ? safeMessage(e)
                    : action.captureFailure(safeMessage(e));
            }
            // Preserve the failure independently of whether placeholders are created.
            // A client with zero usable results must never look like a clean client just
            // because the exception happened before a JobStatus object was produced.
            failureMessage = reason;
            if (statuses == null || statuses.isEmpty()) {
                statuses = unavailableStatusesForClient(client, reason);
            }
            System.out.println("[" + runCorrelationId + "][CLIENT MONITORING WARNING] " + safeClientName(client) + " :: " + reason);
        } finally {
            if (driver != null) {
                if (loginSucceeded && loginPage != null && !loginPage.isLoginPageVisible()) {
                    if (client.isSso()) {
                        System.out.println("[" + runCorrelationId + "][CLIENT LOGOUT] Skipping logout for SSO client — SSO session persists and auto-login would occur.");
                    } else {
                        try {
                            logoutAndConfirmSignIn(driver, loginPage, new ConfigReader(client), client.isSso());
                        } catch (Exception cleanupException) {
                            System.out.println("[" + runCorrelationId + "][WARN] Logout cleanup note: " + safeMessage(cleanupException));
                        }
                    }
                }

                quitBrowserSafely(driver);
            }
        }

        return new ClientRunResult(statuses, failureMessage);
    }

    /**
     * Robust browser cleanup for every client, including SSO clients such as
     * sarvagram. For SSO clients the application logout is intentionally
     * skipped (the SSO session persists), so the browser is left on the
     * authenticated page. Authenticated pages commonly register a
     * beforeunload handler that can leave an orphaned browser window behind
     * after driver.quit(). Non-SSO clients avoid this because logout
     * navigates to the clean sign-in page first. This method neutralizes the
     * beforeunload handler, closes every open window/tab (including any SSO
     * popup or extra tab), and then quits the driver so the browser always
     * closes regardless of client type.
     */
    private void quitBrowserSafely(WebDriver driver) {
        if (driver == null) {
            return;
        }

        // Neutralize beforeunload handlers registered by authenticated pages
        // so they cannot block window closure during quit.
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "try { window.onbeforeunload = null; } catch (e) {}");
        } catch (Exception ignored) {
        }

        // Close every open window/tab so SSO popups or extra tabs opened
        // during the SSO flow do not survive the main window close.
        try {
            for (String handle : driver.getWindowHandles()) {
                try {
                    driver.switchTo().window(handle);
                    driver.close();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        try {
            driver.quit();
        } catch (Exception cleanupException) {
            System.out.println("[WARN] Browser quit cleanup note: " + safeMessage(cleanupException));
        }
    }

    private void logoutAndConfirmSignIn(
        WebDriver driver,
        LoginPage loginPage,
        ConfigReader config,
        boolean ssoEnabled
    ) {
        if (!loginPage.isLoginPageVisible() && !loginPage.isSsoPageVisible()) {
            System.out.println("[CLIENT LOGOUT] Logging out...");
            if (ssoEnabled) {
                loginPage.ssoLogout();
            } else {
                loginPage.logout();
            }
        }

        new WebDriverWait(
            driver,
            Duration.ofSeconds(Math.max(10, config.getExplicitWait()))
        ).until(d -> loginPage.isLoginPageVisible()
            || loginPage.isSsoPageVisible()
            || loginPage.isLoginSuccessful());

        System.out.println("[CLIENT LOGOUT] Sign-in state confirmed.");
    }

    private int monitoringCompleteness(List<JobStatus> statuses) {
        if (statuses == null) return 0;
        int score = 0;
        for (JobStatus status : statuses) {
            if (status == null) continue;
            String name = status.getJobName();
            if ("Post Receipts Job".equalsIgnoreCase(name)
                    || "Encore Download Collection Items Job".equalsIgnoreCase(name)) {
                boolean hasStatus = status.getStatus() != null && !status.getStatus().isBlank();
                boolean hasDate = status.getDateTime() != null && !status.getDateTime().isBlank();
                if (hasStatus) score++;
                if (hasDate && !"N/A".equalsIgnoreCase(status.getDateTime())) score++;
            }
        }
        return score;
    }

    private boolean isCompleteRequiredMonitoring(List<JobStatus> statuses) {
        if (statuses == null) return false;
        JobStatus post = findOptional(statuses, "Post Receipts Job");
        JobStatus collection = findOptional(statuses, "Encore Download Collection Items Job");
        return isUsableRequiredJob(post) && isUsableRequiredJob(collection);
    }

    private boolean isUsableRequiredJob(JobStatus status) {
        if (status == null) return false;
        String rawStatus = status.getStatus() == null ? "" : status.getStatus().trim();
        String date = status.getDateTime() == null ? "" : status.getDateTime().trim();
        return !rawStatus.isBlank() && !date.isBlank() && !"N/A".equalsIgnoreCase(date);
    }

    private boolean isTerminalClientFailure(String failureMessage) {
        String value = failureMessage == null ? "" : failureMessage.toLowerCase(Locale.ROOT);
        return value.contains("server is down")
                || value.contains("login_rejected")
                || value.contains("invalid username")
                || value.contains("invalid password")
                || value.contains("requires mfa/otp");
    }

    private void openClientSafely(WebDriver driver, String url, ConfigReader config) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                driver.get(url);
                // A full document load is useful, but SPA readiness is determined by
                // LoginPage. Do not add another long bootstrap wait here.
                new WebDriverWait(driver, Duration.ofSeconds(Math.max(5, Math.min(15, config.getExplicitWait()))))
                        .until(d -> d.getCurrentUrl() != null && !d.getCurrentUrl().isBlank());
                return;
            } catch (org.openqa.selenium.TimeoutException e) {
                last = e;
                // Chrome can report page-load timeout while the SPA shell is already
                // reachable. Let LoginPage inspect the real application state instead
                // of converting that browser-level timeout into a false server outage.
                try {
                    String current = driver.getCurrentUrl();
                    if (current != null && !current.isBlank()) {
                        System.out.println("[WARN] Initial navigation reached " + current
                                + " but exceeded page-load timeout; continuing with application-state detection.");
                        return;
                    }
                } catch (RuntimeException ignored) {
                }
                System.out.println("[WARN] Initial navigation attempt " + attempt + "/2 timed out; allowing LoginPage to perform the bounded authentication recovery.");
                if (attempt < 2) {
                    try {
                        driver.navigate().refresh();
                    } catch (RuntimeException refreshError) {
                        last = refreshError;
                    }
                }
            }
        }
        throw new IllegalStateException("Client application could not be opened after 2 navigation attempts.", last);
    }

    private List<JobStatus> unavailableStatusesForClient(ClientConfig client, String reason) {
        String clientName = safeClientName(client);
        String cleanReason = trimReason(reason);
        List<JobStatus> result = new ArrayList<>();

        for (String jobName : List.of("Post Receipts Job", "Encore Download Collection Items Job")) {
            JobStatus status = new JobStatus();
            status.setClientName(clientName);
            status.setJobName(jobName);
            status.setStatus("N/A");
            status.setDateTime("N/A");
            status.setJobFailureReason(cleanReason);
            status.setSynthetic(true);
            result.add(status);
        }
        return result;
    }

    private String trimReason(String value) {
        if (value == null) return "N/A";
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() <= 180 ? clean : clean.substring(0, 177) + "...";
    }

    private boolean requiredResultsUnavailable(List<JobStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) return true;
        boolean postAvailable = false;
        boolean collectionAvailable = false;
        for (JobStatus status : statuses) {
            if (status == null) continue;
            if ("Post Receipts Job".equalsIgnoreCase(status.getJobName())) {
                postAvailable = !status.isNotRun() && !status.getRawStatus().isBlank();
            } else if ("Encore Download Collection Items Job".equalsIgnoreCase(status.getJobName())) {
                collectionAvailable = !status.isNotRun() && !status.getRawStatus().isBlank();
            }
        }
        return !postAvailable && !collectionAvailable;
    }

    private void validateMonitoringData(List<JobStatus> statuses) {
        Assert.assertNotNull(statuses, "Job monitoring data must not be null.");
        Assert.assertTrue(statuses.size() >= 2 && statuses.size() <= 3, "Expected two required jobs and at most one optional Upcoming Demand job.");

        validate(findRequired(statuses, "Post Receipts Job"));
        validate(findRequired(statuses, "Encore Download Collection Items Job"));

        JobStatus upcoming = findOptional(statuses, "Encore Up Coming Demands Job");
        if (upcoming != null) {
            validate(upcoming);
        }

        JobStatus post = findRequired(statuses, "Post Receipts Job");
        if (post.getFailedCount() > 0) {

            boolean reasonsCaptured = !post.getFailureReasons().isEmpty();
            boolean gapExplained = post.getJobFailureReason() != null
                    && post.getJobFailureReason().contains("Receipt capture incomplete");
            Assert.assertTrue(
                    reasonsCaptured || gapExplained,
                    "Failed receipts were found but no reason or capture-gap note was recorded.");
        }

        for (JobStatus status : statuses) {
            if (status != null && status.getStatus() != null
                && status.getStatus().toUpperCase().contains("FAIL")) {
                if (status.getJobFailureReason() == null || status.getJobFailureReason().isBlank()) {
                    // Validation must never discard all valid job results for a client.
                    // AdminJobsPage records a capture note when the UI Reason field is
                    // unavailable, so retain the status and let the report show it.
                    System.out.println("[VALIDATION WARN] Job failure Reason was not available for "
                            + status.getJobName() + ".");
                }
            }
        }
    }

    private void validate(JobStatus status) {
        Assert.assertTrue(status.getClientName() != null && !status.getClientName().isBlank(), "Client name must be captured for " + status.getJobName());
        Assert.assertTrue(status.getStatus() != null && !status.getStatus().isBlank(), "Execution status must be captured for " + status.getJobName());
        Assert.assertTrue(status.getDateTime() != null && !status.getDateTime().isBlank(), "Execution End Date/Time must be captured for " + status.getJobName());

        boolean isUpcomingDemand = "Encore Up Coming Demands Job".equalsIgnoreCase(status.getJobName());
        boolean isRequiredJob = !isUpcomingDemand;

        if (isRequiredJob && "N/A".equalsIgnoreCase(status.getStatus())) {
            Assert.assertTrue(isAutomationError(status.getJobFailureReason()),
                    "Required job " + status.getJobName() + " for client " + status.getClientName()
                    + " has N/A status without automation error: " + status.getJobFailureReason());
            System.out.println("[WARN] Required job " + status.getJobName() + " for client " + status.getClientName() + " marked N/A due to automation error: " + status.getJobFailureReason());
        }
        if (isRequiredJob && "N/A".equalsIgnoreCase(status.getDateTime())) {
            Assert.assertTrue(isAutomationError(status.getJobFailureReason()),
                    "Required job " + status.getJobName() + " for client " + status.getClientName()
                    + " has N/A DateTime without automation error: " + status.getJobFailureReason());
        }
    }

    private boolean isAutomationError(String reason) {
        if (reason == null || reason.isBlank()) return false;
        String lower = reason.toLowerCase(Locale.ROOT);
        return lower.contains("stale element")
                || lower.contains("timeout")
                || lower.contains("nosuchelementexception")
                || lower.contains("session")
                || lower.contains("monitoring unavailable")
                || lower.contains("navigation")
                || lower.contains("admin")
                || lower.contains("hover")
                || lower.contains("signin")
                || lower.contains("sign-in")
                || lower.contains("login");
    }

    private JobStatus findRequired(List<JobStatus> statuses, String name) {
        JobStatus found = findOptional(statuses, name);
        if (found == null) {
            throw new IllegalStateException("Missing required job: " + name);
        }
        return found;
    }

    private JobStatus findOptional(List<JobStatus> statuses, String name) {
        return statuses == null ? null : statuses.stream()
            .filter(x -> x != null && name.equalsIgnoreCase(x.getJobName()))
            .findFirst()
            .orElse(null);
    }

    private boolean hasCredentials(ClientConfig client) {
        return client.getUsername() != null && !client.getUsername().isBlank()
                && client.getPassword() != null && !client.getPassword().isBlank();
    }

    private String safeClientName(ClientConfig client) {
        if (client == null) return "Unknown Client";
        String name = client.getDisplayName();
        return name == null || name.isBlank() ? "Unknown Client" : name;
    }

    private boolean isApplicationUnavailable(WebDriver driver) {
        if (driver == null) return false;
        try {
            String url = driver.getCurrentUrl();
            String title = driver.getTitle();
            String source = driver.getPageSource();
            String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
            String s = source == null ? "" : source.toLowerCase(Locale.ROOT);
            String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
            return t.contains("404 not found") || s.contains("404 not found")
                    || s.contains("nginx/") && s.contains("404")
                    || t.contains("502 bad gateway") || s.contains("502 bad gateway")
                    || t.contains("503 service unavailable") || s.contains("503 service unavailable")
                    || t.contains("504 gateway timeout") || s.contains("504 gateway timeout")
                    || (u.contains("/signin") && (t.contains("not found") || s.contains("nginx/")));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isLoginStageFailure(ActionDriver action) {
        if (action == null) return false;
        String step = action.currentStep();
        return step != null && step.toLowerCase(Locale.ROOT).contains("auth");
    }

    private boolean isExplicitLoginRejection(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("LOGIN_REJECTED:")
                    || message.toLowerCase(Locale.ROOT).contains("requires mfa/otp"))) return true;
            current = current.getCause();
        }
        return false;
    }

    private boolean isServerDownLoginFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("SERVER_DOWN:")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String safeMessage(Throwable e) {
        if (e == null) return "Unknown error.";
        String message = e.getMessage();
        String type = e.getClass().getSimpleName();
        return message == null || message.isBlank() ? type : type + ": " + message;
    }

    private static final class ClientRunResult {
        private final List<JobStatus> statuses;
        private final String failureMessage;

        private ClientRunResult(List<JobStatus> statuses, String failureMessage) {
            this.statuses = statuses == null ? new ArrayList<>() : new ArrayList<>(statuses);
            this.failureMessage = failureMessage;
        }
    }
}


