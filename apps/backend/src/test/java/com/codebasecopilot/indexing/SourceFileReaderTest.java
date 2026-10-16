package com.codebasecopilot.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class SourceFileReaderTest {

    private final SourceFileReader reader = new SourceFileReader();

    @TempDir Path dir;

    @Test
    void readsRegularFile() throws IOException {
        Path file = Files.writeString(dir.resolve("a.txt"), "hello");

        assertThat(reader.read(file, 5))
                .isInstanceOfSatisfying(
                        SourceFileReader.Bytes.class,
                        b -> assertThat(b.bytes()).asString().isEqualTo("hello"));
    }

    @Test
    void fileAboveTheLimitChangedSinceTheScan() throws IOException {
        Path file = Files.writeString(dir.resolve("a.txt"), "hello");

        assertThat(reader.read(file, 4))
                .isEqualTo(new SourceFileReader.Failed(ReadFailure.CHANGED_DURING_READ));
    }

    @Test
    void missingFileIsAReadError() {
        assertThat(reader.read(dir.resolve("gone.txt"), 10))
                .isEqualTo(new SourceFileReader.Failed(ReadFailure.READ_ERROR));
    }

    @Test
    void linkOrFifoInPlaceOfTheFileIsNeverOpened() throws Exception {
        Path outside = Files.writeString(dir.resolve("outside.txt"), "secret");
        Path link = Files.createSymbolicLink(dir.resolve("link.txt"), outside);
        assertThat(reader.read(link, 100))
                .isEqualTo(new SourceFileReader.Failed(ReadFailure.CHANGED_DURING_READ));

        Path fifo = dir.resolve("fifo");
        Process mkfifo = new ProcessBuilder("mkfifo", fifo.toString()).start();
        assumeTrue(mkfifo.waitFor() == 0, "mkfifo not available");
        assertThat(reader.read(fifo, 100))
                .isEqualTo(new SourceFileReader.Failed(ReadFailure.CHANGED_DURING_READ));
    }
}
