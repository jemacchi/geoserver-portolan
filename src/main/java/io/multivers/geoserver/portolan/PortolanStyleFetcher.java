package io.multivers.geoserver.portolan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Downloads remote style documents with bounded, non-redirecting HTTP access. */
final class PortolanStyleFetcher implements PortolanStyleImporter.StyleContentFetcher {
    static final int MAX_STYLE_BYTES = 1024 * 1024;

    private final HttpClient client;

    PortolanStyleFetcher() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    PortolanStyleFetcher(HttpClient client) {
        this.client = client;
    }

    @Override
    public String fetch(URI uri) {
        validateRemoteUri(uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "geoserver-portolan")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                response.body().close();
                throw new IllegalArgumentException(
                        "Cannot read style from " + displayUri(uri) + ": HTTP " + response.statusCode());
            }
            long declaredLength =
                    response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declaredLength > MAX_STYLE_BYTES) {
                response.body().close();
                throw new IllegalArgumentException("Style exceeds maximum size of " + MAX_STYLE_BYTES + " bytes");
            }
            try (InputStream body = response.body()) {
                return new String(readLimited(body, MAX_STYLE_BYTES), StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot read style from " + displayUri(uri), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Interrupted while reading style from " + displayUri(uri), exception);
        }
    }

    static void validateRemoteUri(URI uri) {
        if (uri == null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Style URL must use HTTP or HTTPS with no credentials");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (isUnsafe(address)) {
                    throw new IllegalArgumentException("Style URL resolves to a private or local address");
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot resolve style host " + uri.getHost(), exception);
        }
    }

    private static boolean isUnsafe(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    static byte[] readLimited(InputStream input, int maximumBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maximumBytes) {
                throw new IllegalArgumentException("Style exceeds maximum size of " + maximumBytes + " bytes");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    static String displayUri(URI uri) {
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPath() == null ? "" : uri.getPath());
    }
}
