package com.michael.chaos.mybatis.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.michael.chaos.trace.RequestTiming;
import com.michael.chaos.trace.RequestTimingContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.Test;

class RequestTimingMybatisInterceptorTest {

    @Test
    void recordsDatabaseTimeWhenARequestContextIsActive() throws Throwable {
        RequestTiming timing = RequestTiming.start();
        RequestTimingMybatisInterceptor interceptor = new RequestTimingMybatisInterceptor("POSTGRE_SQL");
        Executor executor = mock(Executor.class);
        MappedStatement statement = mock(MappedStatement.class);
        when(executor.update(statement, "value")).thenReturn(1);

        try (RequestTimingContext.Scope ignored = RequestTimingContext.open(timing)) {
            Object result = interceptor.intercept(new Invocation(
                    executor,
                    Executor.class.getMethod("update", MappedStatement.class, Object.class),
                    new Object[] {statement, "value"}));
            assertThat(result).isEqualTo(1);
        }

        assertThat(timing.snapshot().stages()).containsKey("postgresql");
        assertThat(timing.snapshot().stages().get("postgresql").count()).isOne();
    }
}
