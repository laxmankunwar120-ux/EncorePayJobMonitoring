package com.encorepay.utilities;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import com.encorepay.models.ClientConfig;


public class ConfigReader {
    private static final String CONFIG_PATH = "src/main/resources/config.properties";

    private final Properties properties;
    private final ClientConfig clientOverride;

    public ConfigReader() {
        this(null);
    }

    public ConfigReader(ClientConfig clientOverride) {
        this.properties = loadProperties();
        this.clientOverride = clientOverride;
    }

    private Properties loadProperties() {
        Properties p = new Properties();
        File file = new File(CONFIG_PATH);

        // The file is git-ignored so credentials stay out of source control. CI supplies
        // everything through environment variables, so its absence is expected.
        if (!file.exists()) {
            return p;
        }

        try (FileInputStream input = new FileInputStream(file)) {
            p.load(input);
            return p;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load config from " + CONFIG_PATH, e);
        }
    }

    public String getProperty(String key) {
        return getProperty(key, "");
    }

    public String getProperty(String key, String defaultValue) {
        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.isBlank()) return systemValue.trim();

        String value = properties.getProperty(key);
        if (value != null && !value.isBlank()) return value.trim();

        for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.equalsIgnoreCase(key)) {
                String val = properties.getProperty(propertyName);
                if (val != null && !val.isBlank()) return val.trim();
            }
        }

        return defaultValue;
    }

    public int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String getURL() {
        if (clientOverride != null && !clientOverride.getUrl().isBlank()) return clientOverride.getUrl();
        String env = System.getenv("APP_URL");
        return env == null || env.isBlank() ? getProperty("url") : env.trim();
    }

    public String getBrowser() {
        String env = System.getenv("BROWSER");
        return env == null || env.isBlank() ? getProperty("browser", "chrome") : env.trim();
    }

    public String getUsername() {
        if (clientOverride != null && !clientOverride.getUsername().isBlank()) return clientOverride.getUsername();
        String env = System.getenv("ADMIN_USERNAME");
        return env == null || env.isBlank() ? getProperty("username") : env.trim();
    }

    public String getPassword() {
        if (clientOverride != null && !clientOverride.getPassword().isBlank()) return clientOverride.getPassword();
        String env = System.getenv("ADMIN_PASSWORD");
        return env == null || env.isBlank() ? getProperty("password") : env.trim();
    }

    public int getImplicitWait() {
        return getIntProperty("implicitWait", 0);
    }

    public int getExplicitWait() {
        return getIntProperty("explicitWait", 20);
    }

    /**
     * The business timezone that Job Monitoring reports in.
     *
     * Timestamps shown by the application are rendered by the browser's own date formatting, so the
     * browser has to run in this zone for a report to read the same everywhere. The Java side uses
     * the same zone for "business today" and for report metadata. Default Asia/Kolkata.
     */
    public ZoneId getBusinessZone() {
        String zone = getProperty("businessZone", "Asia/Kolkata");
        if (zone == null || zone.isBlank()) zone = "Asia/Kolkata";

        try {
            return ZoneId.of(zone.trim());
        } catch (DateTimeException e) {
            System.out.println("[WARN] Invalid businessZone '" + zone + "', falling back to Asia/Kolkata.");
            return ZoneId.of("Asia/Kolkata");
        }
    }

    public int getPageLoadTimeout() {
        return getIntProperty("pageLoadTimeout", 45);
    }

    /**
     * Budget for the Angular app to bootstrap and render its first view. This is separate from
     * the element wait because a cold CI runner can take far longer to load the bundle and
     * first API call than a warm developer machine, and that delay is not an element problem.
     */
    public int getBootTimeout() {
        return getIntProperty("bootTimeout", 90);
    }

    public int getOverlayTimeout() {
        return getIntProperty("overlayTimeout", 8);
    }

    public int getTransientFeedbackTimeout() {
        return getIntProperty("transientFeedbackTimeout", 10);
    }

    public String getClientName() {
        if (clientOverride != null && !clientOverride.getName().isBlank()) return clientOverride.getName();
        String env = System.getenv("CLIENT_NAME");
        if (env != null && !env.isBlank()) return env.trim();
        String configured = getProperty("clientName", "");
        if (!configured.isBlank()) return configured;
        return deriveClientName(getURL());
    }

    public List<ClientConfig> getClients() {
        List<String> urls = readList("CLIENT_URLS", "clientUrls", "client.urls");
        List<ClientConfig> indexedClients = readIndexedClients();

        if (!urls.isEmpty() || !indexedClients.isEmpty()) {
            List<ClientConfig> mergedClients = mergeClients(buildClients(urls), indexedClients);
            if (!urls.isEmpty() && !indexedClients.isEmpty()) {
                System.out.println("[CONFIG] Client source: merged CLIENT_URLS + CLIENT_N_URL values, "
                    + mergedClients.size() + " client(s) configured.");
            } else if (!urls.isEmpty()) {
                System.out.println("[CONFIG] Client source: CLIENT_URLS, " + mergedClients.size()
                    + " client(s) configured.");
            } else {
                System.out.println("[CONFIG] Client source: CLIENT_N_URL secrets, " + mergedClients.size()
                    + " client(s) configured.");
            }
            warnAboutMissingIndexedCredentials(mergedClients);
            return mergedClients;
        }

        String url = getURL();
        if (url.isBlank()) throw new IllegalStateException("No client URL configured. Set url, APP_URL, CLIENT_URLS, or CLIENT_1_URL.");
        return List.of(new ClientConfig(getClientName(), url, getUsername(), getPassword(), false));
    }

    /**
     * CLIENT_URLS and CLIENT_N_URL are merged so a partially filled CLIENT_URLS list does not silently drop
     * a configured client. A repeated URL is deduplicated by host and path, and the first source wins.
     */
    private List<ClientConfig> mergeClients(List<ClientConfig> fromUrls, List<ClientConfig> indexedClients) {
        Map<String, ClientConfig> merged = new LinkedHashMap<>();

        for (ClientConfig client : fromUrls) {
            if (client == null || client.getUrl() == null || client.getUrl().isBlank()) continue;
            merged.putIfAbsent(normalizeUrl(client.getUrl()), client);
        }

        for (ClientConfig client : indexedClients) {
            if (client == null || client.getUrl() == null || client.getUrl().isBlank()) continue;
            String key = normalizeUrl(client.getUrl());
            if (!merged.containsKey(key)) {
                merged.put(key, client);
            }
        }

        return new ArrayList<>(merged.values());
    }

    /** Reports a configured client that has no credentials, which would fail at sign-in. */
    private void warnAboutMissingIndexedCredentials(List<ClientConfig> clients) {
        for (ClientConfig client : clients) {
            if (isBlank(client.getUsername()) || isBlank(client.getPassword())) {
                System.out.println("[WARN] Client '" + safeName(client) + "' (" + client.getUrl()
                        + ") has no username or password configured and will not be able to sign in.");
            }
        }
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        String trimmed = url.trim();
        try {
            URI uri = URI.create(trimmed.matches("(?i)^https?://.*") ? trimmed : "https://" + trimmed);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            String authority = host + (port > 0 ? ":" + port : "");
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/{2,}", "/");
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            String query = uri.getQuery() == null ? "" : "?" + uri.getQuery();
            return (authority + path + query).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return trimmed.toLowerCase(Locale.ROOT).replaceAll("/+$", "");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safeName(ClientConfig client) {
        String name = client.getName();
        return name == null || name.isBlank() ? "(unnamed)" : name;
    }

    private List<ClientConfig> buildClients(List<String> urls) {
        List<ClientConfig> clients = new ArrayList<>();
        String globalName = firstNonBlank(System.getenv("CLIENT_NAME"), getProperty("clientName", ""));
        for (int index = 0; index < urls.size(); index++) {
            int number = index + 1;
            String url = urls.get(index);
            String name = firstNonBlank(
                indexedValue(number, "NAME", "name"),
                urls.size() == 1 ? globalName : deriveClientName(url)
            );
            String username = firstNonBlank(
                System.getenv("CLIENT_" + number + "_USERNAME"),
                System.getenv("CLIENT_" + number + "_ADMIN_USERNAME"),
                indexedValue(number, "USERNAME", "username"),
                getUsername()
            );
            String password = firstNonBlank(
                System.getenv("CLIENT_" + number + "_PASSWORD"),
                System.getenv("CLIENT_" + number + "_ADMIN_PASSWORD"),
                indexedValue(number, "PASSWORD", "password"),
                getPassword()
            );
            boolean sso = Boolean.parseBoolean(firstNonBlank(
                System.getenv("CLIENT_" + number + "_SSO"),
                getProperty("client." + number + ".sso", "false")
            ));  
            clients.add(new ClientConfig(name, url, username, password, sso));
        }
        return clients;
    }

    private List<ClientConfig> readIndexedClients() {
        List<ClientConfig> clients = new ArrayList<>();
        for (int number = 1; number <= 100; number++) {
            String url = firstNonBlank(
                System.getenv("CLIENT_" + number + "_URL"),
                getProperty("client." + number + ".url", ""),
                getProperty("client" + number + "Url", "")
            );
            if (url.isBlank()) continue;
            String name = firstNonBlank(
                System.getenv("CLIENT_" + number + "_NAME"),
                getProperty("client." + number + ".name", ""),
                deriveClientName(url)
            );
            String username = firstNonBlank(
                System.getenv("CLIENT_" + number + "_USERNAME"),
                System.getenv("CLIENT_" + number + "_ADMIN_USERNAME"),
                getProperty("client." + number + ".username", ""),
                getUsername()
            );
            String password = firstNonBlank(
                System.getenv("CLIENT_" + number + "_PASSWORD"),
                System.getenv("CLIENT_" + number + "_ADMIN_PASSWORD"),
                getProperty("client." + number + ".password", ""),
                getPassword()
            );
            boolean sso = Boolean.parseBoolean(firstNonBlank(
                System.getenv("CLIENT_" + number + "_SSO"),
                getProperty("client." + number + ".sso", "false")
            ));
            clients.add(new ClientConfig(name, url, username, password, sso));
        }
        return clients;
    }

    private List<String> readList(String envName, String... propertyNames) {
        String value = System.getenv(envName);
        if (value == null || value.isBlank()) {
            for (String propertyName : propertyNames) {
                value = getProperty(propertyName, "");
                if (!value.isBlank()) break;
            }
        }
        if (value.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String item : value.split("[,;\n\r]+")) {
            String trimmed = item.trim();
            if (!trimmed.isBlank()) result.add(trimmed);
        }
        return result;
    }

    private String indexedValue(int number, String envSuffix, String propertySuffix) {
        return firstNonBlank(
            System.getenv("CLIENT_" + number + "_" + envSuffix),
            getProperty("client." + number + "." + propertySuffix, ""),
            getProperty("client" + number + propertySuffix, "")
        );
    }

    private String deriveClientName(String url) {
        try {
            String normalized = url.matches("(?i)^https?://.*") ? url : "https://" + url;
            String host = URI.create(normalized).getHost();
            if (host == null || host.isBlank()) return "";
            String[] labels = host.toLowerCase(Locale.ROOT).split("\\.");
            for (String label : labels) {
                if (label.isBlank() || label.equals("uat") || label.equals("test") || label.equals("qa") || label.equals("prod") || label.equals("www")) continue;
                return formatClient(label);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String formatClient(String value) {
        if (value.equalsIgnoreCase("prayaancapital")) return "Prayaan Capital";
        if (value.equalsIgnoreCase("encorecollections")) return "Encore Collections";
        String spaced = value.replace('-', ' ').replace('_', ' ');
        return Arrays.stream(spaced.split("\\s+"))
            .filter(s -> !s.isBlank())
            .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
            .reduce((a, b) -> a + " " + b)
            .orElse("");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    public boolean isSsoEnabled() {
        if (clientOverride != null) {
            return clientOverride.isSso();
        }
        return Boolean.parseBoolean(getProperty("sso", "false"));
    }

    public String getGoogleChatWebhookUrl() {
        String env = System.getenv("GOOGLE_CHAT_WEBHOOK_URL");
        return env == null || env.isBlank()
            ? getProperty("googleChatWebhookUrl", "")
            : env.trim();
    }

    public String getGoogleChatOAuthClientId() {
        String env = System.getenv("GOOGLE_CHAT_OAUTH_CLIENT_ID");
        return env == null || env.isBlank()
            ? getProperty("googleChatOAuthClientId", "")
            : env.trim();
    }

    public String getGoogleChatOAuthClientSecret() {
        String env = System.getenv("GOOGLE_CHAT_OAUTH_CLIENT_SECRET");
        return env == null || env.isBlank()
            ? getProperty("googleChatOAuthClientSecret", "")
            : env.trim();
    }

    public String getGoogleChatOAuthRefreshToken() {
        String env = System.getenv("GOOGLE_CHAT_OAUTH_REFRESH_TOKEN");
        return env == null || env.isBlank()
            ? getProperty("googleChatOAuthRefreshToken", "")
            : env.trim();
    }

    public boolean isEmailNotificationEnabled() {
        String env = System.getenv("REPORT_EMAIL_ENABLED");
        return env == null || env.isBlank()
            ? Boolean.parseBoolean(getProperty("reportEmailEnabled", "false"))
            : Boolean.parseBoolean(env);
    }

    public String getEmailTo() {
        String env = System.getenv("REPORT_EMAIL_TO");
        return env == null || env.isBlank() ? getProperty("reportEmailTo", "") : env.trim();
    }

    public String getEmailCc() {
        String env = System.getenv("REPORT_EMAIL_CC");
        return env == null || env.isBlank() ? getProperty("reportEmailCc", "") : env.trim();
    }

    public String getSmtpHost() {
        String env = System.getenv("SMTP_HOST");
        return env == null || env.isBlank() ? getProperty("smtpHost", "") : env.trim();
    }

    public int getSmtpPort() {
        String env = System.getenv("SMTP_PORT");
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return getIntProperty("smtpPort", 587);
    }

    public String getSmtpUsername() {
        String env = System.getenv("SMTP_USERNAME");
        if (env != null && !env.isBlank()) return env.trim();
        String configured = getProperty("smtpUsername", "");
        if (!configured.isBlank()) return configured;
        return getEmailTo();
    }

    public String getSmtpPassword() {
        String env = System.getenv("SMTP_PASSWORD");
        return env == null || env.isBlank() ? getProperty("smtpPassword", "") : env.trim();
    }
}
