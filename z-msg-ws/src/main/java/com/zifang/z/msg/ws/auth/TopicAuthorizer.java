package com.zifang.z.msg.ws.auth;

import com.zifang.z.msg.api.RealtimeTopics;
import com.zifang.z.msg.api.TopicAuthorizationPolicy;
import com.zifang.z.msg.ws.config.WsProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * topic 订阅/发布授权裁决。
 * <p>
 * 语义按 {@link TopicAuthorizationPolicy} 的约定实现，三态而不是布尔：
 * <ol>
 *   <li>只对 {@code supports(topic)} 为真的策略提问，别模块管的 topic 不插手；</li>
 *   <li>任一策略返回 FALSE 即否决（FALSE 优先于 TRUE，所以问完再判，
 *       不能因为某个策略先放行就短路）；</li>
 *   <li>全部不表态时落到这里的默认策略。</li>
 * </ol>
 * 默认策略两头都是收口的：订阅只放行"自己的 user: + 显式配置的公共 topic + sys:broadcast"，
 * 发布一律拒绝。发布默认放行等于给任意连接一个往别人的 topic 写帧的能力，
 * 群聊/公告这类需求必须由模块注册一条策略明确承担起来。
 */
public class TopicAuthorizer {

    /**
     * 天生就该所有人能听的命名空间：公告没有私密数据，{@code RealtimeTopics} 就是这么定义它的。
     */
    private static final Set<String> ALWAYS_SUBSCRIBABLE =
            new HashSet<String>(Arrays.asList(RealtimeTopics.SYS_BROADCAST));

    private final List<TopicAuthorizationPolicy> policies;
    private final Set<String> publicTopics;

    public TopicAuthorizer(List<TopicAuthorizationPolicy> policies, WsProperties properties) {
        List<TopicAuthorizationPolicy> sorted =
                policies == null ? new ArrayList<TopicAuthorizationPolicy>() : new ArrayList<TopicAuthorizationPolicy>(policies);
        sorted.sort(new java.util.Comparator<TopicAuthorizationPolicy>() {
            @Override
            public int compare(TopicAuthorizationPolicy a, TopicAuthorizationPolicy b) {
                return Integer.compare(a.order(), b.order());
            }
        });
        this.policies = Collections.unmodifiableList(sorted);
        Set<String> pub = new LinkedHashSet<String>();
        if (properties != null && properties.getPublicTopics() != null) {
            for (String t : properties.getPublicTopics()) {
                if (t != null && !t.trim().isEmpty()) {
                    pub.add(t.trim());
                }
            }
        }
        this.publicTopics = Collections.unmodifiableSet(pub);
    }

    public boolean canSubscribe(Long userId, String topic) {
        Boolean voted = vote(topic, true, userId);
        if (voted != null) {
            return voted;
        }
        return defaultSubscribe(userId, topic);
    }

    public boolean canPublish(Long userId, String topic) {
        Boolean voted = vote(topic, false, userId);
        if (voted != null) {
            return voted;
        }
        // 写侧没有"默认允许"这一档：没人表态就是拒绝
        return false;
    }

    /**
     * @param subscribeOp true 问 {@code allowSubscribe}，false 问 {@code allowPublish}
     */
    private Boolean vote(String topic, boolean subscribeOp, Long userId) {
        if (topic == null || topic.trim().isEmpty()) {
            return Boolean.FALSE;
        }
        String t = topic.trim();
        Boolean allow = null;
        for (TopicAuthorizationPolicy p : policies) {
            if (p == null || !p.supports(t)) {
                continue;
            }
            Boolean v;
            try {
                v = subscribeOp ? p.allowSubscribe(userId, t) : p.allowPublish(userId, t);
            } catch (RuntimeException e) {
                // 一条策略炸掉不能让整个连接不可用；按"不表态"处理，同时留痕
                v = null;
            }
            if (Boolean.FALSE.equals(v)) {
                return Boolean.FALSE;
            }
            if (Boolean.TRUE.equals(v)) {
                allow = Boolean.TRUE;
            }
        }
        return allow;
    }

    private boolean defaultSubscribe(Long userId, String topic) {
        if (userId == null) {
            return false;
        }
        if (ALWAYS_SUBSCRIBABLE.contains(topic) || publicTopics.contains(topic)) {
            return true;
        }
        // 自己的收件箱：user:<自己>，前缀对但 id 不是自己一律拒
        return RealtimeTopics.isUser(topic) && RealtimeTopics.userIdOf(topic) == userId.longValue();
    }

    public Set<String> configuredPublicTopics() {
        return publicTopics;
    }
}
