package com.zifang.z.msg.web.host;

import com.zifang.ctc.common.spi.CodeChannelSender;
import com.zifang.z.msg.api.EmailMessage;
import com.zifang.z.msg.api.MessageGateway;
import com.zifang.z.msg.api.SmsMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 验证码下发通道 — 桥接 z-msg MessageGateway (短信/邮件).
 * <p>
 * z-ctc (基础设施层) 不依赖 z-msg (业务层), 只在 z-ctc-common 暴露 {@link CodeChannelSender} SPI;
 * 宿主 (4A 中心那类同时装了 z-ctc-web 的形态) 在这里把 z-msg 的短信/邮件通道接进来.
 * z-msg 默认 provider 是 Mock (日志输出), 生产切 Aliyun/SMTP 只需改 yml 配置.
 */
public final class MsgCodeChannelSenders {

    private static final Logger log = LogManager.getLogger(MsgCodeChannelSenders.class);

    private MsgCodeChannelSenders() {
    }

    private static Map<String, String> codeParams(String scene, String code) {
        Map<String, String> params = new HashMap<>();
        params.put("code", code);
        params.put("bizType", scene);
        return params;
    }

    /**
     * 短信通道: PHONE.
     */
    @Component
    public static class PhoneSmsCodeChannelSender implements CodeChannelSender {

        private final MessageGateway messageGateway;

        public PhoneSmsCodeChannelSender(MessageGateway messageGateway) {
            this.messageGateway = messageGateway;
        }

        @Override
        public String channel() {
            return "PHONE";
        }

        @Override
        public void send(String receiver, String scene, String code) {
            messageGateway.sendSms(SmsMessage.builder()
                    .phone(receiver)
                    .bizType(scene)
                    .templateParams(codeParams(scene, code))
                    .build());
            log.info("[SMS-CODE] receiver={} scene={} (通道: z-msg)", receiver, scene);
        }
    }

    /**
     * 邮件通道: EMAIL.
     */
    @Component
    public static class EmailCodeChannelSender implements CodeChannelSender {

        private final MessageGateway messageGateway;

        public EmailCodeChannelSender(MessageGateway messageGateway) {
            this.messageGateway = messageGateway;
        }

        @Override
        public String channel() {
            return "EMAIL";
        }

        @Override
        public void send(String receiver, String scene, String code) {
            messageGateway.sendEmail(EmailMessage.builder()
                    .to(receiver)
                    .bizType(scene)
                    .templateParams(codeParams(scene, code))
                    .subject("【z-opc】验证码")
                    .build());
            log.info("[EMAIL-CODE] to={} scene={} (通道: z-msg)", receiver, scene);
        }
    }
}
