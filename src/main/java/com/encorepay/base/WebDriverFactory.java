package com.encorepay.base;

import java.util.Locale;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import com.encorepay.utilities.ConfigReader;

import io.github.bonigarcia.wdm.WebDriverManager;

public final class WebDriverFactory {
    private WebDriverFactory() {}

    public static WebDriver create(ConfigReader config) {
        String browser = config.getBrowser() == null
            ? "chrome"
            : config.getBrowser().trim().toLowerCase(Locale.ROOT);
        boolean headless = Boolean.parseBoolean(config.getProperty("headless", "false"));

        switch (browser) {
            case "firefox":
                WebDriverManager.firefoxdriver().setup();
                FirefoxOptions firefoxOptions = new FirefoxOptions();
                firefoxOptions.addPreference("geo.enabled", true);
                firefoxOptions.addPreference("geo.provider.use_corelocation", false);
                firefoxOptions.addPreference("permissions.default.geo", 1);
                if (headless) firefoxOptions.addArguments("-headless");
                return new FirefoxDriver(firefoxOptions);
            case "edge":
            case "msedge":
                WebDriverManager.edgedriver().setup();
                EdgeOptions edgeOptions = new EdgeOptions();
                java.util.Map<String, Object> edgePrefs = new java.util.HashMap<>();
                edgePrefs.put("profile.default_content_setting_values.geolocation", 1);
                edgePrefs.put("profile.default_content_settings.geolocation", 1);
                edgePrefs.put("profile.managed_default_content_settings.geolocation", 1);
                edgePrefs.put("profile.default_content_setting_values.notifications", 1);
                edgeOptions.setExperimentalOption("prefs", edgePrefs);
                edgeOptions.addArguments(
                    "--disable-notifications",
                    "--remote-allow-origins=*",
                    "--use-fake-ui-for-media-stream",
                    "--use-fake-device-for-media-stream",
                    "--enable-features=NetworkService,NetworkServiceInProcess"
                );
                if (headless) edgeOptions.addArguments("--headless=new", "--window-size=1920,1080");
                return new EdgeDriver(edgeOptions);
            case "chrome":
            default:
                WebDriverManager.chromedriver().setup();
                ChromeOptions chromeOptions = new ChromeOptions();
                java.util.Map<String, Object> chromePrefs = new java.util.HashMap<>();
                chromePrefs.put("profile.default_content_setting_values.geolocation", 1);
                chromePrefs.put("profile.default_content_settings.geolocation", 1);
                chromePrefs.put("profile.managed_default_content_settings.geolocation", 1);
                chromePrefs.put("profile.default_content_setting_values.notifications", 1);
                chromeOptions.setExperimentalOption("prefs", chromePrefs);
                chromeOptions.addArguments(
                    "--disable-notifications",
                    "--remote-allow-origins=*",
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-gpu",
                    "--disable-extensions",
                    "--disable-dev-shm",
                    "--use-fake-ui-for-media-stream",
                    "--use-fake-device-for-media-stream",
                    "--enable-features=NetworkService,NetworkServiceInProcess",
                    "--window-size=1920,1080"
                );
                if (headless) chromeOptions.addArguments("--headless=new", "--window-size=1920,1080");
                return new ChromeDriver(chromeOptions);
        }
    }

    public static void configure(WebDriver driver, ConfigReader config) {
        driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(config.getImplicitWait()));
        driver.manage().timeouts().pageLoadTimeout(java.time.Duration.ofSeconds(config.getPageLoadTimeout()));
        driver.manage().window().maximize();

        if (driver instanceof org.openqa.selenium.chromium.ChromiumDriver chromiumDriver) {
            try {
                java.util.Map<String, Object> grantParams = new java.util.HashMap<>();
                grantParams.put("permissions", java.util.List.of("geolocation", "notifications"));
                chromiumDriver.executeCdpCommand("Browser.grantPermissions", grantParams);

                java.util.Map<String, Object> geoParams = new java.util.HashMap<>();
                geoParams.put("latitude", 19.0760);
                geoParams.put("longitude", 72.8777);
                geoParams.put("accuracy", 100);
                chromiumDriver.executeCdpCommand("Emulation.setGeolocationOverride", geoParams);

                java.util.Map<String, Object> scriptParams = new java.util.HashMap<>();
                scriptParams.put("source",
                    "try {"
                    + "  if (navigator.geolocation) {"
                    + "    navigator.geolocation.getCurrentPosition = function(success, error, options) {"
                    + "      if (typeof success === 'function') {"
                    + "        success({ coords: { latitude: 19.0760, longitude: 72.8777, accuracy: 100, altitude: null, altitudeAccuracy: null, heading: null, speed: null }, timestamp: Date.now() });"
                    + "      }"
                    + "    };"
                    + "    navigator.geolocation.watchPosition = function(success, error, options) {"
                    + "      if (typeof success === 'function') {"
                    + "        success({ coords: { latitude: 19.0760, longitude: 72.8777, accuracy: 100, altitude: null, altitudeAccuracy: null, heading: null, speed: null }, timestamp: Date.now() });"
                    + "      }"
                    + "      return 1;"
                    + "    };"
                    + "  }"
                    + "  if (navigator.permissions && navigator.permissions.query) {"
                    + "    const origQuery = navigator.permissions.query.bind(navigator.permissions);"
                    + "    navigator.permissions.query = function(parameters) {"
                    + "      if (parameters && parameters.name === 'geolocation') {"
                    + "        return Promise.resolve({ state: 'granted', onchange: null });"
                    + "      }"
                    + "      return origQuery(parameters);"
                    + "    };"
                    + "  }"
                    + "} catch (e) {}"
                );
                chromiumDriver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", scriptParams);
            } catch (Exception e) {
                System.out.println("[WARN] CDP Geolocation setup note: " + e.getMessage());
            }
        }
    }
}
