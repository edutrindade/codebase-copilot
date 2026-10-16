package com.codebasecopilot.importjob.browse;

import com.codebasecopilot.shared.error.ErrorCode;
import com.codebasecopilot.shared.openapi.ApiErrors;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/local-roots")
class LocalRootsController {

    private final DirectoryBrowser browser;

    LocalRootsController(DirectoryBrowser browser) {
        this.browser = browser;
    }

    @GetMapping
    List<LocalRootResponse> list() {
        return browser.roots().stream()
                .map(root -> new LocalRootResponse(root.key(), root.label()))
                .toList();
    }

    @GetMapping("/{rootKey}/directories")
    @ApiErrors({
        ErrorCode.ROOT_NOT_FOUND,
        ErrorCode.PATH_OUTSIDE_ROOT,
        ErrorCode.DIRECTORY_NOT_FOUND,
        ErrorCode.DIRECTORY_NOT_READABLE
    })
    DirectoryListingResponse directories(
            @PathVariable String rootKey, @RequestParam(required = false) @Nullable String path) {
        return DirectoryListingResponse.of(browser.list(rootKey, path));
    }

    record LocalRootResponse(String key, String label) {}

    record DirectoryListingResponse(
            String path, List<DirectoryResponse> directories, boolean truncated) {

        static DirectoryListingResponse of(DirectoryBrowser.DirectoryListing listing) {
            return new DirectoryListingResponse(
                    listing.path(),
                    listing.directories().stream()
                            .map(
                                    entry ->
                                            new DirectoryResponse(
                                                    entry.name(),
                                                    entry.relativePath(),
                                                    entry.hasChildren(),
                                                    entry.readable()))
                            .toList(),
                    listing.truncated());
        }
    }

    record DirectoryResponse(
            String name, String relativePath, boolean hasChildren, boolean readable) {}
}
