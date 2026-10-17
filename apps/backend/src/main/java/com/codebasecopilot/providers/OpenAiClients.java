package com.codebasecopilot.providers;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.LogLevel;
import com.openai.core.RequestOptions;
import com.openai.core.Timeout;
import com.openai.errors.OpenAIServiceException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

// Shared by the OpenAI adapters. The client never reads OPENAI_* variables (no fromEnv), never
// retries (the only retry layer is EmbeddingRetry, D-051) and never logs: OPENAI_LOG=debug in
// the environment would otherwise print request bodies.
final class OpenAiClients {

    private static final String INSUFFICIENT_QUOTA = "insufficient_quota";
    private static final Set<String> QUOTA_CODES =
            Set.of(
                    "credit_balance_exhausted",
                    "organization_spend_limit_exceeded",
                    "project_spend_limit_exceeded",
                    "organization_usage_limit_exceeded");

    // The base URL decides where the key and code chunks are sent (D-047).
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1");

    static OpenAIClient create(URI baseUrl, String apiKey, Duration timeout) {
        return OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl.toString())
                .apiKey(apiKey)
                .timeout(timeout(timeout))
                .maxRetries(0)
                .logLevel(LogLevel.OFF)
                .build();
    }

    static RequestOptions requestOptions(Duration timeout) {
        return RequestOptions.builder().timeout(timeout(timeout)).build();
    }

    // A 429 is told apart by error.code; error.type may still be insufficient_quota (OpenAI
    // error codes guide, consulted on 16/10/2026). The code is read, never logged (D-051).
    static boolean quotaExceeded(OpenAIServiceException e) {
        return e.code().filter(QUOTA_CODES::contains).isPresent()
                || e.type().filter(INSUFFICIENT_QUOTA::equals).isPresent();
    }

    // Error messages never echo the value: a URL with user info could carry a credential.
    static void validateBaseUrl(URI url, String property) {
        String scheme = url.getScheme() == null ? "" : url.getScheme().toLowerCase(Locale.ROOT);
        String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty() || !(scheme.equals("https") || scheme.equals("http"))) {
            throw new IllegalStateException(property + " must be an absolute http(s) URL");
        }
        if (url.getRawUserInfo() != null) {
            throw new IllegalStateException(property + " must not contain user info");
        }
        if (scheme.equals("http") && !LOOPBACK_HOSTS.contains(host)) {
            throw new IllegalStateException(
                    property + " must use https (http is allowed only for localhost or 127.0.0.1)");
        }
    }

    private static Timeout timeout(Duration timeout) {
        return Timeout.builder().connect(timeout).request(timeout).build();
    }

    private OpenAiClients() {}
}
