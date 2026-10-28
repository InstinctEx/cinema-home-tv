package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import java.io.*;

/** One bounded, atomic return image with the matching video position. */
final class SceneFrame {
  final Bitmap image;
  final String source, uri;
  final int index, position;

  SceneFrame(Bitmap image, String source, String uri, int index, int position) {
    this.image = image;
    this.source = source;
    this.uri = uri;
    this.index = index;
    this.position = position;
  }

  private static AtomicFile file(Context context) {
    return new AtomicFile(new File(context.getFilesDir(), "scene-return.bin"));
  }

  static synchronized SceneFrame read(Context context) {
    AtomicFile file = file(context);
    try (DataInputStream input = new DataInputStream(new BufferedInputStream(file.openRead()))) {
      if (file.getBaseFile().length() > 256_000 || input.readInt() != 0x53434631) return null;
      String source = input.readUTF(), uri = input.readUTF();
      int index = input.readInt(), position = input.readInt();
      if (!(source.equals("aerial") || source.equals("video"))
          || uri.length() > 8192
          || index < 0
          || position < 0) return null;
      byte[] bytes = new byte[256_000];
      int length = 0, n;
      while ((n = input.read(bytes, length, bytes.length - length)) > 0) length += n;
      BitmapFactory.Options bounds = new BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      BitmapFactory.decodeByteArray(bytes, 0, length, bounds);
      if (bounds.outWidth < 1
          || bounds.outHeight < 1
          || bounds.outWidth > 256
          || bounds.outHeight > 144) return null;
      Bitmap image = BitmapFactory.decodeByteArray(bytes, 0, length);
      return image == null ? null : new SceneFrame(image, source, uri, index, position);
    } catch (IOException | RuntimeException ignored) {
      return null;
    }
  }

  void write(Context context) {
    synchronized (SceneFrame.class) {
      if (!(source.equals("aerial") || source.equals("video")) || uri.length() > 8192) return;
      AtomicFile file = file(context);
      FileOutputStream output = null;
      try {
        output = file.startWrite();
        DataOutputStream data = new DataOutputStream(output);
        data.writeInt(0x53434631);
        data.writeUTF(source);
        data.writeUTF(uri);
        data.writeInt(Math.max(0, index));
        data.writeInt(Math.max(0, position));
        if (!image.compress(Bitmap.CompressFormat.JPEG, 80, data))
          throw new IOException("Frame encoding failed");
        data.flush();
        file.finishWrite(output);
      } catch (IOException | RuntimeException ignored) {
        if (output != null) file.failWrite(output);
      }
    }
  }
}
