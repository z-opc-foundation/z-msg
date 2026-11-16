package com.zifang.z.msg.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 端点自带鉴别标记：宿主的 SSO 闸门放行这个端点，鉴别由端点自己负责。
 * <p>
 * <b>为什么需要它</b>：宿主（z-opc 主壳）的 SSO 闸门拦的是所有 api 路径全片，
 * 于是<b>机器对机器</b>的调用方（别的板块的定时任务、z-script 脚本、AK 客户端）
 * 拿不到用户 cookie / Bearer token，就被挡在 401，连自己的 AK 都递不进来。
 * 宿主闸门只认「平台用户」这一种身份，而 AK 客户端的身份由 z-msg 自己判
 * （{@code MsgClientAuthInterceptor}），两头职责不同，不能互相顶替。
 * <p>
 * <b>本注解的语义 = 免登录闸门，但鉴别不许省</b>：标了它的端点必须自带
 * fail-closed 的鉴别（不验 ⇒ 401），不能因为"闸门放行了"就当匿名可信。
 * z-ops 的 {@code WebhookController} 是同款用法（免登录 + HMAC 自验）。
 * <p>
 * <b>识别方式</b>：宿主 SSO 拦截器按注解<b>简单名</b>（{@code "NoLogin"}）反射识别，
 * 所以本仓不需要（也不能）编译期依赖 z-ctc-sso。
 * <p>
 * <b>只允许标在方法上</b>（{@code @Target(METHOD)}）：标在类上会放行整个 controller，
 * 把同类的其它端点一并豁免 —— 那是配置漂移，不是设计。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NoLogin {
}
