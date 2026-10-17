package com.codebasecopilot.shared.web;

import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

// Serves the packaged UI (D-085): a file that exists is served as is; any other path that looks
// like a client route gets index.html, so a reload on /projects/{id} works. API and actuator
// paths and paths with a file extension never fall back, so their 404 stays a ProblemDetail.
// Without a packaged UI there is no index.html and nothing changes.
class SpaResourceResolver extends PathResourceResolver {

    @Override
    protected @Nullable Resource getResource(String resourcePath, Resource location)
            throws IOException {
        Resource resource = super.getResource(resourcePath, location);
        if (resource != null || !isClientRoute(resourcePath)) {
            return resource;
        }
        return super.getResource("index.html", location);
    }

    static boolean isClientRoute(String path) {
        if (path.equals("api")
                || path.startsWith("api/")
                || path.equals("actuator")
                || path.startsWith("actuator/")) {
            return false;
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        return !last.contains(".");
    }
}
