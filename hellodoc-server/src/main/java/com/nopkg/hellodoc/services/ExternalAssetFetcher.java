package com.nopkg.hellodoc.services;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/** Fetches public assets without redirects or unchecked DNS resolution at connect time. */
public final class ExternalAssetFetcher {
    private static final int MAX_BYTES = 50 * 1024 * 1024;
    private ExternalAssetFetcher() {}
    public record Asset(byte[] bytes, String contentType, String fileName) {}

    static InetAddress[] requirePublic(InetAddress[] addresses) throws UnknownHostException {
        if (addresses.length == 0) throw new UnknownHostException("No addresses returned");
        for (InetAddress address : addresses) {
            byte[] b = address.getAddress();
            int first = b[0] & 255;
            int second = b[1] & 255;
            boolean special = b.length == 4 && (first == 0 || first == 10 || first == 127 || first >= 224
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254) || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && (second == 168 || second == 0)) || (first == 198 && (second == 18 || second == 19)))
                    || b.length == 16 && ((first & 0xe0) != 0x20 || (first == 0x20 && second == 0x02)
                    || (first == 0x20 && second == 0x01 && b[2] == 0 && b[3] == 0));
            if (special || address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isMulticastAddress()) throw new UnknownHostException("Access to non-public address is restricted");
        }
        return addresses;
    }

    public static Asset fetch(String input) throws IOException {
        URI uri;
        try { uri = URI.create(input.trim()); } catch (IllegalArgumentException e) { throw new IOException("Invalid URL", e); }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null || uri.getUserInfo() != null) throw new IOException("Invalid URL");
        // Numeric hosts can bypass a client's DNS resolver, so validate them before connecting as well.
        requirePublic(InetAddress.getAllByName(uri.getHost()));
        DnsResolver resolver = new DnsResolver() {
            public InetAddress[] resolve(String host) throws UnknownHostException { return requirePublic(InetAddress.getAllByName(host)); }
            public String resolveCanonicalHostname(String host) { return host; }
        };
        var manager = PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(resolver)
                .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(10))
                        .setSocketTimeout(Timeout.ofSeconds(15)).build()).build();
        var config = RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(15)).build();
        try (var client = HttpClients.custom().setConnectionManager(manager).setDefaultRequestConfig(config)
                .disableRedirectHandling().disableAutomaticRetries().build()) {
            var request = new HttpGet(uri);
            request.setHeader("User-Agent", "Hellodoc-Image-Proxy/1.0");
            return client.execute(request, response -> {
                if (response.getCode() < 200 || response.getCode() >= 300) throw new IOException("Asset server returned HTTP " + response.getCode());
                var entity = response.getEntity();
                String contentType = entity == null ? "" : entity.getContentType();
                if (contentType == null || !(contentType.toLowerCase(Locale.ROOT).startsWith("image/") || contentType.toLowerCase(Locale.ROOT).startsWith("video/"))) throw new IOException("Only image or video content is supported");
                if (entity.getContentLength() > MAX_BYTES) throw new IOException("File is too large to fetch");
                byte[] bytes;
                try (var stream = entity.getContent()) { bytes = stream.readNBytes(MAX_BYTES + 1); }
                if (bytes.length > MAX_BYTES) throw new IOException("File is too large to fetch");
                String path = uri.getPath();
                String name = path == null ? "file" : path.substring(path.lastIndexOf('/') + 1);
                if (name.isBlank()) name = "file";
                if (!name.contains(".")) name += "." + contentType.split("/")[1].split(";")[0];
                return new Asset(bytes, contentType, name);
            });
        }
    }
}
