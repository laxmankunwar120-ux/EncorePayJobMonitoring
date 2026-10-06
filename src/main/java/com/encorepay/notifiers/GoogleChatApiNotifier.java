package com.encorepay.notifiers;

import com.encorepay.utilities.ConfigReader;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GoogleChatApiNotifier {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String CHAT_API = "https://chat.googleapis.com";
    private static final Pattern CHAT_SPACE =
            Pattern.compile("/v1/spaces/([^/]+)/messages(?:$|\\?)");
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private GoogleChatApiNotifier() {
    }

    static void send(String webhook, String htmlReportPath, String message) throws Exception {
        ConfigReader config = new ConfigReader();

        if (!hasOAuthCredentials(config)) {
            sendWebhook(webhook, message);
            return;
        }

        try {
            String accessToken = refreshAccessToken(config);
            String space = extractSpace(webhook);
            JsonObject attachment = null;

            if (htmlReportPath != null && !htmlReportPath.isBlank()) {
                Path report = Path.of(htmlReportPath);
                if (!Files.isRegularFile(report)) {
                    throw new IllegalStateException("HTML report file was not found: " + htmlReportPath);
                }
                attachment = uploadAttachment(accessToken, space, report);
            }

            sendApiMessage(accessToken, space, message, attachment);

            System.out.println(attachment == null
                    ? "[INFO] Google Chat API message sent successfully."
                    : "[INFO] Google Chat API message sent with HTML attachment.");
        } catch (Exception oauthException) {
            System.err.println("[WARN] Google Chat OAuth notification failed: "
                    + abbreviate(oauthException.getMessage(), 500)
                    + ". Falling back to the existing webhook notification.");
            sendWebhook(webhook, message);
            System.out.println("[INFO] Google Chat webhook fallback notification sent successfully.");
        }
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
                .uri(URI.create(CHAT_API + "/upload/v1/" + space + "/attachments:upload?uploadType=multipart"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "multipart/related; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Google Chat attachment upload returned HTTP "
                    + response.statusCode() + ": " + abbreviate(response.body(), 500));
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static void sendApiMessage(String accessToken, String space, String message,
                                         JsonObject attachment) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("text", message);

        if (attachment != null) {
            JsonArray attachments = new JsonArray();
            attachments.add(attachment);
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
        String fileName = report.getFileName().toString();
        String metadata = "{\"filename\":\"" + escapeJson(fileName) + "\"}";
        byte[] fileBytes = Files.readAllBytes(report);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Type: application/json; charset=UTF-8\r\n\r\n");
        write(out, metadata + "\r\n");
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Type: text/html; charset=UTF-8\r\n\r\n");
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