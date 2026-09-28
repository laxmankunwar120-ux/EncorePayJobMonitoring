package com.encorepay.utilities;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ScreenshotUtil {

    private static final String PROJECT_ROOT   = System.getProperty("user.dir");
    private static final String SCREENSHOT_DIR = PROJECT_ROOT + File.separator + "screenshots";

    private static final Map<String, List<Map<String, String>>> allSteps          = new LinkedHashMap<>();
    private static final Map<String, Long>                      testStart         = new HashMap<>();
    private static final Map<String, Long>                      testEnd           = new HashMap<>();
    private static final Map<String, String>                    testStatus        = new HashMap<>();
    private static final Map<String, List<String>>              testDataDetails   = new LinkedHashMap<>();
    private static final Map<String, List<String>>              verificationDetails = new LinkedHashMap<>();

    private static final ThreadLocal<String> currentTestName = new ThreadLocal<>();

    // ── Getters ───────────────────────────────────────────────────────────────

    public static Map<String, List<Map<String, String>>> getAllSteps()          { return allSteps; }
    public static Map<String, Long>                      getTestStart()        { return testStart; }
    public static Map<String, Long>                      getTestEnd()          { return testEnd; }
    public static Map<String, String>                    getTestStatus()       { return testStatus; }
    public static Map<String, List<String>>              getTestDataDetails()  { return testDataDetails; }
    public static Map<String, List<String>>              getVerificationDetails() { return verificationDetails; }

    // ── Test lifecycle ────────────────────────────────────────────────────────

    public static void recordTestStart(String testName) {
        currentTestName.set(testName);
        testStart.put(testName, System.currentTimeMillis());
        allSteps.putIfAbsent(testName, new ArrayList<>());
        testDataDetails.putIfAbsent(testName, new ArrayList<>());
        verificationDetails.putIfAbsent(testName, new ArrayList<>());
    }

    public static void recordTestEnd(String testName, String status) {
        testEnd.put(testName, System.currentTimeMillis());
        testStatus.put(testName, status);
        currentTestName.remove();
    }

    // ── Core screenshot capture ───────────────────────────────────────────────

    /**
     * Full capture: named test + step label.
     * Safe to call even if driver is null, not ready, or page is a 404/error.
     */
    public static String captureScreenshot(WebDriver driver, String testName, String stepLabel) {

        // ── Guard: driver must exist and be capable of taking screenshots ──
        if (driver == null) {
            System.out.println("[WARN] Screenshot skipped — driver is null. Label: " + stepLabel);
            return "";
        }
        if (!(driver instanceof TakesScreenshot)) {
            System.out.println("[WARN] Screenshot skipped — driver does not support screenshots. Label: " + stepLabel);
            return "";
        }

        String resolvedLabel = (stepLabel != null && !stepLabel.isBlank()) ? stepLabel : "Screenshot";
        String fileName      = sanitizeFileName(testName) + "_" + System.currentTimeMillis() + ".png";
        String fullPath      = SCREENSHOT_DIR + File.separator + fileName;
        String relativePath  = "screenshots/" + fileName;

        try {
            // ── Ensure screenshot directory exists ─────────────────────────
            File dir = new File(SCREENSHOT_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // ── Wait for page readiness — short timeout, non-fatal ─────────
            // Uses 3 s (not 10 s) so error pages (404, 503) don't hang the suite.
            waitForPageReady(driver, 3);

            // ── Take the screenshot ────────────────────────────────────────
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            FileUtils.copyFile(src, new File(fullPath));

            // ── Register step ──────────────────────────────────────────────
            Map<String, String> step = new HashMap<>();
            step.put("label", resolvedLabel);
            step.put("path",  relativePath);
            allSteps.computeIfAbsent(testName, k -> new ArrayList<>()).add(step);

            System.out.println("[SCREENSHOT] " + resolvedLabel + " → " + relativePath);
            return relativePath;

        } catch (Exception e) {
            System.out.println("[WARN] Screenshot failed for [" + resolvedLabel + "]: " + e.getMessage());

            // ── Register a no-image step so the report still shows the label ──
            Map<String, String> step = new HashMap<>();
            step.put("label", resolvedLabel + " (capture failed)");
            step.put("path",  "");
            allSteps.computeIfAbsent(testName, k -> new ArrayList<>()).add(step);

            return "";
        }
    }

    public static String captureScreenshot(WebDriver driver, String testName) {
        return captureScreenshot(driver, testName, "Screenshot");
    }

    /**
     * Captures a screenshot under the currently-running test name.
     * Also works during @BeforeSuite (uses the provided label as the test bucket).
     */
    public static String captureCurrentTestStep(WebDriver driver, String stepLabel) {
        return captureScreenshot(driver, ensureCurrentTestName(stepLabel), stepLabel);
    }

    // ── Step registration (no screenshot) ────────────────────────────────────

    public static void addStep(String testName, String label) {
        Map<String, String> step = new HashMap<>();
        step.put("label", label);
        step.put("path",  "");
        allSteps.computeIfAbsent(testName, k -> new ArrayList<>()).add(step);
    }

    // ── Verification & test-data recording ───────────────────────────────────

    public static void addCurrentTestVerification(String detail) {
        addVerification(ensureCurrentTestName(null), detail);
    }

    public static void addCurrentTestData(String detail) {
        addTestData(ensureCurrentTestName(null), detail);
    }

    public static void addTestData(String testName, String detail) {
        String sanitized = sanitizeDetail(detail);
        if (sanitized.isBlank()) return;
        List<String> list   = testDataDetails.computeIfAbsent(testName, k -> new ArrayList<>());
        Set<String>  dedupe = new LinkedHashSet<>(list);
        if (dedupe.add(sanitized)) { list.clear(); list.addAll(dedupe); }
    }

    public static void addVerification(String testName, String detail) {
        String sanitized = sanitizeDetail(detail);
        if (sanitized.isBlank()) return;
        List<String> list   = verificationDetails.computeIfAbsent(testName, k -> new ArrayList<>());
        Set<String>  dedupe = new LinkedHashSet<>(list);
        if (dedupe.add(sanitized)) { list.clear(); list.addAll(dedupe); }
    }

    // ── Summary helpers ───────────────────────────────────────────────────────

    public static String getVerificationSummary(String testName) {
        List<String> d = verificationDetails.getOrDefault(testName, List.of());
        return d.isEmpty() ? "" : String.join(" | ", d);
    }

    public static String getTestDataSummary(String testName) {
        List<String> d = testDataDetails.getOrDefault(testName, List.of());
        return d.isEmpty() ? "" : String.join(" | ", d);
    }

    public static String getTestDataMultiline(String testName) {
        return buildMultiline(testDataDetails.getOrDefault(testName, List.of()));
    }

    public static String getVerificationMultiline(String testName) {
        return buildMultiline(verificationDetails.getOrDefault(testName, List.of()));
    }

    private static String buildMultiline(List<String> details) {
        if (details.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String d : details) {
            if (sb.length() > 0) sb.append('\n');
            sb.append("- ").append(d);
        }
        return sb.toString();
    }

    // ── Cleanup & Reset ───────────────────────────────────────────────────────

    public static void cleanScreenshotsDirectory() {
        try {
            File dir = new File(SCREENSHOT_DIR);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));
                if (files != null) {
                    for (File f : files) {
                        try {
                            f.delete();
                        } catch (Exception ignored) {}
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
        allSteps.clear();
        testStart.clear();
        testEnd.clear();
        testStatus.clear();
        testDataDetails.clear();
        verificationDetails.clear();
        currentTestName.remove();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Waits for document.readyState == "complete" up to timeoutSeconds.
     * Non-fatal — if the page is an error page or JS is broken, we still take the screenshot.
     */
    private static void waitForPageReady(WebDriver driver, int timeoutSeconds) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).until(d ->
                "complete".equals(
                    ((JavascriptExecutor) d).executeScript("return document.readyState")
                )
            );
        } catch (Exception ignored) {
            // Page not fully ready (e.g. 404, broken JS) — screenshot anyway
        }
    }

    /**
     * Returns the current test name if set, otherwise uses the step label
     * (useful during @BeforeSuite / @AfterSuite where no test is active).
     */
    private static String ensureCurrentTestName(String fallbackLabel) {
        String name = currentTestName.get();
        if (name != null && !name.isBlank()) return name;

        // During suite setup/teardown there is no active test — use the label
        // as the bucket name so the screenshot lands somewhere meaningful.
        String bucket = (fallbackLabel != null && !fallbackLabel.isBlank())
            ? fallbackLabel
            : "Suite_Setup";

        recordTestStart(bucket);
        return bucket;
    }

    private static String sanitizeDetail(String value) {
        if (value == null) return "";
        String s = value
            .replace("do_not_disturb_on", "")
            .replace("content_copy", "")
            .replace("close", "")
            .replaceAll("\\s+", " ")
            .trim();
        return s.length() > 320 ? s.substring(0, 317).trim() + "..." : s;
    }

    private static String sanitizeFileName(String value) {
        String s = value == null ? "screenshot" : value
            .replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_")
            .replaceAll("\\s+", "_")
            .trim();
        if (s.isBlank()) s = "screenshot";
        return s.length() > 80 ? s.substring(0, 80).trim() : s;
    }
}