package com.encorepay.pages;

import java.time.Duration;
import java.util.Locale;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.encorepay.actiondriver.ActionDriver;
import com.encorepay.utilities.ConfigReader;

public class BasePage {

/**
     * The application's own header, which the layout renders only for a logged-in user and only
     * as <nav> containing its menu-btn triggers. A bare //nav would also match unrelated markup
     * and could make an unauthenticated page look signed in.
     */
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
        PageFactory.initElements(driver, this);
    }

    protected void awaitAppBootstrap() {
        bootWait.until(d -> documentComplete(d) && hasApplicationIdentity(d) && isVisibleApplicationScreen());
    }

    public void waitForVisibleApplicationScreen() {
        awaitAppBootstrap();
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
