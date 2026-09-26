package com.zifang.z.msg.ws.handler;

import com.zifang.z.msg.ws.config.WsProperties;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 结构守卫：{@link MsgWebSocketHandler} 里每一个 {@code .equals(op)} 分发分支，都必须出现在
 * {@code ready.ops} 报给客户端的清单里，反之亦然。
 * <p>
 * 为什么只能拿源码来数：端到端那半边（{@code MsgWsReadySelfDescriptionTest}）能抓到
 * "报了却没人处理"——那种帧会回 {@code WS_OP_UNSUPPORTED}，客户端看得见；抓不到
 * "加了分支忘了报"——那种漏法的表现是**什么都不发生**，客户端根本不知道有这张帧可发，
 * 而这正是自描述这件事唯一要防的失败。
 * <p>
 * 清单在这里取的是"开关全开"那一版：{@code auth} 在分发处永远在，只是默认不报
 * （默认关的那一半由 {@code MsgWsReadyOptOutsTest} 钉）。
 */
public class MsgWsOpCensusTest {

    private static final String HANDLER_SOURCE =
            "src/main/java/com/zifang/z/msg/ws/handler/MsgWebSocketHandler.java";

    @Test
    public void dispatchedOpBranchesAndTheAdvertisedListAgree() throws Exception {
        File source = handlerSource();
        Set<String> dispatched = new LinkedHashSet<String>();
        Pattern p = Pattern.compile("OP_([A-Z_]+)\\.equals\\(op\\)");
        for (String line : Files.readAllLines(source.toPath(), StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")) {
                // 注释里出现 .equals(op) 是解释写法，不是分发分支
                continue;
            }
            Matcher m = p.matcher(line);
            while (m.find()) {
                dispatched.add(m.group(1).toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (dispatched.isEmpty()) {
            // 数到 0 条只能说明量具瞎了（正则或路径不对），不能读成"双方一致"
            fail("一条 .equals(op) 分支都没数到，量具坏了；读的是 " + source.getAbsolutePath());
        }
        assertEquals(expectedBranchCensus(), dispatched,
                "分发分支本身变了：这个 census 例的前提（handler 有且仅有这几条分支）不成立了，"
                        + "改动分发就要同时改 ready 清单与本例");

        WsProperties allOn = new WsProperties();
        allOn.setInbandAuthEnabled(true);
        Set<String> advertised = new LinkedHashSet<String>(MsgWebSocketHandler.clientOps(allOn));
        assertEquals(dispatched, advertised,
                "分发处的 op 与 ready.ops 报出去的不一致：新增分支要一起登记，删分支要一起摘掉");
    }

    /** 当前分发的六个常量名（小写）；上面那条断言用它把"分支集合本身没悄悄变"钉住。 */
    private static Set<String> expectedBranchCensus() {
        return new LinkedHashSet<String>(java.util.Arrays.asList(
                "ping", "subscribe", "unsubscribe", "publish", "auth"));
    }

    /** surefire 的工作目录是模块根；从聚合根或 IDE 跑时退到带模块名的那条路径。 */
    private static File handlerSource() {
        File direct = new File(HANDLER_SOURCE);
        if (direct.isFile()) {
            return direct;
        }
        File fromRoot = new File("z-msg-ws/" + HANDLER_SOURCE);
        if (fromRoot.isFile()) {
            return fromRoot;
        }
        fail("找不到 handler 源码，这一例只会数出空集合（当前目录: "
                + new File("").getAbsolutePath() + "）");
        return null;
    }
}
