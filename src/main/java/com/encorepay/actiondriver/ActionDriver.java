package com.encorepay.actiondriver;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.encorepay.utilities.ConfigReader;
import com.encorepay.utilities.ScreenshotUtil;

public class ActionDriver {

    private final WebDriver driver;
    private final WebDriverWait wait;
    private final WebDriverWait shortWait;
    private final ConfigReader config;
    private String currentStep = "startup";

    private static final By TRANSIENT_FEEDBACK = By.xpath(
        "//*[contains(@class,'toast') or contains(@class,'snack')"
            + " or contains(@class,'notification')"
            + " or contains(@class,'toastr')"
            + " or contains(@class,'mat-mdc-snack')"
            + " or contains(@class,'mdc-snackbar')"
            + " or contains(@class,'ngx-toastr')"
            + " or @role='alert']");

    private static final By OVERLAYS = By.cssSelector(".cdk-overlay-backdrop, .cdk-overlay-pane, .loader, .spinner, .ngx-spinner, .loading");

    public ActionDriver(WebDriver driver) {
        this(driver, new ConfigReader());
    }

    public ActionDriver(WebDriver driver, ConfigReader config) {
        this.driver = driver;
        this.config = config;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(config.getExplicitWait()));
        this.shortWait = new WebDriverWait(driver, Duration.ofSeconds(3));
    }

    public void waitForUiStable() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(config.getExplicitWait())).until(d ->
                ((JavascriptExecutor) d).executeScript("return document.readyState").equals("complete")
            );
        } catch (Exception ignored) {}

        waitForOverlayToClear();
        waitForTransientFeedbackToClear();
    }

    public void waitForOverlayToClear() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(config.getOverlayTimeout()))
                .until(ExpectedConditions.invisibilityOfElementLocated(OVERLAYS));
        } catch (Exception ignored) {}
    }

    public WebElement findVisible(By locator) {
        waitForOverlayToClear();
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    public WebElement findClickable(By locator) {
        waitForOverlayToClear();
        return wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    public void type(By locator, String value) {
        waitForUiStable();
        WebElement element = findVisible(locator);
        scrollToElement(element);
        clearAndType(element, value);
    }

    public void clearAndType(WebElement element, String value) {
        try {
            element.click();
            element.sendKeys(Keys.chord(Keys.CONTROL, "a"));
            element.sendKeys(Keys.DELETE);
            if (value != null && !value.isEmpty()) {
                element.sendKeys(value);
            }
        } catch (Exception ex) {
            jsSetValue(element, value);
        }
    }

    public void click(By locator) {
        waitForUiStable();
        WebElement element = findClickable(locator);
        safeClick(element);
    }

    public void click(WebElement element) {
        waitForUiStable();
        wait.until(ExpectedConditions.elementToBeClickable(element));
        safeClick(element);
    }

    public boolean isVisible(By locator) {
        try {
            return !driver.findElements(locator).isEmpty()
                && findVisible(locator).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isVisibleNow(By locator) {
        return findFirstVisibleElement(locator) != null;
    }

    public boolean isPresent(By locator) {
        return !driver.findElements(locator).isEmpty();
    }

    public boolean waitForVisibility(By locator) {
        try {
            findVisible(locator);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

   public boolean waitForAnyVisible(List<By> locators) {
    try {
        wait.until(driver -> {
            for (By locator : locators) {
                List<WebElement> elements = driver.findElements(locator);
                for (WebElement element : elements) {
                    if (element.isDisplayed()) {
                        return true;
                    }
                }
            }
            return false;
        });
        return true;
    } catch (Exception e) {
        return false;
    }
}

    public boolean waitForUrlContains(String fragment) {
        try {
            wait.until(ExpectedConditions.urlContains(fragment));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void waitForInvisibility(By locator) {
        try {
            wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
        } catch (Exception ignored) {
        }
    }

    public void waitForTransientFeedbackToClear() {
        waitForTransientFeedbackToClear(driver, this::recordVerification);
    }

 
    public static void waitForTransientFeedbackToClear(WebDriver driver) {
        waitForTransientFeedbackToClear(driver, null);
    }

    private static void waitForTransientFeedbackToClear(WebDriver driver, java.util.function.Consumer<String> verificationSink) {
        try {
            WebElement visibleToast = null;

            for (WebElement element : driver.findElements(TRANSIENT_FEEDBACK)) {
                try {
                    if (element.isDisplayed()) {
                        visibleToast = element;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }

            if (visibleToast == null) {
                return;
            }

            int feedbackTimeout = new ConfigReader().getTransientFeedbackTimeout();

            try {
                if (verificationSink != null) {
                    String text = sanitizeText(visibleToast.getText());
                    if (!text.isBlank()) {
                        verificationSink.accept("Application feedback displayed: " + text);
                    }
                }
            } catch (Exception ignored) {
            }

            try {
                WebElement close = visibleToast.findElement(By.xpath(
                    ".//button[contains(@class,'close')]"
                        + " | .//button[@aria-label='Close' or @aria-label='close']"
                        + " | .//span[normalize-space()='close']/ancestor::button[1]"
                ));
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", close);
            } catch (Exception ignored) {
            }

            try {
                new WebDriverWait(driver, Duration.ofSeconds(feedbackTimeout))
                    .until(ExpectedConditions.invisibilityOfElementLocated(TRANSIENT_FEEDBACK));
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
    }


    public void smoothScrollToTop() {
        try {
            ((JavascriptExecutor) driver)
                .executeScript("window.scrollTo({ top: 0, behavior: 'smooth' });");
        } catch (Exception e) {
            scrollToTop();
        }
    }

    public void scrollToTop() {
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
    }

    public void scrollToElement(By locator) {
        WebElement element = findFirstVisibleElement(locator);
        if (element == null) {
            element = findFirstPresentElement(locator);
        }
        if (element != null) {
            scrollToElement(element);
        }
    }

    public void scrollToElement(WebElement element) {
        try {
            ((JavascriptExecutor) driver)
                .executeScript("arguments[0].scrollIntoView({block:'center'});", element);
        } catch (Exception ignored) {
        }
    }

    public String getText(By locator) {
        try {
            return findVisible(locator).getText().trim();
        } catch (Exception e) {
            return "";
        }
    }


    public boolean validate(boolean condition, String passMsg, String failMsg) {
        if (condition) {
            System.out.println("[PASS] " + passMsg);
        } else {
            System.out.println("[FAIL] " + failMsg);
        }
        return condition;
    }

    public boolean validateVisible(By locator, String elementName) {
        boolean visible = waitForVisibility(locator);
        if (visible) {
            System.out.println("[PASS] " + elementName + " is visible");
        } else {
            System.out.println("[FAIL] " + elementName + " is NOT visible");
        }
        return visible;
    }

    /** Returns the screenshot path so callers can put it in a failure message, or "" on failure. */
    public String captureStep(String label) {
        try {
            waitForUiStable();
            return ScreenshotUtil.captureCurrentTestStep(driver, label);
        } catch (Exception e) {
            System.out.println("[WARN] Screenshot for step '" + label + "' failed: " + e.getMessage());
            return "";
        }
    }

    /**
     * Records the business step the run is on, so a later failure can say where it stopped
     * instead of only reporting that a wait expired.
     */
    public void markStep(String step) {
        if (step == null || step.isBlank()) return;
        currentStep = step.trim();
        System.out.println("[STEP] " + currentStep);
    }

    public String currentStep() {
        return currentStep;
    }

    /**
     * Builds the diagnostic line attached to a genuine client failure: step, URL, title, page
     * state and screenshot path. Never throws, so it cannot mask the failure it is describing.
     */
    public String captureFailure(String reason) {
        StringBuilder detail = new StringBuilder(reason == null ? "Unknown failure." : reason);

        detail.append(" [step=").append(currentStep).append(']');
        detail.append(" [url=").append(readSafely(() -> driver.getCurrentUrl())).append(']');
        detail.append(" [title=").append(readSafely(() -> driver.getTitle())).append(']');
        detail.append(" [readyState=")
            .append(readSafely(() -> String.valueOf(
                ((JavascriptExecutor) driver).executeScript("return document.readyState"))))
            .append(']');

        String screenshot = captureStep("Client failure at " + currentStep);
        detail.append(" [screenshot=").append(screenshot.isBlank() ? "not captured" : screenshot).append(']');

        System.out.println("[FAIL] " + detail);
        return detail.toString();
    }

    private String readSafely(Supplier<String> reader) {
        try {
            String value = reader.get();
            return value == null || value.isBlank() ? "<unavailable>" : cleanForLog(value);
        } catch (Exception e) {
            return "<unavailable: " + e.getClass().getSimpleName() + ">";
        }
    }

    private String cleanForLog(String value) {
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 300 ? cleaned.substring(0, 297) + "..." : cleaned;
    }

    public void captureStep(String label, By focusLocator) {
        WebElement focusElement = findFirstVisibleElement(focusLocator);
        if (focusElement == null) {
            focusElement = findFirstPresentElement(focusLocator);
        }
        try {
            if (focusElement != null) {
                scrollToElement(focusElement);
            }
        } catch (Exception ignored) {
        }
        captureStep(label);
    }

    public void captureStep(String label, WebElement focusElement) {
        try {
            scrollToElement(focusElement);
        } catch (Exception ignored) {
        }
        captureStep(label);
    }

    public void recordVerification(String detail) {
        ScreenshotUtil.addCurrentTestVerification(detail);
        System.out.println("[VERIFY] " + detail);
    }

    public void recordTestData(String detail) {
        ScreenshotUtil.addCurrentTestData(detail);
        System.out.println("[DATA] " + detail);
    }

    private void safeClick(WebElement element) {
        try {
            waitForTransientFeedbackToClear();
            closeFloatingMenus(driver);
            scrollToElement(element);
            waitForOverlayToClear();
            new Actions(driver).moveToElement(element).click().perform();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        }
    }

    public static void closeFloatingMenus(WebDriver driver) {
        try {
            List<WebElement> activeOverlays = driver.findElements(By.cssSelector(".mat-mdc-menu-panel, .cdk-overlay-backdrop, .cdk-overlay-transparent-backdrop"));
            for (WebElement o : activeOverlays) {
                if (o.isDisplayed()) {
                    ((JavascriptExecutor) driver).executeScript("document.body.click();");
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    public static void globalSafeClick(WebDriver driver, WebElement element) {
        try {
            waitForTransientFeedbackToClear(driver);
            boolean isProtectedElement = false;
            try {
                String role = element.getAttribute("role");
                String clazz = element.getAttribute("class");
                if (role != null && (role.contains("menuitem") || role.contains("option"))) {
                    isProtectedElement = true;
                }
                if (clazz != null && (clazz.contains("mat-mdc-menu-item") || clazz.contains("mat-mdc-option"))) {
                    isProtectedElement = true;
                }
            } catch (Exception ignored) {}

            if (!isProtectedElement) {
                closeFloatingMenus(driver);
            }
            
            try {
                // Ensure no standard overlay is actively blocking globally
                new WebDriverWait(driver, Duration.ofSeconds(3))
                    .until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".cdk-overlay-backdrop:not(.cdk-overlay-transparent-backdrop), .ngx-spinner, .loader")));
            } catch (Exception ignored) {}
            
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
            
            try {
                new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.elementToBeClickable(element));
            } catch (Exception ignored) {}
            
            element.click();
        } catch (Exception e) {
            try {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            } catch (Exception fatal) {
                System.out.println("[WARN] Global Safe Click fallback also failed: " + fatal.getMessage());
            }
        }
    }

    private WebElement findFirstVisibleElement(By locator) {
        for (WebElement element : driver.findElements(locator)) {
            try {
                if (element.isDisplayed()) {
                    return element;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private WebElement findFirstPresentElement(By locator) {
        List<WebElement> elements = driver.findElements(locator);
        return elements.isEmpty() ? null : elements.get(0);
    }

    private void jsSetValue(WebElement element, String value) {
        ((JavascriptExecutor) driver).executeScript(
            "arguments[0].value = arguments[1];"
                + "arguments[0].dispatchEvent(new Event('input',{bubbles:true}));"
                + "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));",
            element,
            value == null ? "" : value
        );
    }

    private static String sanitizeText(String value) {
        if (value == null) return "";
        return value
            .replace("do_not_disturb_on", "")
            .replace("content_copy", "")
            .replace("close", "")
            .replaceAll("\\s+", " ")
            .trim();
    }
}
