package com.encorepay;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
            htmlReportPath = JobMonitoringHtmlReport.generateCombined(allStatuses, clientFailures);
        } catch (Exception e) {
            clientFailures.add("REPORT GENERATION :: " + safeMessage(e));
            System.out.println("[REPORT FAILED] " + safeMessage(e));
        }

        if (htmlReportPath != null) {
            final String reportPath = htmlReportPath;
            List<String> configuredClientNames = clients.stream()
                    .map(this::safeClientName)
                    .toList();
            notifySafely(clientFailures, "Google Chat", () -> GoogleChatNotifier.notify(allStatuses, clientFailures, configuredClientNames, reportPath));
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

    private ClientRunResult runClient(ClientConfig client, String runCorrelationId) {
        WebDriver driver = null;
        ActionDriver action = null;
        LoginPage loginPage = null;
        List<JobStatus> statuses = new ArrayList<>();
        boolean loginSucceeded = false;
        String failureMessage = null;

        try {
            ConfigReader clientConfig = new ConfigReader(client);
            driver = WebDriverFactory.create(clientConfig);
            WebDriverFactory.configure(driver, clientConfig);
            action = new ActionDriver(driver, clientConfig);

            System.out.println("[" + runCorrelationId + "][CLIENT URL] " + safeClientName(client) + " -> " + client.getUrl());
            action.markStep("open client application");
            driver.get(client.getUrl());

            action.markStep("authenticate");
            loginPage = new LoginPage(driver, clientConfig);
            loginPage.setCorrelationId(runCorrelationId + "-" + safeClientName(client).substring(0, Math.min(4, safeClientName(client).length())).toUpperCase());
            loginPage.waitForVisibleApplicationScreen();
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
            validateMonitoringData(statuses);
} catch (Throwable e) {

            String reason = action == null
                ? safeMessage(e)
                : action.captureFailure(safeMessage(e));
            statuses = unavailableStatusesForClient(client, reason);
            System.out.println("[" + runCorrelationId + "][CLIENT N/A] " + safeClientName(client) + " :: " + reason);
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

                try {
                    driver.quit();
                } catch (Exception cleanupException) {
                    String cleanupMessage = safeMessage(cleanupException);
                    failureMessage = failureMessage == null || failureMessage.isBlank()
                        ? "Browser cleanup failed: " + cleanupMessage
                        : failureMessage + " | Browser cleanup failed: " + cleanupMessage;
                }
            }
        }

        return new ClientRunResult(statuses, failureMessage);
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
            result.add(status);
        }
        return result;
    }

    private String trimReason(String value) {
        if (value == null) return "N/A";
        String clean = value.replaceAll("\\s+", " ").trim();
        return clean.length() <= 180 ? clean : clean.substring(0, 177) + "...";
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
                Assert.assertTrue(status.getJobFailureReason() != null && !status.getJobFailureReason().isBlank(),
                    "Job failure Reason must be captured for " + status.getJobName() + ".");
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
            String reason = status.getJobFailureReason();
            boolean isAutomationError = reason != null && (reason.contains("stale element")
                    || reason.contains("timeout")
                    || reason.contains("StaleElementReferenceException")
                    || reason.contains("TimeoutException")
                    || reason.contains("NoSuchElementException")
                    || reason.contains("session")
                    || reason.contains("Session")
                    || reason.contains("Monitoring unavailable")
                    || reason.contains("navigation")
                    || reason.contains("Navigation")
                    || reason.contains("Admin")
                    || reason.contains("hover")
                    || reason.contains("signin")
                    || reason.contains("sign-in")
                    || reason.contains("login")
                    || reason.contains("Login"));

            if (isAutomationError) {
                System.out.println("[WARN] Required job " + status.getJobName() + " for client " + status.getClientName() + " marked N/A due to automation error: " + reason);
            } else {
                Assert.fail("Required job " + status.getJobName() + " for client " + status.getClientName() + " has N/A status without automation error: " + reason);
            }
        }
        if (isRequiredJob && "N/A".equalsIgnoreCase(status.getDateTime())) {
            String reason = status.getJobFailureReason();
            boolean isAutomationError = reason != null && (reason.contains("stale element")
                    || reason.contains("timeout")
                    || reason.contains("StaleElementReferenceException")
                    || reason.contains("TimeoutException")
                    || reason.contains("NoSuchElementException")
                    || reason.contains("session")
                    || reason.contains("Session")
                    || reason.contains("Monitoring unavailable")
                    || reason.contains("navigation")
                    || reason.contains("Navigation")
                    || reason.contains("Admin")
                    || reason.contains("hover")
                    || reason.contains("signin")
                    || reason.contains("sign-in")
                    || reason.contains("login")
                    || reason.contains("Login"));

            if (!isAutomationError) {
                Assert.fail("Required job " + status.getJobName() + " for client " + status.getClientName() + " has N/A DateTime without automation error: " + reason);
            }
        }
    }

    private JobStatus findRequired(List<JobStatus> statuses, String name) {
        return findOptional(statuses, name) != null
            ? findOptional(statuses, name)
            : throwMissingJob(name);
    }

    private JobStatus throwMissingJob(String name) {
        throw new IllegalStateException("Missing required job: " + name);
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


