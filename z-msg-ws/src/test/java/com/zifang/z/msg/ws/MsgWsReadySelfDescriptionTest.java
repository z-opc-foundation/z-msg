package com.zifang.z.msg.ws;

import com.zifang.z.msg.api.RealtimeMessage;
import com.zifang.z.msg.api.RealtimeTopics;
import com.zifang.z.msg.api.TopicAuthorizationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ready} 帧的自描述部分：服务端把自己**真的在执行**的 op 清单与资源上限报给客户端。
 * <p>
 * 为什么要报：前端今天只能靠抄 README 的默认值来定心跳间隔和订阅数量，而那些值是宿主可以改的。
 * 宿主把 {@code idle-timeout-seconds} 从 120 改成 31 之后，症状是"连上没多久就断线，
 * 断开的原因在浏览器侧看不出来"——这类两端不对称最难查。
 * <p>
 * 本类把三件事钉成一对：
 * <ol>
 *   <li>报出的数 == 服务端用的数（对岸是注入进来的 {@code WsProperties} bean，且用例真去撞那道闸）；</li>
 *   <li>报出的每条 op 真的有人处理（逐条发一帧，按各自的入参校验点回不同的错误码）；</li>
 *   <li>"加了分发分支却忘了登记"由同包的 {@code MsgWsOpCensusTest} 拦下。</li>
 * </ol>
 * 反方向（默认关 / 不限时该报什么）在 {@code MsgWsReadyOptOutsTest}，两类的读数互为对照。
 * <p>
 * 这里的配置全是**非默认值**（4 / 5 / 31 / 40960）：ready 里任何一处写死都会红。
 */
@SpringBootTest(classes = MsgWsReadySelfDescriptionTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "z-msg.enabled=true",
        "z-msg.web.trusted-header-enabled=true",
        "z-msg.realtime.ticket-secret=ready-self-desc-secret-0123456789ab",
        "z-msg.ws.public-topics[0]=room:lobby",
        "z-msg.ws.max-topics-per-connection=4",
        "z-msg.ws.max-sessions-per-user=5",
        "z-msg.ws.idle-timeout-seconds=31",
        "z-msg.ws.max-text-message-bytes=40960",
        "z-msg.ws.inband-auth-enabled=true"
})
public class MsgWsReadySelfDescriptionTest extends WsHarness {

    @Configuration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean(name = "dataSourceMsg")
        public DataSource dataSourceMsg() {
            return h2("msg_ws_ready_self_desc");
        }

