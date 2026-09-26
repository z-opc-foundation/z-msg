package com.zifang.z.msg.ws;

import com.zifang.z.msg.api.RealtimeMessage;
import com.zifang.z.msg.api.RealtimeTopics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code op=auth} 的默认面：不带 {@code z-msg.ws.inband-auth-enabled} 就是关的。
 * <p>
 * 这条守卫的存在理由：开关关掉时"什么都不发生"很容易被写成"这个 op 静默被忽略"——
 * 客户端以为换了身份、其实没有，比直接报错难查十倍。所以这里既钉"回的是
 * {@code WS_AUTH_DISABLED} 这句点名配置的错"，也钉"同一条连接的其它 op 一切照旧"
 * （否则整类红可能是端点根本没起来，而不是开关在起作用）。
 */
@SpringBootTest(classes = MsgWsInbandAuthDisabledTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "z-msg.enabled=true",
        "z-msg.web.trusted-header-enabled=true",
        "z-msg.realtime.ticket-secret=inband-off-secret-0123456789abcdef",
        "z-msg.ws.public-topics[0]=room:lobby"
})
public class MsgWsInbandAuthDisabledTest extends WsHarness {

    @Configuration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean(name = "dataSourceMsg")
        public DataSource dataSourceMsg() {
            return h2("msg_ws_inband_off");
        }
    }

    @Test
    public void authIsRefusedWithAPropNamedMessageWhileEverythingElseStillWorks() {
        Recorder a = connect(USER_A);
        awaitReady(a);
        String cid = connectionIdOf(a);

        // 票的形状没问题：同样的换票接口给另一个用户签一张，握手能连上
        // —— 排除"被拒是因为票签不出来或端点坏了"这一假绿
        Recorder b = connect(USER_B);
        awaitReady(b);

        send(a, "{\"op\":\"auth\",\"clientMsgId\":\"k1\",\"token\":\"" + ticket(USER_B) + "\"}");
        Map<String, Object> err = awaitErrorCode(a, "WS_AUTH_DISABLED");
        assertEquals("k1", err.get("clientMsgId"));
        assertTrue(String.valueOf(err.get("errorMessage")).contains("z-msg.ws.inband-auth-enabled"),
                "报错要点名是哪个开关没开，否则宿主只会以为是票有问题: " + err);

        // 对照：同一条连接的其它 op 全在
        Map<String, Object> ack = subscribeAndWait(a, "k2", LOBBY);
        assertVerbContains(ack, "subscribed", LOBBY);
        send(a, "{\"op\":\"ping\",\"clientMsgId\":\"k3\"}");
        awaitFrame(a, opIs(RealtimeMessage.OP_PONG), "pong 帧");

        // 关掉时身份与订阅都一点没动
        assertEquals(Long.valueOf(USER_A), registry.find(cid).getUserId().longValue());
        assertTrue(registry.find(cid).subscribes(RealtimeTopics.user(USER_A)),
                "默认面下不该发生任何 topic 变更");
    }
}
