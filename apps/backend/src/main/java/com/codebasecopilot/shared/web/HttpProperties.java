package com.codebasecopilot.shared.web;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Who may talk to the local backend (D-072): Host names the server answers to, and the
// browser origins allowed to change state. The defaults are the Vite dev and preview servers.
@ConfigurationProperties("app.http")
public record HttpProperties(
        @DefaultValue({"localhost", "127.0.0.1", "[::1]"}) List<String> allowedHosts,
        @DefaultValue({
                    "http://localhost:5173",
                    "http://127.0.0.1:5173",
                    "http://localhost:4173",
                    "http://127.0.0.1:4173"
                })
                List<String> allowedOrigins) {

    public HttpProperties {
        allowedHosts = allowedHosts.stream().map(HttpProperties::lower).toList();
        allowedOrigins = allowedOrigins.stream().map(HttpProperties::lower).toList();
        if (allowedHosts.isEmpty() || allowedHosts.stream().anyMatch(String::isBlank)) {
            throw new IllegalStateException("app.http.allowed-hosts must list host names");
        }
        if (allowedOrigins.stream().anyMatch(o -> o.isBlank() || o.endsWith("/"))) {
            throw new IllegalStateException(
                    "app.http.allowed-origins must be scheme://host[:port], without a final /");
        }
    }

    private static String lower(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
