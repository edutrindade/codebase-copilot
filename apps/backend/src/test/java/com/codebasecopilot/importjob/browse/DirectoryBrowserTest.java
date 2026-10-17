package com.codebasecopilot.importjob.browse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.codebasecopilot.shared.localroots.LocalRoots;
import com.codebasecopilot.shared.localroots.LocalRootsProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectoryBrowserTest {

    private static final String NFD = "café";
    private static final String NFC = "café";

    @TempDir Path root;

    private DirectoryBrowser browser;

    @BeforeEach
    void setUp() throws IOException {
        root = root.toRealPath();
        browser =
                new DirectoryBrowser(
                        LocalRoots.from(new LocalRootsProperties(root.toString(), null, List.of())),
                        new BrowseProperties(1000));
    }

    @Test
    void listsAnNfdDirectoryInNfc() throws IOException {
        Files.createDirectories(root.resolve(NFD).resolve("src"));

        DirectoryBrowser.DirectoryListing listing = browser.list("default", null);

        assertThat(listing.directories())
                .containsExactly(new DirectoryBrowser.DirectoryEntry(NFC, NFC, true, true));
    }

    // Linux file systems compare names byte by byte, so an NFD directory is not found by its NFC
    // name there; this is a documented limitation (D-082).
    @Test
    void opensAnNfdDirectoryByItsNfcNameWhereTheFileSystemAllows() throws IOException {
        Files.createDirectories(root.resolve(NFD).resolve("src"));
        assumeTrue(Files.isDirectory(root.resolve(NFC)), "file system is normalization-sensitive");

        DirectoryBrowser.DirectoryListing listing = browser.list("default", NFC);

        assertThat(listing.path()).isEqualTo(NFC);
        assertThat(listing.directories())
                .containsExactly(
                        new DirectoryBrowser.DirectoryEntry("src", NFC + "/src", false, true));
    }
}
