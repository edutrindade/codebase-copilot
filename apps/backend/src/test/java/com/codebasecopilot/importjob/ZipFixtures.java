package com.codebasecopilot.importjob;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// Builds archives in memory. The raw writer emits headers by hand for what ZipOutputStream
// refuses: repeated names, false declared sizes, Unix link attributes and the encryption flag.
final class ZipFixtures {

    private static final int UNIX_SYMLINK = 0120777 << 16;
    private static final int ENCRYPTED = 1;

    private ZipFixtures() {}

    static byte[] zip(String... namesAndContents) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                out.putNextEntry(new ZipEntry(namesAndContents[i]));
                out.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    static byte[] zipOfBytes(String name, byte[] content) {
        return raw().file(name, content).build();
    }

    static byte[] randomBytes(int size, long seed) {
        byte[] data = new byte[size];
        new Random(seed).nextBytes(data);
        return data;
    }

    static Raw raw() {
        return new Raw();
    }

    static final class Raw {

        private record Entry(
                byte[] name,
                byte[] data,
                byte[] compressed,
                long declaredSize,
                int attributes,
                int flags) {}

        private final List<Entry> entries = new ArrayList<>();

        Raw file(String name, byte[] data) {
            return add(name, data, data.length, 0, 0);
        }

        Raw file(String name, String content) {
            return file(name, content.getBytes(StandardCharsets.UTF_8));
        }

        Raw declaringSize(String name, byte[] data, long declaredSize) {
            return add(name, data, declaredSize, 0, 0);
        }

        Raw symlink(String name, String target) {
            return add(
                    name,
                    target.getBytes(StandardCharsets.UTF_8),
                    target.length(),
                    UNIX_SYMLINK,
                    0);
        }

        Raw encrypted(String name, String content) {
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            return add(name, data, data.length, 0, ENCRYPTED);
        }

        private Raw add(String name, byte[] data, long declaredSize, int attributes, int flags) {
            entries.add(
                    new Entry(
                            name.getBytes(StandardCharsets.UTF_8),
                            data,
                            deflate(data),
                            declaredSize,
                            attributes,
                            flags));
            return this;
        }

        byte[] build() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            List<Integer> offsets = new ArrayList<>();
            for (Entry entry : entries) {
                offsets.add(out.size());
                ByteBuffer header = buffer(30 + entry.name().length);
                header.putInt(0x04034b50).putShort((short) 20);
                common(header, entry);
                header.putShort((short) entry.name().length).putShort((short) 0).put(entry.name());
                out.writeBytes(header.array());
                out.writeBytes(entry.compressed());
            }
            int centralStart = out.size();
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                ByteBuffer header = buffer(46 + entry.name().length);
                header.putInt(0x02014b50).putShort((short) (3 << 8 | 20)).putShort((short) 20);
                common(header, entry);
                header.putShort((short) entry.name().length)
                        .putShort((short) 0)
                        .putShort((short) 0)
                        .putShort((short) 0)
                        .putShort((short) 0)
                        .putInt(entry.attributes())
                        .putInt(offsets.get(i))
                        .put(entry.name());
                out.writeBytes(header.array());
            }
            int centralSize = out.size() - centralStart;
            ByteBuffer end = buffer(22);
            end.putInt(0x06054b50)
                    .putShort((short) 0)
                    .putShort((short) 0)
                    .putShort((short) entries.size())
                    .putShort((short) entries.size())
                    .putInt(centralSize)
                    .putInt(centralStart)
                    .putShort((short) 0);
            out.writeBytes(end.array());
            return out.toByteArray();
        }

        // Flags (UTF-8 names), method, time, date, CRC and sizes, shared by both headers.
        private static void common(ByteBuffer header, Entry entry) {
            CRC32 crc = new CRC32();
            crc.update(entry.data());
            header.putShort((short) (0x0800 | entry.flags()))
                    .putShort((short) 8)
                    .putShort((short) 0)
                    .putShort((short) 0x21)
                    .putInt((int) crc.getValue())
                    .putInt(entry.compressed().length)
                    .putInt((int) entry.declaredSize());
        }

        private static ByteBuffer buffer(int size) {
            return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        }

        private static byte[] deflate(byte[] data) {
            try (Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true)) {
                deflater.setInput(data);
                deflater.finish();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[64 * 1024];
                while (!deflater.finished()) {
                    out.write(buffer, 0, deflater.deflate(buffer));
                }
                return out.toByteArray();
            }
        }
    }
}
