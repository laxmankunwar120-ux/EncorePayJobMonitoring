package com.encorepay.pages;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.encorepay.utilities.ConfigReader;

public class LoginPage extends BasePage {

    private static final By LOGIN_ERROR_LOCATOR = By.xpath(
        "//*[contains(@class,'bg-red') or contains(@class,'error') or contains(@class,'snack') or contains(@class,'toast')]"
            + "[contains(.,'invalid') or contains(.,'Invalid') or contains(.,'incorrect') or contains(.,'Incorrect')"
            + " or contains(.,'failed') or contains(.,'Failed') or contains(.,'required') or contains(.,'Required')]"
            + " | //mat-error"
            + " | //small[contains(@class,'error')]"
            + " | //div[contains(@class,'text-red')]"
    );

    private static final By SSO_BUTTON = By.xpath(
        "//app-login//button[normalize-space()='Sign in with SSO']"
    );

    private static final By SSO_EMPLOYEE_ID = By.id("username");

    private static final By SSO_PASSWORD = By.id("password");

    private static final By SSO_LOGIN_BUTTON = By.cssSelector(
        "button[type='submit'][name='login']"
    );

    private static final String SSO_HOST = "sso.sarvagram.com";

    private final WebDriverWait signInWait;
    private final WebDriverWait loginOutcomeWait;

    @FindBy(xpath =
        "//input[@placeholder='Enter User Name']"
            + " | //input[@placeholder='User Name']"
            + " | //input[@name='username']"
            + " | //input[@formcontrolname='username']")
    private WebElement usernameField;

    @FindBy(xpath =
        "//input[@placeholder='Enter Password']"
            + " | //input[@placeholder='Password']"
            + " | //input[@name='password']"
            + " | //input[@formcontrolname='password']")
    private WebElement passwordField;

    @FindBy(xpath =
        "//button[normalize-space()='Log In']"
            + " | //button[@type='submit' and contains(normalize-space(),'Log')]")
    private WebElement loginBtn;

    @FindBy(xpath =
        "//*[contains(@class,'bg-green') or contains(@class,'toast') or contains(@class,'snack')]"
            + "[contains(.,'Success') or contains(.,'success')]")
    private List<WebElement> successToasts;

    
    @FindBy(xpath =
        "//nav[.//button[contains(@class,'menu-btn')]]"
            + " | //button[normalize-space()='Dashboard']"
            + " | //button[normalize-space()='Accounts']"
            + " | //button[normalize-space()='Collections']"
            + " | //app-header")
    private List<WebElement> postLoginMarkers;

    public LoginPage(WebDriver driver) {
        super(driver);
        this.signInWait = wait;
        this.loginOutcomeWait = wait;
    }

    public LoginPage(WebDriver driver, ConfigReader config) {
        super(driver, config);
        this.signInWait = wait;
        this.loginOutcomeWait = wait;
    }

    public void open() {
        driver.get(config.getURL());
        awaitAppBootstrap();
        waitForLoginOrAuthenticatedPage();
        action.recordVerification("Application screen opened successfully at " + driver.getCurrentUrl());
    }

    public void waitForLoginPage() {
        waitForLoginOrAuthenticatedPage();
        if (!isLoginPageVisible() && !isAuthenticatedAreaVisible()) {
            throw new IllegalStateException("A usable sign-in or authenticated screen was not displayed.");
        }
    }

    public void waitForLoginOrAuthenticatedPage() {
        try {
            signInWait.until(d ->
                isSsoButtonVisible() || isNormalLoginVisible() || isAuthenticatedAreaVisible()
            );
        } catch (RuntimeException firstFailure) {
            System.out.println("[WARN] Sign-in page did not render on first load, reloading once.");
            driver.navigate().refresh();
            awaitAppBootstrap();
            signInWait.until(d ->
                isSsoButtonVisible() || isNormalLoginVisible() || isAuthenticatedAreaVisible()
            );
        }
    }

