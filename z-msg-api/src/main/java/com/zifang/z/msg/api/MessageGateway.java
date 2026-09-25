package com.zifang.z.msg.api;

import java.util.Map;

/**
 * 消息下发总入口（业务侧使用这一个）
 * <p>
 * 内部根据配置（z-msg.sms.provider / z-msg.email.provider / z-msg.channel.&lt;CHANNEL&gt;.provider）
 * 路由到具体 {@link ChannelSender}。业务侧不直接依赖具体通道，便于切换供应商。
 * <p>
 * 1.0.0 只有 sendSms / sendEmail 两个方法，IM / Push / In-app / Webhook 只能绕开 gateway 直接
 * 拿 router；1.1.0 起统一走 {@link #send(Message)} 与 {@link #fanOut}。
 */
public interface MessageGateway {

    /**
     * 发送短信。业务侧只需关心"发什么"和"发给谁"，不关心用哪家通道。
     *
     * @param message 短信内容
     * @return 发送结果
     */
    MessageSendResult sendSms(SmsMessage message);

    /**
     * 发送邮件
     */
    MessageSendResult sendEmail(EmailMessage message);

    /**
     * 统一发送入口：按 message.channel 选择 provider。
     *
     * @param message 消息信封
     * @return 发送结果
     */
    MessageSendResult send(Message message);

    /**
     * 一个业务事件同时投递多个通道（偏好过滤 + 静默时段 + 限流 + 模板渲染 + 重试 + 投递日志）。
     *
     * @param bizType   业务类型，用于查模板与偏好
     * @param userId    归属用户，可为 null（无账号的场景，如纯 webhook）
     * @param receivers 各通道接收方 {"SMS":"138...","EMAIL":"a@b.com","IM_DINGTALK":"robot-key"}
     * @param params    模板渲染参数
     * @param locale    多语言，null 走默认语言
     */
    FanOutResult fanOut(String bizType, Long userId, Map<String, String> receivers,
                        Map<String, String> params, String locale);
}
