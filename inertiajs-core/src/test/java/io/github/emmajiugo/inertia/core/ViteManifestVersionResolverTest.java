package io.github.emmajiugo.inertia.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ViteManifestVersionResolverTest {

    @Test
    void resolvesVersionFromManifest() {
        String version = ViteManifestVersionResolver.resolve("static/.vite/manifest.json");
        assertThat(version).isNotNull().isNotBlank().hasSize(32); // MD5 hex = 32 chars
    }

    @Test
    void sameManifestProducesSameHash() {
        String v1 = ViteManifestVersionResolver.resolve("static/.vite/manifest.json");
        String v2 = ViteManifestVersionResolver.resolve("static/.vite/manifest.json");
        assertThat(v1).isEqualTo(v2);
    }

    @Test
    void throwsForMissingManifest() {
        assertThatThrownBy(() -> ViteManifestVersionResolver.resolve("nonexistent/manifest.json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void lazySupplierReturnsConsistentHash() {
        var supplier = ViteManifestVersionResolver.lazy("static/.vite/manifest.json");
        String v1 = supplier.get();
        String v2 = supplier.get();
        assertThat(v1).isEqualTo(v2).hasSize(32);
    }

    @Test
    void lazySupplierReturnsFallbackForMissingManifest() {
        var supplier = ViteManifestVersionResolver.lazy("nonexistent/manifest.json");
        assertThat(supplier.get()).isEqualTo("dev");
    }
}
