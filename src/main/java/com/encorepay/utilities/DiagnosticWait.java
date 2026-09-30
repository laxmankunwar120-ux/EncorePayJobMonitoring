package com.encorepay.utilities;

import java.time.Duration;
import java.util.function.Function;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * A WebDriverWait that explains itself when it times out.
 *
 * The stock Selenium message is only "waiting for com.encorepay.pages.SomePage... (tried for
 * N second(s))", which names the page class and nothing else. That is not enough to tell a
 * session redirect apart from a slow render or an element that never exists. Every wait in the
 * page objects goes through this class, so the timeout message carries the state that actually
 * explains the failure: current URL, title, document readiness, the rendered text, and a
 * screenshot path.
 */
public class DiagnosticWait extends WebDriverWait {

    private final WebDriver driver;
    private final Duration timeout;
    private final String description;

    public DiagnosticWait(WebDriver driver, Duration timeout) {
        this(driver, timeout, null);
    }

    public DiagnosticWait(WebDriver driver, Duration timeout, String description) {
        super(driver, timeout);
        this.driver = driver;
        this.timeout = timeout;
        this.description = description;
    }

    /** Returns a copy that labels its timeouts, for waits whose condition is not self-describing. */
    public DiagnosticWait describedAs(String label) {
        return new DiagnosticWait(driver, timeout, label);
    }

    @Override
    public <V> V until(Function<? super WebDriver, V> isTrue) {
        try {
            return super.until(isTrue);
        } catch (RuntimeException e) {
            throw new TimeoutWithDiagnostics(description, e);
        }
    }

    private final class TimeoutWithDiagnostics extends IllegalStateException {

        private TimeoutWithDiagnostics(String label, Throwable cause) {
            super(buildMessage(label, cause), cause);
        }
    }

    private String buildMessage(String label, Throwable cause) {
        StringBuilder message = new StringBuilder();
        message.append("Timed out after ").append(timeout.getSeconds()).append("s");

        if (label != null && !label.isBlank()) {
            message.append(" waiting for ").append(label);
        }

        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            message.append(" [cause: ").append(cause.getMessage()).append(']');
        }

        message.append('\n');
        message.append(describePageState());
        message.append('\n').append(captureFailureScreenshot(label));

        return message.toString();
    }

    private String describePageState() {
        StringBuilder state = new StringBuilder("Page state: ");

        state.append("url=").append(readUrl());
        state.append(" | title=").append(readTitle());
        state.append(" | readyState=").append(readScript("return document.readyState"));
        state.append(" | views=").append(readScript("return document.querySelectorAll('app-root, app-job, app-job-details, app-login').length"));

        String text = readPageText();
        state.append("\nVisible text: ").append(text.isBlank() ? "<none>" : text);

        return state.toString();
    }

    private String readUrl() {
        try {
            return driver.getCurrentUrl();
        } catch (Exception e) {
            return "<unavailable: " + e.getClass().getSimpleName() + ">";
        }
    }

    private String readTitle() {
        try {
            String title = driver.getTitle();
            return title == null || title.isBlank() ? "<empty>" : title;
        } catch (Exception e) {
            return "<unavailable>";
        }
    }

    private String readPageText() {
        Object text = readScript(
            "return (document.body ? document.body.innerText : '') || ''"
        );
        String value = text == null ? "" : String.valueOf(text);
        value = value.replaceAll("\\s+", " ").trim();
        if (value.length() > 400) {
            value = value.substring(0, 397) + "...";
        }
        return value;
    }

    private Object readScript(String script) {
        try {
            return ((JavascriptExecutor) driver).executeScript(script);
        } catch (Exception e) {
            return "<unavailable>";
        }
    }

    /**
     * The failure screenshot lands in the same directory the workflow already uploads, so a
     * CI failure can be diagnosed from the artifact without rerunning anything.
     */
    private String captureFailureScreenshot(String label) {
        if (!(driver instanceof TakesScreenshot takesScreenshot)) {
            return "Failure screenshot: not supported by this driver.";
        }

        try {
            // The label becomes the file name, so the artifact itself says which wait failed.
            String captured = ScreenshotUtil.captureScreenshot(driver, sanitize(label), "Wait timed out");
            return "Failure screenshot: " + (captured.isBlank() ? "capture failed" : captured);
        } catch (Exception e) {
            return "Failure screenshot: capture threw " + e.getClass().getSimpleName();
        }
    }

    private String sanitize(String value) {
        if (value == null) {
            return "step";
        }
        String sanitized = value.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_").replaceAll("\\s+", "_").trim();
        if (sanitized.isBlank()) {
            return "step";
        }
        return sanitized.length() > 80 ? sanitized.substring(0, 80) : sanitized;
    }
}
