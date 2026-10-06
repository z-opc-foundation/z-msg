package com.zifang.z.msg.core.channel;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zifang.z.msg.api.ChannelSender;
import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.core.config.MessageProperties;
import com.zifang.z.msg.core.domain.entity.MsgDeliveryLogDO;
import com.zifang.z.msg.core.domain.mapper.MsgDeliveryLogMapper;
import com.zifang.z.msg.core.domain.service.MsgUserPreferenceService;
import com.zifang.z.msg.core.ratelimit.ChannelRateLimiter;
import com.zifang.z.msg.core.sender.SenderRegistry;
import com.zifang.z.msg.core.template.MessageTemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>跨节点</b>出站幂等：{@code z_msg_delivery_log.idempotency_key} + {@code uk_channel_biz_dedup}。
 *
 * <p>本 JVM 那张 map 的落位时机在 {@link ChannelRouterIdempotencyTest} 里；这里钉的是另一半 ——
 * 多实例部署时同一份消息落到不同节点只能发一次。仲裁点是发送<b>前</b>插的那一行占位：
 * 撞唯一索引就说明别的节点已经抢到了。</p>
 *
 * <p>判据全部落在<b>具体内容</b>上（谁被调用、哪一行被写、UPDATE 语句里有没有把幂等位清回
 * NULL），不落在"调过某个方法"这种自证不了的计数上。DDL 侧的行为（唯一索引真的拒绝重复、
 * 允许多个 NULL）由 {@code DeliveryLogIdemIndexH2Test} 在真 H2 上证明。</p>
 */
class ChannelRouterIdemSlotTest {

    private static final class CountingSender implements ChannelSender {
        private final String channel;
        private final String provider;
        private final boolean succeed;
        int calls;

        CountingSender(String channel, String provider, boolean succeed) {
            this.channel = channel;
            this.provider = provider;
            this.succeed = succeed;
        }

        @Override
        public String channel() {
            return channel;
        }

        @Override
        public String provider() {
            return provider;
        }

        @Override
        public MessageSendResult send(Message message) {
            calls++;
            return succeed
                    ? MessageSendResult.ok(provider, "OK-" + calls)
                    : MessageSendResult.fail(provider, "PROVIDER_DOWN", "模拟厂商故障");
        }

        @Override
        public boolean ready() {
            return true;
        }
    }

    private MessageProperties props;
    private MsgDeliveryLogMapper mapper;

    @BeforeEach
    void setUp() {
        props = new MessageProperties();
        props.getRetry().setMaxAttempts(0);
        props.getRateLimit().setEnabled(false);
        mapper = Mockito.mock(MsgDeliveryLogMapper.class);
    }

    private void channelCfg(String channelName, String provider, String... fallbacks) {
        MessageProperties.Channel ch = new MessageProperties.Channel();
        ch.setProvider(provider);
        ch.setFallbackChannels(new ArrayList<String>(Arrays.asList(fallbacks)));
        props.getChannel().put(channelName, ch);
    }

    private ChannelRouter newRouter(ChannelSender... senders) {
        SenderRegistry registry = new SenderRegistry(props,
                new ArrayList<ChannelSender>(Arrays.asList(senders)), new ArrayList<>(), new ArrayList<>());
        ChannelRouter router = new ChannelRouter();
        ReflectionTestUtils.setField(router, "senderRegistry", registry);
        ReflectionTestUtils.setField(router, "properties", props);
        ReflectionTestUtils.setField(router, "rateLimiter", new ChannelRateLimiter(1000));
        ReflectionTestUtils.setField(router, "templateEngine", Mockito.mock(MessageTemplateEngine.class));
        ReflectionTestUtils.setField(router, "preferenceService", Mockito.mock(MsgUserPreferenceService.class));
        ReflectionTestUtils.setField(router, "deliveryLogMapper", mapper);
        return router;
    }

    private static Message msg(String channel, String idemKey) {
        Map<String, String> params = new HashMap<>();
        params.put("receiver:push_apns", "device-1");
        return Message.builder()
                .channel(channel)
                .bizType("ORDER_PAID")
                .subject("支付成功")
                .content("您的订单已支付")
                .idempotencyKey(idemKey)
                .params(params)
                .build();
    }

