package com.invi.api.common;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.env.Environment;

/**
 * Startup checks shared by the packaged deployment profiles (https-test, prod). They read the
 * effective settings without connecting to any external service, and their errors name only the
 * property, never its value: a misconfigured value can contain a credential.
 */
final class DeploymentChecks {

    static final String DATASOURCE_URL = "spring.datasource.url";
    private static final String SSL_FACTORY = "org.postgresql.ssl.DefaultJavaSSLFactory";

    private final String profile;
    private final Environment environment;

    DeploymentChecks(String profile, Environment environment) {
        this.profile = profile;
        this.environment = environment;
    }

    /** Hardening every packaged deployment keeps: TLS everywhere, real secrets, minimal surface. */
    void requireHardenedBaseline() {
        validateHttps(property("invi.frontend.base-url"), "invi.frontend.base-url", true);

        String jwt = property("invi.jwt.secret");
        require(!jwt.isBlank() && !jwt.stripLeading().startsWith("dev-only-")
                && jwt.getBytes(StandardCharsets.UTF_8).length >= 64, "invi.jwt.secret");

        validateDatabase(property(DATASOURCE_URL));
        nonBlank("spring.datasource.username");
        nonBlank("spring.datasource.password");
        require(property("invi.r2.account-id").matches("[0-9a-f]{32}"), "invi.r2.account-id");
        nonBlank("invi.r2.access-key-id");
        nonBlank("invi.r2.secret-access-key");
        validateHttps(property("invi.r2.public-base-url"), "invi.r2.public-base-url", false);
        String bucket = property("invi.r2.bucket");
        require(bucket.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")
                && !bucket.equals("invi-media"), "invi.r2.bucket");

        exact("server.forward-headers-strategy", "framework");
        exact("management.endpoints.web.exposure.include", "health");
        exact("management.endpoint.health.show-details", "never");
        exact("spring.jpa.hibernate.ddl-auto", "validate");
    }

    /** The database name in the JDBC URL, already validated by {@link #requireHardenedBaseline}. */
    String databaseName() {
        String value = property(DATASOURCE_URL);
        return parse(value.substring("jdbc:".length()), DATASOURCE_URL).getPath().substring(1);
    }

    void validateHttps(String value, String property, boolean originOnly) {
        URI uri = parse(value, property);
        require("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
                && validPort(uri) && !localHost(uri.getHost()), property);
        require(!originOnly || uri.getRawPath() == null || uri.getRawPath().isEmpty()
                || uri.getRawPath().equals("/"), property);
    }

