package com.nopkg.hellodoc.storage;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Component
public class StorageUrlSigner {

    @Value("${storage.url-signing-secret:${jwt.secret}}")
    private String secret;

    private Mac mac;

    @PostConstruct
    void init() {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            this.mac = m;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot init StorageUrlSigner", e);
        }
    }

    public String sign(String key, long expEpochSeconds) {
        String payload = key + "|" + expEpochSeconds;
        byte[] digest;
        synchronized (this) {
            digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
