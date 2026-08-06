package com.nopkg.hellodoc.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

public final class DigestUtil {
    private DigestUtil() {}

    public static String sha256(String s, boolean base64) {
        Objects.requireNonNull(s, "s");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            if (base64) {
                return Base64.getEncoder().encodeToString(digest);
            }
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }

    public static String sha256Hex(String s) {
        return sha256(s, false);
    }

    public static String sha256Base64(String s) {
        return sha256(s, true);
    }

    public static byte[] sha256Bytes(String s) {
        Objects.requireNonNull(s, "s");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }
}