package io.github.itech_mm.apiro.service.variables;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public final class DynamicVariables {

    public static final String PREFIX = "$";

    private static final List<String> NAMES = List.of("$uuid", "$timestamp", "$isoTimestamp", "$randomInt",
            "$randomEmail", "$randomString");
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int RANDOM_INT_BOUND = 1_000_000;
    private static final int RANDOM_STRING_LENGTH = 8;

    private final Random random;
    private final Clock clock;

    public DynamicVariables(Random random, Clock clock) {
        this.random = random;
        this.clock = clock;
    }

    public static DynamicVariables system() {
        return new DynamicVariables(new SecureRandom(), Clock.systemUTC());
    }

    public static List<String> names() {
        return NAMES;
    }

    public static boolean isKnown(String name) {
        return NAMES.contains(name);
    }

    public static boolean isDynamic(String name) {
        return name != null && name.startsWith(PREFIX);
    }

    public Optional<String> generate(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return switch (name) {
            case "$uuid" -> Optional.of(uuid());
            case "$timestamp" -> Optional.of(String.valueOf(clock.millis()));
            case "$isoTimestamp" -> Optional.of(DateTimeFormatter.ISO_INSTANT.format(clock.instant()));
            case "$randomInt" -> Optional.of(String.valueOf(random.nextInt(RANDOM_INT_BOUND)));
            case "$randomEmail" -> Optional.of("user" + String.format("%08x", random.nextInt()) + "@example.test");
            case "$randomString" -> Optional.of(randomString());
            default -> Optional.empty();
        };
    }

    private String uuid() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x40);
        bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);
        long high = 0;
        long low = 0;
        for (int i = 0; i < 8; i++) {
            high = (high << 8) | (bytes[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            low = (low << 8) | (bytes[i] & 0xff);
        }
        return new UUID(high, low).toString();
    }

    private String randomString() {
        StringBuilder out = new StringBuilder(RANDOM_STRING_LENGTH);
        for (int i = 0; i < RANDOM_STRING_LENGTH; i++) {
            out.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return out.toString();
    }
}