    private void claimAlreadyTaken() {
        Mockito.doThrow(new DuplicateKeyException("uk_channel_biz_dedup"))
                .when(mapper).insert(Mockito.any(MsgDeliveryLogDO.class));
    }

    private void claimOk() {
        Mockito.when(mapper.update(Mockito.isNull(), Mockito.any()))
                .thenReturn(1);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<MsgDeliveryLogDO> capturedUpdate() {
        Wrapper<MsgDeliveryLogDO> found = null;
        for (Invocation inv : Mockito.mockingDetails(mapper).getInvocations()) {
            if ("update".equals(inv.getMethod().getName())) {
                found = (Wrapper<MsgDeliveryLogDO>) inv.getArgument(1);
            }
        }
        assertNotNull(found, "mapper 一次 update 都没被调用：占位行没被就地转正");
        return found;
    }

    /**
     * 按调用顺序收集所有 insert 进来的行。
     * 刻意不用 {@code verify(...atLeastOnce())} 取参数：verify 会把已核对的调用标记掉，
     * 同一个 mock 上第二次 verify 只能拿到之后那几条，循环里就会取错行。
     */
    private List<MsgDeliveryLogDO> capturedInserts() {
        List<MsgDeliveryLogDO> rows = new ArrayList<MsgDeliveryLogDO>();
        for (Invocation inv : Mockito.mockingDetails(mapper).getInvocations()) {
            if ("insert".equals(inv.getMethod().getName())) {
                rows.add((MsgDeliveryLogDO) inv.getArgument(0));
            }
        }
        return rows;
    }

    // ==================== 主测 ====================

    /**
     * 别的节点已经抢到幂等位 → 本节点**一个字都不发**。
     * 这条判据的对照在下面 {@link #送达时占位行就地转正不再另插一行()}：
     * 那里同样的 mapper 不会抛异常，于是消息发得出去、UPDATE 也真的发生了。
     */
    @Test
    void 别的节点已占位时本节点不调厂商() {
        CountingSender primary = new CountingSender(Channels.PUSH_FCM, "ok-provider", true);
        channelCfg("push_fcm", "ok-provider");
        ChannelRouter router = newRouter(primary);
        claimAlreadyTaken();

        MessageSendResult r = router.route(msg(Channels.PUSH_FCM, "order-1"));

        assertEquals("DUPLICATED", r.getErrorCode(),
                "撞 uk_channel_biz_dedup 说明别处已经发过，这次应当判幂等命中");
        assertEquals(0, primary.calls,
                "已经占住的情况下还去调厂商，多实例部署时用户就会收到两条");
    }

    /**
     * 送达路径：占位行<b>就地转正</b>，全程只 INSERT 一次。
     *
     * <p>"只插一次"是这里的主断言：另插一行就意味着同一份消息在投递日志里有两行，
     * 而且多出来的那一行会和占位行抢同一个唯一索引。</p>
     */
    @Test
    void 送达时占位行就地转正不再另插一行() {
        CountingSender primary = new CountingSender(Channels.PUSH_FCM, "ok-provider", true);
        channelCfg("push_fcm", "ok-provider");
        ChannelRouter router = newRouter(primary);
        claimOk();

        assertTrue(router.route(msg(Channels.PUSH_FCM, "order-1")).isSuccess());

        List<MsgDeliveryLogDO> inserts = capturedInserts();
        assertEquals(1, inserts.size(),
                "占位行应当就是最终那一行：送达时 UPDATE 它，不该再插第二行");
        MsgDeliveryLogDO placeholder = inserts.get(0);
        assertEquals(0, placeholder.getStatus().intValue(),
                "插入时这行还只是占位，状态应是 pending");
        assertNotNull(placeholder.getIdempotencyKey(), "占位行必须带着去重令牌，否则唯一索引拦不住任何人");

        assertEquals(1, primary.calls, "前提：消息确实发出去了");
        UpdateWrapperProbe probe = UpdateWrapperProbe.of(capturedUpdate());
        assertTrue(!probe.setClause().contains("idempotency_key"),
                "送达的这一行要保留幂等位（否则重放会再发一遍），实际 SET: " + probe.setClause());
        assertTrue(probe.whereClause().contains("idempotency_key"),
                "UPDATE 必须按去重令牌定位占位行，实际 WHERE: " + probe.whereClause());
    }

    /**
     * 没送达：占位行留下失败记录，但把幂等位<b>还回去</b>。
     *
     * <p>与上面那条是对称的一对：不清回去，一次厂商抖动就把这条消息永久钉死，
     * 运维重放也发不出去（这正是 {@code markIssued} 当初被修成"只送达才占位"的同一个道理，
     * 跨节点这一层曾经完全没有这道闸）。</p>
     */
    @Test
    void 厂商故障时占位行把幂等位还回去() {
        CountingSender primary = new CountingSender(Channels.PUSH_FCM, "down", false);
        channelCfg("push_fcm", "down");
        ChannelRouter router = newRouter(primary);
        claimOk();

        assertTrue(!router.route(msg(Channels.PUSH_FCM, "order-1")).isSuccess());

        UpdateWrapperProbe probe = UpdateWrapperProbe.of(capturedUpdate());
        assertTrue(probe.setClause().contains("idempotency_key = null"),
                "没送达就必须把幂等位清回 NULL，否则这条消息再也发不出去；实际 SET: " + probe.setClause());
    }

    /** 占位这一步坏了不能连带把消息也发不出去 —— 幂等是增强项。 */
    @Test
    void 占位这一步故障不拦投递() {
        CountingSender primary = new CountingSender(Channels.PUSH_FCM, "ok-provider", true);
        channelCfg("push_fcm", "ok-provider");
        ChannelRouter router = newRouter(primary);
        // 只在占位那一次坏：第一次 insert 抛"库还没升级"那类故障，之后的日志行照常写
        Mockito.doThrow(new IllegalStateException("Column 'idempotency_key' not found"))
                .doReturn(1)
                .when(mapper).insert(Mockito.any(MsgDeliveryLogDO.class));
        Mockito.when(mapper.update(Mockito.isNull(), Mockito.any())).thenReturn(1);

        assertTrue(router.route(msg(Channels.PUSH_FCM, "order-1")).isSuccess(),
                "占位不可用时应当照常投递，只是退回'仅本机去重'");

        assertEquals(1, primary.calls, "前提：消息确实发出去了");
        List<MsgDeliveryLogDO> inserts = capturedInserts();
        assertEquals(2, inserts.size(), "占位失败后应当只剩一条最终日志行");
        assertNull(inserts.get(1).getIdempotencyKey(),
                "占位没拿到时，最终行不能自己去抢一个去重位（会撞上别的节点已经占住的位）");
    }

    /** 降级成功：幂等位归"真正送达的那一行"，也就是降级通道那一行。 */
    @Test
    void 降级成功时幂等位归降级那一行() {
        CountingSender primary = new CountingSender(Channels.PUSH_FCM, "down", false);
        CountingSender backup = new CountingSender(Channels.PUSH_APNS, "ok-provider", true);
        channelCfg("push_fcm", "down", Channels.PUSH_APNS);
        channelCfg("push_apns", "ok-provider");
        ChannelRouter router = newRouter(primary, backup);
        claimOk();

        assertTrue(router.route(msg(Channels.PUSH_FCM, "order-1")).isSuccess());

        assertEquals(1, backup.calls, "前提：降级链路是通的");
        List<MsgDeliveryLogDO> inserts = capturedInserts();
        assertEquals(2, inserts.size(), "应当只有主通道的占位行 + 降级那一行");

        MsgDeliveryLogDO fallbackRow = null;
        for (MsgDeliveryLogDO row : inserts) {
            if (Channels.PUSH_APNS.equals(row.getChannel())) {
                fallbackRow = row;
            }
        }
        assertNotNull(fallbackRow, "降级那一行没有落库：实际落了 "
                + Arrays.toString(inserts.toArray()));
        assertNotNull(fallbackRow.getIdempotencyKey(),
                "真正送达的是降级这一行，幂等位必须跟着它走，否则重放会再降级一次");
        assertEquals(1, fallbackRow.getStatus().intValue(), "降级成功应当记成 success");

        UpdateWrapperProbe probe = UpdateWrapperProbe.of(capturedUpdate());
        assertTrue(probe.setClause().contains("idempotency_key = null"),
                "主通道那行没送达，得把幂等位让给降级那一行；实际 SET: " + probe.setClause());
    }

    /**
     * bizType 为空时不占位。
     *
     * <p>{@code DeliveryLogIdemIndexH2Test#bizType为空的行不参与去重} 证明了那一列为空时整行不参与
     * 唯一索引。也就是说占位在这种消息上<b>根本拦不住任何人</b>，只会白白多插一行 pending。
     * 这条判据的用例数据必须真的把 bizType 留空，否则它对这道闸门零区分力。</p>
     */
    @Test
    void bizType为空时不占位() {
        CountingSender primary = new CountingSender(Channels.PUSH_FCM, "ok-provider", true);
        channelCfg("push_fcm", "ok-provider");
        ChannelRouter router = newRouter(primary);
        claimOk();
        Message blankBiz = Message.builder()
                .channel(Channels.PUSH_FCM)
                .bizType("  ")
                .subject("支付成功")
                .content("您的订单已支付")
                .idempotencyKey("order-7")
                .build();

        assertTrue(router.route(blankBiz).isSuccess());

        assertEquals(1, primary.calls, "前提：消息确实发出去了");
        List<MsgDeliveryLogDO> inserts = capturedInserts();
        assertTrue(!inserts.isEmpty(), "前提：投递日志行照常写（这条只管占位，不管日志）");
        for (MsgDeliveryLogDO row : inserts) {
            assertNull(row.getIdempotencyKey(),
                    "bizType 为空时这一行不参与唯一索引，占位白占，只该写一条不带幂等位的日志行；"
                            + "实际写了幂等位: " + row.getIdempotencyKey());
        }
        Mockito.verify(mapper, Mockito.never()).update(Mockito.isNull(), Mockito.any());
    }

    /**
     * 超长令牌折叠到列宽以内，且两个不同的超长接收方不会折成同一个令牌。
     *
     * <p>MySQL 非严格模式会把超长 VARCHAR 静默截断 —— 那样两个不同的消息会折成同一个幂等位，
     * 第二条被误判成"已发过"而丢掉。所以截断必须带全文哈希，不能只留前缀。</p>
     */
    @Test
    void 超长令牌折叠到列宽内且不撞车() {
        StringBuilder longReceiver = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            longReceiver.append("https://push.example.com/hook/");
        }
        String first = longReceiver.toString();
        String second = first + "b";

        ChannelRouter router = newRouter(
                new CountingSender(Channels.PUSH_FCM, "ok-provider", true));
        channelCfg("push_fcm", "ok-provider");
        claimOk();
        List<String> tokens = new ArrayList<>();
        for (String receiver : new String[]{first, second}) {
            Message m = Message.builder()
                    .channel(Channels.PUSH_FCM)
                    .bizType("ORDER_PAID")
                    .subject("支付成功")
                    .content("您的订单已支付")
                    .idempotencyKey("order-8")
                    .receiver(receiver)
                    .build();
            assertTrue(router.route(m).isSuccess());
            List<MsgDeliveryLogDO> inserts = capturedInserts();
            tokens.add(inserts.get(inserts.size() - 1).getIdempotencyKey());
        }

        assertTrue(tokens.get(0).length() <= 191,
                "令牌超了 VARCHAR(191)：严格模式直接报错，非严格模式静默截断。实际长度 " + tokens.get(0).length());
        assertTrue(!tokens.get(0).equals(tokens.get(1)),
                "两个只差最后一个字符的接收方折成了同一个幂等位 —— 纯截断会悄悄丢掉其中一条消息");
    }

    // ==================== 辅助 ====================
    /** 把 wrapper 上那两个子句抠出来，避免测试里散落一堆 instanceof。 */
    private static final class UpdateWrapperProbe {
        private final String set;
        private final String where;

        private UpdateWrapperProbe(String set, String where) {
            this.set = set;
            this.where = where;
        }

        static UpdateWrapperProbe of(Wrapper<MsgDeliveryLogDO> wrapper) {
            String set = "";
            String where = "";
            if (wrapper instanceof com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper) {
                com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<?> uw =
                        (com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<?>) wrapper;
                set = String.valueOf(uw.getSqlSet());
                where = String.valueOf(uw.getSqlSegment());
            }
            return new UpdateWrapperProbe(set, where);
        }

        String setClause() {
            return set;
        }

        String whereClause() {
            return where;
        }
    }
}