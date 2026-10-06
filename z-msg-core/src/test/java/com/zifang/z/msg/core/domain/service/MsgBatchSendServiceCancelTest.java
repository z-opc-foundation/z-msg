package com.zifang.z.msg.core.domain.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zifang.z.msg.api.ChannelSender;
import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.FanOutResult;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.core.channel.ChannelRouter;
import com.zifang.z.msg.core.config.MessageProperties;
import com.zifang.z.msg.core.domain.entity.MsgBatchTaskDO;
import com.zifang.z.msg.core.domain.mapper.MsgBatchTaskMapper;
import com.zifang.z.msg.core.ratelimit.ChannelRateLimiter;
import com.zifang.z.msg.core.sender.SenderRegistry;
import com.zifang.z.msg.core.template.MessageTemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 批量任务的取消请求**不许被执行循环覆盖掉**。
 *
 * <p>修复前有三处合谋导致取消丢失，而单独看每一处都像正常的：</p>
 * <ol>
 *   <li>{@code cancel} 只对 PENDING 落库 {@code status=5}，RUNNING 只往内存集合塞个标记
 *       ⇒ 注释里说的「跨进程取消靠库里那一列」对<b>正在跑的任务</b>根本不成立；</li>
 *   <li>执行循环每处理完一条就整行 {@code updateById(task)}，而那是<b>全字段更新</b>
 *       ⇒ 快照里的 {@code status=RUNNING} 会把 {@code cancel} 刚落的 5 改回 1；</li>
 *   <li>循环结束后<b>无条件</b>设置最终状态 ⇒ 最后一条处理期间点的取消被整个覆盖，
 *       API 返回了 {@code true} 而任务照样报 DONE，库里连一点取消痕迹都没有。</li>
 * </ol>
 */
class MsgBatchSendServiceCancelTest {

    private MsgBatchTaskMapper mapper;
    private MsgBatchSendService svc;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(MsgBatchTaskMapper.class);
        MessageProperties props = new MessageProperties();
        props.getRetry().setMaxAttempts(0);
        props.getRateLimit().setEnabled(false);
        MessageProperties.Channel ch = new MessageProperties.Channel();
        ch.setProvider("ok-provider");
        ch.setFallbackChannels(new ArrayList<String>());
        props.getChannel().put("push_fcm", ch);

        sender = new CountingSender();
        SenderRegistry registry = new SenderRegistry(props,
                new ArrayList<ChannelSender>(Arrays.asList(sender)),
                new ArrayList<>(), new ArrayList<>());
        ChannelRouter router = new ChannelRouter();
        ReflectionTestUtils.setField(router, "senderRegistry", registry);
        ReflectionTestUtils.setField(router, "properties", props);
        ReflectionTestUtils.setField(router, "rateLimiter", new ChannelRateLimiter(1000));
        ReflectionTestUtils.setField(router, "templateEngine", Mockito.mock(MessageTemplateEngine.class));
        ReflectionTestUtils.setField(router, "preferenceService",
                Mockito.mock(com.zifang.z.msg.core.domain.service.MsgUserPreferenceService.class));
        ReflectionTestUtils.setField(router, "deliveryLogMapper",
                Mockito.mock(com.zifang.z.msg.core.domain.mapper.MsgDeliveryLogMapper.class));

