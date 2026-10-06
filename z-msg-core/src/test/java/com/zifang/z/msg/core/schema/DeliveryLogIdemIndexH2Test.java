package com.zifang.z.msg.core.schema;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@code uk_channel_biz_dedup} 在真 H2 上的行为。
 *
 * <p>{@link SchemaParityTest} 只保证两份 DDL 文本一致、列与实体对得上；它<b>不</b>保证这份唯一索引
 * 真按 ChannelRouter 依赖的方式工作。这里的四条都是 ChannelRouter 的判据赖以成立的前提，
 * 少一条，跨节点幂等就会静默失效：</p>
 *
 * <ol>
 *   <li>同一 {@code (channel, biz_type, idempotency_key)} 插第二次必须失败 —— 这就是"别的节点已占位"的信号；</li>
 *   <li>多行 {@code idempotency_key IS NULL} 必须共存 —— 没送达的行写 NULL，不占位；
 *       唯一索引允许多个 NULL，所以它们彼此不会撞；</li>
 *   <li>{@code UPDATE ... SET idempotency_key = null WHERE idempotency_key = ?} 必须真的把位还回去，
 *       还回去之后同一个令牌要能重新占住（重放能发出去）；</li>
 *   <li>{@code biz_type IS NULL} 的行<b>不</b>参与去重 —— 这正是 ChannelRouter 在 bizType 为空时
 *       放弃跨节点占位、退回本机幂等表的原因（否则占位白占）。</li>
 * </ol>
 */
public class DeliveryLogIdemIndexH2Test {

    private static final String H2_SQL = "z-msg/sql/schema-h2.sql";

    @Test
    public void 同一去重位插第二次会被唯一索引挡住() throws Exception {
        try (Connection conn = newH2()) {
            insertLog(conn, "PUSH_FCM", "ORDER_PAID", "PUSH_FCM|ORDER_PAID|order-1|-|-", 1);

            try {
                insertLog(conn, "PUSH_FCM", "ORDER_PAID", "PUSH_FCM|ORDER_PAID|order-1|-|-", 1);
                fail("第二次插入本该被 uk_channel_biz_dedup 挡住 —— "
                        + "它挡不住的话 ChannelRouter 的跨节点判重就是摆设");
            } catch (SQLException expected) {
                assertTrue(String.valueOf(expected.getSQLState()).startsWith("23"),
                        "期望完整性约束违约，实际 SQLState=" + expected.getSQLState()
                                + "（这说明撞的可能是别的约束，判据没测到点上）");
            }
            assertEquals(1, countLogs(conn), "失败的那次插入不该留下行");
        }
    }

    @Test
    public void 未送达的行写NULL彼此不撞() throws Exception {
        try (Connection conn = newH2()) {
            // 同一个 biz 连续失败 3 次：3 行都写 NULL，谁也不该把谁顶掉
            insertLog(conn, "PUSH_FCM", "ORDER_PAID", null, 2);
            insertLog(conn, "PUSH_FCM", "ORDER_PAID", null, 2);
            insertLog(conn, "PUSH_FCM", "ORDER_PAID", null, 2);

            assertEquals(3, countLogs(conn),
                    "唯一索引里允许多个 NULL：没送达的行不占位，重放还得能发出去");
        }
    }

    @Test
    public void 幂等位还回去之后同一个令牌能重新占住() throws Exception {
        try (Connection conn = newH2()) {
            String token = "PUSH_FCM|ORDER_PAID|order-9|-|-";
            insertLog(conn, "PUSH_FCM", "ORDER_PAID", token, 0);
            assertEquals(1, releaseIdemSlot(conn, token),
                    "占位行还在时应当恰好更新 1 行（ChannelRouter 靠返回值判断有没有认领到）");

            // 位已经还回去 → 重放必须能重新占住，否则这条消息被永久钉死
            insertLog(conn, "PUSH_FCM", "ORDER_PAID", token, 0);
            assertEquals(2, countLogs(conn), "重放应当能重新占住同一个幂等位");
        }
    }

    @Test
    public void bizType为空的行不参与去重() throws Exception {
        try (Connection conn = newH2()) {
            String token = "PUSH_FCM||order-3|-|-";
            insertLog(conn, "PUSH_FCM", null, token, 0);
            insertLog(conn, "PUSH_FCM", null, token, 0);

            assertEquals(2, countLogs(conn),
                    "biz_type 可空，它为 NULL 时整行不参与唯一索引去重 —— "
                            + "ChannelRouter 因此在 bizType 为空时直接放弃跨节点占位，这条断言是那个判断的依据");
        }
    }

    // ---------------- helpers ----------------

    private static Connection newH2() throws Exception {
        String url = "jdbc:h2:mem:idem_slot_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        Connection conn = DriverManager.getConnection(url, "sa", "");
        try (Statement st = conn.createStatement()) {
            st.execute(String.join("\n", read()));
        }
        return conn;
    }

    private static void insertLog(Connection conn, String channel, String bizType,
                                  String idemKey, int status) throws SQLException {
        String sql = "INSERT INTO z_msg_delivery_log "
                + "(biz_type, channel, idempotency_key, provider, status, retry_count, max_retry,"
                + " created_time, updated_time) VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            ps.setString(1, bizType);
            ps.setString(2, channel);
            ps.setString(3, idemKey);
            ps.setString(4, "test-provider");
            ps.setInt(5, status);
            ps.setInt(6, 0);
            ps.setInt(7, 3);
            ps.setTimestamp(8, now);
            ps.setTimestamp(9, now);
            ps.executeUpdate();
        }
    }

    /**
     * 与 ChannelRouter.finalizeWrapper 里那段 {@code setSql("idempotency_key = null")} 等价的语句。
     * 这里手写而不是把 wrapper 拿来跑，是为了这条测试只依赖 DDL、不依赖 MyBatis 的 SQL 生成；
     * wrapper 生成的 SET 子句长什么样由 {@code ChannelRouterIdemSlotTest} 断言。
     */
    private static int releaseIdemSlot(Connection conn, String token) throws SQLException {
        String sql = "UPDATE z_msg_delivery_log SET status = ?, updated_time = ?, idempotency_key = null "
                + "WHERE channel = ? AND biz_type = ? AND idempotency_key = ? AND status = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, 2);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.setString(3, "PUSH_FCM");
            ps.setString(4, "ORDER_PAID");
            ps.setString(5, token);
            ps.setInt(6, 0);
            return ps.executeUpdate();
        }
    }

    private static int countLogs(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM z_msg_delivery_log")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static List<String> read() throws Exception {
        InputStream in = DeliveryLogIdemIndexH2Test.class.getClassLoader().getResourceAsStream(H2_SQL);
        if (in == null) {
            throw new IllegalStateException("classpath 上找不到 " + H2_SQL);
        }
        List<String> lines = new ArrayList<String>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
}