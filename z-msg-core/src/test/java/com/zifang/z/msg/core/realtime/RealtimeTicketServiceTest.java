package com.zifang.z.msg.core.realtime;

import com.zifang.z.msg.core.config.MessageProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ws-ticket 的签发与校验。这里最关键的是"负向断言有猎物"：
 * 每条"应当被拒"的用例都构造了一个真实可解析但语义非法的 token，
 * 而不是只喂空串 —— 否则 verify() 整体返回 null 也能让测试全绿。
 */
public class RealtimeTicketServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdef";

    private static MessageProperties props(String secret, int ttlSeconds) {
        MessageProperties p = new MessageProperties();
        p.getRealtime().setTicketSecret(secret);
        p.getRealtime().setTicketTtlSeconds(ttlSeconds);
        return p;
    }

    @Test
    public void roundTripBindsTheUserIdItWasIssuedFor() {
        RealtimeTicketService svc = new RealtimeTicketService(props(SECRET, 60));
        String t1 = svc.issue(1001L);
        String t2 = svc.issue(1002L);
        String again = svc.issue(1001L);
        assertNotNull(t1);
        assertNotNull(t2);
        assertNotEqualStrings(t1, again, "同一用户重复签发也应带不同 nonce（ticket 进日志时不该可重放比对）");
        assertEquals(Long.valueOf(1001L), svc.verify(t1));
        assertEquals(Long.valueOf(1002L), svc.verify(t2));
        assertEquals(Long.valueOf(1001L), svc.verify(again));
        // 1001 的 ticket 换不来 1002 的身份
        assertFalse(Long.valueOf(1002L).equals(svc.verify(t1)));
    }

    @Test
    public void unconfiguredSecretFailsClosed() {
        RealtimeTicketService svc = new RealtimeTicketService(props("", 60));
        assertFalse(svc.isConfigured());
        assertNull(svc.issue(1L), "没配密钥时绝不能签发一个可用弱密钥的 ticket");
        assertNull(svc.verify("anything"));
    }

    @Test
    public void tokenSignedWithAnotherSecretIsRejected() {
        RealtimeTicketService issuer = new RealtimeTicketService(props(SECRET, 60));
        RealtimeTicketService other = new RealtimeTicketService(props("a-completely-different-secret", 60));
        String forged = issuer.issue(42L);
        assertEquals(Long.valueOf(42L), issuer.verify(forged), "先确认猎物在：issuer 自己能验通");
        assertNull(other.verify(forged), "换一把密钥必须验不过");
    }

    @Test
    public void tamperedPayloadIsRejected() {
        RealtimeTicketService svc = new RealtimeTicketService(props(SECRET, 60));
        String token = svc.issue(7L);
        int dot = token.lastIndexOf('.');
        String payload = token.substring(0, dot);
        String sig = token.substring(dot + 1);
        // 把 payload 里的 userId 换成 999 再重新 base64：签名对不上
        String decoded = new String(java.util.Base64.getUrlDecoder().decode(payload),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(decoded.contains("|7|"), "前置：ticket 载荷里确实带着 userId，否则这条测试没有猎物");
        String swapped = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                decoded.replace("|7|", "|999|").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertNull(svc.verify(swapped + "." + sig), "改过 userId 的 ticket 必须失效");
        // 只截掉签名也要拒
        assertNull(svc.verify(payload + "."));
    }

    @Test
    public void expiredTokenIsRejected() throws InterruptedException {
        RealtimeTicketService svc = new RealtimeTicketService(props(SECRET, 1));
        String token = svc.issue(5L);
        assertEquals(Long.valueOf(5L), svc.verify(token), "刚签发时必须可用");
        Thread.sleep(1500L);
        assertNull(svc.verify(token), "超过 TTL 后必须拒绝");
    }

    @Test
    public void malformedInputNeverThrows() {
        RealtimeTicketService svc = new RealtimeTicketService(props(SECRET, 60));
        String[] junk = {null, "", ".", "abc", "a.b.c", "!!!.!!!",
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("x|1".getBytes()) + ".&&&"};
        for (String s : junk) {
            assertNull(svc.verify(s), "非法输入要返回 null 而不是抛异常: " + s);
        }
        assertNull(svc.issue(null));
    }

    @Test
    public void consumeAllowsExactlyOneUseWhileVerifyStaysRepeatable() {
        RealtimeTicketService svc = new RealtimeTicketService(props(SECRET, 60));
        String token = svc.issue(88L);
        // verify 是可重复的纯验签：既有调用方（握手之外的自检、宿主的其它场景）靠这一点，
        // 哪天有人把它改成"顺手记账"，这两行会先红。
        assertEquals(Long.valueOf(88L), svc.verify(token));
        assertEquals(Long.valueOf(88L), svc.verify(token), "verify 不得消费 ticket");
        // 换 consume：第一次开得出身份
        assertEquals(Long.valueOf(88L), svc.consume(token), "首次使用必须放行");
        assertNull(svc.consume(token), "同一张票第二次握手必须被拒（票进过 access log，重放窗口要关掉）");
        // 并且拒的是这张票，不是这个身份：另换一张照样能开
        assertEquals(Long.valueOf(88L), svc.consume(svc.issue(88L)),
                "重放被拒不能顺手把用户拉黑");
    }

    @Test
    public void consumptionIsRecordedPerTicketNotPerUser() {
        RealtimeTicketService svc = new RealtimeTicketService(props(SECRET, 60));
        String a = svc.issue(9L);
        String b = svc.issue(9L);
        assertFalse(a.equals(b), "前置：两张票确实不同，否则这条测试没有猎物");
        assertEquals(Long.valueOf(9L), svc.consume(a));
        assertEquals(Long.valueOf(9L), svc.consume(b), "同一用户并行开两条连接不该互相挡");
        assertNull(svc.consume(a));
        assertNull(svc.consume(b));
        // consume 之后 verify 仍然验得通：这不是"票作废了"，只是"这张票已经换过连接了"。
        // 钉住这一点是因为它决定了 consume 能不能被当成吊销来用——不能。
        assertEquals(Long.valueOf(9L), svc.verify(a), "consume 不是吊销，别把 verify 当安全闸门用");
    }

    @Test
    public void expiredOrUnconfiguredTicketNeverEntersTheLedger() {
        RealtimeTicketService fresh = new RealtimeTicketService(props(SECRET, 1));
        String token = fresh.issue(5L);
        sleep(1500L);
        assertNull(fresh.consume(token), "超过 TTL 后 consume 也必须拒");
        // 过期判定排在记账之前：一张过期的票不该占一条记录，否则扫描过期票就能灌满上限
        assertEquals(0, fresh.trackedConsumptions(), "过期票不得进账，账上这一条都没有才对");
        assertNull(new RealtimeTicketService(props("", 60)).consume(token), "没配密钥时一律拒");
    }

    @Test
    public void consumptionLedgerStaysBoundedAndLetsLegitimateHandshakesThrough() {
        final RealtimeTicketService svc = new RealtimeTicketService(props(SECRET, 60));
        int n = RealtimeTicketService.MAX_CONSUMED_TRACKED + 500;
        String[] tokens = new String[n];
        for (int i = 0; i < n; i++) {
            tokens[i] = svc.issue(1000L + i);
        }
        for (int i = 0; i < n; i++) {
            assertEquals(Long.valueOf(1000L + i), svc.consume(tokens[i]),
                    "第 " + i + " 张新票不该被拒：上限只能牺牲记账，不能变成登不上");
        }
        assertTrue(svc.trackedConsumptions() <= RealtimeTicketService.MAX_CONSUMED_TRACKED,
                "记账条数必须被上限压住，否则这一步就是个内存漏：" + svc.trackedConsumptions());
        // 上限之外的代价照实钉住：被挤掉记录的旧票会重新可用。测试里它是"能连上"，
        // 而这正是 §9 写下"洪泛时保证降级"的那个口子——不钉住，注释就成了空话。
        assertNotNull(svc.consume(tokens[0]), "被挤出记账的票会重新放行：这条保证在超量时是降级而不是拒绝");
    }

    @Test
    public void malformedInputNeverThrowsOnConsume() {
        RealtimeTicketService svc = new RealtimeTicketService(props(SECRET, 60));
        String[] junk = {null, "", ".", "abc", "a.b.c", "!!!.!!!", tamper(svc.issue(3L))};
        for (String s : junk) {
            assertNull(svc.consume(s), "非法输入要返回 null 而不是抛异常: " + s);
        }
        assertEquals(0, svc.trackedConsumptions(), "非法输入不进账，否则一次扫描就能灌满记账");
    }

    /** 只动最后两个字符：形状仍是一张合法的 ticket，但签名一定对不上。 */
    private static String tamper(String token) {
        return token.substring(0, token.length() - 2) + "xy";
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static void assertNotEqualStrings(String a, String b, String msg) {
        assertFalse(a.equals(b), msg);
    }
}
