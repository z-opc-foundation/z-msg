package com.zifang.z.msg.web.host;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 宿主侧胶水装配 (2026-10-03 自 z-opc main-starter 平移):
 * MsgCodeChannelSenders (PHONE/EMAIL 验证码通道挂入 z-ctc CodeChannelSender SPI).
 *
 * <p>跟随 z.msg.host.enabled 开关 (默认关): 寄生 all-in-one 模式由宿主打开,
 * standalone 模式不开 (z-msg 容器对外只暴露 REST/SMS 端点, 验证码走平台侧走法不同).
 */
@Configuration
@ConditionalOnProperty(prefix = "z.msg.host", name = "enabled", havingValue = "true", matchIfMissing = false)
@ComponentScan(basePackages = "com.zifang.z.msg.web.host")
public class MsgHostAutoConfiguration {
}