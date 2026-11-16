package com.zifang.z.msg.web.api;

import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.core.domain.entity.MsgClientDO;
import com.zifang.z.msg.web.annotation.NoLogin;
import com.zifang.z.msg.web.auth.MsgClientAuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对外发送端点：{@code POST /api/msg/open/send} —— 机器对机器（别的板块 / 脚本 / 外部系统）走这里。
 * <p>
 * <b>为什么不在 {@code /api/msg/send} 上直接支持 AK</b>：宿主的 SSO 闸门拦的是所有 api 路径，
 * 只认「平台用户」这一种身份。机器调用方没有用户 cookie / Bearer token，请求根本到不了 z-msg，
 * AK 会被挡在闸门外（实测 401「未登录或Token无效」）。z-script 的 AK 端点早就用
 * {@code sso.exclude-paths} 做过同样的豁免 —— 豁免之后就没有任何登录态可依，
 * 于是这个端点只能<b>只认 AK，且 fail-closed</b>：没有 {@code X-Api-Key} 就是 401，不退回平台身份。
 * <p>
 * <b>豁免方式用注解而不是 exclude-paths</b>：{@link NoLogin} 由端点自己扛，
 * 不依赖宿主 properties 里的路径清单（两处口径一旦漂移就是配置漂移）。
 * 宿主 SSO 拦截器按注解<b>简单名</b>反射识别，本仓不需要编译期依赖 z-ctc-sso。
 * <p>
 * <b>与平台端点的差别只在准入</b>：投递管道完全一致（同一个 {@link MsgDirectSender}），
 * 通道白名单 / 日配额 / IP 白名单照常生效（{@code MsgClientAuthInterceptor} 干的活）。
 * <p>
 * <b>AK 明文不落库</b>：服务端只存 sha-256 摘要，校验时对来头做同样摘要再比对。
 */
@RestController
@RequestMapping("/api/msg/open")
public class MsgOpenSendController {

    /**
     * 401 正文：对外端点没有 AK ⇒ 不放行。
     */
    public static final String NO_API_KEY_MESSAGE =
            "对外发送端点只认客户端 AK：请在请求头带 X-Api-Key";

    @Resource
    private MsgDirectSender sender;

    /**
     * 用客户端 AK 直发一条消息。
     *
     * @param req     请求体（{@code Message} 的直填子集），可空
     * @param request 当前请求，用于取已校验的客户端身份
     * @return 成功时 {@code data} 里带 {@code msgId}；失败时是带人话的 4xx/5xx
     */
    @NoLogin
    @PostMapping("/send")
    public ResponseEntity<Result<Map<String, Object>>> send(@RequestBody(required = false) MsgSendRequest req,
                                                             HttpServletRequest request) {
        MsgClientDO client = MsgClientAuthInterceptor.clientOf(request);
        return sender.sendForClient(req, client);
    }

    /**
     * 客户端自 introspect：自己是谁、今天还能发几条、能走哪几条通道。
     * <p>
     * <b>为什么不复用 {@code GET /api/msg/channel/list}</b>：那个端点暴露的是**本进程**的通道接线
     * （provider 名、是否 mock 兜底、配置里挂了谁），那是运维视图，不该给外部调用方看；
     * 而 AK 客户端真正需要的是「我这份凭据能干什么」，那就是这张自己的单子。
     */
    @NoLogin
    @GetMapping("/client/me")
    public ResponseEntity<Result<Map<String, Object>>> me(HttpServletRequest request) {
        MsgClientDO client = MsgClientAuthInterceptor.clientOf(request);
        if (client == null) {
            return MsgDirectSender.unauthorized(NO_API_KEY_MESSAGE);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("appCode", client.getAppCode());
        data.put("appName", client.getAppName());
        data.put("keyPrefix", client.getKeyPrefix());
        data.put("status", client.getStatus());
        data.put("expireTime", client.getExpireTime());
        data.put("dailyQuota", client.getDailyQuota());
        data.put("quotaUsed", client.getQuotaUsed());
        data.put("allowedChannels", client.getAllowedChannels());
        data.put("description", client.getDescription());
        return ResponseEntity.ok(Result.success(data));
    }
}
