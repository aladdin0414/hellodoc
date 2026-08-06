package com.nopkg.hellodoc.util;

import com.nopkg.hellodoc.common.util.DigestUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.*;

public class DigestUtilTest {
    @Test
    void sha256Hex_hello_matchesExpected() {
        String hex = DigestUtil.sha256Hex("hello");
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hex);
    }

    @Test
    void sha256Base64_hello_matchesExpected() {
        String b64 = DigestUtil.sha256Base64("hello");
        assertEquals("LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ=", b64);
    }

    @Test
    void sha256Bytes_lengthIs32() {
        byte[] bytes = DigestUtil.sha256Bytes("hello");
        assertEquals(32, bytes.length);
    }

    @Test
    void sha256_null_throwsNpe() {
        Executable exe = () -> DigestUtil.sha256(null, false);
        assertThrows(NullPointerException.class, exe);
    }
}