package com.encorepay.pages;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.encorepay.utilities.ConfigReader;

/** Thrown when the application host rejects the runner outright (HTTP 403 / WAF / geo / IP
 *  allow-list). Deterministic and not recoverable by reloading, so login fails fast. */
class LoginBlockedException extends RuntimeException {
    LoginBlockedException(String message) {
        super("ACCESS_BLOCKED: " + message);
    }
}


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

    private static final By MFA_OTP_FIELD = By.xpath(
        "//app-login//input[@name='otp' or @placeholder='Enter OTP']"
    );

    private static final By LOGIN_SUBMIT = By.xpath(
        "//app-login//form//button[@type='submit' and contains(normalize-space(.),'Log In')]"
            + " | //app-login//form//button[@type='submit' and contains(normalize-space(.),'Verify OTP')]"
    );

    private static final String SSO_HOST = "sso.sarvagram.com";

    private final WebDriverWait signInWait;
    private final WebDriverWait loginOutcomeWait;

    /** Where in the login flow we currently are; surfaced in every failure message. */
    private String loginStage = "init";

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
        this.loginOutcomeWait = new WebDriverWait(driver, Duration.ofSeconds(45));
    }

    public LoginPage(WebDriver driver, ConfigReader config) {
        super(driver, config);
        this.signInWait = wait;

        this.loginOutcomeWait = new WebDriverWait(
            driver,
            Duration.ofSeconds(Math.max(config.getExplicitWait(), 45))
        );
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

        final long startedAt = System.currentTimeMillis();
        // A stalled SPA (shell loaded, app never hydrates) is the dominant CI failure for
        // hosts behind slow or filtered network paths. Healthy apps render in a few seconds
        // and never reach these thresholds, so the reloads only ever cost time on a client
        // that is already going to time out. Spread a bounded number of reloads across the
        // wait budget so a transient bundle/API stall gets several chances to recover instead
        // of just one. A hard 403 (host blocking the runner IP) is detected above and thrown
        // before any reload, so it is never retried pointlessly.
        final long[] reloadAtMs = {20_000L, 45_000L, 70_000L};
        final int[] reloadsDone = {0};
        loginStage = "waiting-for-sign-in-ui";
        loginOutcomeWait.until(d -> {
            String accessFailure = detectInfrastructureFailure();
            if (!accessFailure.isBlank()) {
                // Carry the real reason in the message but throw a dedicated exception type so a
                // hard block (403/WAF) fails fast instead of being swallowed by the retry loop.
                throw new LoginBlockedException(accessFailure);
            }
            if (isSsoButtonVisible() || isNormalLoginVisible() || isAuthenticatedAreaVisible()) {
                return true;
            }
            long elapsed = System.currentTimeMillis() - startedAt;
            if (reloadsDone[0] < reloadAtMs.length && elapsed >= reloadAtMs[reloadsDone[0]]) {
                int attemptNo = reloadsDone[0] + 1;
                reloadsDone[0]++;
                System.out.println("[LOGIN] Sign-in UI still not rendered after " + (elapsed / 1000)
                        + "s (shell loaded, app not hydrated); mid-wait reload "
                        + attemptNo + "/" + reloadAtMs.length + ". " + describePageState());
                try {
                    d.navigate().refresh();
                } catch (RuntimeException reloadError) {
                    System.out.println("[LOGIN] Mid-wait reload " + attemptNo + " failed: " + reloadError.getMessage());
                }
            }
            return false;
        });
    }

    private String detectInfrastructureFailure() {
        try {
            String url = driver.getCurrentUrl();
            String title = driver.getTitle();
            String source = driver.getPageSource();
            String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
            String s = source == null ? "" : source.toLowerCase(Locale.ROOT);
            String u = url == null ? "" : url.toLowerCase(Locale.ROOT);

            // 403 = the host is rejecting THIS runner (WAF / geo / IP allow-list). This is not a
            // transient stall and not a login problem, so it must never be reloaded or retried.
            if (t.contains("403 forbidden") || s.contains("403 forbidden")
                    || t.contains("access denied") || s.contains("access denied")
                    || s.contains("just a moment") || s.contains("attention required")
                    || s.contains("cf-chl") || s.contains("cloudflare")) {
                return "ACCESS_BLOCKED: Application host returned 403 Forbidden to the runner "
                        + "(IP/geo/WAF block - works locally but is blocked from CI).";
            }
            if (t.contains("404 not found") || s.contains("404 not found")
                    || s.contains("nginx/") && s.contains("404")) {
                return "Server is down (HTTP 404 - application endpoint is unavailable)";
            }
            if (t.contains("502 bad gateway") || s.contains("502 bad gateway")
                    || t.contains("503 service unavailable") || s.contains("503 service unavailable")
                    || t.contains("504 gateway timeout") || s.contains("504 gateway timeout")
                    || s.contains("bad gateway") || s.contains("service unavailable")) {
                return "Server is down (application service is unavailable)";
            }
            if (s.contains("err_connection_refused") || s.contains("err_connection_reset")
                    || s.contains("err_name_not_resolved") || s.contains("err_connection_timed_out")) {
                return "Server is down (application connection is unavailable)";
            }
            if ((t.contains("not found") || s.contains("404")) && !u.contains("#/signin")) {
                return "Server is down (application page was not found)";
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public void login(String user, String pass) {
        if (isAuthenticatedAreaVisible()) {
            action.recordVerification("Existing authenticated application UI was already visible; sign-in was not repeated.");
            return;
        }

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {

                waitForLoginOrAuthenticatedPage();
                if (isAuthenticatedAreaVisible()) {
                    action.recordVerification("User reached the authenticated area after sign-in attempt " + attempt + ".");
                    action.captureStep("Login Success");
                    return;
                }

                if (isSsoButtonVisible()) {
                    throw new IllegalStateException("SSO login page detected for a normal-login client configuration.");
                }

                attemptLogin(user, pass);
                loginStage = "awaiting-login-outcome";
                loginOutcomeWait.until(d -> isLoginSuccessful() || isLoginRejected() || isMfaChallengeVisible());

                if (isMfaChallengeVisible()) {
                    throw new IllegalStateException("Login requires MFA/OTP. The configured automation credentials do not include an OTP.");
                }
                if (isLoginRejected()) {
                    throw new IllegalStateException("LOGIN_REJECTED: " + getFailureContext());
                }
                if (isLoginSuccessful()) {
                    action.recordVerification("User reached the authenticated area after sign-in attempt " + attempt + ".");
                    action.captureStep("Login Success");
                    return;
                }
                throw new IllegalStateException("Login did not reach the authenticated application state.");
            } catch (RuntimeException e) {
                lastFailure = e;
                if (containsMarker(e, "ACCESS_BLOCKED:")) {
                    // Hard host block (403/WAF): deterministic, reloading cannot help, so fail fast.
                    captureLoginFailureEvidence();
                    throw e;
                }
                if (isLoginRejected() || isMfaChallengeVisible() || containsMarker(e, "LOGIN_REJECTED:")) {
                    throw e;
                }
                System.out.println("[WARN] Login attempt " + attempt + "/2 did not complete: " + getFailureContext());
                if (attempt < 2) {
                    try {
                        driver.navigate().refresh();
                    } catch (RuntimeException recoveryError) {
                        lastFailure = recoveryError;
                        System.out.println("[WARN] Login retry page recovery failed: " + getFailureContext());
                    }
                }
            }
        }

        captureLoginFailureEvidence();
        throw buildLoginFailure("LOGIN", lastFailure);
    }

    public void ssoLogin(String employeeId, String password) {
        if (isAuthenticatedAreaVisible()) {
            action.recordVerification("Existing SSO-authenticated application UI was already visible; sign-in was not repeated.");
            return;
        }

        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                waitForLoginOrAuthenticatedPage();
                if (isAuthenticatedAreaVisible()) return;

                WebElement ssoButton = wait.until(ExpectedConditions.elementToBeClickable(SSO_BUTTON));
                ssoButton.click();
                loginStage = "sso-redirect";
                action.captureStep("SSO Sign-In Clicked (attempt " + attempt + ")");
                wait.until(ExpectedConditions.urlContains(SSO_HOST));

                WebElement employeeField = wait.until(ExpectedConditions.visibilityOfElementLocated(SSO_EMPLOYEE_ID));
                employeeField.clear();
                employeeField.sendKeys(employeeId);
                WebElement ssoPasswordField = wait.until(ExpectedConditions.visibilityOfElementLocated(SSO_PASSWORD));
                ssoPasswordField.clear();
                ssoPasswordField.sendKeys(password);
                loginStage = "sso-credentials-typed";
                action.captureStep("SSO Credentials Entered (attempt " + attempt + ")");
                wait.until(ExpectedConditions.elementToBeClickable(SSO_LOGIN_BUTTON)).click();
                loginStage = "awaiting-login-outcome";

                wait.until(d -> !d.getCurrentUrl().toLowerCase(Locale.ROOT).contains(SSO_HOST));
                wait.until(d -> {
                    String url = d.getCurrentUrl().toLowerCase(Locale.ROOT);
                    return !url.contains("#/signin") && !url.contains("/signin");
                });
                wait.until(d -> isAuthenticatedApplicationVisible());
                waitForLoginOutcome();

                if (!isLoginSuccessful()) {
                    throw new IllegalStateException("SSO login did not reach the authenticated application state.");
                }
                action.recordVerification("User reached the authenticated area after SSO sign-in attempt " + attempt + ".");
                action.captureStep("SSO Login Success");
                return;
            } catch (RuntimeException e) {
                lastError = e;
                if (containsMarker(e, "ACCESS_BLOCKED:")) {
                    captureLoginFailureEvidence();
                    throw e;
                }
                if (isLoginRejected() || isMfaChallengeVisible()) throw e;
                System.out.println("[WARN] SSO login attempt " + attempt + "/2 did not complete: " + getFailureContext());
                if (attempt < 2) {
                    try { driver.navigate().refresh(); } catch (Exception ignored) { }
                }
            }
        }
        captureLoginFailureEvidence();
        throw buildLoginFailure("SSO", lastError);
    }

    public void login() {
        login(config.getUsername(), config.getPassword());
    }

    private RuntimeException buildLoginFailure(String flow, Throwable error) {
        String code = classifyLoginFailure(error);
        String message = switch (code) {
            case "ACCESS_BLOCKED" -> "Application access rejected (403 Forbidden) - the host is blocking this runner's IP/region (works locally, blocked from CI). Not a login or credential issue.";
            case "SERVER_DOWN" -> "Server is down - " + flow + " login could not reach the application after 2 attempts.";
            case "LOGIN_TIMEOUT" -> "Login timed out - " + flow + " authentication transition did not complete after 2 attempts.";
            case "BROWSER_FAILURE" -> "Browser/session failure - " + flow + " login could not be completed after 2 attempts.";
            default -> "Application not ready - " + flow + " login could not reach a usable authenticated state after 2 attempts.";
        };
        return new IllegalStateException(code + ": " + message, error);
    }

    private String classifyLoginFailure(Throwable error) {
        if (containsMarker(error, "ACCESS_BLOCKED:")) return "ACCESS_BLOCKED";
        if (containsMarker(error, "SERVER_DOWN:")) return "SERVER_DOWN";
        if (containsMarker(error, "LOGIN_REJECTED:")) return "LOGIN_REJECTED";
        if (containsMarker(error, "MFA_REQUIRED:")) return "MFA_REQUIRED";

        Throwable current = error;
        while (current != null) {
            String type = current.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            String message = current.getMessage();
            String value = message == null ? "" : message.toLowerCase(Locale.ROOT);

            if (type.contains("nosuchsession") || type.contains("invalidsession")
                    || value.contains("invalid session id") || value.contains("no such session")
                    || value.contains("disconnected: not connected to devtools")) {
                return "BROWSER_FAILURE";
            }
            if (type.contains("timeout") || value.contains("timeout")
                    || value.contains("timed out") || value.contains("did not complete")) {
                return "LOGIN_TIMEOUT";
            }
            if (current instanceof WebDriverException && value.contains("session")) {
                return "BROWSER_FAILURE";
            }
            current = current.getCause();
        }
        return "APPLICATION_NOT_READY";
    }

    private boolean containsMarker(Throwable error, String marker) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(marker)) return true;
            current = current.getCause();
        }
        return false;
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

        return isAuthenticatedAreaVisible();
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

            if (url.contains("#/signin") || url.contains("/signin")) {
                return true;
            }
            return isDisplayed(usernameField) && isDisplayed(loginBtn);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAuthenticatedAreaVisible() {

        return isAuthenticatedApplicationVisible();
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
        loginStage = "typing-credentials";
        waitForLoginPage();

        String username = user == null ? "" : user.trim();
        String password = pass == null ? "" : pass;
        type(usernameField, username);
        type(passwordField, password);

        signInWait.until(d -> {
            try {
                String actualUser = usernameField.getAttribute("value");
                String actualPassword = passwordField.getAttribute("value");
                return username.equals(actualUser == null ? "" : actualUser.trim())
                    && !password.isBlank() && actualPassword != null && !actualPassword.isBlank();
            } catch (Exception e) {
                return false;
            }
        });

        action.captureStep("Credentials Entered");
        loginStage = "submitting-credentials";
        action.click(LOGIN_SUBMIT);
        loginStage = "credentials-submitted";
    }

    private boolean isMfaChallengeVisible() {
        try {
            if (isDisplayed(MFA_OTP_FIELD)) return true;
            String page = driver.findElement(By.tagName("body")).getText();
            String normalized = page == null ? "" : page.toLowerCase(Locale.ROOT);
            return normalized.contains("verify otp") || normalized.contains("otp will expire");
        } catch (Exception e) {
            return false;
        }
    }

    private void waitForLoginOutcome() {
        wait.until(d -> isLoginSuccessful() || isLoginRejected() || isMfaChallengeVisible());
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
        StringBuilder context = new StringBuilder();
        context.append("stage=").append(loginStage);
        String error = getLoginErrorMessage();
        if (!error.isBlank()) {
            context.append(" | visible message: ").append(error);
        }
        context.append(" | ").append(describePageState());
        return context.toString();
    }

    /**
     * Best-effort evidence capture right before a login failure is thrown: screenshot plus a
     * one-line page snapshot, so a CI log alone shows whether the sign-in form was missing,
     * the app never hydrated, or a block/error page was served.
     */
    private void captureLoginFailureEvidence() {
        System.out.println("[LOGIN FAILURE EVIDENCE] stage=" + loginStage + " | " + describePageState());
        try {
            action.captureStep("Login failure (" + loginStage + ")");
        } catch (RuntimeException ignored) {
        }
    }

    /** Compact one-line snapshot of what the browser actually shows, for CI logs. */
    private String describePageState() {
        try {
            String url = driver.getCurrentUrl();
            String title = driver.getTitle();
            String readyState = "unknown";
            try {
                readyState = String.valueOf(
                        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("return document.readyState"));
            } catch (Exception ignored) {
            }
            String bodySnippet = "";
            try {
                String body = driver.findElement(By.tagName("body")).getText();
                bodySnippet = body == null ? "" : body.replaceAll("\\s+", " ").trim();
                if (bodySnippet.length() > 160) {
                    bodySnippet = bodySnippet.substring(0, 160) + "...";
                }
            } catch (Exception ignored) {
            }
            boolean formVisible;
            try {
                formVisible = isNormalLoginVisible();
            } catch (Exception e) {
                formVisible = false;
            }
            boolean ssoVisible;
            try {
                ssoVisible = isSsoButtonVisible();
            } catch (Exception e) {
                ssoVisible = false;
            }
            return "url=" + url
                    + " | title=" + title
                    + " | readyState=" + readyState
                    + " | usernameFieldVisible=" + formVisible
                    + " | ssoButtonVisible=" + ssoVisible
                    + " | bodySnippet=\"" + bodySnippet + "\"";
        } catch (Exception e) {
            return "page state unavailable: " + e.getMessage();
        }
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
