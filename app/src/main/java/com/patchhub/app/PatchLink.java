package com.patchhub.app;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Normalizes direct HTTPS links and GitHub blob links; no redirects are followed. */
final class PatchLink {
    static String normalize(String value) throws URISyntaxException {
        URI uri = new URI(value.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getPort() != -1 || uri.getRawFragment() != null)
            throw new IllegalArgumentException("Use a direct HTTPS URL without credentials, custom ports, or fragments.");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("github.com")) {
            String path = uri.getRawPath();
            String[] parts = path == null ? new String[0] : path.split("/", 6);
            if (parts.length != 6 || !"blob".equals(parts[3]) || parts[1].isEmpty() || parts[2].isEmpty() || parts[4].isEmpty() || parts[5].isEmpty() || uri.getRawQuery() != null)
                throw new IllegalArgumentException("Use a GitHub file URL containing /blob/ or a direct HTTPS file URL.");
            return "https://raw.githubusercontent.com/" + parts[1] + "/" + parts[2] + "/" + parts[4] + "/" + parts[5];
        }
        return uri.toASCIIString();
    }
}
