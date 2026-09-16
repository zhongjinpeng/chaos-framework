package com.michael.chaos.gateway.filter;

import com.michael.chaos.trace.RequestTiming;
import java.util.concurrent.atomic.AtomicBoolean;
import reactor.core.publisher.Mono;

/** Shared reactive timing operator used by Gateway filters. */
final class GatewayRequestTimingSupport {

    private GatewayRequestTimingSupport() {
    }

    static <T> Mono<T> time(Mono<T> publisher, RequestTiming timing, String stageName) {
        if (timing == null) {
            return publisher;
        }
        return Mono.defer(() -> {
            long startedAtNanos = System.nanoTime();
            AtomicBoolean recorded = new AtomicBoolean();
            Runnable record = () -> {
                if (recorded.compareAndSet(false, true)) {
                    timing.record(stageName, System.nanoTime() - startedAtNanos);
                }
            };
            return publisher
                    .doOnEach(signal -> {
                        if (signal.isOnComplete() || signal.isOnError()) {
                            record.run();
                        }
                    })
                    .doOnCancel(record);
        });
    }
}
