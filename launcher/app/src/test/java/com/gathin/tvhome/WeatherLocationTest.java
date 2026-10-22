package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class WeatherLocationTest {
    @Test public void blankShippedConfigurationCannotEnableARequest() {
        assertFalse(WeatherLocation.valid("", "", ""));
        assertFalse(WeatherLocation.valid("Place", "", "0"));
        assertFalse(WeatherLocation.valid(null, "0", "0"));
    }
    @Test public void rejectsNonFiniteAndOutOfRangeCoordinates() {
        for (String value : new String[]{"NaN", "Infinity", "91", "-91", "not-a-coordinate"})
            assertFalse(WeatherLocation.valid("Place", value, "0"));
        assertFalse(WeatherLocation.valid("Place", "0", "181"));
    }
    @Test public void acceptsExplicitCoordinatesIncludingZeroAndBoundaries() {
        assertTrue(WeatherLocation.valid("Place", "0", "0"));
        assertTrue(WeatherLocation.valid("Place", "90", "-180"));
    }
    @Test public void weatherRemainsOptInInSettingsAndRestoreDefaults() {
        assertEquals(false, HomeSettings.defaults().get("weather_enabled"));
        assertEquals(false, HomeSettings.validate(HomeSettings.defaults()).get("weather_enabled"));
    }
}
