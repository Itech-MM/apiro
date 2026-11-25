package io.github.itech_mm.apiro.service.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DynamicVariablesTest {

    private static final Instant NOW = Instant.parse("2026-11-24T10:15:30Z");

    private static DynamicVariables fixed() {
        return new DynamicVariables(new Random(42), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void uuidIsVersionFour() {
        String value = fixed().generate("$uuid").orElseThrow();
        assertTrue(value.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"), value);
    }

    @Test
    void timestampsFollowTheClock() {
        DynamicVariables dynamic = fixed();
        assertEquals(String.valueOf(NOW.toEpochMilli()), dynamic.generate("$timestamp").orElseThrow());
        assertEquals("2026-11-24T10:15:30Z", dynamic.generate("$isoTimestamp").orElseThrow());
    }

    @Test
    void randomIntStaysInRange() {
        DynamicVariables dynamic = fixed();
        for (int i = 0; i < 200; i++) {
            int value = Integer.parseInt(dynamic.generate("$randomInt").orElseThrow());
            assertTrue(value >= 0 && value < 1_000_000, String.valueOf(value));
        }
    }

    @Test
    void randomEmailAndStringHaveExpectedShape() {
        DynamicVariables dynamic = fixed();
        assertTrue(dynamic.generate("$randomEmail").orElseThrow().matches("user[0-9a-f]{8}@example\\.test"));
        assertTrue(dynamic.generate("$randomString").orElseThrow().matches("[A-Za-z0-9]{8}"));
    }

    @Test
    void everyCallGivesANewValue() {
        DynamicVariables dynamic = fixed();
        assertNotEquals(dynamic.generate("$uuid").orElseThrow(), dynamic.generate("$uuid").orElseThrow());
        assertNotEquals(dynamic.generate("$randomEmail").orElseThrow(), dynamic.generate("$randomEmail").orElseThrow());
    }

    @Test
    void unknownNamesAreNotGenerated() {
        assertTrue(fixed().generate("$nope").isEmpty());
        assertTrue(fixed().generate("uuid").isEmpty());
        assertFalse(DynamicVariables.isKnown("$nope"));
        assertTrue(DynamicVariables.isKnown("$uuid"));
        assertTrue(DynamicVariables.isDynamic("$nope"));
        assertFalse(DynamicVariables.isDynamic("token"));
    }

    @Test
    void everyListedNameGenerates() {
        DynamicVariables dynamic = fixed();
        for (String name : DynamicVariables.names()) {
            assertTrue(dynamic.generate(name).isPresent(), name);
        }
    }
}
