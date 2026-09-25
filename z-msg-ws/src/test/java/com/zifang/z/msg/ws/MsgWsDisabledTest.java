package com.zifang.z.msg.ws;

import com.zifang.z.msg.ws.session.WsSessionRegistry;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code z-msg.ws.enabled=false} 必须一个 bean 都不注册、端点也不存在。
 * <p>
 * 开关是对外广告出来的配置，广告出去就得兑现；而"兑现"在这里只能证否——关掉之后连一条正经
 * 握手请求都会拿到 404，而不是仍然连得上。对照用同一上下文里的 HTTP 接口，
 * 排除"整个应用没起来"这种假绿。
 */
@SpringBootTest(classes = MsgWsDisabledTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "z-msg.enabled=true",
        "z-msg.web.trusted-header-enabled=true",
        "z-msg.realtime.ticket-secret=unused-but-set-0123456789abcdef",
        "z-msg.ws.enabled=false"
})
public class MsgWsDisabledTest {

    @Configuration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean(name = "dataSourceMsg")
        public DataSource dataSourceMsg() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:msg_ws_off;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
                    + ";INIT=RUNSCRIPT FROM 'classpath:z-msg/sql/schema-h2.sql'");
            ds.setUser("sa");
            return ds;
        }
    }

    @Autowired
    private ApplicationContext context;
    @Autowired
    private TestRestTemplate rest;
    @Value("${local.server.port}")
    private int port;

    @Test
    public void turningTheModuleOffRemovesTheEndpointButNotTheRestOfZMsg() throws Exception {
        assertEquals(0, context.getBeanNamesForType(WsSessionRegistry.class).length,
                "关掉之后不该还有连接注册表");
        assertEquals(404, WsHarness.handshakeStatus(port, "/api/msg/ws", true),
                "端点必须真的不存在");

        // 对照：同一个上下文里站内信接口照常活着——证明上面那个 404 是"模块关了"而不是"应用没起"。
        // 不带身份应当是 401 而不是 404，这就是"路径存在"的证据。
        assertEquals(401, rest.getForEntity("/api/msg/inbox/list", String.class).getStatusCodeValue(),
                "z-msg-web 的接口应当仍在，只是缺身份");
    }
}
