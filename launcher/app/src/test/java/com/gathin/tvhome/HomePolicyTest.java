package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class HomePolicyTest {
    @Test public void playingProgressAdvancesWhilePausedProgressStaysStill() {
        assertEquals(3500, HomePolicy.playbackPosition(1000, 500, 1f, true, 3000, 5000));
        assertEquals(1000, HomePolicy.playbackPosition(1000, 500, 1f, false, 3000, 5000));
        assertEquals(5000, HomePolicy.playbackPosition(4500, 500, 2f, true, 3000, 5000));
        assertEquals(1000, HomePolicy.playbackPosition(1000, 500, Float.NaN, true, 3000, 5000));
    }
    @Test public void rotationWrapsWithoutLeavingTheCollection() {
        assertEquals("aurora", HomePolicy.wallpaper("rotation", 0, 0));
        assertEquals("aurora", HomePolicy.wallpaper("rotation", 0, 6));
        assertEquals("clouds", HomePolicy.wallpaper("rotation", 0, -1));
    }
    @Test public void explicitWallpaperNeverChangesWithTime() {
        assertEquals("aurora", HomePolicy.wallpaper("aurora", 23, 5));
    }
    @Test public void automaticTracksActualSunriseAndSunset() {
        assertEquals("moon_lake", HomePolicy.dayScene(300,460,1100));
        assertEquals("dunes", HomePolicy.dayScene(445,460,1100));
        assertEquals("aurora", HomePolicy.dayScene(720,460,1100));
        assertEquals("dunes", HomePolicy.dayScene(1080,460,1100));
        assertEquals("moon_lake", HomePolicy.dayScene(1200,460,1100));
    }
    @Test public void invalidSunTimesUseSafeClockFallback() {
        assertEquals("moon_lake",HomePolicy.dayScene(1200,-1,-1));
    }
    @Test public void progressHandlesMissingAndInvalidDurations() {
        assertEquals(0, HomePolicy.progress(200,0));
        assertEquals(0, HomePolicy.progress(-1,500));
        assertEquals(50, HomePolicy.progress(250,500));
        assertEquals(100, HomePolicy.progress(Long.MAX_VALUE,500));
    }
    @Test public void onlyKnownMediaAppsCanPublishPersonalRows() {
        assertTrue(HomePolicy.trustedMediaPackage("com.nuvio.tv"));
        assertTrue(HomePolicy.trustedMediaPackage("com.nuvio.app"));
        assertTrue(HomePolicy.trustedMediaPackage("com.stremio.one"));
        assertFalse(HomePolicy.trustedMediaPackage("com.nuvio.tv.fake"));
        assertFalse(HomePolicy.trustedMediaPackage(null));
    }
    @Test public void backdropDecodeKeepsEnoughDetailWithoutFullResolutionPixels() {
        assertEquals(2, HomePolicy.artworkSampleSize(3840, 2160, 1280, 720));
        assertEquals(1, HomePolicy.artworkSampleSize(1280, 720, 1280, 720));
        assertEquals(4, HomePolicy.artworkSampleSize(500, 750, 118, 177));
    }
    @Test public void extremeArtworkAspectRatioStillHasBoundedDecodeMemory() {
        int sample = HomePolicy.artworkSampleSize(20000, 1000, 200, 200);
        assertTrue((long) (20000 / sample) * (1000 / sample) <= 4L * 200 * 200);
        sample = HomePolicy.artworkSampleSize(Integer.MAX_VALUE, Integer.MAX_VALUE, 1, 1);
        assertTrue((long) (Integer.MAX_VALUE / sample) * (Integer.MAX_VALUE / sample) <= 4);
    }
    @Test public void smallArtworkIsNotUpsampledDuringDecode() {
        assertEquals(1, HomePolicy.artworkSampleSize(64, 36, 1280, 720));
    }
    @Test(expected = IllegalArgumentException.class) public void invalidArtworkDimensionsCannotLoopOrDivideByZero() {
        HomePolicy.artworkSampleSize(1920, 1080, 0, 720);
    }
}
