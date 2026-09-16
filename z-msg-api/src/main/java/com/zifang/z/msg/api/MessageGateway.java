package com.zifang.z.msg.api;

/**
 * 消息下发总入口（业务侧使用这一个）
 * <p>
 * 内部根据配置（z-msg.sms.provider / z-msg.email.provider）路由到具体 SmsSender / EmailSender。
 * 业务侧不直接依赖具体通道，便于切换供应商。
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
}
