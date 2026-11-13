package com.chaos.core.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Reads a byte array with an explicit allocation limit and one-byte overflow probe.
 */
public final class BoundedStreams {
    private BoundedStreams() {}

    /**
     * Reads from the current position without closing the caller-owned stream.
     * Empty content is accepted; callers decide whether it is meaningful.
     *
     * @param input caller-owned input stream
     * @param maxBytes maximum returned size in bytes, from zero to Integer.MAX_VALUE - 8
     * @param exceeded supplies the caller's exception when another byte exists beyond the limit
     * @return complete content, never exceeding maxBytes
     * @throws IOException when the input cannot be read
     */
    public static byte[] read(
            InputStream input, long maxBytes, Supplier<? extends RuntimeException> exceeded)
            throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(exceeded, "exceeded");
        if (maxBytes < 0 || maxBytes > Integer.MAX_VALUE - 8L) {
            throw new IllegalArgumentException("maxBytes is outside the byte array range");
        }
        byte[] content = input.readNBytes((int) maxBytes);
        if (content.length == maxBytes && input.read() != -1) {
            throw Objects.requireNonNull(exceeded.get(), "overflow exception");
        }
        return content;
    }
}
