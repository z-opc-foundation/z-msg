package com.zifang.z.msg.web.api;

import com.zifang.util.core.meta.Result;
import com.zifang.z.meta.api.annotation.BizApi;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizParam;
import com.zifang.z.msg.core.domain.entity.MsgUserPreferenceDO;
import com.zifang.z.msg.core.domain.service.MsgUserPreferenceService;
import com.zifang.z.msg.web.auth.MsgPrincipalResolver;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户渠道偏好 API。
 * <p>
 * {@code userId} 一律由服务端解出并覆盖请求体里的值：原来的
 * {@code upsert(@RequestBody MsgUserPreferenceDO)} 直接采信客户端写的 userId，
 * 相当于任何人都能改掉别人的通知开关（给别人全部静音只要一个 curl）。
 */
@RestController
@RequestMapping("/api/msg/preference")
public class MsgPreferenceController {

    @Resource
    private MsgUserPreferenceService preferenceService;
    @Resource
    private MsgPrincipalResolver principalResolver;

    @PostMapping("/upsert")
    @BizApi(summary = "upsert", description = "", tags = { "z-msg" })
    public Result<MsgUserPreferenceDO> upsert(HttpServletRequest request,
                                              @RequestBody @BizParam(name = "p", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.BODY) MsgUserPreferenceDO p) {
        p.setUserId(principalResolver.require(request));
        return Result.success(preferenceService.upsert(p));
    }

    @GetMapping("/my")
    @BizApi(summary = "myPreferences", description = "", tags = { "z-msg" })
    public Result<List<MsgUserPreferenceDO>> myPreferences(
            HttpServletRequest request,
            @RequestParam(defaultValue = "IN_APP")
            @BizParam(name = "bizType", type = BizField.FieldType.STRING, description = "", position = BizParam.Position.QUERY) String bizType) {
        Long me = principalResolver.require(request);
        List<MsgUserPreferenceDO> list = new ArrayList<MsgUserPreferenceDO>();
        MsgUserPreferenceDO exact = preferenceService.lookup(me, bizType);
        if (exact != null) {
            list.add(exact);
        }
        // bizType 本身就是 '*' 时不用再查一遍全局，否则同一条会重复出现
        if (!"*".equals(bizType)) {
            MsgUserPreferenceDO global = preferenceService.lookup(me, "*");
            if (global != null) {
                list.add(global);
            }
        }
        return Result.success(list);
    }

    @DeleteMapping("/{id}")
    @BizApi(summary = "delete", description = "", tags = { "z-msg" })
    public Result<Boolean> delete(HttpServletRequest request, @PathVariable @BizParam(name = "id", type = BizField.FieldType.INTEGER, description = "", position = BizParam.Position.PATH) Long id) {
        Long me = principalResolver.require(request);
        return Result.success(preferenceService.deleteOwned(id, me));
    }
}



