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

    private static void assertNotEqualStrings(String a, String b, String msg) {
        assertFalse(a.equals(b), msg);
    }
}
