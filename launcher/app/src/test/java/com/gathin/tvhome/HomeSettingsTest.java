package com.gathin.tvhome;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class HomeSettingsTest {
    @Test public void defaultBackupCanRoundTripIncludingDisabledToggles() {
        Map<String, Object> settings = HomeSettings.defaults(); settings.put("music_strip", false);
        assertEquals(settings, HomeSettings.validate(settings));
    }
    @Test public void backupCannotInstallTimersOrRemoteInterception() {
        assertTrue(HomeSettings.validate(Map.of("sleep_deadline", 123L, "smarttube_key", 3, "wallpaper_uri", "content://other/private")).isEmpty());
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsOutOfRangeDimming() {
        HomeSettings.validate(Map.of("wallpaper_dim", 90));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsFractionalInteger() {
        HomeSettings.validate(Map.of("wallpaper_interval", 30.5));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsInvalidWallpaperBeforeApplyingAnySettings() {
        HomeSettings.validate(Map.of("wallpaper_selection", "content://private/file", "music_strip", false));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsMalformedDockPackage() {
        HomeSettings.validate(Map.of("dock_apps", "com.nuvio.tv,not a package"));
    }
}
