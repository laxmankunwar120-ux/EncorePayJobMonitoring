package com.encorepay.base;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import com.encorepay.utilities.ConfigReader;

import io.github.bonigarcia.wdm.WebDriverManager;

public final class WebDriverFactory {

    private WebDriverFactory() {
    }

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
                Map<String, Object> edgePrefs = new HashMap<>();
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
                Map<String, Object> chromePrefs = new HashMap<>();
                chromePrefs.put("profile.default_content_setting_values.geolocation", 1);
                chromePrefs.put("profile.default_content_settings.geolocation", 1);
                chromePrefs.put("profile.managed_default_content_settings.geolocation", 1);
                chromePrefs.put("profile.managed_default_content_settings.notifications", 1);
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
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(config.getImplicitWait()));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(config.getPageLoadTimeout()));

        try {
            driver.manage().window().setSize(new org.openqa.selenium.Dimension(1920, 1080));
        } catch (Exception e) {
            System.out.println("[WARN] Browser window resize skipped: " + e.getMessage());
        }

        if (driver instanceof ChromiumDriver chromiumDriver) {
            try {
                Map<String, Object> timezoneParams = new HashMap<>();
                timezoneParams.put("timezoneId", config.getBusinessZone().getId());
                chromiumDriver.executeCdpCommand("Emulation.setTimezoneOverride", timezoneParams);

                System.out.println("[INFO] Browser timezone set to " + config.getBusinessZone().getId()
                    + " (JVM default is " + java.time.ZoneId.systemDefault().getId() + ").");

                Map<String, Object> grantParams = new HashMap<>();
                grantParams.put("permissions", List.of("geolocation", "notifications"));
                chromiumDriver.executeCdpCommand("Browser.grantPermissions", grantParams);

                Map<String, Object> geoParams = new HashMap<>();
                geoParams.put("latitude", 19.0760);
                geoParams.put("longitude", 72.8777);
                geoParams.put("accuracy", 100);
                chromiumDriver.executeCdpCommand("Emulation.setGeolocationOverride", geoParams);

                Map<String, Object> scriptParams = new HashMap<>();
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
