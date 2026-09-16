package com.zifang.z.msg.web.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.core.domain.entity.MsgBatchTaskDO;
import com.zifang.z.msg.core.domain.service.MsgBatchSendService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量发送 API (Phase 2.4)
 * <p>
 * POST /api/msg/batch/submit        提交批量任务 (异步执行)
 * GET  /api/msg/batch/{id}          查询进度
 * POST /api/msg/batch/list          分页任务列表
 * POST /api/msg/batch/{id}/cancel   取消任务
 */
@RestController
@RequestMapping("/api/msg/batch")
public class MsgBatchController {

    @Resource
    private MsgBatchSendService batchSendService;

    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
        String bizType = (String) body.getOrDefault("bizType", "");
        String channel = (String) body.getOrDefault("channel", "IN_APP");
        @SuppressWarnings("unchecked")
        Map<String, String> params = (Map<String, String>) body.getOrDefault("params", new HashMap<>());
        @SuppressWarnings("unchecked")
        List<Map<String, String>> receivers = (List<Map<String, String>>) body.getOrDefault("receivers", new java.util.ArrayList<>());
        MsgBatchTaskDO task = batchSendService.submit(bizType, channel, params, receivers);
        Map<String, Object> res = new HashMap<>();
        res.put("taskId", task.getId());
        res.put("status", task.getStatus());
        res.put("totalCount", task.getTotalCount());
        return Result.success(res);
    }

    @GetMapping("/{id}")
    public Result<MsgBatchTaskDO> progress(@PathVariable Long id) {
        MsgBatchTaskDO task = batchSendService.getTask(id);
        return task != null ? Result.success(task) : Result.<MsgBatchTaskDO>fail("任务不存在");
    }

    @PostMapping("/list")
    public Result<IPage<MsgBatchTaskDO>> list(@RequestBody Map<String, Object> body) {
        int page = body.containsKey("page") ? ((Number) body.get("page")).intValue() : 1;
        int size = body.containsKey("size") ? ((Number) body.get("size")).intValue() : 20;
        Integer status = body.containsKey("status") ? ((Number) body.get("status")).intValue() : null;
        return Result.success(batchSendService.page(page, size, status));
    }

    @PostMapping("/{id}/cancel")
    public Result<String> cancel(@PathVariable Long id) {
        // 暂不实现真实取消（标记为 failed），未来扩展
        MsgBatchTaskDO task = batchSendService.getTask(id);
        if (task == null) {
            return Result.fail("任务不存在");
        }

        if (task.getStatus() == 2 || task.getStatus() == 3 || task.getStatus() == 4) {
            return Result.fail("任务已结束，无法取消");
        }
        return Result.success("cancel_not_implemented");
    }
}
