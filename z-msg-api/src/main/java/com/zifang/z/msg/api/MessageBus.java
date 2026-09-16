package com.zifang.z.msg.api;

public interface MessageBus {
    void publish(MessageEvent event);

    void subscribe(String eventType, MessageHandler handler);

    @FunctionalInterface
    interface MessageHandler {
        void handle(MessageEvent event);
    }
}
