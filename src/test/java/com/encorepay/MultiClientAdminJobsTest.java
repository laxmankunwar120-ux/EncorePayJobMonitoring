package com.encorepay;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("test-output", "google-chat-sent.marker"));
        } catch (Exception e) {
            System.out.println("[WARN] Could not clear Google Chat delivery marker: " + e.getMessage());
        }
    }

    @Test(priority = 1, description = "Monitor all configured EncorePay clients sequentially")
    public void monitorAllConfiguredClients() {
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

            ClientRunResult result = runClient(client);
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

        List<String> configuredClientNames = clients.stream()
                .map(this::safeClientName)
                .toList();

        if (htmlReportPath != null) {
            final String reportPath = htmlReportPath;
            notifySafely(clientFailures, "Google Chat",
                    () -> GoogleChatNotifier.notify(allStatuses, clientFailures, configuredClientNames, reportPath));
            notifySafely(clientFailures, "Email", () -> EmailNotifier.notify(allStatuses, reportPath));
        } else {
            clientFailures.add("NOTIFICATION :: No HTML report was generated.");
        }

        if (!clientFailures.isEmpty()) {
            Assert.fail("One or more clients failed: " + String.join(" | ", clientFailures));
        }

        System.out.println("[MULTI-CLIENT] All configured clients completed successfully.");
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
            return disambiguateDuplicateClientNames(first);
        }

        if ("multiple".equalsIgnoreCase(runMode)) {
            System.out.println("[RUN MODE] MULTIPLE - Running all configured clients: " + clients.size());
            return disambiguateDuplicateClientNames(clients);
        }

        throw new IllegalArgumentException(
                "Invalid runMode: " + runMode + ". Allowed values are single or multiple.");
    }

    private List<ClientConfig> disambiguateDuplicateClientNames(List<ClientConfig> clients) {
        Map<String, Integer> seen = new LinkedHashMap<>();
        List<ClientConfig> result = new ArrayList<>();

        for (ClientConfig client : clients) {
            String name = safeClientName(client);
            int count = seen.merge(name, 1, Integer::sum);

            if (count == 1) {
                result.add(client);
                continue;
            }

            String unique = name + " (" + hostOf(client) + ")";
            System.out.println("[WARN] Duplicate client name '" + name + "' - reporting as '" + unique + "'.");
            result.add(new ClientConfig(
                    unique, client.getUrl(), client.getUsername(), client.getPassword(), client.isSso()));
        }

        return result;
    }

    private String hostOf(ClientConfig client) {
        try {
            String host = URI.create(client.getUrl()).getHost();
            if (host != null && !host.isBlank()) {
                return host;
            }
        } catch (Exception ignored) {
        }
        return "client " + Integer.toHexString(System.identityHashCode(client));
    }

    private ClientRunResult runClient(ClientConfig client) {
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

            System.out.println("[CLIENT URL] " + safeClientName(client) + " -> " + client.getUrl());
            action.markStep("open client application");
            driver.get(client.getUrl());

            action.markStep("authenticate");
            loginPage = new LoginPage(driver, clientConfig);
            loginPage.waitForVisibleApplicationScreen();
            if (client.isSso()) {
                loginPage.ssoLogin(client.getUsername(), client.getPassword());
            } else {
                loginPage.login(client.getUsername(), client.getPassword());
            }
            loginSucceeded = true;

            action.markStep("navigate to Admin Jobs");
            AdminJobsPage adminJobsPage = new AdminJobsPage(driver, clientConfig);
            adminJobsPage.navigateToAdminJobs();

            if (!adminJobsPage.isJobsPageLoaded()) {
                throw new IllegalStateException("Admin Jobs page did not load.");
            }

            action.markStep("monitor configured jobs");
            statuses = adminJobsPage.monitorAllConfiguredJobs();
            validateMonitoringData(statuses, clientNameOf(client), clientFailuresForValidation(statuses));
        } catch (Exception e) {
            failureMessage = action == null
                ? safeMessage(e)
                : action.captureFailure(safeMessage(e));
        } finally {
            if (driver != null) {
                if (loginSucceeded && loginPage != null && !loginPage.isLoginPageVisible()) {
                    if (client.isSso()) {
                        System.out.println("[CLIENT LOGOUT] Skipping logout for SSO client — SSO session persists and auto-login would occur.");
                    } else {
                        try {
                            logoutAndConfirmSignIn(driver, loginPage, new ConfigReader(client), client.isSso());
                        } catch (Exception cleanupException) {
                            System.out.println("[WARN] Logout cleanup note: " + safeMessage(cleanupException));
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

    private String clientNameOf(ClientConfig client) {
        return safeClientName(client);
    }

    private List<String> clientFailuresForValidation(List<JobStatus> statuses) {
        List<String> issues = new ArrayList<>();
        JobStatus post = findOptional(statuses, "Post Receipts Job");
        if (post != null && post.getFailedCount() > 0
                && (post.getFailureReasons() == null || post.getFailureReasons().isEmpty())
                && (post.getJobFailureReason() == null || post.getJobFailureReason().isBlank())) {
            issues.add("Failed receipts were found but no receipt-level failure reason was captured.");
        }
        return issues;
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

    private void validateMonitoringData(List<JobStatus> statuses, String clientName, List<String> captureIssues) {
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
            int failedReceipts = post.getFailedCount();
            int capturedReasonRecords = post.getFailureReasonRecordCount();

            if (capturedReasonRecords == 0) {
                appendReceiptCaptureIssue(post,
                        "Failed receipts were found, but no receipt-level failure reason was captured.");
            } else if (capturedReasonRecords != failedReceipts) {
                appendReceiptCaptureIssue(post,
                        "Receipt failure reason capture is incomplete: failed receipts = "
                                + failedReceipts + ", reason records captured = " + capturedReasonRecords + ".");
            }
        }

        for (JobStatus status : statuses) {
            if (status != null && status.getStatus() != null
                && status.getStatus().toUpperCase().contains("FAIL")) {
                Assert.assertTrue(status.getJobFailureReason() != null && !status.getJobFailureReason().isBlank(),
                    "Job failure Reason must be captured for " + status.getJobName() + ".");
            }
        }
    }

    private void appendReceiptCaptureIssue(JobStatus status, String issue) {
        String existing = status.getJobFailureReason();
        if (existing == null || existing.isBlank()) {
            status.setJobFailureReason(issue);
        } else if (!existing.contains(issue)) {
            status.setJobFailureReason(existing + "; " + issue);
        }
        System.out.println("[WARN] " + status.getClientName() + " :: " + issue);
    }

    private void validate(JobStatus status) {
        Assert.assertTrue(status.getClientName() != null && !status.getClientName().isBlank(), "Client name must be captured for " + status.getJobName());
        Assert.assertTrue(status.getStatus() != null && !status.getStatus().isBlank(), "Execution status must be captured for " + status.getJobName());
        Assert.assertTrue(status.getDateTime() != null && !status.getDateTime().isBlank(), "Execution End Date/Time must be captured for " + status.getJobName());
        Assert.assertFalse("NOT CAPTURED".equalsIgnoreCase(status.getStatus()), "Execution status was not captured for " + status.getJobName());
        Assert.assertFalse("NOT CAPTURED".equalsIgnoreCase(status.getDateTime()), "Execution End Date/Time was not captured for " + status.getJobName());
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
