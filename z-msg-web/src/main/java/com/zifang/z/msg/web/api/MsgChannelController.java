package com.zifang.z.msg.web.api;

import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.api.ChannelSender;
import com.zifang.z.msg.api.Channels;
import com.zifang.z.msg.core.config.MessageProperties;
import com.zifang.z.msg.core.sender.SenderRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通道自省：这台进程里每条通道实际挂着谁。
 * <p>
 * 存在的意义是" advertised 的能力必须真兑现"。{@code real=false} 的行意思是
 * 这条通道当前只有 mock 兜底 —— 发出去会返回成功，但没有任何东西真的到达对方。
 * 没有这一层，配置写错（provider 名拼错、厂商开关没打开）只有在用户说"我没收到短信"时才暴露。
 */
@RestController
@RequestMapping("/api/msg/channel")
public class MsgChannelController {

    @Resource
    private SenderRegistry senderRegistry;
    @Resource
    private MessageProperties properties;

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        Set<String> all = new LinkedHashSet<String>(Channels.ALL);
        all.addAll(senderRegistry.channels());

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (String channel : all) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("channel", channel);
            row.put("providers", senderRegistry.providers(channel));
            row.put("activeProvider", senderRegistry.activeProvider(channel));
            row.put("configuredProvider", properties.channelCfg(channel).getProvider());
            boolean real = senderRegistry.hasRealSender(channel);
            row.put("real", real);
            row.put("enabled", properties.channelCfg(channel).isEnabled());
            row.put("fallbackChannels", properties.channelCfg(channel).getFallbackChannels());
            ChannelSender sender = real ? senderRegistry.pick(channel) : null;
            row.put("ready", sender == null ? Boolean.FALSE : Boolean.valueOf(sender.ready()));
            rows.add(row);
        }
        return Result.success(rows);
    }

    /**
     * 单条通道的明细，包含 mock 兜底时该看的那句诊断。
     */
    @GetMapping("/detail")
    public Result<Map<String, Object>> detail(@RequestParam String channel) {
        String normalized = Channels.normalize(channel);
        if (normalized == null || !Channels.isKnown(normalized)) {
            return Result.<Map<String, Object>>fail("未知通道: " + channel).code(400);
        }
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("channel", normalized);
        row.put("providers", senderRegistry.providers(normalized));
        row.put("activeProvider", senderRegistry.activeProvider(normalized));
        row.put("real", senderRegistry.hasRealSender(normalized));
        ChannelSender sender = senderRegistry.pick(normalized);
        row.put("selected", sender.getClass().getSimpleName());
        row.put("selectedIsMock", sender.isMock());
        return Result.success(row);
    }
}
