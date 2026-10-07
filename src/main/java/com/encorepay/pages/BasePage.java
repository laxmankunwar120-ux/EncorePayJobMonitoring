package com.encorepay.pages;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.encorepay.actiondriver.ActionDriver;
import com.encorepay.utilities.ConfigReader;
import com.encorepay.utilities.RetryUtils;

public class BasePage {


    protected static final By APPLICATION_NAVIGATION = By.xpath(
        "//nav[.//button[contains(@class,'menu-btn')]]"
            + " | //button[normalize-space()='Dashboard']"
            + " | //button[normalize-space()='Admin'] | //button[normalize-space()='Collections']"
            + " | //a[normalize-space()='Dashboard'] | //a[normalize-space()='Admin']"
            + " | //a[normalize-space()='Collections']");
    protected static final By APPLICATION_LOGIN = By.xpath(
        "//app-login//input | //input[@name='username' or @formcontrolname='username']"
            + " | //app-login//button[normalize-space()='Sign in with SSO']"
            + " | //button[normalize-space()='Log In']");

    protected final WebDriver driver;
    protected final WebDriverWait wait;
    protected final WebDriverWait shortWait;
    protected final WebDriverWait bootWait;
    protected final ConfigReader config;
    protected final ActionDriver action;

    public BasePage(WebDriver driver) {
        this(driver, new ConfigReader());
    }

