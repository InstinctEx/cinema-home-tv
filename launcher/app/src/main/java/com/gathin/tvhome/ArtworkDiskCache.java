package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;

/** Private, bounded, replace-on-success artwork storage. Called only by image workers. */
final class ArtworkDiskCache {
    private final File directory;
    private final long limit;

    ArtworkDiskCache(File directory, long limit) {
        this.directory = directory;
        this.limit = limit;
        directory.mkdirs();
    }

    private File file(String source) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder();
        for (byte value : hash) name.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return new File(directory, name + ".art");
    }

    synchronized byte[] read(String source) {
        try {
            File entry = file(source);
            if (!entry.isFile() || entry.length() == 0 || entry.length() > 8_000_000) return null;
            byte[] result = Files.readAllBytes(entry.toPath());
            entry.setLastModified(System.currentTimeMillis());
            return result;
        } catch (Exception ignored) { return null; }
    }

    synchronized void write(String source, byte[] bytes) {
        if (bytes.length == 0 || bytes.length > Math.min(limit, 8_000_000L)) return;
        File temporary = null;
        try {
            temporary = File.createTempFile("incoming-", ".tmp", directory);
            Files.write(temporary.toPath(), bytes);
            File target = file(source);
            Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            trim();
        } catch (Exception ignored) {
            // Storage pressure must never prevent the in-memory image from being displayed.
        } finally { if (temporary != null) temporary.delete(); }
    }

    private void trim() throws IOException {
        File[] entries = directory.listFiles((dir, name) -> name.endsWith(".art"));
        if (entries == null) return;
        Arrays.sort(entries, Comparator.comparingLong(File::lastModified));
        long total = 0;
        for (File entry : entries) total += entry.length();
        for (File entry : entries) {
            if (total <= limit) break;
            long size = entry.length();
            if (entry.delete()) total -= size;
        }
    }
}
