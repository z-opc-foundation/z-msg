package com.zifang.z.msg.core.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
     *
     * <p><b>运行中的任务也必须落库</b>。此前只有 PENDING 才写 {@code status=5}，
     * RUNNING 只是往内存集合里塞个标记 —— 于是
     * {@link #isCancelRequested} 里那条"查库"的跨进程分支，对<b>正在跑的任务</b>永远是 false，
     * 而上面那句注释（"跨进程取消靠库里那一列"）对运行中任务压根不成立。
     * 更糟的是 {@link #executeAsync} 的执行循环每处理完一条就整行 {@code updateById}，
     * 会把 {@code cancel} 刚落的 5 又改回 1（RUNNING）。三处一起才造成"取消请求丢失"。</p>
     *
     * @return false 表示任务不存在，或已处于终态（无可取消）
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
        Integer previous = task.getStatus();
        if (previous == null || previous == STATUS_PENDING || previous == STATUS_RUNNING) {
            task.setStatus(STATUS_CANCELLED);
            if (previous != null && previous == STATUS_RUNNING) {
                // 正在跑：先不写 finishedTime（任务还要把当前这条跑完），
                // 真正的结束时间由执行循环收尾时补上
                task.setErrorMessage("已请求取消，正在跑的部分会跑完当前这条后停下");
            } else {
                task.setFinishedTime(LocalDateTime.now());
            }
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
            // 计数更新**刻意不碰 status**：
            // 整行 updateById 会把内存快照里的 status(RUNNING) 写回去，
            // 而 cancel() 刚把库里那一列改成了 5(CANCELLED) —— 于是取消请求被这一行抹掉。
            // 只 set 计数字段，status 留给下面两处显式处理。
            updateProgress(task);
        }

        // 收尾：先看有没有人在过程中请求过取消。
        // 这一步必须在设置最终状态之前 —— 否则"最后一条处理期间点的取消"会被整个覆盖掉，
        // API 返回了 true 而任务照样报 DONE，库里连一点取消痕迹都没有。
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

        finishWithOutcome(task);
    }

    /**
     * 收尾写终态，带一道"只在我还认为它在跑时才改"的条件。
     *
     * <p>{@code .eq("status", STATUS_RUNNING)} 不是多余的保险：它挡的是
     * 「{@code isCancelRequested} 查完、这里还没写」这个窗口里进来的 cancel。
     * 条件更新影响 0 行就说明状态已经被别人改过（多半是被取消了），此时<b>不能</b>覆盖。</p>
     */
    private void finishWithOutcome(MsgBatchTaskDO task) {
        int finalStatus = task.getFailedCount() > 0
                ? (task.getSuccessCount() > 0 ? STATUS_PARTIAL : STATUS_FAILED) : STATUS_DONE;
        LocalDateTime now = LocalDateTime.now();
        int affected = batchTaskMapper.update(null,
                new UpdateWrapper<MsgBatchTaskDO>()
                        .set("status", finalStatus)
                        .set("finished_time", now)
                        .set("updated_time", now)
                        .eq("id", task.getId())
                        .eq("status", STATUS_RUNNING));
        if (affected == 0) {
            log.info("[BatchSend] taskId={} 收尾时状态已被改动（多半是取消），不覆盖；"
                    + "已收到的 success={} failed={}", task.getId(),
                    task.getSuccessCount(), task.getFailedCount());
            return;
        }
        log.info("[BatchSend] completed taskId={} success={} failed={} status={}",
                task.getId(), task.getSuccessCount(), task.getFailedCount(), finalStatus);
    }

    /** 只更新进度计数，不碰 status —— 理由见 {@link #executeAsync} 循环末尾的注释。 */
    private void updateProgress(MsgBatchTaskDO task) {
        batchTaskMapper.update(null,
                new UpdateWrapper<MsgBatchTaskDO>()
                        .set("success_count", task.getSuccessCount())
                        .set("failed_count", task.getFailedCount())
                        .set("total_count", task.getTotalCount())
                        .set("updated_time", LocalDateTime.now())
                        .eq("id", task.getId()));
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
