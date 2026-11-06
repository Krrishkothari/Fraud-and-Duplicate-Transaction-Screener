package com.txnscreen.algo.bloom;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.common.hash.Hashing;

class MurmurHash3Test {

    private static final long[] SEEDS = {0, 42};
    private static final int STRING_COUNT = 2_000;
    private static final int MAX_STRING_LENGTH = 100;
    private static final int TAIL_LENGTH_COUNT = 16;
    private static final long RANDOM_SEED = 20_261_106L;
    private static final int FIRST_NON_ASCII_CHAR = 0x80;

    @Test
    void matchesGuavaForRandomStrings() {
        Random random = new Random(RANDOM_SEED);
        Set<Integer> tailLengths = new HashSet<>();

        for (int i = 0; i < STRING_COUNT; i++) {
            String text = randomString(random);
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            tailLengths.add(bytes.length % TAIL_LENGTH_COUNT);

            for (long seed : SEEDS) {
                Hash128 expected = guavaHash(bytes, seed);
                assertEquals(expected, MurmurHash3.hash128(text, seed), "string overload, seed " + seed);
                assertEquals(expected, MurmurHash3.hash128(bytes, seed), "byte overload, seed " + seed);
            }
        }

        assertEquals(TAIL_LENGTH_COUNT, tailLengths.size(), "every tail length 0 to 15 must be covered");
    }

    @Test
    void emptyInputWithSeedZeroHashesToZero() {
        Hash128 hash = MurmurHash3.hash128(new byte[0], 0);

        assertEquals(0L, hash.h1());
        assertEquals(0L, hash.h2());
    }

    private static Hash128 guavaHash(byte[] bytes, long seed) {
        byte[] digest = Hashing.murmur3_128((int) seed).hashBytes(bytes).asBytes();
        ByteBuffer buffer = ByteBuffer.wrap(digest).order(ByteOrder.LITTLE_ENDIAN);
        return new Hash128(buffer.getLong(), buffer.getLong());
    }

    /**
     * Mixes ASCII and multi-byte characters. Multi-byte characters encode to bytes with the high bit set,
     * which is where a missing unsigned conversion would change the hash.
     */
    private static String randomString(Random random) {
        int length = random.nextInt(MAX_STRING_LENGTH + 1);
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int character = random.nextBoolean()
                    ? random.nextInt(FIRST_NON_ASCII_CHAR)
                    : random.nextInt(FIRST_NON_ASCII_CHAR, Character.MIN_SURROGATE);
            text.append((char) character);
        }
        return text.toString();
    }
}
