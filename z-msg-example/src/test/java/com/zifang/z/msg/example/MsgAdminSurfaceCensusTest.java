package com.zifang.z.msg.example;

import com.zifang.z.msg.web.config.AdminEndpointGate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端点普查：拿真宿主（示例应用 = web + im 全量装配）里活着的 handler mapping，
 * 逐个路径问一遍闸门"你是不是管理面"，然后把答案钉死。
 * <p>
 * 为什么要钉两侧：
 * <ul>
 *   <li>{@code blocked} 不等于清单 ⇒ 有人改了前缀或挪了路径，让原本该关的管理面从闸门底下走出去了；</li>
 *   <li>{@code open} 不等于清单 ⇒ 有人新加了一个 controller，而它没被登记进 {@link AdminEndpointGate#ADMIN_PREFIXES}。
 *       新端点必须显式过一次这道判断才进得来，这正是"每个作者记得抄一遍 if"的那个洞的反面。</li>
 * </ul>
 * 只统计 {@code com.zifang.z.msg.web}/{@code .im}：宿主自己的 controller（这里是 {@code /demo/*}）
 * 归宿主自己判，库不该替它表态。
 */
@SpringBootTest(classes = MsgExampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MsgAdminSurfaceCensusTest {

    private static final Set<String> SHIPPED_PREFIXES = new LinkedHashSet<String>(
            java.util.Arrays.asList("com.zifang.z.msg.web.", "com.zifang.z.msg.im."));

    /** 默认关时被挡在门外的全部路径。 */
    private static final Set<String> ADMIN = sorted(
            "/api/msg/template",
            "/api/msg/template/{id}",
            "/api/msg/template/list",
            "/api/msg/template/{id}/approve",
            "/api/msg/template/{id}/reject",
            "/api/msg/template/cache/refresh",
            "/api/msg/batch/submit",
            "/api/msg/batch/{id}",
            "/api/msg/batch/list",
            "/api/msg/batch/{id}/cancel",
            "/api/msg/delivery/list",
            "/api/msg/delivery/stats");

    /** 其余全部：身份来自 MsgPrincipalResolver 的用户面，以及通道自省。 */
    private static final Set<String> OPEN = sorted(
            "/api/msg/publish",
            "/api/msg/channel/list",
            "/api/msg/channel/detail",
            "/api/msg/inbox/list",
            "/api/msg/inbox/unread-count",
            "/api/msg/inbox/detail",
            "/api/msg/inbox/read",
            "/api/msg/inbox/read-all",
            "/api/msg/inbox/delete",
            "/api/msg/inbox/delete-all",
            "/api/msg/inbox/ws-token",
            "/api/msg/preference/upsert",
            "/api/msg/preference/my",
            "/api/msg/preference/{id}",
            "/api/msg/im/read/mark",
            "/api/msg/im/read/unread",
            "/api/msg/im/read/summary",
            "/api/msg/im/read/receipts",
            "/api/msg/im/message/send",
            "/api/msg/im/message/history",
            "/api/msg/im/message/at",
            "/api/msg/im/message/head",
            "/api/msg/im/conversation/list",
            "/api/msg/im/conversation/single",
            "/api/msg/im/conversation/group",
            "/api/msg/im/conversation/members",
            "/api/msg/im/conversation/member/add",
            "/api/msg/im/conversation/member/remove",
            "/api/msg/im/conversation/member/role",
            "/api/msg/im/conversation/leave",
            "/api/msg/im/conversation/mute",
            "/api/msg/im/conversation/clear");

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    /**
     * 前缀匹配自身的边界不在这里测（{@code MsgAdminEndpointGateTest} 里不用起上下文就能测）；
     * 这里只测一件事：一个真宿主 JVM 里实际存在的端点，被闸门的判定分成了哪两堆。
     */
    @Test
    public void everyShippedEndpointIsClassifiedOnTheRecord() {
        Set<String> blocked = new TreeSet<String>();
        Set<String> open = new TreeSet<String>();
        int shipped = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry
                : handlerMapping.getHandlerMethods().entrySet()) {
            if (!isShipped(entry.getValue().getBeanType().getName())) {
                continue;
            }
            for (String path : patternsOf(entry.getKey())) {
                shipped++;
                (AdminEndpointGate.isAdminPath(path) ? blocked : open).add(path);
            }
        }
        assertTrue(shipped > 40,
                "一个 handler 都没普查到，那下面两条断言全是空跑，实际 " + shipped);
        assertEquals(ADMIN, blocked, "闸门实际拦下的路径与管理面清单不符");
        assertEquals(OPEN, open, "有端点没被登记过（新增管理面要先加进 ADMIN_PREFIXES，别让它默认放行）");
    }

    // ---------------- helpers ----------------

    private static boolean isShipped(String className) {
        for (String prefix : SHIPPED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Boot 2.7 默认走 PathPatternParser，显式配了 ant 策略时才是 PatternsRequestCondition，两边都得读得出来。 */
    private static Set<String> patternsOf(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            Set<String> out = new LinkedHashSet<String>();
            for (PathPattern pattern : info.getPathPatternsCondition().getPatterns()) {
                out.add(pattern.getPatternString());
            }
            return out;
        }
        return info.getPatternsCondition() == null
                ? Collections.<String>emptySet()
                : info.getPatternsCondition().getPatterns();
    }

    private static Set<String> sorted(String... paths) {
        Set<String> out = new TreeSet<String>();
        Collections.addAll(out, paths);
        return out;
    }
}
