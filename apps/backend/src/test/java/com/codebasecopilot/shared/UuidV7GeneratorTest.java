package com.codebasecopilot.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

    @Test
    void generatesVersion7WithRfc9562Variant() {
        UUID id = new UuidV7Generator().newId();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void encodesTimestampInMostSignificantBits() {
        Instant now = Instant.parse("2026-10-15T12:00:00.123Z");
        UuidV7Generator generator = new UuidV7Generator(fixedClock(now), new SecureRandom());

        UUID id = generator.newId();

        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(now.toEpochMilli());
    }

    @Test
    void ordersIdsFromDifferentMilliseconds() {
        Instant earlier = Instant.parse("2026-10-15T12:00:00.000Z");
        Instant later = earlier.plusMillis(1);
        SecureRandom random = new SecureRandom();

        UUID first = new UuidV7Generator(fixedClock(earlier), random).newId();
        UUID second = new UuidV7Generator(fixedClock(later), random).newId();

        assertThat(first.toString()).isLessThan(second.toString());
    }

    private static Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
