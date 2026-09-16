package com.michael.chaos.web.support;

import com.michael.chaos.core.exception.BizException;
import com.michael.chaos.core.exception.ChaosException;
import com.michael.chaos.core.exception.CommonErrorCode;
import com.michael.chaos.web.idempotent.Idempotent;
import com.michael.chaos.web.ratelimit.RateLimit;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Web 层 MockMvc 测试用控制器。
 */
@RestController
public class TestController {

    public final AtomicInteger created = new AtomicInteger();

    @Idempotent
    @PostMapping("/orders")
    public Map<String, Object> create(@RequestParam(defaultValue = "false") boolean fail) {
        if (fail) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "invalid order");
        }
        return Map.of("id", created.incrementAndGet());
    }

    @Idempotent(requireKey = false)
    @PostMapping("/orders/optional-key")
    public Map<String, Object> optionalKey() {
        return Map.of("id", created.incrementAndGet());
    }

    /**
     * 201 + Location 的创建类接口：回放必须连状态码和 Location 一起还原。
     */
    @Idempotent
    @PostMapping("/orders/created")
    public ResponseEntity<Map<String, Object>> createdWithLocation() {
        int id = created.incrementAndGet();
        return ResponseEntity.created(URI.create("/orders/" + id)).body(Map.of("id", id));
    }

    /**
     * 关闭回放的幂等接口：重复请求仍然返回 409。
     */
    @Idempotent(replay = false)
    @PostMapping("/orders/no-replay")
    public Map<String, Object> noReplay() {
        return Map.of("id", created.incrementAndGet());
    }

    @RateLimit(permitsPerSecond = 1)
    @GetMapping("/limited")
    public String limited() {
        return "ok";
    }

    @GetMapping("/string-entity")
    public ResponseEntity<String> stringEntity() {
        return ResponseEntity.ok("plain");
    }

    @GetMapping("/object-string")
    public Object objectString() {
        return "plain-object";
    }

    @GetMapping("/map")
    public Map<String, Object> map() {
        return Map.of("date", "2026-01-02", "count", 1);
    }

    @GetMapping("/status")
    public String status() {
        throw new ResponseStatusException(HttpStatus.GONE, "resource gone with secret detail");
    }

    @GetMapping("/server-error")
    public String serverError() {
        throw new ChaosException(CommonErrorCode.INTERNAL_ERROR, "jdbc:mysql://10.0.0.1 password=secret");
    }

    @GetMapping("/header")
    public String header(@RequestHeader("X-Required") String required) {
        return required;
    }

    @GetMapping("/boom")
    public String boom() {
        throw new IllegalStateException("boom");
    }
}
