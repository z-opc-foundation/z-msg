package com.zifang.z.msg.im;

import com.zifang.z.msg.core.json.MsgJson;
import com.zifang.z.msg.im.domain.service.ImConversationService;
import com.zifang.z.msg.im.domain.service.ImMessageService;
import com.zifang.z.msg.im.domain.service.ImReadService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * im 测试的公共骨架：真 Boot 上下文、真 H2、真 HTTP。
 * <p>
 * <b>这里刻意不做任何"手工接线"</b>：{@link TestApp} 只有 {@code @Configuration +
 * @EnableAutoConfiguration} 和一把顶掉 MySQL 的 {@code dataSourceMsg}，没有
 * {@code @ComponentScan}、没有 {@code @SpringBootApplication}、没有 {@code @MapperScan}，
 * 也没有 {@code new} 过任何一个 service。所有 im 的 bean 只可能来自
 * {@code META-INF/spring.factories} 那一条自动装配登记 —— 这正是本模块要证的东西：
 * 宿主只要把 jar 放进 classpath 就能用。手搭 bean 的测试只能证明"我会手工接线"。
 * <p>
 * 等待一律用 {@link #waitUntil}（轮询到期限）而不是 {@code sleep(固定值)}：帧由服务端线程发，
 * 固定等待在负载高的机器上必然假红。
 */
@SpringBootTest(classes = ImSpringTestSupport.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "z-msg.enabled=true",
        // 测试用可信头当身份，等价于"网关已鉴权"。生产默认 false（见 TrustedHeaderPrincipalResolver）。
        "z-msg.web.trusted-header-enabled=true",
        // 只为 WebSocket ticket 准备的一把测试密钥，不是任何环境的真凭据
        "z-msg.realtime.ticket-secret=im-test-ticket-secret-0123456789abcdef",
        "z-msg.ws.public-topics[0]=room:lobby",
        "debug=true"
})
public abstract class ImSpringTestSupport {

    /** 单聊双方 */
    protected static final long A = 9101L;
    protected static final long B = 9102L;
    /** 与两个主角色不相干的人：越权断言用 */
    protected static final long OUTSIDER = 9103L;
    protected static final long OUTSIDER_2 = 9104L;

    protected static final long WAIT_TIMEOUT_MS = 15_000L;

    private static final String[] TABLES = {
            "z_msg_im_read_receipt", "z_msg_im_message", "z_msg_im_member", "z_msg_im_conversation"
    };

    /**
     * 每个 Spring 上下文一把独立的库：{@code DB_CLOSE_DELAY=-1} 会让先建的库一直活着，
     * 第二个上下文的 {@code INIT=RUNSCRIPT} 落在同一个库里就会互相看见对方的数据。
     */
    private static final AtomicInteger DB_SEQ = new AtomicInteger();

    @Autowired
    protected ImConversationService conversationService;
    @Autowired
    protected ImMessageService messageService;
    @Autowired
    protected ImReadService readService;
    @Autowired
    protected TestRestTemplate rest;
    @Value("${local.server.port}")
    protected int port;
    @javax.annotation.Resource(name = "dataSourceMsg")
    protected DataSource dataSourceMsg;

    @Configuration
    @EnableAutoConfiguration
    static class TestApp {
        /**
         * 顶掉 core 那台 MySQL/Druid 的 dataSourceMsg。
         */
        @Bean(name = "dataSourceMsg")
        public DataSource dataSourceMsg() {
            return h2();
        }
    }

