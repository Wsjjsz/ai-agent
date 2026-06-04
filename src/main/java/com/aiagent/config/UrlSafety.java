package com.aiagent.config;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

/**
 * Shared SSRF guard for user-controlled remote URLs.
 */
public final class UrlSafety {

    private UrlSafety() {
    }

    public static URI requireSafeHttpUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }
        try {
            URI uri = URI.create(rawUrl.trim()).normalize();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new IllegalArgumentException("Only HTTP/HTTPS URLs are allowed");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("URL host is required");
            }
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isUnsafeAddress(address)) {
                    throw new IllegalArgumentException("Internal network URLs are blocked");
                }
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("URL is not reachable or safe", e);
        }
    }

    public static boolean isSafeHttpUrl(String rawUrl) {
        try {
            requireSafeHttpUrl(rawUrl);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isUnsafeAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0
                    || first == 10
                    || first == 127
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168);
        }
        if (bytes.length == 16) {
            int first = bytes[0] & 0xff;
            return (first & 0xfe) == 0xfc;
        }
        return true;
    }
}
