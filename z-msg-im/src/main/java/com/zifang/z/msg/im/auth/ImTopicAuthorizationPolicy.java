package com.zifang.z.msg.im.auth;

import com.zifang.z.msg.api.RealtimeTopics;
import com.zifang.z.msg.api.TopicAuthorizationPolicy;
import com.zifang.z.msg.im.domain.service.ImConversationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * IM 对 {@code room:<conversationId>} 的订阅授权：只有会话成员才放行。
 * <p>
 * 三态语义里最要紧的一条是<strong>什么时候必须闭嘴</strong>：
 * <ul>
 *   <li>不是 {@code room:} 前缀 → {@code null}。user:/tenant:/biz: 由别的模块管；</li>
 *   <li>{@code room:lobby} 这种 key 不是会话 id 的、以及 {@code room:123} 但这个 id
 *       根本不在本模块会话表里的 → {@code null}。
 *       <strong>这里返回 FALSE 会把整条默认授权链否掉</strong>：
 *       {@code z-msg.ws.public-topics} 里显式声明的公共房间本来是给所有人听的，
 *       而 {@code TopicAuthorizer} 的规则是"任一 FALSE 立即否决"，一个越权的 FALSE
 *       就等于本模块把别人家的房间锁了。</li>
 * </ul>
 * 只有"这个会话是我的、而你不是成员"才判 FALSE —— 那种情况下拒绝是本分。
 * <p>
 * {@code allowPublish} 用同一套三态，只是表态的口径不同：{@code room:} 的 key 是数字
 * <b>并且</b>这一行确实在本模块会话表里时一律 FALSE —— 发言必须走 REST（要落库、要占 seq）。
 * 放开 {@code op=publish} 等于允许任意连接往房间里塞不落库的假消息，
 * 客户端重连按 seq 补拉时对不上账，比少一个功能严重得多。
 * 别人家的 {@code room:lobby} 与不存在的会话 id 仍然返回 {@code null}：
 * 写侧 {@code TopicAuthorizer.canPublish} 本来就把"没人表态"当成拒绝，
 * 这里多给一个 FALSE 不会更安全，只会把宿主自己实现的 room 锁死。
 */
@Component
public class ImTopicAuthorizationPolicy implements TopicAuthorizationPolicy {

    private static final Logger log = LogManager.getLogger(ImTopicAuthorizationPolicy.class);

    @Resource
    private ImConversationService conversationService;

    @Override
    public boolean supports(String topic) {
        return RealtimeTopics.isRoom(topic);
    }

    @Override
    public Boolean allowSubscribe(Long userId, String topic) {
        Long conversationId = conversationIdOf(topic);
        if (conversationId == null) {
            // 不归本模块管的 room topic：交给公共 topic 配置和默认策略
            return null;
        }
        if (!conversationService.conversationExists(conversationId)) {
            return null;
        }
        if (userId == null) {
            return Boolean.FALSE;
        }
        boolean member = conversationService.isMember(conversationId, userId);
        if (!member && log.isDebugEnabled()) {
            log.debug("[z-msg-im] 拒绝订阅 {}：userId={} 不是会话 {} 成员", topic, userId, conversationId);
        }
        return member ? Boolean.TRUE : Boolean.FALSE;
    }

    @Override
    public Boolean allowPublish(Long userId, String topic) {
        Long conversationId = conversationIdOf(topic);
        if (conversationId == null) {
            // room:lobby 这种 key 不是会话 id 的：本模块没资格替宿主否决
            return null;
        }
        if (!conversationService.conversationExists(conversationId)) {
            // 表里没有这一行：可能是别的模块自己的 room 实现，闭嘴
            return null;
        }
        // 我家的房间：发言只能走 REST（落库 + 占 seq），谁都不放行，成员也一样
        return Boolean.FALSE;
    }

    /**
     * 比默认策略（order=100）晚一点问也无妨，但本模块的策略更具体，放前面能少跑一次默认判定；
     * 真正的短路靠的是"越权时给 FALSE"，与顺序无关。
     */
    @Override
    public int order() {
        return 50;
    }

    /**
     * topic 的 key 部分转成会话 id；不是数字（含空 key）时返回 null 表示"这个房间不是我的"。
     */
    static Long conversationIdOf(String topic) {
        if (!RealtimeTopics.isRoom(topic)) {
            return null;
        }
        String key = RealtimeTopics.keyOf(topic);
        if (key == null) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(Long.parseLong(trimmed));
        } catch (NumberFormatException notAnId) {
            return null;
        }
    }
}