        /**
         * {@code team:} 谁都能订，且刻意不在 public-topics 里。
         * 需要它是因为"撞 topic 上限"要能订到一个**不是自动订阅**的 topic：
         * 公共 topic 在建连时就绑满了，非公共的默认策略又一律不放行。
         */
        @Bean
        public TopicAuthorizationPolicy teamAnyPolicy() {
            return new TopicAuthorizationPolicy() {
                @Override
                public boolean supports(String topic) {
                    return topic != null && topic.startsWith("team:");
                }

                @Override
                public Boolean allowSubscribe(Long userId, String topic) {
                    return Boolean.TRUE;
                }
            };
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void limitsInReadyAreTheSameNumbersTheServerEnforces() {
        Recorder a = connect(USER_A);
        Map<String, Object> ready = payloadOf(awaitFrame(a, opIs(RealtimeMessage.OP_READY), "ready 帧"));
        Object rawLimits = ready.get("limits");
        assertTrue(rawLimits instanceof Map, "ready 里该有 limits 对象: " + ready);
        Map<String, Object> limits = (Map<String, Object>) rawLimits;

        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                        "maxTopicsPerConnection", "maxSessionsPerUser",
                        "idleTimeoutSeconds", "maxTextMessageBytes")), limits.keySet(),
                "四项都该在，且不该有第五项（多一项就多一个没人执行的真值源）");
        // 对岸取注入进来的那个 bean：注册表的挤占、handler 的上限、容器的 buffer 用的是同一个实例
        assertEquals(wsProperties.getMaxTopicsPerConnection(), intOf(limits.get("maxTopicsPerConnection")),
                "报的 topic 上限与服务端用的不是同一个数");
        assertEquals(wsProperties.getMaxSessionsPerUser(), intOf(limits.get("maxSessionsPerUser")),
                "报的每用户连接上限与服务端用的不是同一个数");
        assertEquals(wsProperties.getIdleTimeoutSeconds(), intOf(limits.get("idleTimeoutSeconds")),
                "报的空闲超时与设进容器的那个不是同一个数（前端按它算心跳间隔）");
        assertEquals(wsProperties.getMaxTextMessageBytes(), intOf(limits.get("maxTextMessageBytes")),
                "报的单帧上限与设进容器的 buffer 不是同一个数");

        // 报数只是广告，下面这两步才证明它就是那道闸。
        // 自动订阅已占 3 条（user:<自己> + sys:broadcast + room:lobby），上限 4。
        assertVerbContains(subscribeAndWait(a, "l1", "team:one"), "subscribed", "team:one");
        assertEquals(4, registry.find(connectionIdOf(a)).topics().size(),
                "订到第 4 条时应当正好贴着上限");
        subscribe(a, "l2", "team:two");
        Map<String, Object> overflow = awaitFrame(a,
                f -> "WS_TOPIC_LIMIT".equals(f.get("errorCode")), "第 5 条订阅的拒绝");
        assertTrue(String.valueOf(overflow.get("errorMessage"))
                        .contains("=" + wsProperties.getMaxTopicsPerConnection()),
                "拒绝理由里点的数必须与 ready 报的是同一个，否则客户端要记两套账: " + overflow);
        assertEquals("l2", overflow.get("clientMsgId"), "被拒的那一帧要能对回号");
        assertEquals(4, registry.find(connectionIdOf(a)).topics().size(),
                "越界的那一帧应当整帧不改状态，现役数正好停在上限");
    }

    /**
     * 逐条发一帧只带 {@code op} 的裸帧，按各分支自己的入参校验点回不同的错误码。
     * <p>
     * 断言"不是 WS_OP_UNSUPPORTED"还不够——那只要分支在末尾 {@code return} 一下就能骗过去。
     * 所以每条 op 钉的是它**独有**的那个形状：pong / 缺 topics / 缺 topic 权限 / 缺 token。
     */
    @Test
    public void everyAdvertisedOpReachesItsOwnBranch() {
        Recorder a = connect(USER_A);
        List<String> ops = stringList(payloadOf(awaitFrame(a, opIs(RealtimeMessage.OP_READY), "ready 帧"))
                .get("ops"));
        assertEquals(Arrays.asList("ping", "subscribe", "unsubscribe", "publish", "auth"), ops,
                "本类开了 inband-auth-enabled，清单应当是这五条（默认面见 MsgWsReadyOptOutsTest）");

        for (String op : ops) {
            String cid = "probe-" + op;
            send(a, "{\"op\":\"" + op + "\",\"clientMsgId\":\"" + cid + "\"}");
            Map<String, Object> reply = awaitFrame(a, f -> cid.equals(f.get("clientMsgId")),
                    "op=" + op + " 的回帧");
            assertFalse("WS_OP_UNSUPPORTED".equals(reply.get("errorCode")),
                    "ready 报了支持 op=" + op + "，实际却回 unsupported: " + reply);
            if ("ping".equals(op)) {
                assertEquals(RealtimeMessage.OP_PONG, reply.get("op"), "ping 的回帧: " + reply);
                continue;
            }
            assertEquals(RealtimeMessage.OP_ERROR, reply.get("op"), "op=" + op + " 应回 error: " + reply);
            // 错误码之外再点一句只有那条分支才会说的话：四个 op 里有三个都回 WS_BAD_FRAME，
            // 只看错误码分不出"走到了自己的分支"和"在最前面就被同一句挡回来"。
            String expectedCode = "publish".equals(op) ? "WS_TOPIC_FORBIDDEN" : "WS_BAD_FRAME";
            String expectedWords = "publish".equals(op) ? "代发"
                    : "auth".equals(op) ? "token" : "topics";
            assertEquals(expectedCode, reply.get("errorCode"),
                    "op=" + op + " 没走到自己那条分支（拿到的是别的错误码）: " + reply);
            assertTrue(String.valueOf(reply.get("errorMessage")).contains(expectedWords),
                    "op=" + op + " 的报错该点名为它准备的入参（" + expectedWords + "）: " + reply);
        }
    }

    /**
     * 加法不能动旧面：{@code connectionId/userId/topics} 是 1.2.0 发布件里 ready 就有的三个键，
     * 前端拿 connectionId 对号、拿 topics 画已订列表。
     */
    @Test
    public void readyStillCarriesWhatItDidBefore() {
        Recorder a = connect(USER_A);
        Map<String, Object> ready = payloadOf(awaitFrame(a, opIs(RealtimeMessage.OP_READY), "ready 帧"));
        assertEquals(USER_A, ((Number) ready.get("userId")).longValue());
        String cid = String.valueOf(ready.get("connectionId"));
        assertFalse(cid.isEmpty() || "null".equals(cid), "connectionId 不能为空: " + ready);
        assertTrue(registry.find(cid) != null, "ready 报的 connectionId 在注册表里找不到: " + cid);
        List<String> topics = stringList(ready.get("topics"));
        assertTrue(topics.contains(RealtimeTopics.user(USER_A)), "自带的收件箱订阅: " + topics);
        assertTrue(topics.contains(LOBBY), "配置里的公共 topic 要自动订上: " + topics);
    }

    private static int intOf(Object v) {
        assertTrue(v instanceof Number, "limits 里的值应当是数字，实际: " + v);
        return ((Number) v).intValue();
    }
}
