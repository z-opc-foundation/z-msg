package com.zifang.z.msg.api;

import java.io.Serializable;

/**
 * WebSocket / SSE 线格式消息 (1.1.0)
 * <p>
 * 一个实例 = 一帧。帧类型见 {@code OP_*} 常量，协议细节见
 * {@code _doc/001_WS_PROTOCOL.md}。payload 一律是 JSON 字符串（由上层序列化），
 * 传输层不理解其结构，便于 IM / 站内信 / 业务自定义事件共用一条连接。
 */
public class RealtimeMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---------- client -> server ----------
    /**
     * 订阅 topic：{"op":"subscribe","topics":["user:1001","room:lobby"]}
     */
    public static final String OP_SUBSCRIBE = "subscribe";
    public static final String OP_UNSUBSCRIBE = "unsubscribe";
    /**
     * 服务端代发（IM 侧写权限校验后转投）：{"op":"publish","topic":"room:lobby","payload":"{...}"}
     */
    public static final String OP_PUBLISH = "publish";
    public static final String OP_PING = "ping";

    // ---------- server -> client ----------
    public static final String OP_PONG = "pong";
    /**
     * 连接建立时下发，带 connectionId + 已自动订阅的 topic，客户端据此判断是否重连成功
     */
    public static final String OP_READY = "ready";
    /**
     * 业务消息：{"op":"message","topic":"...","seq":12,"ts":1700000000000,"payload":"{...}"}
     */
    public static final String OP_MESSAGE = "message";
    public static final String OP_ACK = "ack";
    public static final String OP_ERROR = "error";

    // ---------- message kind（payload 之外的业务语义） ----------
    public static final String KIND_CHAT = "chat";
    public static final String KIND_INBOX = "inbox";
    public static final String KIND_TYPING = "typing";
    public static final String KIND_PRESENCE = "presence";
    public static final String KIND_READ = "read";
    public static final String KIND_SYS = "sys";

    private String op;
    /**
     * 业务语义，见 KIND_*；控制帧可为空
     */
    private String kind;
    private String topic;
    private String from;
    /**
     * 发送方本地 id，服务端回 ack 时原样带回，客户端据此把"发送中"气泡改成"已送达"
     */
    private String clientMsgId;
    /**
     * topic 内单调递增序列号；0 = 无序（如 pong / 控制帧）
     */
    private long seq;
    /**
     * 服务端 epoch millis
     */
    private long ts;
    /**
     * JSON 字符串
     */
    private String payload;
    /**
     * op=error 时的错误码
     */
    private String errorCode;
    private String errorMessage;

    public RealtimeMessage() {
    }

    public static RealtimeMessage message(String topic, String kind, String payload, long seq) {
        RealtimeMessage m = new RealtimeMessage();
        m.op = OP_MESSAGE;
        m.kind = kind;
        m.topic = topic;
        m.payload = payload;
        m.seq = seq;
        m.ts = System.currentTimeMillis();
        return m;
    }

    public static RealtimeMessage frame(String op) {
        RealtimeMessage m = new RealtimeMessage();
        m.op = op;
        m.ts = System.currentTimeMillis();
        return m;
    }

    public static RealtimeMessage error(String errorCode, String errorMessage) {
        RealtimeMessage m = frame(OP_ERROR);
        m.errorCode = errorCode;
        m.errorMessage = errorMessage;
        return m;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public void setClientMsgId(String clientMsgId) {
        this.clientMsgId = clientMsgId;
    }

    public long getSeq() {
        return seq;
    }

    public void setSeq(long seq) {
        this.seq = seq;
    }

    public long getTs() {
        return ts;
    }

    public void setTs(long ts) {
        this.ts = ts;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "RealtimeMessage{op='" + op + "', kind='" + kind + "', topic='" + topic + "', seq=" + seq + '}';
    }
}
