package com.zifang.z.msg.web.api;

import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.core.domain.entity.MsgUserPreferenceDO;
import com.zifang.z.msg.core.domain.service.MsgUserPreferenceService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 用户渠道偏好 API (Phase 2)
 * <p>
 * POST /api/msg/preference/upsert     创建/更新偏好
 * POST /api/msg/preference/my          获取用户自身偏好
 * DELETE /api/msg/preference/{id}      删除偏好
 */
@RestController
@RequestMapping("/api/msg/preference")
public class MsgPreferenceController {

    @Resource
    private MsgUserPreferenceService preferenceService;

    @PostMapping("/upsert")
    public Result<MsgUserPreferenceDO> upsert(@RequestBody MsgUserPreferenceDO p) {
        return Result.success(preferenceService.upsert(p));
    }

    @GetMapping("/my")
    public Result<List<MsgUserPreferenceDO>> myPreferences(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "IN_APP") String bizType) {
        // 返回该用户的所有偏好（精确 + 全局 *）
        java.util.List<MsgUserPreferenceDO> list = new java.util.ArrayList<>();
        MsgUserPreferenceDO exact = preferenceService.lookup(userId, bizType);
        if (exact != null) {
            list.add(exact);
        }

        MsgUserPreferenceDO globalPref = preferenceService.lookup(userId, "*");
        if (globalPref != null) {
            list.add(globalPref);
        }

        return Result.success(list);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(preferenceService.delete(id));
    }
}
