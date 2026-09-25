package com.zifang.z.msg.ws;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 没配 {@code z-msg.realtime.ticket-secret} 时的 fail-closed 形状。
 * <p>
 * 这是库最容易被宿主踩到的一格：站内信照常落库、端点照常注册、浏览器连上去却静默收不到东西。
 * 所以这里既要钉住"绝不接受无凭据握手"，也要钉住"明确报错让人知道差哪个配置"。
 * <p>
 * 正向对照（配了密钥就签得出票、连得上）在 {@link MsgWsEndToEndTest}：同一个进程里没法
 * 既改配置又复用同一个上下文。这里额外用"同一条 URL 的另一种请求要 400"来排除
 * "这条路径根本是死的"这一种假绿。
 */
@SpringBootTest(classes = MsgWsFailClosedTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "z-msg.enabled=true",
        "z-msg.web.trusted-header-enabled=true"
})
public class MsgWsFailClosedTest extends WsHarness {

    @Configuration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean(name = "dataSourceMsg")
        public DataSource dataSourceMsg() {
            return h2("msg_ws_nosecret");
        }
    }

    @Test
    public void tokenEndpointRefusesToIssueAndSaysWhichPropertyIsMissing() {
        ResponseEntity<String> resp = exchange("/api/msg/inbox/ws-token", HttpMethod.GET, USER_A);
        assertEquals(503, codeOf(resp),
                "没配密钥时换票必须 503，而不是签一张弱密钥的票，body=" + resp.getBody());
        assertTrue(resp.getBody() != null && resp.getBody().contains("z-msg.realtime.ticket-secret"),
                "报错要直接点出缺哪个配置，否则宿主只会看到一句签发失败: " + resp.getBody());
    }

    @Test
    public void handshakeReachesTheInterceptorAndIsRefusedRatherThan404() throws Exception {
        // 端点存在（否则会 404）、拦截器跑到、因为没密钥而 503。
        // 对照在 MsgWsEndToEndTest：同一个请求形状、只改配置，那边拿到的是 401（缺 token）而不是 503，
        // 两条合起来才说明 503 出自"缺密钥"这一支。
        assertEquals(503, handshakeStatus(port, wsProperties.getPath(), true),
                "端点已注册但无凭据，应当由拦截器给出 503");
        assertEquals(0, registry.onlineConnections(), "被拒的握手不能留下任何连接");
    }
}
