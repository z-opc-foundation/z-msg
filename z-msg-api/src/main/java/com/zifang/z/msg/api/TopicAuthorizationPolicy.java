package com.zifang.z.msg.api;

/**
 * topic 订阅/发布授权策略 (1.1.0)
 * <p>
 * 传输层（z-msg-ws）在 subscribe / publish 两帧上逐个询问容器内所有策略：
 * 任一返回 {@code TRUE} 即放行，任一返回 {@code FALSE} 即拒绝（优先于放行），
 * 全部返回 {@code null}（不表态）时落到传输层的默认策略：
 * 只允许订阅 {@code user:<自己>} 与配置里显式声明的公共 topic。
 * <p>
 * 之所以是"三态"而不是布尔：一条连接上会有多种 topic（自己的收件箱、
 * 群聊、租户公告），没有哪个模块能单独判完，IM 只关心 room:，站内信只关心 user:。
 */
public interface TopicAuthorizationPolicy {

    /**
     * 是否对 topic 有管辖权（前缀匹配）
     */
    boolean supports(String topic);

    /**
     * @param userId 当前连接绑定的登录用户，未登录为 null
     * @return TRUE 放行 / FALSE 拒绝 / null 不表态
     */
    Boolean allowSubscribe(Long userId, String topic);

    /**
     * 客户端通过 {@code op=publish} 请求服务端代发时的写权限，默认全部不表态
     */
    default Boolean allowPublish(Long userId, String topic) {
        return null;
    }

    /**
     * 多个策略时的判定顺序，小的先问
     */
    default int order() {
        return 100;
    }
}
