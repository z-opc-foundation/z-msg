package com.zifang.z.msg.core.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zifang.z.msg.core.channel.ChannelRouter;
import com.zifang.z.msg.core.domain.entity.MsgBatchTaskDO;
import com.zifang.z.msg.core.domain.mapper.MsgBatchTaskMapper;
import com.zifang.z.msg.core.template.MessageTemplateEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量发送服务 (Phase 2.4)
 * <p>
 * 创建批量任务 → 异步执行 → 逐条投递 → 更新进度。
 * 前端通过 /api/msg/batch/{id} 轮询 progress。
 */
@Service
public class MsgBatchSendService {

    private static final Logger log = LogManager.getLogger(MsgBatchSendService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private MsgBatchTaskMapper batchTaskMapper;
    @Resource
    private ChannelRouter channelRouter;
    @Resource
    private MessageTemplateEngine templateEngine;

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
        task.setStatus(0); // pending
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
        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());
        batchTaskMapper.insert(task);
        log.info("[BatchSend] submitted taskId={} bizType={} channel={} count={}",
                task.getId(), bizType, channel, receivers.size());
        executeAsync(task.getId());
        return task;
    }

    @Async("msgAsyncExecutor")
    public void executeAsync(Long taskId) {
        MsgBatchTaskDO task = batchTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setStatus(1); // running
        task.setUpdatedTime(LocalDateTime.now());
        batchTaskMapper.updateById(task);

        Map<String, String> sharedParams = parseJsonMap(task.getParamsJson());
        List<Map<String, String>> receivers = parseReceivers(task.getReceiversJson());

        for (Map<String, String> receiverMap : receivers) {
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
                List<ChannelRouter.SendOutcome> outcomes = channelRouter.fanOut(
                        task.getBizType(), null, receiverChannels, merged, null);
                boolean ok = outcomes.stream().anyMatch(ChannelRouter.SendOutcome::isSuccess);
                if (ok) {
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
                ? (task.getSuccessCount() > 0 ? 3 : 4) : 2); // done/partial/failed
        task.setFinishedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());
        batchTaskMapper.updateById(task);
        log.info("[BatchSend] completed taskId={} success={} failed={}",
                taskId, task.getSuccessCount(), task.getFailedCount());
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
