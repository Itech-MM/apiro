package io.github.itech_mm.apiro.spec;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ManifestVersionTest {

    @Test
    void currentVersionIsPositive() {
        assertTrue(ManifestVersion.CURRENT >= 1);
    }
}