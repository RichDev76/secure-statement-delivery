package com.example.statementservice.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/** Captures everything a class logs inside a try-with-resources block, so a test can assert on it. */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Level originalLevel;

    private LogCapture(Class<?> type, Level forcedLevel) {
        this.logger = (Logger) LoggerFactory.getLogger(type);
        this.originalLevel = logger.getLevel();
        if (forcedLevel != null) {
            this.logger.setLevel(forcedLevel);
        }
        this.appender.start();
        this.logger.addAppender(this.appender);
    }

    public static LogCapture forClass(Class<?> type) {
        return new LogCapture(type, null);
    }

    /** Ambient level isn't guaranteed to allow the level under test through; force it explicitly. */
    public static LogCapture forClassAtLevel(Class<?> type, Level level) {
        return new LogCapture(type, level);
    }

    /** Includes the throwable's message too - log.error(msg, ex) puts it outside the formatted message. */
    public List<String> lines() {
        return appender.list.stream()
                .map(event -> {
                    var proxy = event.getThrowableProxy();
                    return proxy == null
                            ? event.getFormattedMessage()
                            : event.getFormattedMessage() + " | " + proxy.getMessage();
                })
                .toList();
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(originalLevel);
    }
}
