package com.codebasecopilot.indexing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import org.springframework.stereotype.Component;

// Reads a scanned file once, as bytes (D-012, D-042). Links are never followed and only regular
// files are opened, so a FIFO swapped in after the scan is not read.
@Component
class SourceFileReader {

    sealed interface Result {}

    record Bytes(byte[] bytes) implements Result {}

    record Failed(ReadFailure reason) implements Result {}

    Result read(Path file, long maxBytes) {
        try {
            BasicFileAttributes before = attributes(file);
            if (!before.isRegularFile() || before.size() > maxBytes) {
                return new Failed(ReadFailure.CHANGED_DURING_READ);
            }
            byte[] bytes;
            try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                bytes = in.readNBytes(Math.toIntExact(maxBytes + 1));
            }
            BasicFileAttributes after = attributes(file);
            if (bytes.length > maxBytes
                    || bytes.length != before.size()
                    || after.size() != before.size()
                    || !after.lastModifiedTime().equals(before.lastModifiedTime())
                    || !Objects.equals(after.fileKey(), before.fileKey())) {
                return new Failed(ReadFailure.CHANGED_DURING_READ);
            }
            return new Bytes(bytes);
        } catch (IOException e) {
            return new Failed(ReadFailure.READ_ERROR);
        }
    }

    private static BasicFileAttributes attributes(Path file) throws IOException {
        return Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }
}
