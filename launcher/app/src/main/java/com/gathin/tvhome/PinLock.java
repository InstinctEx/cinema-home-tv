package com.gathin.tvhome;

import android.util.Base64;
import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** A local convenience lock for hidden apps, never a device-wide parental-control boundary. */
final class PinLock {
  static boolean valid(String pin) {
    return FeaturePolicy.pin(pin);
  }

  static String create(String pin) throws Exception {
    if (!valid(pin)) throw new IllegalArgumentException("Use 4–12 digits or directions");
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    return Base64.encodeToString(salt, Base64.NO_WRAP)
        + ":"
        + Base64.encodeToString(derive(pin, salt), Base64.NO_WRAP);
  }

  static boolean matches(String stored, String pin) {
    try {
      if (!valid(pin)) return false;
      String[] parts = stored.split(":");
      return MessageDigest.isEqual(
          Base64.decode(parts[1], Base64.NO_WRAP),
          derive(pin, Base64.decode(parts[0], Base64.NO_WRAP)));
    } catch (Exception e) {
      return false;
    }
  }

  private static byte[] derive(String pin, byte[] salt) throws Exception {
    PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, 120000, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } finally {
      spec.clearPassword();
    }
  }
}
