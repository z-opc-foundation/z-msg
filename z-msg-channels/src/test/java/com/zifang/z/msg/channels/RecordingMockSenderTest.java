package com.zifang.z.msg.channels;

import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.api.Message;
import com.zifang.z.msg.api.MessageSendResult;
import com.zifang.z.msg.channels.provider.RecordingMockSender;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 录制 mock：isMock()=true（契约第二条：投递日志记 status=3 而非成功），请求进内存。
 */
class RecordingMockSenderTest {

    @Test
    void recordsMessagesAndFlagsItselfAsMock() {
        RecordingMockSender mock = new RecordingMockSender(Channels.PUSH_JPUSH);
        assertTrue(mock.isMock());
        assertEquals("mock", mock.provider());
        assertEquals(Channels.PUSH_JPUSH, mock.channel());
        assertTrue(mock.ready());

        MessageSendResult r = mock.send(Message.builder().channel(Channels.PUSH_JPUSH)
                .receiver("device-1").bizType("NEWS").subject("标题").content("正文").build());

        assertTrue(r.isSuccess());
        assertEquals("mock", r.getProvider());
        assertEquals(1, mock.recorded().size());
        RecordingMockSender.Recorded rec = mock.recorded().get(0);
        assertEquals("device-1", rec.receiver);
        assertEquals("标题", rec.subject);
        assertEquals("正文", rec.content);

        mock.clear();
        assertEquals(0, mock.recorded().size());
    }

    @Test
    void robotProviderWithMockFlagRecordsInsteadOfSending() {
        // z-msg.channel.<ch>.mock=true：provider 选择不变，但停掉真实外发、翻转 isMock，
        // 并在 provider 实例上留下可查的请求记录（本地开发/example 用法）
        com.zifang.z.msg.channels.config.ChannelsProperties props =
                new com.zifang.z.msg.channels.config.ChannelsProperties();
        com.zifang.z.msg.channels.config.ChannelsProperties.ChannelCfg cfg =
                new com.zifang.z.msg.channels.config.ChannelsProperties.ChannelCfg();
        cfg.setMock(true); // 故意不配 token：ready()==true 只能来自 mock 短路
        props.put(Channels.IM_WECOM, cfg);
        com.zifang.z.msg.channels.provider.WecomRobotSender sender =
                new com.zifang.z.msg.channels.provider.WecomRobotSender(
                        props, new com.zifang.z.msg.channels.http.SimpleHttpClient());

        assertTrue(sender.ready(), "mock 模式不需要凭据也 ready");
        assertTrue(sender.isMock(), "mock=true 时 isMock 必须翻转为 true（契约第二条）");
        MessageSendResult r = sender.send(Message.builder().channel(Channels.IM_WECOM)
                .bizType("X").subject("s").content("c").build());
        assertTrue(r.isSuccess());
        assertEquals(1, sender.recorded().size());
        assertEquals("c", sender.recorded().get(0).content);
    }
}
