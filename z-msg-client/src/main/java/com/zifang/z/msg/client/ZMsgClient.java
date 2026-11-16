package com.zifang.z.msg.client;

import com.zifang.z.msg.client.model.ZMsgSendRequest;

import java.util.List;
import java.util.Map;

/**
 * z-msg 消息中心 HTTP 客户端。
 * <p>
 * 两种身份，二选一：
 * <ul>
 *   <li><b>AK 客户端</b>：建 client 时给 access key（{@code ZMsgClientFactory.remote(baseUrl, apiKey)}），
 *       请求自动带 {@code X-Api-Key} 头 —— 别的板块 / 脚本 / 外部系统走这条；</li>
 *   <li><b>平台用户</b>：方法参数给 token（{@code Authorization: Bearer}）—— 页面上的操作走这条。</li>
 * </ul>
 * 两者都没有时，服务端回 401（未识别调用者身份）。
 */
public interface ZMsgClient {

    /**
     * 直发一条消息：指定通道 + 指定收件人 + 运行时全文。
     *
     * @param request 请求体
     * @param token   平台 token；用 AK 身份时传 null
     * @return 服务端 {@code data}（含 msgId / channel / provider / bizType）
     * @throws ZMsgClientException 服务端返回非 2xx，或网络失败
     */
    Map<String, Object> send(ZMsgSendRequest request, String token);

    /**
     * 直发一条消息，<b>请求体原样透传</b>（键名不重写）。
     * <p>
     * 给「字段由调用方自己拼」的场景用（脚本里的 {@code msg.send([...]}）。
     * 键名按平台口径用 snake_case（{@code user_id} / {@code biz_type} …）；
     * 服务端 DTO 同时接受 camelCase。
     *
     * @param body  请求体
     * @param token 平台 token；用 AK 身份时传 null
     */
    Map<String, Object> sendRaw(Map<String, Object> body, String token);

    /**
     * 批量下发：一次提交多个收件人，服务端异步投递。
     *
     * @param body {@code bizType/channel/params/receivers}
     * @param token 平台 token；用 AK 身份时传 null
     */
    Map<String, Object> batchSubmit(Map<String, Object> body, String token);

    /**
     * 按业务事件发布（正文由服务端模板渲染）。
     *
     * @param eventType 事件类型
     * @param userId    收件人（站内信落库用）
     * @param params    模板参数
     * @param token     平台 token；用 AK 身份时传 null
     */
    Map<String, Object> publish(String eventType, Long userId, Map<String, Object> params, String token);

    /**
     * 通道清单（有哪些通道可发、各自状态如何）。
     * <p>
     * 需要<b>平台身份</b>：它回的是本进程的通道接线（provider 名、是否 mock 兜底），
     * 属运维视图，AK 客户端拿不到（也不该拿）—— AK 客户端要看自己能用什么，
     * 调 {@link #clientInfo()}。
     */
    List<Map<String, Object>> channels(String token);

    /**
     * 这份 AK 自己的单子：appCode / 日配额 / 已用额度 / 允许的通道。
     * <p>
     * 需要<b>客户端 AK</b>（没建 AK 就抛 {@link IllegalStateException}）。
     */
    Map<String, Object> clientInfo();
}