    /**
     * 一把纯内存的 H2，两份脚本一起跑：core 的 {@code schema-h2.sql}（站内信那套）+ 本模块的
     * {@code im-schema-h2.sql}（四张 im 表）。H2 的 {@code INIT} 里语句用 {@code \;} 分隔。
     */
    protected static DataSource h2() {
        String db = "msg_im_" + DB_SEQ.incrementAndGet();
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + db + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
                + ";INIT=RUNSCRIPT FROM 'classpath:z-msg/sql/schema-h2.sql'"
                + "\\;RUNSCRIPT FROM 'classpath:z-msg/sql/im-schema-h2.sql'");
        ds.setUser("sa");
        return ds;
    }

    /**
     * 每条用例从空表开始。这里删的是本模块自己的四张表，站内信表不归本模块管、也不碰。
     */
    @BeforeEach
    public void cleanImTables() throws Exception {
        try (Connection c = dataSourceMsg.getConnection(); Statement s = c.createStatement()) {
            for (String table : TABLES) {
                s.execute("DELETE FROM " + table);
            }
        }
    }

    // ---------------------------------------------------------------- 轮询

    protected static boolean waitUntil(BooleanSupplier condition) {
        return waitUntil(condition, WAIT_TIMEOUT_MS);
    }

    protected static boolean waitUntil(BooleanSupplier condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        do {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.currentTimeMillis() < deadline);
        return condition.getAsBoolean();
    }

    protected static void awaitUntilTrue(BooleanSupplier condition, String what) {
        if (!waitUntil(condition)) {
            fail("超时未等到：" + what);
        }
    }

    // ---------------------------------------------------------------- HTTP

    /** 带身份的 POST：身份只走 header（可信头 resolver），body 里写 userId 是无效的。 */
    protected ResponseEntity<String> postJson(String path, Long userId, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (userId != null) {
            headers.set("X-Msg-User-Id", String.valueOf(userId));
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<String>(body == null ? "{}" : body, headers),
                String.class);
    }

    /**
     * 未登录探针：POST <b>同一份 body</b>，只是什么凭证都不带。
     * <p>
     * 为什么这一轮不能走 {@link #postJson}：JDK 的 {@code HttpURLConnection}
     * （{@code TestRestTemplate} 在本仓库的 classpath 下用的就是它）在读一个
     * "body 已经以 streaming 方式写出去、服务端回了 401"的响应时，会直接抛
     * {@code HttpRetryException: cannot retry due to server authentication, in streaming
     * mode} —— Spring 只要 body 非空就会调 {@code setFixedLengthStreamingMode}，
     * 于是"401 这件事"在客户端根本读不到（实测：换成完全不带 body 也一样抛，
     * 所以躲不开，只能在客户端这一侧绕）。
     * 403/400/500 都不受影响，所以整个测试面只有"未登录"这一轮要绕。
     * <p>
     * 这里自己开一条连接、<b>不设</b>任何 streaming 模式，body 由 JDK 缓冲到 connect 时再写，
     * 于是 401 的状态码与 body 都拿得回来。请求形状与 {@link #postJson} 完全一致
     * （同一个 path、同样的 JSON body、同样的 Content-Type），少的只有身份 header ——
     * 这正是这一轮要隔离出的唯一变量。
     */
    protected ResponseEntity<String> postJsonWithoutCredentials(String path, String body) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("http://localhost:" + port + path).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout((int) WAIT_TIMEOUT_MS);
            conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            byte[] payload = (body == null ? "{}" : body).getBytes("UTF-8");
            OutputStream os = conn.getOutputStream();
            try {
                os.write(payload);
            } finally {
                os.close();
            }
            int status = conn.getResponseCode();
            java.io.InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String text = readFully(in);
            return new ResponseEntity<String>(text, HttpStatus.valueOf(status));
        } catch (Exception e) {
            throw new IllegalStateException("未登录探针跑不动 " + path + ": " + e, e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readFully(java.io.InputStream in) throws java.io.IOException {
        if (in == null) {
            return "";
        }
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[512];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }

    protected ResponseEntity<String> get(String path, Long userId) {
        HttpHeaders headers = new HttpHeaders();
        if (userId != null) {
            headers.set("X-Msg-User-Id", String.valueOf(userId));
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<Void>(headers), String.class);
    }

    /** 解出 {@code Result} 的外壳：{@code {code, message, data}}。 */
    @SuppressWarnings("unchecked")
    protected static Map<String, Object> bodyOf(ResponseEntity<String> resp) {
        Map<String, Object> body = MsgJson.toMap(resp.getBody());
        assertNotNull(body, "响应不是 JSON 对象: " + resp.getBody());
        return body;
    }

    protected static int codeOf(ResponseEntity<String> resp) {
        Object code = bodyOf(resp).get("code");
        assertTrue(code instanceof Number, "响应里没有数字 code: " + resp.getBody());
        return ((Number) code).intValue();
    }

    @SuppressWarnings("unchecked")
    protected static <T> T dataOf(ResponseEntity<String> resp) {
        assertOk(resp);
        Object data = bodyOf(resp).get("data");
        return (T) data;
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> dataMap(ResponseEntity<String> resp) {
        Object data = dataOf(resp);
        assertTrue(data instanceof Map, "data 不是对象: " + resp.getBody());
        return (Map<String, Object>) data;
    }

    protected static void assertOk(ResponseEntity<String> resp) {
        assertEquals(200, resp.getStatusCodeValue(), "HTTP 状态: " + resp.getBody());
        assertEquals(200, codeOf(resp), "业务 code 应为 200: " + resp.getBody());
    }

    protected static void assertOk(ResponseEntity<String> resp, String what) {
        assertEquals(200, resp.getStatusCodeValue(), what + " 的 HTTP 状态: " + resp.getBody());
        assertEquals(200, codeOf(resp), what + " 的业务 code 应为 200: " + resp.getBody());
    }

    /** 从 JSON 数字里取 long（Jackson 会按大小给 Integer/Long）。 */
    protected static long longOf(Object raw) {
        assertTrue(raw instanceof Number, "期望数字，实际 " + (raw == null ? "null" : raw.getClass()) + " " + raw);
        return ((Number) raw).longValue();
    }

    /**
     * 取一个 id 字段，并且**要求它在线上是字符串**。
     * <p>
     * 不复用 {@link #longOf}：这条断言的全部意义就是"它不是数字"。雪花是 19 位十进制，
     * 浏览器 {@code JSON.parse} 会把它舍进 double（{@code 2103885891501236225} →
     * {@code ...200}），前端拿舍过的值去拼 {@code room:<id>} 或回传 REST，症状是
     * "刚建好的会话 403 无权访问"——而这个 id 在服务端从未存在过。
     * 哪天有人把 {@code ToStringSerializer} 摘掉，红的是这里，不是前端。
     */
    protected static long idOf(Object raw) {
        assertTrue(raw instanceof String,
                "id 必须出字符串，出数字就是让前端去舍入 19 位雪花；实际 "
                        + (raw == null ? "null" : raw.getClass().getName()) + " " + raw);
        return Long.parseLong((String) raw);
    }

    protected static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<String, Object>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    protected static String json(Map<String, Object> body) {
        return MsgJson.toJson(body);
    }
}
