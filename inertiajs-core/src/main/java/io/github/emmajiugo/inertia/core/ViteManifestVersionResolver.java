package io.github.emmajiugo.inertia.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * Resolves the Inertia asset version from a Vite manifest.json file.
 * Computes an MD5 hash of the manifest content so the version changes
 * automatically whenever frontend assets are rebuilt.
 *
 * Usage:
 * <pre>
 * InertiaConfig.builder()
 *     .version(ViteManifestVersionResolver.resolve("static/.vite/manifest.json"))
 *     .templateResolver(...)
 *     .build();
 * </pre>
 *
 * Or as a lazy supplier (re-reads on every request, useful in dev):
 * <pre>
 * InertiaConfig.builder()
 *     .versionSupplier(ViteManifestVersionResolver.lazy("static/.vite/manifest.json"))
 *     .build();
 * </pre>
 */
public final class ViteManifestVersionResolver {

    private ViteManifestVersionResolver() {}

    /**
     * Reads the manifest file from the classpath and returns its MD5 hash.
     * Call once at startup for production use.
     */
    public static String resolve(String classpathLocation) {
        String content = loadFromClasspath(classpathLocation);
        return md5(content);
    }

    /**
     * Returns a supplier that re-reads the manifest on each call.
     * Useful during development when assets change frequently.
     */
    public static Supplier<String> lazy(String classpathLocation) {
        return () -> {
            try {
                String content = loadFromClasspath(classpathLocation);
                return md5(content);
            } catch (Exception e) {
                return "dev";
            }
        };
    }

    private static String loadFromClasspath(String location) {
        try (InputStream is = ViteManifestVersionResolver.class.getClassLoader()
                .getResourceAsStream(location)) {
            if (is == null) {
                throw new IllegalArgumentException(
                        "Vite manifest not found on classpath: " + location
                                + " — have you run 'npm run build'?");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read Vite manifest: " + location, e);
        }
    }

    private static String md5(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
