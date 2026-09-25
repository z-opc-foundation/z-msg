package com.zifang.z.msg.core.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zifang.z.msg.api.FanOutResult;
import com.zifang.z.msg.core.channel.ChannelRouter;
import com.zifang.z.msg.core.domain.entity.MsgBatchTaskDO;
import com.zifang.z.msg.core.domain.mapper.MsgBatchTaskMapper;
import com.zifang.z.msg.core.template.MessageTemplateEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 批量发送服务 (Phase 2.4)
 * <p>
 * 创建批量任务 → 异步执行 → 逐条投递 → 更新进度。
 * 前端通过 /api/msg/batch/{id} 轮询 progress。
 * <p>
 * 1.1.0 修掉三处：
 * <ul>
 *   <li>{@code submit()} 直接调本对象的 {@code executeAsync()}，绕过 Spring 代理，
 *       {@code @Async} 完全失效——几千人的批量任务其实是同步跑在 HTTP 请求线程上的。
 *       现在显式提交到 {@code msgAsyncExecutor}。</li>
 *   <li>{@code cancel} 只返回 "cancel_not_implemented"；现在是协作式取消，
 *       执行循环每条都查一次取消标记。</li>
 *   <li>IN_APP 批量时 receiver 是 userId，之前一律传 userId=null，
 *       站内信通道拿不到归属人就整批失败。</li>
 * </ul>
 */
@Service
public class MsgBatchSendService {

    /**
     * 任务状态：0 pending / 1 running / 2 done / 3 partial / 4 failed / 5 cancelled
     */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_PARTIAL = 3;
    public static final int STATUS_FAILED = 4;
    public static final int STATUS_CANCELLED = 5;

    private static final Logger log = LogManager.getLogger(MsgBatchSendService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 已请求取消的任务 id；执行循环逐条检查。进程重启后丢失，所以同时把 status=5 落库，
     * 循环里的 selectById 会看到（跨进程取消靠库里那一列）。
     */
    private final java.util.Set<Long> cancelRequested = ConcurrentHashMap.newKeySet();

    @Resource
    private MsgBatchTaskMapper batchTaskMapper;
    @Resource
    private ChannelRouter channelRouter;
    @Resource
    private MessageTemplateEngine templateEngine;
    @Resource(name = "msgAsyncExecutor")
    private Executor msgAsyncExecutor;

    /**
     * 创建批量任务（立即返回 taskId，异步执行）
     */
    public MsgBatchTaskDO submit(String bizType, String channel,
                                 Map<String, String> params,
                                 List<Map<String, String>> receivers) {
        MsgBatchTaskDO task = new MsgBatchTaskDO();
        task.setBizType(bizType);
        task.setChannel(channel);
        task.setTotalCount(receivers.size());
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setStatus(STATUS_PENDING);
        try {
            task.setParamsJson(MAPPER.writeValueAsString(params));
        } catch (Exception ex) {
            task.setParamsJson("{}");
        }
        try {
            task.setReceiversJson(MAPPER.writeValueAsString(receivers));
        } catch (Exception ex) {
            task.setReceiversJson("[]");
        }
        MessageTemplateEngine.TemplateEntry entry = templateEngine.lookup(bizType, channel, null);
        if (entry != null) {
            task.setTemplateId(entry.id);
        }
        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());
        batchTaskMapper.insert(task);
        log.info("[BatchSend] submitted taskId={} bizType={} channel={} count={}",
                task.getId(), bizType, channel, receivers.size());
        final Long taskId = task.getId();
        msgAsyncExecutor.execute(() -> executeAsync(taskId));
        return task;
    }

    /**
     * 协作式取消：正在跑的任务在下一条之前停下，未跑的任务直接标记取消。
     */
    public boolean cancel(Long taskId) {
        MsgBatchTaskDO task = batchTaskMapper.selectById(taskId);
        if (task == null) {
            return false;
        }
        cancelRequested.add(taskId);
        if (task.getStatus() != null
                && (task.getStatus() == STATUS_DONE || task.getStatus() == STATUS_FAILED
                || task.getStatus() == STATUS_PARTIAL)) {
            return false;
        }
        if (task.getStatus() == null || task.getStatus() == STATUS_PENDING) {
            task.setStatus(STATUS_CANCELLED);
            task.setFinishedTime(LocalDateTime.now());
            task.setUpdatedTime(LocalDateTime.now());
            batchTaskMapper.updateById(task);
        }
        return true;
    }

