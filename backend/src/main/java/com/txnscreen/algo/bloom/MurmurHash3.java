package com.txnscreen.algo.bloom;

import java.nio.charset.StandardCharsets;

/**
 * MurmurHash3_x64_128, following Austin Appleby's reference implementation.
 */
public final class MurmurHash3 {

    private static final long C1 = 0x87c37b91114253d5L;
    private static final long C2 = 0x4cf5ad432745937fL;
    private static final long FMIX_C1 = 0xff51afd7ed558ccdL;
    private static final long FMIX_C2 = 0xc4ceb9fe1a85ec53L;
    private static final long H1_ADD = 0x52dce729L;
    private static final long H2_ADD = 0x38495ab5L;
    private static final long UNSIGNED_INT_MASK = 0xFFFFFFFFL;
    private static final long UNSIGNED_BYTE_MASK = 0xFFL;
    private static final int BLOCK_BYTES = 16;
    private static final int LONG_BYTES = 8;

    private MurmurHash3() {
    }

    /**
     * Hashes the UTF-8 bytes of a string.
     *
     * <p>Time complexity: O(len), where len is the UTF-8 byte length.
     * Space complexity: O(1), beyond the temporary O(len) UTF-8 copy of the string.
     *
     * @param s the string to hash
     * @param seed the seed, of which only the low 32 bits are used, read as unsigned
     * @return the 128-bit hash
     */
    public static Hash128 hash128(String s, long seed) {
        return hash128(s.getBytes(StandardCharsets.UTF_8), seed);
    }

    /**
     * Hashes a byte array.
     *
     * <p>Time complexity: O(len). Space complexity: O(1).
     *
     * @param data the bytes to hash
     * @param seed the seed, of which only the low 32 bits are used, read as unsigned
     * @return the 128-bit hash
     */
    public static Hash128 hash128(byte[] data, long seed) {
        long h1 = seed & UNSIGNED_INT_MASK;
        long h2 = h1;

        int tail = data.length - data.length % BLOCK_BYTES;
        for (int offset = 0; offset < tail; offset += BLOCK_BYTES) {
            h1 ^= mixK1(readLongLittleEndian(data, offset));
            h1 = Long.rotateLeft(h1, 27);
            h1 += h2;
            h1 = h1 * 5 + H1_ADD;

            h2 ^= mixK2(readLongLittleEndian(data, offset + LONG_BYTES));
            h2 = Long.rotateLeft(h2, 31);
            h2 += h1;
            h2 = h2 * 5 + H2_ADD;
        }

        long k1 = 0;
        long k2 = 0;
        // Every case deliberately falls through, as in the reference implementation.
        switch (data.length % BLOCK_BYTES) {
            case 15: k2 ^= unsignedByte(data, tail + 14) << 48;
            case 14: k2 ^= unsignedByte(data, tail + 13) << 40;
            case 13: k2 ^= unsignedByte(data, tail + 12) << 32;
            case 12: k2 ^= unsignedByte(data, tail + 11) << 24;
            case 11: k2 ^= unsignedByte(data, tail + 10) << 16;
            case 10: k2 ^= unsignedByte(data, tail + 9) << 8;
            case 9:
                k2 ^= unsignedByte(data, tail + 8);
                h2 ^= mixK2(k2);
            case 8: k1 ^= unsignedByte(data, tail + 7) << 56;
            case 7: k1 ^= unsignedByte(data, tail + 6) << 48;
            case 6: k1 ^= unsignedByte(data, tail + 5) << 40;
            case 5: k1 ^= unsignedByte(data, tail + 4) << 32;
            case 4: k1 ^= unsignedByte(data, tail + 3) << 24;
            case 3: k1 ^= unsignedByte(data, tail + 2) << 16;
            case 2: k1 ^= unsignedByte(data, tail + 1) << 8;
            case 1:
                k1 ^= unsignedByte(data, tail);
                h1 ^= mixK1(k1);
            default:
        }

        h1 ^= data.length;
        h2 ^= data.length;

        h1 += h2;
        h2 += h1;

        h1 = fmix64(h1);
        h2 = fmix64(h2);

        h1 += h2;
        h2 += h1;

        return new Hash128(h1, h2);
    }

    private static long mixK1(long k1) {
        return Long.rotateLeft(k1 * C1, 31) * C2;
    }

    private static long mixK2(long k2) {
        return Long.rotateLeft(k2 * C2, 33) * C1;
    }

    private static long fmix64(long value) {
        long k = value;
        k ^= k >>> 33;
        k *= FMIX_C1;
        k ^= k >>> 33;
        k *= FMIX_C2;
        k ^= k >>> 33;
        return k;
    }

    private static long readLongLittleEndian(byte[] data, int offset) {
        long value = 0;
        for (int i = LONG_BYTES - 1; i >= 0; i--) {
            value = (value << LONG_BYTES) | unsignedByte(data, offset + i);
        }
        return value;
    }

    private static long unsignedByte(byte[] data, int index) {
        return data[index] & UNSIGNED_BYTE_MASK;
    }
}
