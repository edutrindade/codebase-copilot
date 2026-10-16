package com.codebasecopilot.importjob;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

class ZipExtractor {

    record Limits(
            int maxEntries,
            long maxUncompressedBytes,
            long maxEntryBytes,
            long maxCompressionRatio,
            long compressionRatioMinBytes,
            int maxDepth,
            int maxPathLength) {}

    record Result(int entryCount, long uncompressedBytes) {}

    private static final Pattern DRIVE_LETTER = Pattern.compile("[A-Za-z]:");

    private final Limits limits;

    ZipExtractor(Limits limits) {
        this.limits = limits;
    }

    // target must not exist; on rejection the caller deletes whatever was written.
    Result extract(Path zip, Path target) throws IOException, ZipRejectedException {
        Path root = Files.createDirectory(target).toRealPath();
        try (ZipFile file = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            if (file.size() > limits.maxEntries()) {
                throw new ZipRejectedException(ImportErrorCode.ZIP_TOO_MANY_ENTRIES);
            }
            int entries = 0;
            long total = 0;
            Enumeration<? extends ZipEntry> iterator = file.entries();
            while (iterator.hasMoreElements()) {
                ZipEntry entry = iterator.nextElement();
                if (++entries > limits.maxEntries()) {
                    throw new ZipRejectedException(ImportErrorCode.ZIP_TOO_MANY_ENTRIES);
                }
                List<String> segments = segments(entry.getName());
                if (segments.isEmpty()) {
                    continue;
                }
                Path destination = root.resolve(String.join("/", segments)).normalize();
                if (!destination.startsWith(root)) {
                    throw new ZipRejectedException(ImportErrorCode.ZIP_UNSAFE_PATH);
                }
                try {
                    if (entry.isDirectory()) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        total += copy(file, entry, destination, total);
                    }
                } catch (FileAlreadyExistsException e) {
                    throw new ZipRejectedException(ImportErrorCode.ZIP_DUPLICATE_ENTRY);
                }
            }
            return new Result(entries, total);
        } catch (ZipException | IllegalArgumentException e) {
            // Malformed archive, encrypted entry or undecodable name.
            throw new ZipRejectedException(ImportErrorCode.ZIP_INVALID);
        }
    }

    private List<String> segments(String name) throws ZipRejectedException {
        if (name.length() > limits.maxPathLength()
                || name.startsWith("/")
                || name.chars().anyMatch(c -> c == '\\' || Character.isISOControl(c))) {
            throw new ZipRejectedException(ImportErrorCode.ZIP_UNSAFE_PATH);
        }
        List<String> segments = new ArrayList<>();
        for (String segment : name.split("/")) {
            if (segment.equals("..")) {
                throw new ZipRejectedException(ImportErrorCode.ZIP_UNSAFE_PATH);
            }
            if (!segment.isEmpty() && !segment.equals(".")) {
                segments.add(segment);
            }
        }
        if (!segments.isEmpty() && DRIVE_LETTER.matcher(segments.getFirst()).matches()) {
            throw new ZipRejectedException(ImportErrorCode.ZIP_UNSAFE_PATH);
        }
        if (segments.size() > limits.maxDepth()) {
            throw new ZipRejectedException(ImportErrorCode.ZIP_TOO_DEEP);
        }
        return segments;
    }

    // CREATE_NEW rejects duplicates, including case variants on case-insensitive file systems.
    private long copy(ZipFile file, ZipEntry entry, Path destination, long totalSoFar)
            throws IOException, ZipRejectedException {
        long compressed = Math.max(1, entry.getCompressedSize());
        long written = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = file.getInputStream(entry);
                OutputStream out =
                        Files.newOutputStream(
                                destination,
                                StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.WRITE)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                written += read;
                if (written > limits.maxEntryBytes()) {
                    throw new ZipRejectedException(ImportErrorCode.ZIP_ENTRY_TOO_LARGE);
                }
                if (totalSoFar + written > limits.maxUncompressedBytes()) {
                    throw new ZipRejectedException(ImportErrorCode.ZIP_UNCOMPRESSED_TOO_LARGE);
                }
                if (written > limits.compressionRatioMinBytes()
                        && written / compressed > limits.maxCompressionRatio()) {
                    throw new ZipRejectedException(ImportErrorCode.ZIP_COMPRESSION_RATIO_EXCEEDED);
                }
                out.write(buffer, 0, read);
            }
        }
        return written;
    }
}
