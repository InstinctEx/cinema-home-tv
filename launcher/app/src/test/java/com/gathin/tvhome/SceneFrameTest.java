package com.gathin.tvhome;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SceneFrameTest {
  @Test
  public void frameAndResumePositionRoundTripTogetherAndCorruptionIsIgnored() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    Bitmap bitmap = Bitmap.createBitmap(256, 144, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(Color.BLUE);
    new SceneFrame(bitmap, "aerial", "https://example.com/video.mov", 4, 27000).write(context);
    SceneFrame restored = SceneFrame.read(context);
    assertNotNull(restored);
    assertEquals(27000, restored.position);
    assertEquals(4, restored.index);
    assertEquals("aerial", restored.source);
    assertEquals("https://example.com/video.mov", restored.uri);
    assertTrue(Color.blue(restored.image.getPixel(120, 60)) > 220);
    File file = new File(context.getFilesDir(), "scene-return.bin");
    assertTrue(file.length() < 256_000);
    java.nio.file.Files.write(file.toPath(), new byte[] {1, 2, 3});
    assertNull(SceneFrame.read(context));
  }
}
