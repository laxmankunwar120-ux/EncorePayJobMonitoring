package com.encorepay.utilities;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

public final class ScreenshotUtil {

    private static final String SCREENSHOT_DIR =
            System.getProperty("user.dir") + File.separator + "screenshots";

    private static final Map<String, List<Map<String, String>>> STEPS = new LinkedHashMap<>();
    private static final Map<String, List<String>> TEST_DATA = new LinkedHashMap<>();
    private static final Map<String, List<String>> VERIFICATIONS = new LinkedHashMap<>();
    private static final Map<String, Long> TEST_START = new LinkedHashMap<>();

    private static final ThreadLocal<String> CURRENT_TEST = new ThreadLocal<>();

    private ScreenshotUtil() {
    }

    public static void recordTestStart(String testName) {
        CURRENT_TEST.set(testName);
        TEST_START.put(testName, System.currentTimeMillis());
        stepsFor(testName);
        TEST_DATA.computeIfAbsent(testName, k -> new ArrayList<>());
        VERIFICATIONS.computeIfAbsent(testName, k -> new ArrayList<>());
    }

    public static void addCurrentTestData(String detail) {
        addDetail(TEST_DATA, currentTestName(null), detail);
    }

    public static void addCurrentTestVerification(String detail) {
        addDetail(VERIFICATIONS, currentTestName(null), detail);
    }

    /** Returns the repo-relative screenshot path so a failure message can point at it. */
    public static String captureCurrentTestStep(WebDriver driver, String stepLabel) {
        return captureScreenshot(driver, currentTestName(stepLabel), stepLabel);
    }

    /** Returns a repo-relative path, or an empty string when the capture was not possible. */
    public static String captureScreenshot(WebDriver driver, String testName, String stepLabel) {
        String label = stepLabel == null || stepLabel.isBlank() ? "Screenshot" : stepLabel;

        if (driver == null || !(driver instanceof TakesScreenshot)) {
            System.out.println("[WARN] Screenshot skipped, driver cannot capture. Label: " + label);
            registerStep(testName, label, "");
            return "";
        }

        String fileName = sanitizeFileName(testName) + "_" + System.currentTimeMillis() + ".png";
        String relativePath = "screenshots" + File.separator + fileName;

        try {
            File dir = new File(SCREENSHOT_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("Unable to create " + SCREENSHOT_DIR);
            }

            // Short wait so error pages do not stall the suite, but the capture still happens.
            waitForPageReady(driver, 3);

            File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            FileUtils.copyFile(source, new File(SCREENSHOT_DIR + File.separator + fileName));

            registerStep(testName, label, relativePath);
            System.out.println("[SCREENSHOT] " + label + " -> " + relativePath);
            return relativePath;
        } catch (Exception e) {
            System.out.println("[WARN] Screenshot failed for [" + label + "]: " + e.getMessage());
            registerStep(testName, label + " (capture failed)", "");
            return "";
        }
    }

    public static void cleanScreenshotsDirectory() {
        try {
            File dir = new File(SCREENSHOT_DIR);
            if (dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));
                if (files != null) {
                    for (File file : files) {
                        try {
                            file.delete();
                        } catch (Exception ignored) {
                            // A locked file is reported by the next run's cleanup.
                        }
                    }
                }
                System.out.println("[CLEANUP] Previous screenshots cleared.");
            } else if (!dir.exists()) {
                dir.mkdirs();
            }
        } catch (Exception e) {
            System.out.println("[WARN] Unable to clear screenshots: " + e.getMessage());
        }
        reset();
    }

    public static void reset() {
        STEPS.clear();
        TEST_DATA.clear();
        VERIFICATIONS.clear();
        TEST_START.clear();
        CURRENT_TEST.remove();
    }

    private static List<Map<String, String>> stepsFor(String testName) {
        return STEPS.computeIfAbsent(testName, k -> new ArrayList<>());
    }

    private static void registerStep(String testName, String label, String path) {
        Map<String, String> step = new HashMap<>();
        step.put("label", label);
        step.put("path", path);
        stepsFor(testName).add(step);
    }

    private static void addDetail(Map<String, List<String>> target, String testName, String detail) {
        String sanitized = sanitizeDetail(detail);
        if (sanitized.isBlank()) {
            return;
        }
        List<String> values = target.computeIfAbsent(testName, k -> new ArrayList<>());
        if (!values.contains(sanitized)) {
            values.add(sanitized);
        }
    }

    /** Falls back to the label so captures taken outside a test still land in a bucket. */
    private static String currentTestName(String fallbackLabel) {
        String name = CURRENT_TEST.get();
        if (name != null && !name.isBlank()) {
            return name;
        }

        String bucket = fallbackLabel == null || fallbackLabel.isBlank() ? "Suite_Setup" : fallbackLabel;
        recordTestStart(bucket);
        return bucket;
    }

    private static void waitForPageReady(WebDriver driver, int timeoutSeconds) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).until(d ->
                    "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
        } catch (Exception ignored) {
            // Error pages and broken scripts still deserve a screenshot.
        }
    }

    private static String sanitizeDetail(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value
                .replace("do_not_disturb_on", "")
                .replace("content_copy", "")
                .replace("close", "")
                .replaceAll("\\s+", " ")
                .trim();
        return sanitized.length() > 320 ? sanitized.substring(0, 317).trim() + "..." : sanitized;
    }

    private static String sanitizeFileName(String value) {
        String sanitized = value == null ? "screenshot" : value
                .replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_")
                .replaceAll("\\s+", "_")
                .trim();
        if (sanitized.isBlank()) {
            sanitized = "screenshot";
        }
        return sanitized.length() > 80 ? sanitized.substring(0, 80).trim() : sanitized;
    }
}
