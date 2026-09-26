package com.zifang.z.msg.example.chat;

import com.zifang.z.msg.api.RealtimeTopics;
import com.zifang.z.msg.api.TopicAuthorizationPolicy;
import com.zifang.z.msg.ws.config.WsProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 大厅（lobby）发言授权 —— "五分钟搭一个聊天室"里唯一需要宿主写的那段代码。
 * <p>
 * 传输层默认只放开订阅（自己的 {@code user:}、配置的公共 topic、{@code sys:broadcast}），
 * **写侧一律拒**：否则任意一条连接都能往别人的 {@code room:} / {@code user:} 里塞帧。
 * 所以要让人在大厅里说话，就得有一个策略明确表态；不认识的 topic 一律返回 {@code null}（不表态），
 * 让 {@code z-msg-im} 那样的成员表策略去判其它房间 —— 这正是三态语义存在的地方，
 * 返回 {@code FALSE} 会把别人的策略一起否掉。
 * <p>
 * 判据取 {@link WsProperties#getPublicTopics()}，不在这再抄一遍 "room:lobby"：
 * 宿主改了 yml 而策略还认老 topic 的话，界面会显示"连上了但没人说话"。
 * <p>
 * 一个诚实的边界：这里放行的是"能不能往这个 topic 发帧"，不是"帧里写的是不是真的"。
 * 载荷里客户端自称的 {@code from} 不会被服务端改写 —— 要服务端署名的聊天室，
 * 走 {@code z-msg-im}：发言落库时 sender_user_id 由服务端从连接身份写死，seq 由服务端分配。
 */
@Component
public class LobbyChatPolicy implements TopicAuthorizationPolicy {

    private final WsProperties wsProperties;

    public LobbyChatPolicy(WsProperties wsProperties) {
        this.wsProperties = wsProperties;
    }

    @Override
    public boolean supports(String topic) {
        if (!RealtimeTopics.isRoom(topic)) {
            return false;
        }
        List<String> publicTopics = wsProperties.getPublicTopics();
        return publicTopics != null && publicTopics.contains(topic);
    }

    @Override
    public Boolean allowSubscribe(Long userId, String topic) {
        // 有登录态就能听；没登录的连接连握手都进不来，这里再兜一层
        return userId != null ? Boolean.TRUE : null;
    }

    @Override
    public Boolean allowPublish(Long userId, String topic) {
        return userId != null ? Boolean.TRUE : null;
    }

    @Override
    public int order() {
        return 10;
    }
}
