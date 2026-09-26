package com.zifang.z.msg.channels.support;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试期内存 appender：挂到现役 Log4j2 配置上收集日志原文。
 * <p>
 * log4j2-test.xml 里 {@code com.zifang} 是 additivity=false，只挂 root 一条都收不到，
 * 所以两个 LoggerConfig 都要挂。用完必须 close() 摘除，避免污染其他测试类。
 */
public final class LogCapture implements AutoCloseable {

    private final CapturingAppender appender = new CapturingAppender();
    private final LoggerContext context;
    private final LoggerConfig touchedRoot;
    private final LoggerConfig touchedZifang;

    private LogCapture() {
        context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        config.addAppender(appender);
        touchedRoot = config.getRootLogger();
        touchedRoot.addAppender(appender, Level.DEBUG, null);
        touchedZifang = config.getLoggers().get("com.zifang");
        if (touchedZifang != null) {
            touchedZifang.addAppender(appender, Level.DEBUG, null);
        }
        context.updateLoggers();
    }

    public static LogCapture start() {
        return new LogCapture();
    }

    public List<String> lines() {
        return appender.lines;
    }

    public String text() {
        StringBuilder sb = new StringBuilder();
        for (String l : appender.lines) {
            sb.append(l).append('\n');
        }
        return sb.toString();
    }

    @Override
    public void close() {
        touchedRoot.removeAppender(appender.getName());
        if (touchedZifang != null) {
            touchedZifang.removeAppender(appender.getName());
        }
        context.updateLoggers();
    }

    private static final class CapturingAppender extends AbstractAppender {
        final List<String> lines = new CopyOnWriteArrayList<>();

        CapturingAppender() {
            super("z-msg-test-capture", null,
                    PatternLayout.newBuilder().withPattern("%m").build(), true,
                    Property.EMPTY_ARRAY);
            start();
        }

        @Override
        public void append(org.apache.logging.log4j.core.LogEvent event) {
            lines.add(event.getLoggerName() + " | "
                    + event.getMessage().getFormattedMessage());
        }
    }
}