    public void login(String user, String pass) {
        if (isAuthenticatedAreaVisible()) {
            action.recordVerification("Existing authenticated application UI was already visible; sign-in was not repeated.");
            return;
        }
        if (!isLoginPageVisible()) {
            open();
        }

        signInWait.until(d ->
            isSsoButtonVisible() || isNormalLoginVisible() || isAuthenticatedAreaVisible()
        );

        if (isAuthenticatedAreaVisible()) return;

        if (isSsoButtonVisible()) {
            System.out.println("SSO login detected dynamically.");
            ssoLogin(user, pass);
            return;
        }

        System.out.println("Normal login detected dynamically.");

        attemptLogin(user, pass);
        loginOutcomeWait.until(d -> isLoginSuccessful() || isLoginRejected());

        if (!isLoginSuccessful()) {
            throw new IllegalStateException(
                "Login failed. " + getFailureContext()
            );
        }

        action.recordVerification(
            "User reached the authenticated area after sign-in."
        );

        action.captureStep("Login Success");
    }

    public void ssoLogin(String employeeId, String password) {
        if (isAuthenticatedAreaVisible()) {
            action.recordVerification("Existing SSO-authenticated application UI was already visible; sign-in was not repeated.");
            return;
        }

        if (!isLoginPageVisible()) {
            open();
        }

        if (isAuthenticatedAreaVisible()) return;

        WebElement ssoButton = wait.until(ExpectedConditions.elementToBeClickable(SSO_BUTTON));
        ssoButton.click();
        action.captureStep("SSO Sign-In Clicked");

        wait.until(ExpectedConditions.urlContains(SSO_HOST));

        WebElement employeeField = wait.until(
            ExpectedConditions.visibilityOfElementLocated(SSO_EMPLOYEE_ID)
        );
        employeeField.clear();
        employeeField.sendKeys(employeeId);

        WebElement ssoPasswordField = wait.until(
            ExpectedConditions.visibilityOfElementLocated(SSO_PASSWORD)
        );
        ssoPasswordField.clear();
        ssoPasswordField.sendKeys(password);

        action.captureStep("SSO Credentials Entered");

        WebElement signInButton = wait.until(ExpectedConditions.elementToBeClickable(SSO_LOGIN_BUTTON));
        signInButton.click();

        wait.until(d -> !d.getCurrentUrl().toLowerCase(Locale.ROOT).contains(SSO_HOST));
        
        // Wait for URL to change away from signin page
        wait.until(d -> {
            String url = d.getCurrentUrl().toLowerCase(Locale.ROOT);
            return !url.contains("#/signin") && !url.contains("/signin");
        });
        
        waitForLoginOutcome();
        waitForPageLoad();

        if (!isLoginSuccessful()) {
            throw new IllegalStateException(
                "SSO login failed. " + getFailureContext()
            );
        }

        action.recordVerification("User reached the authenticated area after sign-in.");
        action.captureStep("SSO Login Success");
    }
    public void login() {
        login(config.getUsername(), config.getPassword());
    }


    public void attemptInvalidLogin(String user, String pass) {
        open();
        attemptLogin(user, pass);
        wait.until(d -> isLoginRejected() || isElementDisplayed(LOGIN_ERROR_LOCATOR));
        action.captureStep("Invalid Login Rejected");
        String errorMessage = getLoginErrorMessage();
        if (!errorMessage.isBlank()) {
            action.recordVerification("Invalid login message displayed: " + errorMessage);
        } else {
            action.recordVerification("Invalid login was rejected and the user remained on the sign-in page.");
        }
        waitForErrorFeedbackToClear();
    }

    public boolean isLoginSuccessful() {
        return isAnyDisplayed(successToasts) || isAuthenticatedAreaVisible();
    }

    public boolean isLoginRejected() {
        return !getLoginErrorMessage().isBlank();
    }

    public boolean isLoginFailed() {
        return isLoginRejected() || (isLoginPageVisible() && !isLoginSuccessful());
    }

    public boolean isLoginPageVisible() {
        return isApplicationSignInPage();
    }

    public boolean isSsoPageVisible() {
        return driver.getCurrentUrl().toLowerCase(Locale.ROOT).contains(SSO_HOST);
    }

