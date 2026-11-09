package com.nopkg.hellodoc.services;

import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import static org.junit.jupiter.api.Assertions.*;

class ExternalAssetFetcherTest {
    @Test void rejectsNonPublicAndMixedDnsAnswers() throws Exception {
        assertThrows(Exception.class, () -> ExternalAssetFetcher.requirePublic(new InetAddress[] { InetAddress.getByName("127.0.0.1") }));
        assertThrows(Exception.class, () -> ExternalAssetFetcher.requirePublic(new InetAddress[] { InetAddress.getByName("8.8.8.8"), InetAddress.getByName("10.0.0.1") }));
        assertThrows(Exception.class, () -> ExternalAssetFetcher.requirePublic(new InetAddress[] { InetAddress.getByName("fc00::1") }));
        assertThrows(Exception.class, () -> ExternalAssetFetcher.requirePublic(new InetAddress[] { InetAddress.getByName("100.64.0.1") }));
        assertThrows(Exception.class, () -> ExternalAssetFetcher.requirePublic(new InetAddress[] { InetAddress.getByName("64:ff9b::a00:1") }));
        assertThrows(Exception.class, () -> ExternalAssetFetcher.requirePublic(new InetAddress[] { InetAddress.getByName("2002:0a00:0001::") }));
        assertThrows(Exception.class, () -> ExternalAssetFetcher.fetch("http://127.0.0.1/redirect"));
        assertDoesNotThrow(() -> ExternalAssetFetcher.requirePublic(new InetAddress[] { InetAddress.getByName("8.8.8.8") }));
    }
}
