package com.gathin.tvhome;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class FeaturePolicyTest {
  @Test
  public void moveClampsAtEndsAndPreservesItems() {
    assertEquals(List.of("b", "a", "c"), FeaturePolicy.move(List.of("a", "b", "c"), "b", -8));
    assertEquals(List.of("a", "c", "b"), FeaturePolicy.move(List.of("a", "b", "c"), "b", 8));
  }

  @Test(expected = IllegalArgumentException.class)
  public void absentItemCannotBeInserted() {
    FeaturePolicy.move(List.of("a"), "b", 1);
  }

  @Test
  public void uploadLimitsIncludeExistingStorage() {
    assertTrue(FeaturePolicy.uploadAllowed(100, true, 0));
    assertFalse(FeaturePolicy.uploadAllowed(-1, true, 0));
    assertFalse(FeaturePolicy.uploadAllowed(FeaturePolicy.PHOTO_LIMIT + 1, false, 0));
    assertFalse(FeaturePolicy.uploadAllowed(100, true, FeaturePolicy.STORAGE_LIMIT - 50));
  }

  @Test
  public void pathTraversalAndWrongMediaTypesAreRejected() {
    assertFalse(FeaturePolicy.mediaId("../secret.mp4"));
    assertFalse(FeaturePolicy.mediaId("file.html"));
    assertTrue(FeaturePolicy.mediaId("12345678-1234-1234-1234-123456789abc.mp4"));
  }

  @Test
  public void oneGigabyteTvGetsLiteEffectsEvenWithoutFirmwareFlag() {
    assertTrue(FeaturePolicy.lowEnd(false, 1024L * 1024 * 1024, 4));
    assertFalse(FeaturePolicy.lowEnd(false, 3L * 1024 * 1024 * 1024, 4));
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyNamesAreRejected() {
    FeaturePolicy.label("\n\r");
  }
}
