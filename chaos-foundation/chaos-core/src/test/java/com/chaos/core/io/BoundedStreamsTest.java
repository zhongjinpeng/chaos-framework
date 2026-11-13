package com.chaos.core.io;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

class BoundedStreamsTest {
    @Test
    void exactLimitAndEmptyInputAreAcceptedWithoutClosing() throws Exception {
        var input =
                new ByteArrayInputStream(new byte[] {1, 2, 3}) {
                    @Override
                    public void close() {
                        fail("Caller owns stream");
                    }
                };
        assertArrayEquals(
                new byte[] {1, 2, 3},
                BoundedStreams.read(input, 3, () -> new IllegalStateException("too big")));
        assertEquals(
                0,
                BoundedStreams.read(
                                new ByteArrayInputStream(new byte[0]),
                                0,
                                () -> new IllegalStateException("too big"))
                        .length);
    }

    @Test
    void overflowConsumesOnlyOneExtraByteAndUsesCallerError() {
        var input = new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5});
        var expected = new IllegalArgumentException("business limit");
        assertSame(
                expected,
                assertThrows(
                        IllegalArgumentException.class,
                        () -> BoundedStreams.read(input, 2, () -> expected)));
        assertEquals(2, input.available());
        assertThrows(
                IllegalArgumentException.class,
                () -> BoundedStreams.read(input, Long.MAX_VALUE, () -> expected));
    }

    @Test
    void readFailureIsNotReportedAsOverflow() {
        var input =
                new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("broken");
                    }
                };
        assertEquals(
                "broken",
                assertThrows(
                                IOException.class,
                                () ->
                                        BoundedStreams.read(
                                                input, 10, () -> new IllegalArgumentException()))
                        .getMessage());
    }
}
