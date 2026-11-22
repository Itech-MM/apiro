package io.github.itech_mm.apiro.model;

public record AuthConfig(AuthType type, String username, String password, String token, String keyName,
                         String keyValue) {

    public AuthConfig {
        type = type == null ? AuthType.NONE : type;
        username = username == null ? "" : username;
        password = password == null ? "" : password;
        token = token == null ? "" : token;
        keyName = keyName == null ? "" : keyName;
        keyValue = keyValue == null ? "" : keyValue;
    }

    public static AuthConfig none() {
        return new AuthConfig(AuthType.NONE, null, null, null, null, null);
    }

    public static AuthConfig basic(String username, String password) {
        return new AuthConfig(AuthType.BASIC, username, password, null, null, null);
    }

    public static AuthConfig bearer(String token) {
        return new AuthConfig(AuthType.BEARER, null, null, token, null, null);
    }

    public static AuthConfig apiKey(String keyName, String keyValue) {
        return new AuthConfig(AuthType.API_KEY, null, null, null, keyName, keyValue);
    }
    
    public static AuthConfig inherit() {
        return new AuthConfig(AuthType.INHERIT, null, null, null, null, null);
    }

    public boolean isInherit() {
        return type == AuthType.INHERIT;
    }
}