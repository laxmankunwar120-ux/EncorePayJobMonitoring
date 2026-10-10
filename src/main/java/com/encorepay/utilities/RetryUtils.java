package com.encorepay.utilities;

import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public final class RetryUtils {

    private RetryUtils() {
    }

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long DEFAULT_BASE_DELAY_MS = 500;
    private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    private static final long MAX_DELAY_MS = 10_000;

    public static <T> T retry(Supplier<T> operation) {
        return retry(operation, DEFAULT_MAX_ATTEMPTS, DEFAULT_BASE_DELAY_MS, DEFAULT_BACKOFF_MULTIPLIER);
    }

    public static <T> T retry(Supplier<T> operation, int maxAttempts) {
        return retry(operation, maxAttempts, DEFAULT_BASE_DELAY_MS, DEFAULT_BACKOFF_MULTIPLIER);
    }

    public static <T> T retry(Supplier<T> operation, int maxAttempts, long baseDelayMs, double backoffMultiplier) {
        Exception lastException = null;
        long delay = baseDelayMs;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            } catch (StaleElementReferenceException e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    sleep(delay);
                    delay = Math.min((long) (delay * backoffMultiplier), MAX_DELAY_MS);
                }
            } catch (Exception e) {
                if (isRetryable(e)) {
                    lastException = e;
                    if (attempt < maxAttempts) {
                        sleep(delay);
                        delay = Math.min((long) (delay * backoffMultiplier), MAX_DELAY_MS);
                    }
                } else {
                    throw e;
                }
            }
        }

        throw new RetryExhaustedException("Operation failed after " + maxAttempts + " attempts", lastException);
    }

    public static void run(Runnable operation) {
        run(operation, DEFAULT_MAX_ATTEMPTS, DEFAULT_BASE_DELAY_MS, DEFAULT_BACKOFF_MULTIPLIER);
    }

    public static void run(Runnable operation, int maxAttempts) {
        run(operation, maxAttempts, DEFAULT_BASE_DELAY_MS, DEFAULT_BACKOFF_MULTIPLIER);
    }

    public static void run(Runnable operation, int maxAttempts, long baseDelayMs, double backoffMultiplier) {
        Exception lastException = null;
        long delay = baseDelayMs;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                operation.run();
                return;
            } catch (StaleElementReferenceException e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    sleep(delay);
                    delay = Math.min((long) (delay * backoffMultiplier), MAX_DELAY_MS);
                }
            } catch (Exception e) {
                if (isRetryable(e)) {
                    lastException = e;
                    if (attempt < maxAttempts) {
                        sleep(delay);
                        delay = Math.min((long) (delay * backoffMultiplier), MAX_DELAY_MS);
                    }
                } else {
                    throw e;
                }
            }
        }

        throw new RetryExhaustedException("Operation failed after " + maxAttempts + " attempts", lastException);
    }

    public static <T> T retryFindElement(WebDriver driver, Function<WebDriver, T> finder) {
        return retryFindElement(driver, finder, DEFAULT_MAX_ATTEMPTS);
    }

    public static <T> T retryFindElement(WebDriver driver, Function<WebDriver, T> finder, int maxAttempts) {
        return retry(() -> {
            T result = finder.apply(driver);
            if (result == null) {
                throw new RuntimeException("Element not found");
            }
            return result;
        }, maxAttempts);
    }

    public static <T> T retryClick(WebElement element) {
        return retryClick(element, DEFAULT_MAX_ATTEMPTS);
    }

    public static <T> T retryClick(WebElement element, int maxAttempts) {
        return retry(() -> {
            element.click();
            return null;
        }, maxAttempts);
    }

    public static void waitForPageReady(WebDriver driver) {
        retry(() -> {
            if (driver instanceof RemoteWebDriver) {
                Object state = ((RemoteWebDriver) driver).executeScript("return document.readyState");
                if (!"complete".equals(state)) {
                    throw new RuntimeException("Page not ready: " + state);
                }
            }
            return null;
        }, DEFAULT_MAX_ATTEMPTS, 200, 2.0);
    }

    public static boolean isRetryable(Throwable e) {
        if (e == null) return false;
        if (e instanceof StaleElementReferenceException) return true;
        String message = e.getMessage();
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("stale element")
                || lower.contains("element not interactable")
                || lower.contains("element click intercepted")
                || lower.contains("timeout")
                || lower.contains("no such window")
                || lower.contains("session")
                || lower.contains("unexpected alert")
                || lower.contains("modal dialog")
                || lower.contains("element is not attached")
                || lower.contains("disconnected")
                || lower.contains("target frame detached");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry sleep interrupted", e);
        }
    }

    public static final class RetryExhaustedException extends RuntimeException {
        public RetryExhaustedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
