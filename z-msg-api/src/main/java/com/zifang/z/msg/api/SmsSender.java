package com.zifang.z.msg.api;

/**
 * 短信下发通道 SPI
 * <p>
 * 实现方可以是 Mock（开发环境）、阿里云、腾讯云、Twilio 等。
 * Spring 自动装配：注入所有 SmsSender Bean，运行时按配置挑选一个。
 */
public interface SmsSender {

    /**
     * 通道名（如 "mock" / "aliyun" / "tencent"），与 application.yml 中 z-msg.sms.provider 对应
     */
    String name();

    /**
     * 发送短信
     *
     * @param message 短信内容
     * @return 发送结果（成功/失败 + Provider messageId 或错误信息）
     */
    MessageSendResult send(SmsMessage message);
}