        svc = new MsgBatchSendService();
        ReflectionTestUtils.setField(svc, "batchTaskMapper", mapper);
        ReflectionTestUtils.setField(svc, "channelRouter", router);
        ReflectionTestUtils.setField(svc, "templateEngine", Mockito.mock(MessageTemplateEngine.class));
    }

    /** {@code onSend} 用来把"取消"塞到<b>正在处理某一条</b>的那个时间点上去。 */
    private static final class CountingSender implements ChannelSender {
        int calls;
        Runnable onSend;

        @Override
        public String channel() {
            return Channels.PUSH_FCM;
        }

        @Override
        public String provider() {
            return "ok-provider";
        }

        @Override
        public MessageSendResult send(Message message) {
            calls++;
            if (onSend != null) {
                onSend.run();
            }
            return MessageSendResult.ok("ok-provider", "OK-" + calls);
        }

        @Override
        public boolean ready() {
            return true;
        }
    }

    private CountingSender sender;

    private MsgBatchTaskDO task(int status) {
        MsgBatchTaskDO t = new MsgBatchTaskDO();
        t.setId(1L);
        t.setBizType("ORDER_PAID");
        t.setChannel("push_fcm");
        t.setStatus(status);
        t.setTotalCount(1);
        t.setSuccessCount(0);
        t.setFailedCount(0);
        t.setReceiversJson("[{\"receiver\":\"device-1\"}]");
        t.setParamsJson("{}");
        return t;
    }

    @SuppressWarnings("unchecked")
    private List<Wrapper<MsgBatchTaskDO>> capturedUpdates() {
        List<Wrapper<MsgBatchTaskDO>> out = new ArrayList<Wrapper<MsgBatchTaskDO>>();
        for (org.mockito.invocation.Invocation inv : Mockito.mockingDetails(mapper).getInvocations()) {
            if ("update".equals(inv.getMethod().getName())) {
                out.add((Wrapper<MsgBatchTaskDO>) inv.getArgument(1));
            }
        }
        return out;
    }

    private String sqlOf(Wrapper<MsgBatchTaskDO> w) {
        if (w instanceof com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper) {
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<?> uw =
                    (com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<?>) w;
            return String.valueOf(uw.getSqlSet()) + " | WHERE " + String.valueOf(uw.getSqlSegment());
        }
        return "<不是 UpdateWrapper: " + w.getClass().getName() + ">";
    }

    // ==================================================================
    // 1. cancel 对 RUNNING 也必须落库
    // ==================================================================

    @Test
    void cancelOnRunningTaskPersistsStatus() {
        Mockito.when(mapper.selectById(1L)).thenReturn(task(MsgBatchSendService.STATUS_RUNNING));

        assertTrue(svc.cancel(1L), "运行中的任务应当接受取消请求");

        ArgumentCaptor<MsgBatchTaskDO> cap = ArgumentCaptor.forClass(MsgBatchTaskDO.class);
        Mockito.verify(mapper).updateById(cap.capture());
        assertEquals(Integer.valueOf(MsgBatchSendService.STATUS_CANCELLED), cap.getValue().getStatus(),
                "cancel 对 RUNNING 也必须把 status=5 落库 —— 只写内存集合的话，"
                        + "跨进程那条（isCancelRequested 里的查库）对运行中的任务永远是 false");
    }

    @Test
    void cancelOnPendingTaskStillPersistsAndStampsFinishTime() {
        Mockito.when(mapper.selectById(1L)).thenReturn(task(MsgBatchSendService.STATUS_PENDING));

        assertTrue(svc.cancel(1L));
        ArgumentCaptor<MsgBatchTaskDO> cap = ArgumentCaptor.forClass(MsgBatchTaskDO.class);
        Mockito.verify(mapper).updateById(cap.capture());
        assertEquals(Integer.valueOf(MsgBatchSendService.STATUS_CANCELLED), cap.getValue().getStatus());
        assertNotNull(cap.getValue().getFinishedTime(),
                "还没开始跑的任务当场就结束了，finishedTime 必须写（对照组：别把 PENDING 的行为改坏）");
    }

    @Test
    void cancelOnTerminalTaskReturnsFalse() {
        for (int terminal : new int[]{MsgBatchSendService.STATUS_DONE,
                MsgBatchSendService.STATUS_FAILED, MsgBatchSendService.STATUS_PARTIAL}) {
            MsgBatchTaskMapper m = Mockito.mock(MsgBatchTaskMapper.class);
            Mockito.when(m.selectById(1L)).thenReturn(task(terminal));
            MsgBatchSendService s = new MsgBatchSendService();
            ReflectionTestUtils.setField(s, "batchTaskMapper", m);

            assertFalse(s.cancel(1L), "终态任务（status=" + terminal + "）不该接受取消");
            Mockito.verify(m, Mockito.never()).updateById(Mockito.any(MsgBatchTaskDO.class));
        }
    }

    // ==================================================================
    // 2. 执行循环里的进度更新不得碰 status
    // ==================================================================

    @Test
    void progressUpdateNeverTouchesStatus() {
        Mockito.when(mapper.selectById(1L)).thenReturn(task(MsgBatchSendService.STATUS_PENDING));
        Mockito.when(mapper.update(Mockito.isNull(), Mockito.any())).thenReturn(1);

        svc.executeAsync(1L);

        List<Wrapper<MsgBatchTaskDO>> updates = capturedUpdates();
        assertFalse(updates.isEmpty(), "前提：执行循环确实更新了进度");
        for (Wrapper<MsgBatchTaskDO> w : updates) {
            String sql = sqlOf(w);
            // 收尾那次会 set status（带 eq(status, RUNNING) 条件），进度那次不许
            if (!sql.contains("finished_time")) {
                assertFalse(sql.contains("status"),
                        "进度更新里出现了 status —— 整行 updateById 会把内存快照的 RUNNING 写回去，"
                                + "把 cancel 刚落库的 5 抹掉。实际 SQL: " + sql);
            }
        }
    }

    // ==================================================================
    // 3. 收尾写终态必须是条件更新，且被取消时不得覆盖
    // ==================================================================

    @Test
    void finalStatusUpdateIsGuardedByCurrentStatus() {
        Mockito.when(mapper.selectById(1L)).thenReturn(task(MsgBatchSendService.STATUS_PENDING));
        Mockito.when(mapper.update(Mockito.isNull(), Mockito.any())).thenReturn(1);

        svc.executeAsync(1L);

        String finalSql = null;
        for (Wrapper<MsgBatchTaskDO> w : capturedUpdates()) {
            String sql = sqlOf(w);
            if (sql.contains("finished_time")) {
                finalSql = sql;
            }
        }
        assertNotNull(finalSql, "前提：收尾确实写了终态");
        assertTrue(finalSql.contains("status"),
                "收尾必须设置 status，实际 SQL: " + finalSql);
        // 断言 WHERE 段的内容，不去匹配 "=?" 这种字面量：
        // MyBatis-Plus 生成的是 "id = #{...}"（等号与占位符之间有空格），
        // 照着某个格式去拼字符串，改一次 MP 的渲染就会把判据自己判红。
        String where = finalSql.substring(finalSql.indexOf("WHERE"));
        assertTrue(where.contains("id") && where.contains("status"),
                "收尾必须是条件更新：WHERE 里要同时限定 id 与当前 status，"
                        + "否则「isCancelRequested 查完、这里还没写」这个窗口里进来的 cancel 会被覆盖。"
                        + "实际 WHERE: " + where);
    }

    /**
     * 端到端：取消发生在<b>最后一条正在处理的时候</b>，任务必须报 CANCELLED 而不是 DONE。
     *
     * <p>这正是"API 撒谎"的那一格：{@code cancel} 返回 {@code true}，
     * 任务却跑完并报 DONE，库里没有任何取消痕迹。</p>
     *
     * <p><b>用例数据必须让取消真的落在"处理中"</b>：只有 1 条 receiver，
     * 且取消是在 {@code sender.send()} 内部触发的 —— 此时循环开头的
     * {@code isCancelRequested} 检查<b>已经过去</b>了，所以只有收尾那道闸能拦住它。
     * 若把取消放在循环开始前，那条路径修复前后都通，这条判据就成了废的。</p>
     */
    @Test
    void cancelWhileLastItemIsBeingSentBeatsCompletion() {
        Mockito.when(mapper.selectById(1L)).thenReturn(task(MsgBatchSendService.STATUS_PENDING));
        Mockito.when(mapper.update(Mockito.isNull(), Mockito.any())).thenReturn(1);
        sender.onSend = () -> svc.cancel(1L);

        svc.executeAsync(1L);

        assertEquals(1, sender.calls, "前提：确实只处理了一条");

        ArgumentCaptor<MsgBatchTaskDO> cap = ArgumentCaptor.forClass(MsgBatchTaskDO.class);
        Mockito.verify(mapper, Mockito.atLeastOnce()).updateById(cap.capture());
        MsgBatchTaskDO last = cap.getAllValues().get(cap.getAllValues().size() - 1);
        assertEquals(Integer.valueOf(MsgBatchSendService.STATUS_CANCELLED), last.getStatus(),
                "取消发生在最后一条处理期间，循环开头的检查已经过去了；"
                        + "收尾必须先看 isCancelRequested 再写终态，否则就是"
                        + "「cancel 返回 true、任务却报 DONE」");
        assertNotNull(last.getFinishedTime(), "取消收尾要写 finishedTime");
    }

    @Test
    void normalTaskStillReportsDone() {
        Mockito.when(mapper.selectById(1L)).thenReturn(task(MsgBatchSendService.STATUS_PENDING));
        Mockito.when(mapper.update(Mockito.isNull(), Mockito.any())).thenReturn(1);

        svc.executeAsync(1L);

        String finalSql = null;
        for (Wrapper<MsgBatchTaskDO> w : capturedUpdates()) {
            String sql = sqlOf(w);
            if (sql.contains("finished_time")) {
                finalSql = sql;
            }
        }
        assertNotNull(finalSql, "没被取消的任务必须正常收尾");
        Map<String, Object> params = new HashMap<>();
        assertTrue(finalSql.contains("status"), "实际 SQL: " + finalSql);
        assertEquals(0, params.size());
    }

    /**
     * 同一条不变式的<b>第二条写库路径</b>：进度更新不得走整行 {@code updateById}。
     *
     * <p>{@link #progressUpdateNeverTouchesStatus} 只看 {@code update(null, wrapper)} 那条路的
     * SQL 形状，把进度更新换回 {@code updateById(task)} 时它就<b>一条也收集不到</b>，
     * 于是整套判据全绿（首跑时正是这样）。</p>
     *
     * <p>所以这里按<b>调用次数</b>钉：执行一条 receiver 的任务期间，
     * {@code updateById} 应当只被调 <b>1 次</b> —— 那是循环开始前标记 RUNNING 的那次。
     * 进度更新再来一次，就说明它整行写回了 {@code status=RUNNING}，
     * 会把 {@code cancel()} 刚落库的 5 覆盖掉。</p>
     */
    @Test
    void progressUpdateDoesNotGoThroughFullRowUpdate() {
        Mockito.when(mapper.selectById(1L)).thenReturn(task(MsgBatchSendService.STATUS_PENDING));
        Mockito.when(mapper.update(Mockito.isNull(), Mockito.any())).thenReturn(1);

        svc.executeAsync(1L);

        int fullRowUpdates = 0;
        for (org.mockito.invocation.Invocation inv : Mockito.mockingDetails(mapper).getInvocations()) {
            if ("updateById".equals(inv.getMethod().getName())) {
                fullRowUpdates++;
            }
        }
        assertEquals(1, fullRowUpdates,
                "执行一条 receiver 的任务期间只应有开头标记 RUNNING 那一次 updateById；"
                        + "多出来的就是进度更新整行写回 status=RUNNING，"
                        + "会覆盖 cancel 刚落库的 status=5");
    }
}
