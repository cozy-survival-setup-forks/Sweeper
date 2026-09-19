package dev.sweeper.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UtilTest {

    @Test
    void parsesDurations() {
        assertEquals(Duration.ofSeconds(30), Durations.parse("30s"));
        assertEquals(Duration.ofMinutes(10), Durations.parse("10m"));
        assertEquals(Duration.ofSeconds(5400), Durations.parse("1h30m"));
        assertEquals(Duration.ofSeconds(45), Durations.parse("45"));
        assertEquals(Duration.ofSeconds(90), Durations.parse("1m 30s"));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> Durations.parse("soon"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parse("10x"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parse(""));
    }

    @Test
    void formatsCountdowns() {
        assertEquals("0s", TimeFormat.compact(0));
        assertEquals("45s", TimeFormat.compact(45));
        assertEquals("1m 30s", TimeFormat.compact(90));
        assertEquals("2h", TimeFormat.compact(7200));
        assertEquals("1h 1m 1s", TimeFormat.compact(3661));
    }

    @Test
    void convertsLegacyCodes() {
        assertEquals("<#FF8C8C>hi<white>", Legacy.toMiniMessage("&#FF8C8Chi&f"));
        assertEquals("<#112233>x", Legacy.toMiniMessage("&x&1&1&2&2&3&3x"));
        assertEquals("<bold>a<reset>", Legacy.toMiniMessage("&la&r"));
        assertEquals("<bold>plain</bold>", Legacy.toMiniMessage("<bold>plain</bold>"));
    }
}