    private void validateDatabase(String value) {
        require(value.startsWith("jdbc:postgresql://"), DATASOURCE_URL);
        URI uri = parse(value.substring("jdbc:".length()), DATASOURCE_URL);
        require(uri.getHost() != null && !localHost(uri.getHost()) && validPort(uri)
                && uri.getRawUserInfo() == null && uri.getRawFragment() == null, DATASOURCE_URL);
        String database = uri.getPath();
        require(database != null && database.length() > 1 && !database.substring(1).isBlank()
                && !database.substring(1).contains("/"), DATASOURCE_URL);
        Map<String, String> parameters = new HashMap<>();
        String query = uri.getRawQuery();
        require(query != null && !query.isEmpty(), DATASOURCE_URL);
        for (String parameter : query.split("&", -1)) {
            int equals = parameter.indexOf('=');
            require(equals > 0, DATASOURCE_URL);
            String key = decode(parameter.substring(0, equals));
            String decodedValue = decode(parameter.substring(equals + 1));
            String foldedKey = key.toLowerCase(Locale.ROOT);
            require(!key.isBlank() && !parameters.containsKey(foldedKey)
                    && !foldedKey.equals("user") && !foldedKey.equals("username")
                    && !foldedKey.equals("password") && !foldedKey.equals("sslpassword")
                    && !foldedKey.equals("sslhostnameverifier") && !foldedKey.equals("sslfactoryarg"), DATASOURCE_URL);
            // PostgreSQL property names are case-sensitive. Do not accept lookalike SSL keys.
            if (foldedKey.equals("sslmode") || foldedKey.equals("sslfactory") || foldedKey.equals("ssl")) {
                require(key.equals(foldedKey), DATASOURCE_URL);
            }
            parameters.put(foldedKey, decodedValue);
        }
        require("verify-full".equals(parameters.get("sslmode"))
                && SSL_FACTORY.equals(parameters.get("sslfactory")), DATASOURCE_URL);
        require(!parameters.containsKey("ssl") || "true".equals(parameters.get("ssl")), DATASOURCE_URL);
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            throw invalid(DATASOURCE_URL);
        }
    }

    private URI parse(String value, String property) {
        try {
            return new URI(value);
        } catch (URISyntaxException ignored) {
            // Never attach parser exceptions: their messages contain the original URI.
            throw invalid(property);
        }
    }

    private static boolean validPort(URI uri) {
        return uri.getPort() == -1 || (uri.getPort() >= 1 && uri.getPort() <= 65535);
    }

    static boolean localHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.equals("localhost") || normalized.endsWith(".localhost")) {
            return true;
        }
        if (normalized.startsWith("[")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        // Inspect literal addresses only. DNS resolution would make startup network-dependent.
        if (!normalized.contains(":")) {
            if (normalized.matches("127(?:\\.[0-9]{1,3}){3}") || normalized.equals("0.0.0.0")) {
                return true;
            }
            // Non-canonical numeric hosts can be interpreted as loopback by URL/OS parsers.
            return normalized.matches("(?:0x[0-9a-f]+|[0-9]+)(?:\\.(?:0x[0-9a-f]+|[0-9]+)){0,3}")
                    && !normalized.matches("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}");
        }
        int zone = normalized.indexOf('%');
        if (zone >= 0) {
            normalized = normalized.substring(0, zone);
        }
        if (normalized.contains(".")) {
            int lastColon = normalized.lastIndexOf(':');
            String[] octets = normalized.substring(lastColon + 1).split("\\.");
            if (octets.length != 4) {
                return true;
            }
            try {
                int a = Integer.parseInt(octets[0]);
                int b = Integer.parseInt(octets[1]);
                int c = Integer.parseInt(octets[2]);
                int d = Integer.parseInt(octets[3]);
                normalized = normalized.substring(0, lastColon + 1)
                        + Integer.toHexString((a << 8) | b) + ":" + Integer.toHexString((c << 8) | d);
            } catch (NumberFormatException ignored) {
                return true;
            }
        }
        String[] halves = normalized.split("::", -1);
        String[] left = halves[0].isEmpty() ? new String[0] : halves[0].split(":");
        String[] right = halves.length == 2 && !halves[1].isEmpty() ? halves[1].split(":") : new String[0];
        int[] words = new int[8];
        try {
            for (int i = 0; i < left.length; i++) {
                words[i] = Integer.parseInt(left[i], 16);
            }
            for (int i = 0; i < right.length; i++) {
                words[8 - right.length + i] = Integer.parseInt(right[i], 16);
            }
        } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
            return true;
        }
        boolean zeroPrefix = true;
        for (int i = 0; i < 5; i++) {
            zeroPrefix &= words[i] == 0;
        }
        return zeroPrefix && ((words[5] == 0 && words[6] == 0 && words[7] <= 1)
                || ((words[5] == 0 || words[5] == 0xffff) && (words[6] >>> 8) == 127));
    }

    void nonBlank(String name) {
        require(!property(name).isBlank(), name);
    }

    void exact(String name, String expected) {
        require(expected.equals(property(name)), name);
    }

    String property(String name) {
        try {
            String value = environment.getProperty(name);
            require(value != null, name);
            return value;
        } catch (RuntimeException ignored) {
            // Placeholder/conversion errors can also include secret configuration values.
            throw invalid(name);
        }
    }

    void require(boolean condition, String property) {
        if (!condition) {
            throw invalid(property);
        }
    }

    IllegalStateException invalid(String property) {
        return new IllegalStateException("Invalid " + profile + " configuration: " + property);
    }
}
