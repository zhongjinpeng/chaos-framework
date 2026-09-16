package com.michael.chaos.mybatis.observability;

import com.michael.chaos.trace.RequestTimingContext;
import java.util.Locale;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * Aggregates MyBatis executor time into the active request timing context.
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
        }),
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class
        })
})
public final class RequestTimingMybatisInterceptor implements Interceptor {

    private final String stageName;

    /**
     * Create an interceptor named after the configured database type.
     */
    public RequestTimingMybatisInterceptor(String databaseType) {
        this.stageName = normalizeDatabaseType(databaseType);
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        return RequestTimingContext.call(stageName, invocation::proceed);
    }

    private static String normalizeDatabaseType(String value) {
        if (value == null || value.isBlank()) {
            return "database";
        }
        return value.toLowerCase(Locale.ROOT).replace("_", "");
    }
}