    public void executeAsync(Long taskId) {
        MsgBatchTaskDO task = batchTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        if (task.getStatus() != null && task.getStatus() == STATUS_CANCELLED) {
            cancelRequested.remove(taskId);
            return;
        }
        task.setStatus(STATUS_RUNNING);
        task.setUpdatedTime(LocalDateTime.now());
        batchTaskMapper.updateById(task);

        Map<String, String> sharedParams = parseJsonMap(task.getParamsJson());
        List<Map<String, String>> receivers = parseReceivers(task.getReceiversJson());

        for (Map<String, String> receiverMap : receivers) {
            if (isCancelRequested(taskId)) {
                task.setStatus(STATUS_CANCELLED);
                task.setErrorMessage("已取消，已成功 " + task.getSuccessCount() + " 条");
                task.setFinishedTime(LocalDateTime.now());
                task.setUpdatedTime(LocalDateTime.now());
                batchTaskMapper.updateById(task);
                cancelRequested.remove(taskId);
                log.info("[BatchSend] cancelled taskId={} success={} failed={}",
                        taskId, task.getSuccessCount(), task.getFailedCount());
                return;
            }
            String receiver = receiverMap.get("receiver");
            if (receiver == null || receiver.isEmpty()) {
                task.setFailedCount(task.getFailedCount() + 1);
                continue;
            }
            // 合并 receiver 级 params
            Map<String, String> merged = new HashMap<>(sharedParams);
            receiverMap.forEach((k, v) -> {
                if (!"receiver".equals(k)) {
                    merged.put(k, v);
                }
            });
            try {
                Map<String, String> receiverChannels = new HashMap<>();
                receiverChannels.put(task.getChannel(), receiver);
                FanOutResult res = channelRouter.fanOut(
                        task.getBizType(), parseUserId(receiverMap, task.getChannel()),
                        receiverChannels, merged, sharedParams.get("locale"));
                if (res.anyDelivered()) {
                    task.setSuccessCount(task.getSuccessCount() + 1);
                } else {
                    task.setFailedCount(task.getFailedCount() + 1);
                }
            } catch (Exception e) {
                log.error("[BatchSend] receiver={} error={}", receiver, e.getMessage());
                task.setFailedCount(task.getFailedCount() + 1);
            }
            task.setUpdatedTime(LocalDateTime.now());
            batchTaskMapper.updateById(task);
        }

        task.setStatus(task.getFailedCount() > 0
                ? (task.getSuccessCount() > 0 ? STATUS_PARTIAL : STATUS_FAILED) : STATUS_DONE);
        task.setFinishedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());
        batchTaskMapper.updateById(task);
        log.info("[BatchSend] completed taskId={} success={} failed={}",
                taskId, task.getSuccessCount(), task.getFailedCount());
    }

    private boolean isCancelRequested(Long taskId) {
        if (cancelRequested.contains(taskId)) {
            return true;
        }
        MsgBatchTaskDO fresh = batchTaskMapper.selectById(taskId);
        return fresh != null && fresh.getStatus() != null && fresh.getStatus() == STATUS_CANCELLED;
    }

    /**
     * IN_APP / PUSH 之类通道的归属人：显式给 userId 用显式的，
     * 否则站内信直接把 receiver 当 userId 解。
     */
    private Long parseUserId(Map<String, String> receiverMap, String channel) {
        String raw = receiverMap.get("userId");
        if (raw == null || raw.isEmpty()) {
            raw = receiverMap.get("receiver");
        }
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public MsgBatchTaskDO getTask(Long id) {
        return batchTaskMapper.selectById(id);
    }

    public IPage<MsgBatchTaskDO> page(int pageNum, int pageSize, Integer status) {
        LambdaQueryWrapper<MsgBatchTaskDO> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(MsgBatchTaskDO::getStatus, status);
        }
        qw.orderByDesc(MsgBatchTaskDO::getCreatedTime);
        return batchTaskMapper.selectPage(new Page<>(pageNum, pageSize), qw);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseJsonMap(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseReceivers(String json) {
        if (json == null || json.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<Map<String, String>>>() {
            });
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }
}
