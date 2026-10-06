package com.encorepay.models;

public final class ClientConfig {

    private final String name;
    private final String url;
    private final String username;
    private final String password;
    private final boolean sso;

    public ClientConfig(String name, String url, String username, String password) {
        this(name, url, username, password, false);
    }

    public ClientConfig(String name, String url, String username, String password, boolean sso) {
        this.name = name == null ? "" : name.trim();
        this.url = url == null ? "" : url.trim();
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password.trim();
        this.sso = sso;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isSso() {
        return sso;
    }

    public String getDisplayName() {
        return name.isBlank() ? url : name;
    }
}

