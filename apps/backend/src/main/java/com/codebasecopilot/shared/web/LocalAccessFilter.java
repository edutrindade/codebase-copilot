package com.codebasecopilot.shared.web;

import com.codebasecopilot.shared.error.ApiException;
import com.codebasecopilot.shared.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

// The backend has no authentication, so only the local UI may reach it (D-072): a foreign Host
// is DNS rebinding or remote access, and a foreign Origin on a state change is CSRF. A missing
// Origin (curl, tests) passes; browsers always send one on cross-origin POST, PUT and DELETE.
// The backend's own origin passes on any port: the UI it serves is same-origin, and the Host it
// is compared with was already accepted (D-085).
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class LocalAccessFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LocalAccessFilter.class);
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final HttpProperties properties;
    private final HandlerExceptionResolver resolver;

    LocalAccessFilter(HttpProperties properties, HandlerExceptionResolver resolver) {
        this.properties = properties;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ErrorCode denied = check(request);
        if (denied == null) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Request rejected: method={} code={}", request.getMethod(), denied);
        resolver.resolveException(request, response, null, new ApiException(denied));
    }

    private @Nullable ErrorCode check(HttpServletRequest request) {
        if (!properties.allowedHosts().contains(host(request))) {
            return ErrorCode.FORBIDDEN_HOST;
        }
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || SAFE_METHODS.contains(request.getMethod())) {
            return null;
        }
        String normalized = origin.strip().toLowerCase(Locale.ROOT);
        if (properties.allowedOrigins().contains(normalized) || sameOrigin(normalized, request)) {
            return null;
        }
        return ErrorCode.FORBIDDEN_ORIGIN;
    }

    private static boolean sameOrigin(String origin, HttpServletRequest request) {
        String host = request.getHeader(HttpHeaders.HOST);
        return host != null
                && origin.equals(
                        request.getScheme() + "://" + host.strip().toLowerCase(Locale.ROOT));
    }

    // The name in the Host header without the port; "[::1]:8080" keeps its brackets.
    static String host(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.HOST);
        String value = header == null ? request.getServerName() : header.strip();
        int end;
        if (value.startsWith("[")) {
            end = value.indexOf(']') + 1;
        } else {
            end = value.indexOf(':');
        }
        return (end > 0 ? value.substring(0, end) : value).toLowerCase(Locale.ROOT);
    }
}
