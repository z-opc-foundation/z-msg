package com.zifang.z.msg.core.bus;

import com.zifang.z.msg.api.MessageBus;
import com.zifang.z.msg.api.MessageEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class DefaultMessageBus implements MessageBus {

    private final Map<String, List<MessageHandler>> handlers = new ConcurrentHashMap<>();
    private final List<MessageHandler> globalHandlers = new CopyOnWriteArrayList<>();

    @Override
    public void publish(MessageEvent event) {
        // 全局 handler
        for (MessageHandler h : globalHandlers) {
            h.handle(event);
        }
        // 按 eventType 匹配的 handler
        List<MessageHandler> specific = handlers.get(event.getEventType());
        if (specific != null) {
            for (MessageHandler h : specific) {
                h.handle(event);
            }
        }
    }

    @Override
    public void subscribe(String eventType, MessageHandler handler) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public void registerGlobal(MessageHandler handler) {
        globalHandlers.add(handler);
    }
}
