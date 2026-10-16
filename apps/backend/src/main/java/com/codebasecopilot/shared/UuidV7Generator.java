package com.codebasecopilot.shared;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UuidV7Generator implements IdGenerator {

    private final Clock clock;
    private final SecureRandom random;

    public UuidV7Generator() {
        this(Clock.systemUTC(), new SecureRandom());
    }

    UuidV7Generator(Clock clock, SecureRandom random) {
        this.clock = clock;
        this.random = random;
    }

    @Override
    public UUID newId() {
        long timestamp = clock.millis() & 0xFFFF_FFFF_FFFFL;
        long randA = random.nextLong() & 0x0FFFL;
        long randB = random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL;

        long mostSigBits = (timestamp << 16) | (0x7L << 12) | randA;
        long leastSigBits = 0x8000_0000_0000_0000L | randB;
        return new UUID(mostSigBits, leastSigBits);
    }
}
