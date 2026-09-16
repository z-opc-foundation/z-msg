package com.zifang.z.msg.web.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.core.domain.entity.InAppMessage;
import com.zifang.z.msg.core.domain.mapper.InAppMessageMapper;
import com.zifang.z.msg.core.domain.service.MsgDeliveryLogService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 消息中心 API (Phase 1 扩展)
 * <p>
 * 原有: POST /api/msg/publish, GET /api/msg/list, POST /api/msg/read/{id}, GET /api/msg/unread/{userId}
 * 新增: POST /api/msg/read-all/{userId}  批量全部已读
 * POST /api/msg/delete/{id}         删除单条
 * POST /api/msg/delete-all/{userId}  批量删除全部
 */
@RestController
@RequestMapping("/api/msg")
public class MessageController {

    @Resource
    private com.zifang.z.msg.api.MessageBus messageBus;
    @Resource
    private InAppMessageMapper messageMapper;
    @Resource
    private MsgDeliveryLogService deliveryLogService;

    @PostMapping("/publish")
    public Result<String> publish(@RequestParam String eventType, @RequestParam Long userId,
                                  @RequestBody(required = false) Map<String, Object> params) {
        messageBus.publish(new com.zifang.z.msg.api.MessageEvent(eventType, userId, params));
        return Result.success("ok");
    }

    @GetMapping("/list")
    public Result<List<InAppMessage>> list(@RequestParam Long userId,
                                           @RequestParam(required = false, defaultValue = "0") Integer unreadOnly) {
        LambdaQueryWrapper<InAppMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(InAppMessage::getUserId, userId);
        if (unreadOnly == 1) {
            qw.eq(InAppMessage::getIsRead, 0);
        }

        qw.orderByDesc(InAppMessage::getCreatedTime);
        return Result.success(messageMapper.selectList(qw));
    }

    @PostMapping("/read/{id}")
    public Result<Boolean> markRead(@PathVariable Long id) {
        InAppMessage msg = messageMapper.selectById(id);
        if (msg == null) {
            return Result.<Boolean>fail("消息不存在").code(400);
        }

        msg.setIsRead(1);
        return Result.success(messageMapper.updateById(msg) > 0);
    }

    @PostMapping("/read-all/{userId}")
    public Result<Integer> markAllRead(@PathVariable Long userId) {
        LambdaQueryWrapper<InAppMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(InAppMessage::getUserId, userId).eq(InAppMessage::getIsRead, 0);
        InAppMessage unread = new InAppMessage();
        unread.setIsRead(1);
        return Result.success(messageMapper.update(unread, qw));
    }

    @PostMapping("/delete/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(messageMapper.deleteById(id) > 0);
    }

    @PostMapping("/delete-all/{userId}")
    public Result<Integer> deleteAll(@PathVariable Long userId) {
        LambdaQueryWrapper<InAppMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(InAppMessage::getUserId, userId);
        return Result.success(messageMapper.delete(qw));
    }

    @GetMapping("/unread/{userId}")
    public Result<Integer> unreadCount(@PathVariable Long userId) {
        LambdaQueryWrapper<InAppMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(InAppMessage::getUserId, userId).eq(InAppMessage::getIsRead, 0);
        Long count = messageMapper.selectCount(qw);
        return Result.success(count != null ? count.intValue() : 0);
    }

    @GetMapping("/detail/{id}")
    public Result<InAppMessage> detail(@PathVariable Long id) {
        InAppMessage msg = messageMapper.selectById(id);
        return msg != null ? Result.success(msg) : Result.<InAppMessage>fail("消息不存在").code(404);
    }

    @GetMapping("/delivery/stats")
    public Result<Map<String, Long>> deliveryStats() {
        java.util.Map<String, Long> stats = new java.util.HashMap<>();
        stats.put("success", deliveryLogService.countSuccess(null, null, null));
        stats.put("failed", deliveryLogService.countFailed(null, null, null));
        stats.put("total", stats.get("success") + stats.get("failed"));
        return Result.success(stats);
    }
}
