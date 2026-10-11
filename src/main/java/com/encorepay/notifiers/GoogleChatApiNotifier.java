package com.encorepay.notifiers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import com.encorepay.utilities.ConfigReader;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class GoogleChatApiNotifier {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String CHAT_API = "https://chat.googleapis.com";
    private static final Pattern CHAT_SPACE =
            Pattern.compile("/v1/spaces/([^/]+)/messages(?:$|\\?)");
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private GoogleChatApiNotifier() {
    }

    static void send(String webhook, String htmlReportPath) throws Exception {
        ConfigReader config = new ConfigReader();

        if (!hasOAuthCredentials(config)) {
            System.out.println("[WARN] Google Chat OAuth credentials are not configured"
                + " (GOOGLE_CHAT_OAUTH_CLIENT_ID, GOOGLE_CHAT_OAUTH_CLIENT_SECRET,"
                + " GOOGLE_CHAT_OAUTH_REFRESH_TOKEN). Incoming webhooks cannot carry file attachments,"
                + " so the report could not be attached to this notification.");
            emitAnnotation("warning", "Google Chat OAuth credentials are not configured",
                "Incoming webhooks cannot carry file attachments, so the report was not attached to"
                + " this notification. Set GOOGLE_CHAT_OAUTH_CLIENT_ID, GOOGLE_CHAT_OAUTH_CLIENT_SECRET"
                + " and GOOGLE_CHAT_OAUTH_REFRESH_TOKEN to attach the report.");
            sendFallback(webhook);
            return;
        }

        try {
            String accessToken = refreshAccessToken(config);
            String space = extractSpace(webhook);
            Path report = resolveReport(htmlReportPath);

            JsonArray attachments = new JsonArray();
            attachments.add(uploadAttachment(accessToken, space, screenshotReport(report)));

            sendApiMessage(accessToken, space, "Hi Team, Job status report.", attachments);

            if (attachments.size() > 0) {
                System.out.println("[INFO] Google Chat API message sent with "
                        + attachments.size() + " attachment(s).");
                emitAnnotation("notice", "Google Chat notification sent",
                        "Job status report message sent with the full page screenshot. The HTML report"
                        + " is attached to this run's encorepay-html-report artifact.");
            }
        } catch (Exception oauthException) {
            System.err.println("[ERROR] Google Chat OAuth notification failed: "
                    + abbreviate(oauthException.getMessage(), 500)
                    + ". " + describeOAuthFailure(oauthException));
            emitAnnotation("error", "Google Chat report attachment failed",
                    abbreviate(oauthException.getMessage(), 300) + " " + describeOAuthFailure(oauthException)
                    + " A short fallback notification was sent through the webhook.");
            sendFallback(webhook);
        }
    }

    private static void sendFallback(String webhook) {
        String runUrl = runUrl();
        String text = "Job status report: the report could not be attached."
                + (runUrl.isBlank() ? "" : " Open the GitHub run: " + runUrl);

        try {
            sendWebhook(webhook, text);
            System.out.println("[INFO] Google Chat fallback notification posted.");
        } catch (Exception fallbackError) {
            System.err.println("[ERROR] Google Chat fallback notification failed: "
                    + abbreviate(fallbackError.getMessage(), 300));
        }
    }

    private static Path resolveReport(String htmlReportPath) {
        if (htmlReportPath == null || htmlReportPath.isBlank()) {
            throw new IllegalStateException(
                    "The HTML report path is not available, so the report cannot be attached.");
        }

        Path report = Path.of(htmlReportPath);
        if (!Files.isRegularFile(report)) {
            throw new IllegalStateException("HTML report file was not found: " + htmlReportPath);
        }
        return report;
    }

    private static String runUrl() {
        String reportUrl = env("REPORT_URL");
        if (!reportUrl.isBlank()) {
            return reportUrl;
        }

        String server = env("GITHUB_SERVER_URL");
        String repository = env("GITHUB_REPOSITORY");
        String runId = env("GITHUB_RUN_ID");
        if (!server.isBlank() && !repository.isBlank() && !runId.isBlank()) {
            return server + "/" + repository + "/actions/runs/" + runId + "#artifacts";
        }
        return "";
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    private static void emitAnnotation(String level, String title, String message) {
        if (!"true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"))) {
            return;
        }
        System.out.println("::" + level + "::" + title + " - " + escapeAnnotation(message));
    }

    private static String escapeAnnotation(String value) {
        return value
                .replace("%", "%25")
                .replace("\r", "%0D")
                .replace("\n", "%0A");
    }

    private static String describeOAuthFailure(Throwable error) {
        String message = String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains("invalid_grant")) {
            return "The refresh token is expired or revoked. Google expires refresh tokens 7 days after"
                + " creation while the OAuth app is in Testing mode - re-authorize to obtain a fresh one.";
        }
        if (message.contains("invalid_client")) {
            return "The OAuth client ID or secret is rejected. Check GOOGLE_CHAT_OAUTH_CLIENT_ID"
                + " and GOOGLE_CHAT_OAUTH_CLIENT_SECRET.";
        }
        if (message.contains("403") || message.contains("forbidden")
                || message.contains("permission_denied") || message.contains("permission denied")) {
            return "Permission denied. The OAuth consent screen needs the"
                + " https://www.googleapis.com/auth/chat.messages.create scope, and the user who"
                + " authorized the token must still be a member of the target space.";
        }
        if (message.contains("404") || message.contains("not found")) {
            return "The space was not found. GOOGLE_CHAT_WEBHOOK_URL must name the same space the"
                + " OAuth credentials can access.";
        }
        return "Verify the OAuth trio and that the Google Chat API is enabled in the Cloud project.";
    }

    private static boolean hasOAuthCredentials(ConfigReader config) {
        return !blank(config.getGoogleChatOAuthClientId())
                && !blank(config.getGoogleChatOAuthClientSecret())
                && !blank(config.getGoogleChatOAuthRefreshToken());
    }

    private static void sendWebhook(String webhook, String message) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("text", message);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhook))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Google Chat webhook returned HTTP "
                    + response.statusCode() + ": " + abbreviate(response.body(), 500));
        }
    }

    @SuppressWarnings("unchecked")
    private static Path screenshotReport(Path html) throws Exception {
        String base = html.getFileName().toString().replaceAll("\\.html?$", "");
        Path png = html.toAbsolutePath().resolveSibling(base + ".png");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-gpu",
                "--disable-dev-shm-usage", "--hide-scrollbars", "--window-size=1600,1000");
        ChromeDriver driver = new ChromeDriver(options);
        try {
            driver.get(html.toAbsolutePath().toUri().toString());
            Map<String, Object> metrics = driver.executeCdpCommand("Page.getLayoutMetrics", Map.of());
            Map<String, Object> size = (Map<String, Object>) (metrics.containsKey("cssContentSize")
                    ? metrics.get("cssContentSize") : metrics.get("contentSize"));
            double width = ((Number) size.get("width")).doubleValue();
            double height = ((Number) size.get("height")).doubleValue();

            Map<String, Object> clip = Map.of("x", 0, "y", 0, "width", width,
                    "height", height, "scale", 2);
            Map<String, Object> shot = driver.executeCdpCommand("Page.captureScreenshot",
                    Map.of("format", "png", "captureBeyondViewport", true,
                           "fromSurface", true, "clip", clip));
            Files.write(png, Base64.getDecoder().decode((String) shot.get("data")));
            return png;
        } finally {
            driver.quit();
        }
    }

    private static String refreshAccessToken(ConfigReader config) throws Exception {
        String form = "client_id=" + encode(config.getGoogleChatOAuthClientId())
                + "&client_secret=" + encode(config.getGoogleChatOAuthClientSecret())
                + "&refresh_token=" + encode(config.getGoogleChatOAuthRefreshToken())
                + "&grant_type=refresh_token";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Google OAuth token refresh returned HTTP "
                    + response.statusCode() + ": " + abbreviate(response.body(), 500));
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String accessToken = json.has("access_token") ? json.get("access_token").getAsString() : "";
        if (accessToken.isBlank()) {
            throw new IllegalStateException("Google OAuth token refresh returned no access_token.");
        }
        return accessToken;
    }

    private static JsonObject uploadAttachment(String accessToken, String space, Path report) throws Exception {
        String boundary = "EncorePayBoundary" + System.nanoTime();
        byte[] body = buildMultipartBody(boundary, report);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CHAT_API + "/upload/v1/" + space + "/attachments:upload"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Google Chat attachment upload returned HTTP "
                    + response.statusCode() + ": " + abbreviate(response.body(), 500));
        }
        JsonObject uploaded = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject dataRef = uploaded.has("attachmentDataRef")
                ? uploaded.getAsJsonObject("attachmentDataRef")
                : null;

        if (dataRef == null || !dataRef.has("attachmentUploadToken")) {
            throw new IllegalStateException(
                    "Google Chat attachment upload returned no attachmentUploadToken: "
                    + abbreviate(response.body(), 800));
        }

        JsonObject attachmentRef = new JsonObject();
        attachmentRef.add("attachmentDataRef", dataRef);
        return attachmentRef;
    }

    private static void sendApiMessage(String accessToken, String space, String message,
                                        JsonArray attachments) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("text", message);

        if (attachments != null && attachments.size() > 0) {
            body.add("attachment", attachments);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CHAT_API + "/v1/" + space + "/messages"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Google Chat API returned HTTP "
                    + response.statusCode() + ": " + abbreviate(response.body(), 500));
        }
    }

    private static byte[] buildMultipartBody(String boundary, Path report) throws IOException {
        String fileName = escapeHeader(report.getFileName().toString());
        String metadata = "{\"filename\":\"" + escapeJson(fileName) + "\"}";
        byte[] fileBytes = Files.readAllBytes(report);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"metadata\"\r\n");
        write(out, "Content-Type: application/json; charset=UTF-8\r\n\r\n");
        write(out, metadata + "\r\n");

        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n");
        String lower = fileName.toLowerCase();
        String contentType = lower.endsWith(".png") ? "image/png"
                : lower.endsWith(".zip") ? "application/zip"
                : lower.endsWith(".pdf") ? "application/pdf"
                : "text/html; charset=UTF-8";
        write(out, "Content-Type: " + contentType + "\r\n\r\n");
        out.write(fileBytes);
        write(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String extractSpace(String webhook) {
        Matcher matcher = CHAT_SPACE.matcher(webhook == null ? "" : webhook);
        if (!matcher.find()) {
            throw new IllegalStateException("Unable to determine the Google Chat space from GOOGLE_CHAT_WEBHOOK_URL.");
        }
        return "spaces/" + matcher.group(1);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeHeader(String value) {
        return value.replace("\\", "_").replace("\"", "_").replace("\r", "_").replace("\n", "_");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String abbreviate(String value, int maxLength) {
        String text = value == null ? "" : value.replace("\n", " ").replace("\r", " ").trim();
        return text.length() <= maxLength ? text : text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
