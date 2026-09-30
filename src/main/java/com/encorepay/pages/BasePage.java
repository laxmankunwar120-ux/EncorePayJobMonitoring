package com.encorepay.pages;

import java.time.Duration;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.encorepay.actiondriver.ActionDriver;
import com.encorepay.utilities.ConfigReader;
import com.encorepay.utilities.DiagnosticWait;

public class BasePage {

    protected final WebDriver driver;
    protected final DiagnosticWait wait;
    protected final DiagnosticWait shortWait;
    protected final ConfigReader config;
    protected final ActionDriver action;

    public BasePage(WebDriver driver) {
        this(driver, new ConfigReader());
    }

    public BasePage(WebDriver driver, ConfigReader config) {
        this.driver = driver;
        // Every page wait goes through DiagnosticWait so a timeout reports the page state
        // that caused it instead of only the page class name.
        this.wait = new DiagnosticWait(driver, Duration.ofSeconds(config.getExplicitWait()));
        this.shortWait = new DiagnosticWait(driver, Duration.ofSeconds(5));
        this.config = config;
        this.action = new ActionDriver(driver, config);
        PageFactory.initElements(driver, this);
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

    protected WebElement findVisibleElement(By locator) {
        return action.findVisible(locator);
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