    public BasePage(WebDriver driver, ConfigReader config) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(config.getExplicitWait()));
        this.shortWait = new WebDriverWait(driver, Duration.ofSeconds(5));
        this.bootWait = new WebDriverWait(driver, Duration.ofSeconds(config.getBootTimeout()));
        this.config = config;
        this.action = new ActionDriver(driver, config);
        this.correlationId = new AtomicReference<>(UUID.randomUUID().toString().substring(0, 8));
        PageFactory.initElements(driver, this);
    }

    private final AtomicReference<String> correlationId;

    public String getCorrelationId() {
        return correlationId.get();
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId.set(correlationId);
    }

    protected void log(String message) {
        System.out.println("[" + correlationId + "] " + message);
    }

    protected void logWarn(String message) {
        System.out.println("[WARN][" + correlationId + "] " + message);
    }

    protected void logError(String message) {
        System.err.println("[ERROR][" + correlationId + "] " + message);
    }

    protected void awaitAppBootstrap() {
        bootWait.until(d -> {
            String accessFailure = detectApplicationAccessFailure(d);
            if (!accessFailure.isBlank()) {
                throw new IllegalStateException(accessFailure);
            }
            return documentComplete(d) && hasApplicationIdentity(d) && isVisibleApplicationScreen();
        });
    }

    public void waitForVisibleApplicationScreen() {
        awaitAppBootstrap();
    }

    private String detectApplicationAccessFailure(WebDriver d) {
        try {
            String title = d.getTitle();
            String source = d.getPageSource();
            String normalizedTitle = title == null ? "" : title.toLowerCase(Locale.ROOT);
            String normalizedSource = source == null ? "" : source.toLowerCase(Locale.ROOT);

            if (normalizedTitle.contains("403") || normalizedTitle.contains("forbidden")
                    || normalizedSource.contains("403 forbidden")) {
                return "Application access was rejected with HTTP 403 Forbidden. "
                        + "URL: " + d.getCurrentUrl()
                        + " | title: " + title;
            }

            if (normalizedTitle.contains("access denied")
                    || normalizedSource.contains("access denied")) {
                return "Application access was rejected with an Access Denied page. "
                        + "URL: " + d.getCurrentUrl()
                        + " | title: " + title;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    protected boolean isVisibleApplicationScreen() {
        return anyVisible(APPLICATION_LOGIN) || isAuthenticatedApplicationVisible();
    }

    protected boolean isAuthenticatedApplicationVisible() {
        return anyVisible(APPLICATION_NAVIGATION);
    }

    protected boolean anyVisible(By locator) {
        try {
            for (WebElement element : driver.findElements(locator)) {
                try {
                    if (element.isDisplayed()) return true;
                } catch (StaleElementReferenceException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean documentComplete(WebDriver d) {
        try {
            Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
            return "complete".equalsIgnoreCase(String.valueOf(state));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean hasApplicationIdentity(WebDriver d) {
        try {
            String url = d.getCurrentUrl();
            String title = d.getTitle();
            if (url == null || url.isBlank() || !url.toLowerCase(Locale.ROOT).startsWith("http")) {
                return false;
            }
            String expectedHost = config.getURL() == null ? "" : config.getURL().toLowerCase(Locale.ROOT);
            String current = url.toLowerCase(Locale.ROOT);
            String pageTitle = title == null ? "" : title.toLowerCase(Locale.ROOT);
            return pageTitle.contains("encore") || (!expectedHost.isBlank() && current.startsWith(expectedHost.split("#", 2)[0]));
        } catch (Exception ignored) {
            return false;
        }
    }

    protected void validateSessionAndWindow() {
        validateSessionAndWindow(3);
    }

    protected void validateSessionAndWindow(int maxRecoveryAttempts) {
        for (int attempt = 1; attempt <= maxRecoveryAttempts; attempt++) {
            try {
                String url = driver.getCurrentUrl();
                String title = driver.getTitle();
                String windowHandle = driver.getWindowHandle();
                log("Session valid: url=" + url + ", title=" + title + ", handle=" + windowHandle);
                return;
            } catch (NoSuchWindowException e) {
                logWarn("Window lost on attempt " + attempt + "/" + maxRecoveryAttempts + ": " + e.getMessage());
                if (attempt < maxRecoveryAttempts) {
                    recoverWindow();
                } else {
                    throw new IllegalStateException("Cannot recover browser window after " + maxRecoveryAttempts + " attempts", e);
                }
            } catch (Exception e) {
                if (isSessionLost(e)) {
                    logWarn("Session lost on attempt " + attempt + "/" + maxRecoveryAttempts + ": " + e.getMessage());
                    if (attempt < maxRecoveryAttempts) {
                        recoverSession();
                    } else {
                        throw new IllegalStateException("Cannot recover browser session after " + maxRecoveryAttempts + " attempts", e);
                    }
                } else {
                    throw e;
                }
            }
        }
    }

    private boolean isSessionLost(Throwable e) {
        if (e == null) return false;
        String message = e.getMessage();
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("session")
                || lower.contains("no such window")
                || lower.contains("session deleted")
                || lower.contains("session not found")
                || lower.contains("invalid session")
                || lower.contains("disconnected");
    }

    private void recoverWindow() {
        try {
            String originalHandle = null;
            try {
                originalHandle = driver.getWindowHandle();
            } catch (Exception ignored) {}
            
            for (String handle : driver.getWindowHandles()) {
                if (!handle.equals(originalHandle)) {
                    driver.switchTo().window(handle);
                    log("Switched to window: " + handle);
                    return;
                }
            }
            
            if (originalHandle != null) {
                driver.switchTo().window(originalHandle);
                log("Reverted to original window: " + originalHandle);
            }
        } catch (Exception e) {
            logWarn("Window recovery failed: " + e.getMessage());
        }
    }

    private void recoverSession() {
        try {
            driver.get(config.getURL());
            awaitAppBootstrap();
            log("Session recovered by navigating to base URL");
        } catch (Exception e) {
            logWarn("Session recovery failed: " + e.getMessage());
        }
    }

    protected void waitForVisibility(WebElement element) {
        wait.until(ExpectedConditions.visibilityOf(element));
    }

    protected void waitForClickability(WebElement element) {
        wait.until(ExpectedConditions.elementToBeClickable(element));
    }

    protected void click(WebElement element) {
        waitForClickability(element);
        element.click();
    }

    protected boolean isDisplayed(WebElement element) {
        try {
            return element != null && element.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    protected boolean isDisplayed(By locator) {
        try {
            return driver.findElements(locator).stream().anyMatch(this::isDisplayed);
        } catch (Exception e) {
            return false;
        }
    }

    protected WebElement visible(WebElement element) {
        return wait.until(ExpectedConditions.visibilityOf(element));
    }

    protected WebElement clickable(WebElement element) {
        return wait.until(ExpectedConditions.elementToBeClickable(element));
    }

    protected void type(WebElement element, String value) {
        action.clearAndType(element, value == null ? "" : value);
    }

    protected void type(By locator, String value) {
        action.type(locator, value == null ? "" : value);
    }

    protected void scrollIntoView(WebElement element) {
        action.scrollToElement(element);
    }

    protected void waitForPageLoad() {
        action.waitForUiStable();
    }

    protected void waitForUiStable() {
        action.waitForUiStable();
    }

    protected void waitForTransientFeedbackToClear() {
        action.waitForTransientFeedbackToClear();
    }

    protected <T> T retry(Function<WebDriver, T> operation) {
        return RetryUtils.retry(() -> operation.apply(driver));
    }

    protected <T> T retry(Function<WebDriver, T> operation, int maxAttempts) {
        return RetryUtils.retry(() -> operation.apply(driver), maxAttempts);
    }

    protected void retry(Runnable operation) {
        RetryUtils.run(operation);
    }

    protected void retry(Runnable operation, int maxAttempts) {
        RetryUtils.run(operation, maxAttempts);
    }

    protected WebElement findVisibleWithRetry(By locator) {
        return action.findVisible(locator);
    }

    protected WebElement findVisibleWithRetry(By locator, int maxAttempts) {
        return RetryUtils.retryFindElement(driver, d -> action.findVisible(locator), maxAttempts);
    }

    protected boolean clickWithRetry(WebElement element) {
        try {
            RetryUtils.retryClick(element);
            return true;
        } catch (RetryUtils.RetryExhaustedException e) {
            return false;
        }
    }

    protected WebElement visibleElement(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected void clearAndType(WebElement element, String value) {
        action.clearAndType(element, value == null ? "" : value);
    }

    protected void jsClick(WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        } catch (Exception e) {
            element.click();
        }
    }
}


