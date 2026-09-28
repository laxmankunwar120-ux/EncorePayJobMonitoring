package com.encorepay.base;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;

import com.encorepay.actiondriver.ActionDriver;
import com.encorepay.utilities.ConfigReader;
import com.encorepay.utilities.JobMonitoringHtmlReport;
import com.encorepay.utilities.ScreenshotUtil;

import io.github.bonigarcia.wdm.WebDriverManager;

public class BaseClass {

    private static final String CONFIG_PATH =
            "src/main/resources/config.properties";

    protected WebDriver driver;
    protected Properties prop;
    protected ConfigReader config;
    protected ActionDriver action;

    @BeforeSuite(alwaysRun = true)
    public void setupSuiteBase() {
        ScreenshotUtil.cleanScreenshotsDirectory();
        JobMonitoringHtmlReport.cleanReportsDirectory();
    }

    @BeforeClass(alwaysRun = true)
    public void setUpBase() {

        config = new ConfigReader();
        prop = loadProperties();

        driver = createDriver(config.getBrowser());

        driver.manage().timeouts().implicitlyWait(
                Duration.ofSeconds(config.getImplicitWait()));

        driver.manage().timeouts().pageLoadTimeout(
                Duration.ofSeconds(getIntProperty("pageLoadTimeout", 45)));

        driver.manage().window().maximize();

        action = new ActionDriver(driver);

        driver.get(config.getURL());

        waitForPageReady();
    }

    @AfterClass(alwaysRun = true)
    public void tearDownBase() {

        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }

    public WebDriver getDriver() {
        return driver;
    }

    protected void recordTestData(String detail) {

        ScreenshotUtil.addCurrentTestData(detail);

        System.out.println("[DATA] " + detail);
    }

    protected void recordVerification(String detail) {

        ScreenshotUtil.addCurrentTestVerification(detail);

        System.out.println("[VERIFY] " + detail);
    }

    protected void captureStep(String label) {

        ScreenshotUtil.captureCurrentTestStep(driver, label);
    }

    protected void waitForPageReady() {

        try {

            new org.openqa.selenium.support.ui.WebDriverWait(
                    driver,
                    Duration.ofSeconds(config.getExplicitWait()))

                    .until(d ->
                            "complete".equals(
                                    ((JavascriptExecutor) d)
                                            .executeScript(
                                                    "return document.readyState")));

        } catch (Exception ignored) {
        }
    }

    protected void logTestResult(ITestResult result) {

        String testName = result.getName();

        switch (result.getStatus()) {

            case ITestResult.SUCCESS:

                System.out.println("PASSED: " + testName);
                break;

            case ITestResult.FAILURE:

                System.out.println("FAILED: " + testName);

                if (result.getThrowable() != null) {
                    result.getThrowable().printStackTrace();
                }

                break;

            case ITestResult.SKIP:

                System.out.println("SKIPPED: " + testName);
                break;

            default:

                System.out.println("UNKNOWN STATUS: " + testName);
        }
    }

    private WebDriver createDriver(String browserName) {

        String browser = browserName == null
                ? "chrome"
                : browserName.trim().toLowerCase(Locale.ROOT);

        boolean headless =
                Boolean.parseBoolean(getProperty("headless", "false"));

        switch (browser) {

            case "firefox":

                WebDriverManager.firefoxdriver().setup();

                FirefoxOptions firefoxOptions = new FirefoxOptions();

                if (headless) {
                    firefoxOptions.addArguments("-headless");
                }

                return new FirefoxDriver(firefoxOptions);

            case "edge":
            case "msedge":

                WebDriverManager.edgedriver().setup();

                EdgeOptions edgeOptions = new EdgeOptions();

                edgeOptions.addArguments(
                        "--disable-notifications",
                        "--remote-allow-origins=*");

                if (headless) {
                    edgeOptions.addArguments(
                            "--headless=new",
                            "--window-size=1920,1080");
                }

                return new EdgeDriver(edgeOptions);

            case "chrome":
            default:

                WebDriverManager.chromedriver().setup();

                ChromeOptions chromeOptions = new ChromeOptions();

                chromeOptions.addArguments(
                        "--disable-notifications",
                        "--remote-allow-origins=*",
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-gpu",
                        "--disable-extensions",
                        "--disable-dev-shm",
                        "--window-size=1920,1080");

                if (headless) {
                    chromeOptions.addArguments(
                            "--headless=new",
                            "--window-size=1920,1080");
                }

                return new ChromeDriver(chromeOptions);
        }
    }

    private Properties loadProperties() {

        Properties properties = new Properties();

        try (FileInputStream fis =
                     new FileInputStream(CONFIG_PATH)) {

            properties.load(fis);

            return properties;

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Unable to load config from " + CONFIG_PATH,
                    e);
        }
    }

    private String getProperty(
            String key,
            String defaultValue) {

        return prop == null
                ? defaultValue
                : prop.getProperty(key, defaultValue).trim();
    }

    private int getIntProperty(
            String key,
            int defaultValue) {

        try {

            return Integer.parseInt(
                    getProperty(key, String.valueOf(defaultValue)));

        } catch (NumberFormatException e) {

            return defaultValue;
        }
    }
}