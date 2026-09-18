package com.michael.chaos.core.idempotent.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.michael.chaos.core.idempotent.IdempotentRecord;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 内存响应快照存储测试。
 */
class InMemoryIdempotentRecordStoreTest {

    private static IdempotentRecord record(String body) {
        return new IdempotentRecord(200, "text/plain", body.getBytes(StandardCharsets.UTF_8), Map.of(), 1L);
    }

    /**
     * 保存的首次响应快照要能原样读回，重复请求回放才有意义。
     */
    @Test
    void shouldReturnSavedRecord() {
        InMemoryIdempotentRecordStore store = new InMemoryIdempotentRecordStore();
        store.save("k1", record("first"), Duration.ofMinutes(1));

        assertTrue(store.find("k1").isPresent());
        assertEquals("first", new String(store.find("k1").orElseThrow().body(), StandardCharsets.UTF_8));
    }

    /**
     * 过期快照必须读不到，否则客户端会拿到过时的响应。
     */
    @Test
    void shouldNotReturnExpiredRecord() throws InterruptedException {
        InMemoryIdempotentRecordStore store = new InMemoryIdempotentRecordStore();
        store.save("k1", record("first"), Duration.ofMillis(1));
        Thread.sleep(10);

        assertFalse(store.find("k1").isPresent());
        assertEquals(0, store.size());
    }

    /**
     * 同键再次保存以最新为准，避免残留旧快照。
     */
    @Test
    void shouldOverwriteExistingRecord() {
        InMemoryIdempotentRecordStore store = new InMemoryIdempotentRecordStore();
        store.save("k1", record("first"), Duration.ofMinutes(1));
        store.save("k1", record("second"), Duration.ofMinutes(1));

        assertEquals(1, store.size());
        assertEquals("second", new String(store.find("k1").orElseThrow().body(), StandardCharsets.UTF_8));
    }

    /**
     * 删除后立即读不到，配合业务失败时的清理逻辑。
     */
    @Test
    void shouldRemoveRecord() {
        InMemoryIdempotentRecordStore store = new InMemoryIdempotentRecordStore();
        store.save("k1", record("first"), Duration.ofMinutes(1));
        store.remove("k1");

        assertFalse(store.find("k1").isPresent());
    }

    /**
     * 容量耗尽时淘汰最早写入的条目，而不是拒绝写入：拒绝写入会让新请求永远存不下快照，
     * 攻击者用随机幂等 key 灌满容量即可让回放整体失效。
     */
    @Test
    void shouldEvictOldestWhenCapacityReached() {
        InMemoryIdempotentRecordStore store = new InMemoryIdempotentRecordStore(2);
        store.save("k1", record("1"), Duration.ofMinutes(1));
        store.save("k2", record("2"), Duration.ofMinutes(1));
        store.save("k3", record("3"), Duration.ofMinutes(1));

        assertEquals(2, store.size());
        assertFalse(store.find("k1").isPresent());
        assertTrue(store.find("k3").isPresent());
    }
}