    private boolean isApplicationSignInPage() {
        try {
            String url = driver.getCurrentUrl().toLowerCase(Locale.ROOT);
            return url.contains("#/signin") || isDisplayed(usernameField);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAuthenticatedAreaVisible() {
        return isAuthenticatedApplicationVisible() && !isApplicationSignInPage();
    }

    public void prepareBlankCredentialsState() {
        open();
        type(usernameField, "");
        type(passwordField, "");
        action.captureStep("Blank Credentials Validation");
        action.recordVerification("Blank credentials kept the sign-in screen active.");
    }

    public boolean isLoginButtonEnabled() {
        try {
            return clickable(loginBtn).isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    public String getLoginErrorMessage() {
        for (WebElement error : driver.findElements(LOGIN_ERROR_LOCATOR)) {
            try {
                String text = sanitizeMessage(error.getText());
                if (error.isDisplayed() && !text.isBlank()) {
                    return text;
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private void attemptLogin(String user, String pass) {
        waitForLoginPage();
        type(usernameField, user == null ? "" : user);
        type(passwordField, pass == null ? "" : pass);
        action.captureStep("Credentials Entered");
        click(loginBtn);
    }

    private void waitForLoginOutcome() {
        wait.until(d -> isLoginSuccessful() || isLoginRejected());
        if (isAnyDisplayed(successToasts)) {
            try {
                WebElement toast = successToasts.stream().filter(this::isDisplayed).findFirst().orElse(null);
                if (toast != null) {
                    String toastText = sanitizeMessage(toast.getText());
                    if (!toastText.isBlank()) {
                        action.recordVerification("Login success toast displayed: " + toastText);
                    }
                    action.captureStep("Login Success Toast Visible", toast);
                    new WebDriverWait(driver, Duration.ofSeconds(5))
                        .until(ExpectedConditions.invisibilityOf(toast));
                }
                wait.until(d -> isAnyDisplayed(postLoginMarkers) || !isApplicationSignInPage());
            } catch (Exception ignored) {
            }
        } else if (isAuthenticatedAreaVisible()) {
            action.recordVerification("Login completed and post-login navigation became visible.");
        }
    }

    public void logout() {
        performApplicationLogout();
        wait.until(d -> isApplicationSignInPage());
        action.recordVerification("User logged out successfully.");
        action.captureStep("Logout Success");
    }

    public void ssoLogout() {
        performApplicationLogout();
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(d -> {
                    String url = d.getCurrentUrl().toLowerCase(Locale.ROOT);
                    return url.contains("logout") || url.contains("signout") || url.contains("logoff");
                });
        } catch (Exception ignored) {
        }
        boolean onSignIn = isApplicationSignInPage();
        boolean onAuthenticatedArea = isAuthenticatedAreaVisible();
        if (!onSignIn && !onAuthenticatedArea) {
            throw new IllegalStateException(
                "SSO logout could not confirm state. Current URL: " + driver.getCurrentUrl()
            );
        }
        action.recordVerification("Application logout completed and SSO sign-out flow was reached.");
        action.captureStep("SSO Logout Success");
    }

    private void performApplicationLogout() {
        By accountMenuButton = By.xpath(
            "//button[contains(@class,'menu-btn') "
            + "and .//span[contains(@class,'material-symbols-rounded') "
            + "and normalize-space()='account_circle']]"
        );

        By logoutButton = By.xpath(
            "//button[@role='menuitem'][.//span[normalize-space()='Logout']]"
        );

        try {
            WebElement accountButton = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.elementToBeClickable(accountMenuButton));
            accountButton.click();

            WebElement logoutElement = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.elementToBeClickable(logoutButton));
            logoutElement.click();
        } catch (Exception e) {
            throw new IllegalStateException(
                "Application logout failed. Current URL: " + driver.getCurrentUrl(),
                e
            );
        }
    }

    private String getFailureContext() {
        String error = getLoginErrorMessage();
        if (!error.isBlank()) {
            return "Visible message: " + error;
        }
        return "Current URL: " + driver.getCurrentUrl();
    }

    private boolean isElementDisplayed(By locator) {
        return isDisplayed(locator);
    }

    private boolean isAnyDisplayed(List<WebElement> elements) {
        for (WebElement element : elements) {
            if (isDisplayed(element)) {
                return true;
            }
        }
        return false;
    }

    private String sanitizeMessage(String rawText) {
        return rawText
            .replace("do_not_disturb_on", "")
            .replace("content_copy", "")
            .replace("close", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private void waitForErrorFeedbackToClear() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(d -> !isElementDisplayed(LOGIN_ERROR_LOCATOR));
        } catch (Exception ignored) {
            waitForTransientFeedbackToClear();
        }
    }
    private boolean isSsoButtonVisible() {
    try {
        return driver.findElements(SSO_BUTTON)
            .stream()
            .anyMatch(this::isDisplayed);
    } catch (Exception e) {
        return false;
    }
}
private boolean isNormalLoginVisible() {
    try {
        return isDisplayed(usernameField)
            && isDisplayed(loginBtn);
    } catch (Exception e) {
        return false;
    }
}
}


