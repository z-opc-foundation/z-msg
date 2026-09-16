package com.zifang.z.msg.web.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.core.domain.entity.MsgTemplateDO;
import com.zifang.z.msg.core.domain.service.MsgTemplateService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 模板管理 API (Phase 1 + Phase 3 审批流)
 * <p>
 * POST /api/msg/template/list              分页列表
 * POST /api/msg/template                    创建
 * PUT  /api/msg/template/{id}              更新 (自动版本快照)
 * GET  /api/msg/template/{id}              详情
 * DELETE /api/msg/template/{id}            删除
 * POST /api/msg/template/{id}/approve      审批通过 (Phase 3)
 * POST /api/msg/template/{id}/reject       审批拒绝 (Phase 3)
 * POST /api/msg/template/cache/refresh     手动刷新缓存
 */
@RestController
@RequestMapping("/api/msg/template")
public class MsgTemplateController {

    @Resource
    private MsgTemplateService templateService;

    @Resource
    private com.zifang.z.msg.core.template.MessageTemplateEngine templateEngine;

    @PostMapping("/list")
    public Result<IPage<MsgTemplateDO>> list(@RequestBody Map<String, Object> body) {
        int page = body.containsKey("page") ? ((Number) body.get("page")).intValue() : 1;
        int size = body.containsKey("size") ? ((Number) body.get("size")).intValue() : 20;
        String bizType = (String) body.getOrDefault("bizType", "");
        String channel = (String) body.getOrDefault("channel", "");
        return Result.success(templateService.page(page, size, bizType, channel));
    }

    @GetMapping("/{id}")
    public Result<MsgTemplateDO> getById(@PathVariable Long id) {
        MsgTemplateDO t = templateService.getById(id);
        return t != null ? Result.success(t) : Result.<MsgTemplateDO>fail("模板不存在");
    }

    @PostMapping
    public Result<MsgTemplateDO> create(@RequestBody MsgTemplateDO t) {
        return Result.success(templateService.create(t));
    }

    @PutMapping("/{id}")
    public Result<MsgTemplateDO> update(@PathVariable Long id,
                                        @RequestBody MsgTemplateDO t,
                                        @RequestParam(required = false, defaultValue = "") String changeNote) {
        String operator = "system";
        return Result.success(templateService.update(id, t, changeNote, operator));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(templateService.delete(id));
    }

    @PostMapping("/{id}/approve")
    public Result<MsgTemplateDO> approve(@PathVariable Long id,
                                         @RequestParam(required = false, defaultValue = "system") String approver,
                                         @RequestParam(required = false, defaultValue = "") String remark) {
        return Result.success(templateService.approve(id, approver, remark));
    }

    @PostMapping("/{id}/reject")
    public Result<MsgTemplateDO> reject(@PathVariable Long id,
                                        @RequestParam(required = false, defaultValue = "system") String approver,
                                        @RequestParam(required = false, defaultValue = "") String reason) {
        return Result.success(templateService.reject(id, approver, reason));
    }

    @PostMapping("/cache/refresh")
    public Result<String> refreshCache() {
        templateEngine.refreshCache();
        return Result.success("模板缓存已刷新");
    }
}
