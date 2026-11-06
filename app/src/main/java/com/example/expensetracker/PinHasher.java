package com.example.expensetracker;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Salted, iterated PIN hash (PBKDF2 with HMAC-SHA256, implemented on {@link Mac} so it works on every
 * Android version from API 23; the built-in PBKDF2WithHmacSHA256 factory needs API 26). Pure Java.
 */
final class PinHasher {
    static final int DEFAULT_ITERATIONS = 20_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PinHasher() {}

    static byte[] newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return salt;
    }

    static String hash(String pin, byte[] salt, int iterations) {
        return toHex(pbkdf2(pin.getBytes(StandardCharsets.UTF_8), salt, iterations));
    }

    /** Constant-time comparison of the stored hash with the hash of {@code pin}. */
    static boolean verify(String pin, byte[] salt, int iterations, String expectedHex) {
        byte[] actual = pbkdf2(pin.getBytes(StandardCharsets.UTF_8), salt, iterations);
        return MessageDigest.isEqual(actual, fromHex(expectedHex));
    }

    /** PBKDF2-HMAC-SHA256, one 32-byte output block (RFC 8018). */
    static byte[] pbkdf2(byte[] password, byte[] salt, int iterations) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(password.length == 0 ? new byte[1] : password, "HmacSHA256"));
            byte[] block = new byte[salt.length + 4];
            System.arraycopy(salt, 0, block, 0, salt.length);
            block[salt.length + 3] = 1; // block index 1, big endian
            byte[] u = mac.doFinal(block);
            byte[] result = u.clone();
            for (int i = 1; i < iterations; i++) {
                u = mac.doFinal(u);
                for (int j = 0; j < result.length; j++) result[j] ^= u[j];
            }
            return result.length == KEY_BYTES ? result : java.util.Arrays.copyOf(result, KEY_BYTES);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    static byte[] fromHex(String hex) {
        if (hex == null || hex.length() % 2 != 0) return new byte[0];
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(2 * i), 16);
            int lo = Character.digit(hex.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) return new byte[0];
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
