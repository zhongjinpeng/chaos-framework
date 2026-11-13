package com.chaos.core.digest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class Sha256Tests {
  @Test
  void textBytesAndChunkedUploadsProduceTheSamePersistedDigest() {
    String text = " 合同📄\n";
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    String expected = "cd153b384cc338e860ad21b1448e1fde9cdc0113e4927838ef2712c0af6aa226";
    assertEquals(expected, Sha256.hex(text));
    assertEquals(expected, Sha256.hex(bytes));
    var streamDigest = Sha256.newDigest();
    streamDigest.update(bytes, 0, 3);
    streamDigest.update(bytes, 3, bytes.length - 3);
    assertEquals(expected, HexFormat.of().formatHex(streamDigest.digest()));
  }
}
