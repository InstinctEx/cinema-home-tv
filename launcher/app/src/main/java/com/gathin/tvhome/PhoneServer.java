package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** A temporary paired editor; no unauthenticated route or directory listing. */
final class PhoneServer implements AutoCloseable {
  interface Host {
    JSONObject state() throws Exception;

    void edit(JSONObject command) throws Exception;

    default boolean active() {
      return false;
    }

    default void navigate(String key) {
      throw new IllegalArgumentException("Return Home to navigate");
    }
  }

  private final Context context;
  private final MediaLibrary media;
  private volatile java.lang.ref.WeakReference<Host> host;
  private JSONObject lastState = new JSONObject();
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ThreadPoolExecutor clients =
      new ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4));
  private final ServerSocket server;
  final String token;
  private volatile boolean closed;
  private final Runnable expiry = this::close;

  PhoneServer(Context context, MediaLibrary media, Host host) throws IOException {
    this.context = context.getApplicationContext();
    this.media = media;
    this.host = new java.lang.ref.WeakReference<>(host);
    try {
      lastState = host.state();
    } catch (Exception ignored) {
    }
    byte[] bytes = new byte[24];
    new SecureRandom().nextBytes(bytes);
    StringBuilder key = new StringBuilder();
    for (byte b : bytes) key.append(String.format(Locale.ROOT, "%02x", b & 255));
    token = key.toString();
    server = new ServerSocket(0, 4);
    main.postDelayed(expiry, 15 * 60_000L);
    new Thread(
            () -> {
              while (!closed)
                try {
                  Socket socket = server.accept();
                  socket.setSoTimeout(30_000);
                  try {
                    clients.execute(() -> serve(socket));
                  } catch (RejectedExecutionException e) {
                    socket.close();
                  }
                } catch (IOException e) {
                  if (!closed) close();
                }
            },
            "CinemaPhoneAccept")
        .start();
  }

  boolean isClosed() {
    return closed;
  }

  void bind(Host owner) {
    host = new java.lang.ref.WeakReference<>(owner);
  }

  String address() {
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        NetworkInterface network = interfaces.nextElement();
        if (!network.isUp() || network.isLoopback()) continue;
        Enumeration<InetAddress> addresses = network.getInetAddresses();
        while (addresses.hasMoreElements()) {
          InetAddress address = addresses.nextElement();
          if (address instanceof Inet4Address && address.isSiteLocalAddress())
            return "http://"
                + address.getHostAddress()
                + ":"
                + server.getLocalPort()
                + "/"
                + token
                + "/";
        }
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private <T> T onMain(Callable<T> action) throws Exception {
    FutureTask<T> task = new FutureTask<>(action);
    main.post(task);
    try {
      return task.get(8, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      task.cancel(false);
      throw e;
    }
  }

  private void serve(Socket socket) {
    try (Socket connection = socket) {
      InputStream input = new BufferedInputStream(connection.getInputStream());
      OutputStream output = connection.getOutputStream();
      String first = line(input);
      String[] request = first.split(" ");
      if (request.length != 3) {
        reply(output, 400, "text/plain", "Invalid request".getBytes(StandardCharsets.UTF_8));
        return;
      }
      Map<String, String> headers = new HashMap<>();
      int headerBytes = first.length();
      for (String line; !(line = line(input)).isEmpty(); ) {
        headerBytes += line.length();
        if (headerBytes > 16384) throw new IOException("Headers too large");
        int split = line.indexOf(':');
        if (split < 1) throw new IOException("Invalid header");
        String key = line.substring(0, split).toLowerCase(Locale.ROOT);
        if (headers.put(key, line.substring(split + 1).trim()) != null)
          throw new IOException("Duplicate header");
      }
      String prefix = "/" + token + "/";
      String path = new URI(request[1]).getPath();
      if (path == null || !path.startsWith(prefix)) {
        reply(output, 404, "text/plain", new byte[0]);
        return;
      }
      String route = path.substring(prefix.length());
      String method = request[0];
      if (headers.containsKey("origin")) {
        URI origin = new URI(headers.get("origin"));
        if (!Objects.equals(origin.getRawAuthority(), headers.get("host"))
            || !"http".equals(origin.getScheme())) {
          reply(output, 403, "text/plain", new byte[0]);
          return;
        }
      }
      if (headers.containsKey("transfer-encoding")) {
        reply(output, 400, "text/plain", new byte[0]);
        return;
      }
      if ("GET".equals(method)) {
        if (route.startsWith("api/cities/")) {
          String city = FeaturePolicy.label(route.substring(11));
          byte[] result =
              DailyWallpaper.get(
                  "https://geocoding-api.open-meteo.com/v1/search?name="
                      + URLEncoder.encode(city, "UTF-8")
                      + "&count=8&language=en&format=json",
                  150000);
          reply(output, 200, "application/json", result);
          return;
        }
        if (route.equals("api/scenes")) {
          reply(
              output,
              200,
              "application/json",
              new JSONObject()
                  .put("videos", SceneLibrary.catalogue(context))
                  .toString()
                  .getBytes(StandardCharsets.UTF_8));
          return;
        }
        if (route.equals("api/state")) {
          reply(
              output,
              200,
              "application/json",
              onMain(
                      () -> {
                        Host owner = host.get();
                        if (owner != null && (owner.active() || lastState.length() == 0))
                          lastState = owner.state();
                        lastState.put(
                            "remote",
                            RemoteControls.capabilities(context, owner != null && owner.active()));
                        return lastState;
                      })
                  .toString()
                  .getBytes(StandardCharsets.UTF_8));
          return;
        }
        if (route.startsWith("media/")) {
          File file = media.file(route.substring(6));
          if (!file.isFile()) {
            reply(output, 404, "text/plain", new byte[0]);
            return;
          }
          sendFile(output, file, headers.get("range"));
          return;
        }
        if (route.startsWith("wallpapers/")) {
          String name = route.substring(11);
          if (!Arrays.asList(
                  "misty.jpg",
                  "dunes.jpg",
                  "moon_lake.jpg",
                  "aurora.jpg",
                  "golden_coast.jpg",
                  "clouds.jpg",
                  "dusk_peaks.jpg")
              .contains(name)) {
            reply(output, 404, "text/plain", new byte[0]);
            return;
          }
          try (InputStream image = context.getAssets().open("wallpapers/" + name);
              ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = image.read(chunk)) != -1) bytes.write(chunk, 0, n);
            reply(output, 200, "image/jpeg", bytes.toByteArray());
          }
          return;
        }
        String asset = route.isEmpty() ? "index.html" : route;
        if (!Arrays.asList(
                "index.html", "phone.js", "phone.css", "icons.svg", "outfit.ttf", "scene-policy.js")
            .contains(asset)) {
          reply(output, 404, "text/plain", new byte[0]);
          return;
        }
        try (InputStream data =
            asset.equals("outfit.ttf") ? openFont() : context.getAssets().open("phone/" + asset)) {
          ByteArrayOutputStream buffer = new ByteArrayOutputStream();
          byte[] chunk = new byte[8192];
          int n;
          while ((n = data.read(chunk)) != -1) buffer.write(chunk, 0, n);
          reply(
              output,
              200,
              asset.endsWith("svg")
                  ? "image/svg+xml"
                  : asset.endsWith("ttf")
                      ? "font/ttf"
                      : asset.endsWith("js")
                          ? "text/javascript"
                          : asset.endsWith("css") ? "text/css" : "text/html",
              buffer.toByteArray());
        }
        return;
      }
      if (!"POST".equals(method)) {
        reply(output, 405, "text/plain", new byte[0]);
        return;
      }
      long length = Long.parseLong(headers.getOrDefault("content-length", "-1"));
      try {
        if (route.equals("api/upload/photo") || route.equals("api/upload/video")) {
          String id = media.upload(input, length, route.endsWith("video"));
          reply(
              output,
              200,
              "application/json",
              new JSONObject().put("id", id).toString().getBytes(StandardCharsets.UTF_8));
          return;
        }
        if (!route.equals("api/edit") || length < 1 || length > 65536)
          throw new IllegalArgumentException("Invalid request size");
        byte[] body = new byte[(int) length];
        int offset = 0;
        while (offset < body.length) {
          int n = input.read(body, offset, body.length - offset);
          if (n < 0) throw new EOFException();
          offset += n;
        }
        JSONObject command = new JSONObject(new String(body, StandardCharsets.UTF_8));
        onMain(
            () -> {
              Host owner = host.get();
              if (command.optString("op").equals("remote"))
                RemoteControls.execute(context, owner, command.getString("key"));
              else {
                if (owner == null)
                  throw new IllegalArgumentException("Return to Cinema Home to edit settings");
                owner.edit(command);
              }
              return true;
            });
        reply(output, 200, "application/json", "{\"saved\":true}".getBytes(StandardCharsets.UTF_8));
      } catch (Exception failure) {
        Throwable cause = failure instanceof ExecutionException ? failure.getCause() : failure;
        String message =
            cause instanceof IllegalArgumentException
                ? cause.getMessage()
                : "Could not complete this change";
        reply(
            output,
            400,
            "application/json",
            new JSONObject().put("error", message).toString().getBytes(StandardCharsets.UTF_8));
      }
    } catch (Exception ignored) {
      /* A timed-out or disconnected phone cannot affect launcher navigation. */
    }
  }

  @android.annotation.SuppressLint("ResourceType")
  private InputStream openFont() {
    // The font is a file-backed resource; stream its exact bundled bytes for the paired editor.
    return context.getResources().openRawResource(R.font.outfit);
  }

  private static String line(InputStream input) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    int b;
    while ((b = input.read()) != -1) {
      if (b == '\n') return buffer.toString("US-ASCII").replace("\r", "");
      if (buffer.size() >= 8192) throw new IOException("Line too long");
      buffer.write(b);
    }
    throw new EOFException();
  }

  private static void reply(OutputStream out, int code, String type, byte[] data)
      throws IOException {
    head(out, code, type, data.length, null);
    out.write(data);
    out.flush();
  }

  private static void head(OutputStream out, int code, String type, long length, String extra)
      throws IOException {
    String status =
        code == 200
            ? "OK"
            : code == 206
                ? "Partial Content"
                : code == 404
                    ? "Not Found"
                    : code == 403
                        ? "Forbidden"
                        : code == 405
                            ? "Method Not Allowed"
                            : code == 416 ? "Range Not Satisfiable" : "Bad Request";
    String value =
        "HTTP/1.1 "
            + code
            + " "
            + status
            + "\r\nContent-Type: "
            + type
            + "\r\nContent-Length: "
            + length
            + "\r\n"
            + "Connection: close\r\n"
            + "Cache-Control: no-store\r\n"
            + "X-Content-Type-Options: nosniff\r\n"
            + "Referrer-Policy: no-referrer\r\n"
            + "Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self';"
            + " img-src 'self' blob: https://sylvan.apple.com; media-src 'self' blob:"
            + " https://sylvan.apple.com; frame-ancestors 'none'; base-uri 'none'\r\n"
            + (extra == null ? "" : extra)
            + "\r\n";
    out.write(value.getBytes(StandardCharsets.US_ASCII));
  }

  private static void sendFile(OutputStream out, File file, String range) throws IOException {
    long start = 0, end = file.length() - 1;
    int status = 200;
    if (range != null) {
      try {
        if (!range.matches("bytes=[0-9]+-[0-9]*")) throw new IllegalArgumentException();
        String[] ends = range.substring(6).split("-", -1);
        start = Long.parseLong(ends[0]);
        if (!ends[1].isEmpty()) end = Math.min(end, Long.parseLong(ends[1]));
        if (start > end || start < 0) throw new IllegalArgumentException();
        status = 206;
      } catch (Exception e) {
        head(out, 416, "text/plain", 0, "Content-Range: bytes */" + file.length() + "\r\n");
        return;
      }
    }
    long count = end - start + 1;
    head(
        out,
        status,
        file.getName().endsWith("mp4") ? "video/mp4" : "image/jpeg",
        count,
        "Accept-Ranges: bytes\r\n"
            + (status == 206
                ? "Content-Range: bytes " + start + "-" + end + "/" + file.length() + "\r\n"
                : ""));
    try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
      input.seek(start);
      byte[] chunk = new byte[32768];
      while (count > 0) {
        int n = input.read(chunk, 0, (int) Math.min(chunk.length, count));
        if (n < 0) break;
        out.write(chunk, 0, n);
        count -= n;
      }
    }
    out.flush();
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    main.removeCallbacks(expiry);
    try {
      server.close();
    } catch (IOException ignored) {
    }
    clients.shutdownNow();
  }
}
