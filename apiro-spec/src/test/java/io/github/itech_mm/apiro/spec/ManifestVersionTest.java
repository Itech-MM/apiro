package io.github.itech_mm.apiro.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ManifestVersionTest {
    private static Manifest manifestWithVersion(int version) {
        return new Manifest()
                .setManifestVersion(version)
                .setGenerator(new Generator().setName("test"))
                .setEndpoints(Arrays.asList(
                        new Endpoint().setId("GET /ping#P.ping()").setMethod("GET").setPath("/ping")));
    }

    @Test
    void versionIsPinnedSoABumpIsDeliberate() {
        // Bump only for a breaking format change (design section 9), and update this test with it.
        assertEquals(1, ManifestVersion.CURRENT);
    }

    @Test
    void currentVersionIsAccepted() {
        assertEquals(0, ManifestValidator.validate(manifestWithVersion(ManifestVersion.CURRENT)).size());
    }

    @Test
    void higherMajorVersionIsRejectedWithAClearMessage() {
        String problem = ManifestValidator.validate(manifestWithVersion(ManifestVersion.CURRENT + 1)).get(0);
        assertTrue(problem.contains("manifestVersion " + (ManifestVersion.CURRENT + 1)), problem);
        assertTrue(problem.contains("newer than the supported version " + ManifestVersion.CURRENT), problem);
    }
}
