package com.intellilib.server.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class CryptoUtil {
    private static final SecureRandom RNG = new SecureRandom();
    private CryptoUtil() {}

    public static String randomToken(int bytes) {
        byte[] data = new byte[bytes];
        RNG.nextBytes(data);
        return HexFormat.of().formatHex(data);
    }

    public static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** Normalizes raw values coming from QR/barcode scanners before hashing or matching. */
    public static String normalizeScannedCode(String value) {
        if (value == null) return "";
        return value.strip()
                .replace("\\uFEFF", "")
                .replace("\\u200B", "")
                .replace("\\u200C", "")
                .replace("\\u200D", "");
    }

    public static String hmacSha256(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
