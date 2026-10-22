package com.gathin.tvhome;

import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ArtworkDiskCacheTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void artworkSurvivesLoaderRestartWithoutNetwork() throws Exception {
        File root = temporary.newFolder(); byte[] image = new byte[]{1, 2, 3, 4};
        new ArtworkDiskCache(root, 128).write("https://example.org/art", image);
        assertArrayEquals(image, new ArtworkDiskCache(root, 128).read("https://example.org/art"));
        assertEquals(1, root.listFiles().length);
        assertFalse(root.listFiles()[0].getName().contains("example"));
    }
    @Test public void evictsOldestArtworkAndKeepsTotalWithinBudget() throws Exception {
        File root = temporary.newFolder(); ArtworkDiskCache cache = new ArtworkDiskCache(root, 12);
        cache.write("old", new byte[8]); root.listFiles()[0].setLastModified(1000);
        cache.write("new", new byte[8]);
        assertNull(cache.read("old")); assertNotNull(cache.read("new"));
        assertTrue(java.util.Arrays.stream(root.listFiles()).mapToLong(File::length).sum() <= 12);
    }
    @Test public void rejectedOversizeReplacementDoesNotDestroyWorkingImage() throws Exception {
        ArtworkDiskCache cache = new ArtworkDiskCache(temporary.newFolder(), 16);
        cache.write("same", new byte[]{9, 8}); cache.write("same", new byte[17]);
        assertArrayEquals(new byte[]{9, 8}, cache.read("same"));
    }
}
