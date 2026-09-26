package com.zifang.z.msg.ws;

import com.zifang.z.msg.api.RealtimeMessage;
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
 * {@code ready} 自描述的另一半：**关掉的闸门既不报数、也确实不拦**。
 * <p>
 * 这一类比 {@link MsgWsReadySelfDescriptionTest} 更容易写空：断言"limits 里没有
 * maxTopicsPerConnection"这个键，只要把它无条件删掉就能全绿。所以每一项缺席都配了正面猎物——
 * 上限 0 时真订 8 条、每用户连接 0 时真开 3 条、auth 没报但发过去拿到的是点名开关的
 * {@code WS_AUTH_DISABLED}（不是 unsupported，也不是静默）。
 * <p>
 * 为什么不能报 0：{@code max-topics-per-connection=0} 的语义是"不限"（见 {@code WsProperties}），
 * 而前端读到 {@code "maxTopicsPerConnection": 0} 只会理解成"一条都不许订"。
 * 一个键的缺席与一个 0，中间差着"这项能力是被禁了还是压根没闸"。
 */
@SpringBootTest(classes = MsgWsReadyOptOutsTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "z-msg.enabled=true",
        "z-msg.web.trusted-header-enabled=true",
        "z-msg.realtime.ticket-secret=ready-opt-out-secret-0123456789ab",
        "z-msg.ws.public-topics[0]=room:lobby",
        "z-msg.ws.max-topics-per-connection=0",
        "z-msg.ws.max-sessions-per-user=0",
        "z-msg.ws.idle-timeout-seconds=0"
})
public class MsgWsReadyOptOutsTest extends WsHarness {

    @Configuration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean(name = "dataSourceMsg")
        public DataSource dataSourceMsg() {
            return h2("msg_ws_ready_opt_outs");
        }

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
    public void limitsThatAreSwitchedOffAreAbsentAndActuallyNotEnforced() {
        Recorder a = connect(USER_A);
        Map<String, Object> ready = payloadOf(awaitFrame(a, opIs(RealtimeMessage.OP_READY), "ready 帧"));
        Map<String, Object> limits = (Map<String, Object>) ready.get("limits");
        assertTrue(limits != null, "ready 里该有 limits: " + ready);
        assertEquals(new LinkedHashSet<String>(Arrays.asList("maxTextMessageBytes")), limits.keySet(),
                "三项被配成「不限」，一个都不该报（报 0 会被前端读成「一条都不许」）");
        assertEquals(wsProperties.getMaxTextMessageBytes(), ((Number) limits.get("maxTextMessageBytes")).intValue(),
                "唯一常报的那项也要与设进容器的 buffer 同源");

        // 猎物一：topic 上限没报，是因为真没有这道闸——一次订 8 条全过。
        String[] eight = new String[8];
        for (int i = 0; i < eight.length; i++) {
            eight[i] = "team:t" + i;
        }
        assertVerbContains(subscribeAndWait(a, "u1", eight), "subscribed", "team:t7");
        assertEquals(8 + 3, registry.find(connectionIdOf(a)).topics().size(),
                "默认值 64 都没到，0 更不该拦（拦了这里就会少一帧 ack 而卡在超时）");

        // 猎物二：每用户连接上限没报，是因为真不限——同一用户连开 3 条都在。
        Recorder b = connect(USER_A);
        awaitReady(b);
        Recorder c = connect(USER_A);
        awaitReady(c);
        assertEquals(3, registry.onlineConnections(),
                "max-sessions-per-user=0 时不该挤掉任何一条，ready 也不该报这个数");
    }

    /**
     * 空闲超时配 0 的语义是"别动容器默认值"（那是容器级的、会连累宿主自己的端点），
     * 所以 z-msg 手里没有"实际生效值"可以报——报了就是假的。
     * 这一条钉的是「没设过就不报」，而不是"容器到底多久断"（那要在真实容器上挂两分钟才量得出）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void anIdleTimeoutWeNeverSetIsNotAdvertised() {
        assertEquals(0, wsProperties.getIdleTimeoutSeconds(), "本类的配置就是「别设容器超时」，先钉住前提");
        Recorder a = connect(USER_A);
        Map<String, Object> ready = payloadOf(awaitFrame(a, opIs(RealtimeMessage.OP_READY), "ready 帧"));
        Map<String, Object> limits = (Map<String, Object>) ready.get("limits");
        assertFalse(limits.containsKey("idleTimeoutSeconds"),
                "没设进容器的数值不该报给前端当心跳依据: " + limits);
    }

    @Test
    public void authIsAbsentFromTheAdvertisedOpsButStillAnswersByName() {
        Recorder a = connect(USER_A);
        List<String> ops = stringList(payloadOf(awaitFrame(a, opIs(RealtimeMessage.OP_READY), "ready 帧"))
                .get("ops"));
        assertEquals(Arrays.asList("ping", "subscribe", "unsubscribe", "publish"), ops,
                "默认关的 op 不该出现在清单里（开着时它是第五条，见 MsgWsReadySelfDescriptionTest）");

        // 缺席只是"不广告"，不是"这扇门上没锁"：发过去要拿到点名开关的错，而不是 unsupported。
        send(a, "{\"op\":\"auth\",\"clientMsgId\":\"o1\",\"token\":\"" + ticket(USER_B) + "\"}");
        Map<String, Object> err = awaitErrorCode(a, "WS_AUTH_DISABLED");
        assertEquals("o1", err.get("clientMsgId"));
        assertTrue(String.valueOf(err.get("errorMessage")).contains("z-msg.ws.inband-auth-enabled"),
                "报错要点名开关: " + err);

        // 对照：报出来的 op 一切照旧（否则"清单少了 auth"可能是端点坏了）
        assertVerbContains(subscribeAndWait(a, "o2", "team:open"), "subscribed", "team:open");
        send(a, "{\"op\":\"ping\",\"clientMsgId\":\"o3\"}");
        awaitFrame(a, opIs(RealtimeMessage.OP_PONG), "pong 帧");
    }
}
