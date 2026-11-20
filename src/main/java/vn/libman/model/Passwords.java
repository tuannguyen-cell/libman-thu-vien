package vn.libman.model;

import java.security.*;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Passwords {

  private Passwords() {}

  private static byte[] derive(String password, byte[] salt) {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 210000, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(spec)
        .getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    } finally {
      spec.clearPassword();
    }
  }

  public static String hash(String password) {
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    var b = Base64.getEncoder();
    return (
      b.encodeToString(salt) + ":" + b.encodeToString(derive(password, salt))
    );
  }

  public static boolean matches(String password, String stored) {
    var b = Base64.getDecoder();
    var parts = stored.split(":");
    return MessageDigest.isEqual(
      b.decode(parts[1]),
      derive(password, b.decode(parts[0]))
    );
  }
}
