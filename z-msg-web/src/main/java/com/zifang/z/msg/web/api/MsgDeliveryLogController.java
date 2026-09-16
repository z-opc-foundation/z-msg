package com.zifang.z.msg.web.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.core.domain.entity.MsgDeliveryLogDO;
import com.zifang.z.msg.core.domain.service.MsgDeliveryLogService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 投递日志 API (Phase 2)
 * <p>
 * POST /api/msg/delivery/list   分页列表
 * POST /api/msg/delivery/stats   统计
 */
@RestController
@RequestMapping("/api/msg/delivery")
public class MsgDeliveryLogController {

    @Resource
    private MsgDeliveryLogService deliveryLogService;

    @PostMapping("/list")
    public Result<IPage<MsgDeliveryLogDO>> list(@RequestBody Map<String, Object> body) {
        int page = body.containsKey("page") ? ((Number) body.get("page")).intValue() : 1;
        int size = body.containsKey("size") ? ((Number) body.get("size")).intValue() : 20;
        String bizType = (String) body.getOrDefault("bizType", "");
        String channel = (String) body.getOrDefault("channel", "");
        Integer status = body.containsKey("status") ? ((Number) body.get("status")).intValue() : null;
        Long userId = body.containsKey("userId") ? ((Number) body.get("userId")).longValue() : null;
        return Result.success(deliveryLogService.page(page, size, bizType, channel, status, userId));
    }

    @PostMapping("/stats")
    public Result<Map<String, Long>> stats(@RequestBody Map<String, Object> body) {
        String channel = (String) body.getOrDefault("channel", "");
        Map<String, Long> stats = new HashMap<>();
        stats.put("success", deliveryLogService.countSuccess(null, null, channel));
        stats.put("failed", deliveryLogService.countFailed(null, null, channel));
        stats.put("total", stats.get("success") + stats.get("failed"));
        return Result.success(stats);
    }
}
