package com.chaos.web.exception;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chaos.core.exception.ChaosException;
import com.chaos.core.exception.CommonErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 全局异常处理器日志行为测试。
 */
class GlobalExceptionHandlerLoggingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private Level previousLevel;

    @BeforeEach
    void setUp() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
    }

    /**
     * 5xx 业务异常必须输出异常堆栈。
     */
    @Test
    void serverErrorShouldLogStack() {
        handler.handleChaosException(new ChaosException(CommonErrorCode.INTERNAL_ERROR));

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }

    /**
     * 框架 5xx 异常必须输出异常堆栈。
     */
    @Test
    void frameworkServerErrorShouldLogStack() throws Exception {
        handler.handleException(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }

    /**
     * 参数校验只记录字段路径，不输出字段值和堆栈。
     */
    @Test
    void validationErrorShouldLogFieldNamesWithoutStack() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "items[0].capabilityId",
                null, false, null, null, "must not be null"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new MethodParameter(
                        GlobalExceptionHandlerLoggingTest.class.getDeclaredMethod("handle", Object.class), 0),
                binding);

        handler.handleMethodArgumentNotValid(exception, new MockHttpServletRequest("POST", "/orders"));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
        assertThat(event.getFormattedMessage()).contains("items[0].capabilityId");
        assertThat(event.getThrowableProxy()).isNull();
    }

    /**
     * 4xx 业务异常不应输出 ERROR 堆栈。
     */
    @Test
    void clientErrorShouldNotLogError() {
        handler.handleChaosException(new ChaosException(CommonErrorCode.BAD_REQUEST));

        assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    private void handle(Object value) {
    }
}
