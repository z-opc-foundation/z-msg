package com.zifang.z.msg.web.api;

import com.zifang.util.core.meta.Result;
import com.zifang.z.msg.core.domain.entity.MsgClientDO;
import com.zifang.z.msg.core.domain.service.MsgClientService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端凭据管理 API（对外调用方的 app + AK）
 * <p>
 * POST /api/msg/client/list     列表（不含 AK 明文，只有末 8 位）
 * POST /api/msg/client/create   建一个，明文 AK 只在这里返回一次
 * POST /api/msg/client/update   改属性（不动 AK）
 * POST /api/msg/client/rotate   轮换 AK，明文只返回一次
 * POST /api/msg/client/toggle   启停
 * POST /api/msg/client/delete   删
 * <p>
 * 这组端点归管理面（{@code AdminEndpointGate} 的 {@code /api/msg/client} 前缀），
 * 与 template/batch/delivery 同款：不带身份，靠宿主把闸门打开。
 */
@RestController
@RequestMapping("/api/msg/client")
public class MsgClientController {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private MsgClientService clientService;

    @PostMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (MsgClientDO c : clientService.list()) {
            out.add(toView(c));
        }
        return Result.success(out);
    }

    /**
     * 建凭据。返回里的 {@code plainKey} 只此一次可见 —— 服务端只留 sha-256 摘要。
     */
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String appCode = str(body.get("appCode"));
        String appName = str(body.get("appName"));
        Integer dailyQuota = num(body.get("dailyQuota"));
        String allowedChannels = str(body.get("allowedChannels"));
        String ipWhitelist = str(body.get("ipWhitelist"));
        String description = str(body.get("description"));
        LocalDateTime expireTime = parseDateTime(str(body.get("expireTime")));

        MsgClientService.CreatedClient created = clientService.create(
                appCode, appName, dailyQuota, allowedChannels, ipWhitelist, expireTime, description);

        Map<String, Object> data = toView(created.getClient());
        data.put("plainKey", created.getPlainKey());
        data.put("plainKeyNotice", "明文 AK 只显示这一次，服务端只留摘要；丢了请用 rotate 轮换");
        return Result.success(data);
    }

    @PostMapping("/update")
    public Result<Boolean> update(@RequestBody Map<String, Object> body) {
        Long id = longNum(body.get("id"));
        boolean ok = clientService.update(id,
                str(body.get("appName")),
                num(body.get("dailyQuota")),
                str(body.get("allowedChannels")),
                str(body.get("ipWhitelist")),
                parseDateTime(str(body.get("expireTime"))),
                str(body.get("description")));
        return Result.success(ok);
    }

    @PostMapping("/rotate")
    public Result<Map<String, Object>> rotate(@RequestBody Map<String, Object> body) {
        Long id = longNum(body.get("id"));
        MsgClientService.CreatedClient created = clientService.rotate(id);
        Map<String, Object> data = toView(created.getClient());
        data.put("plainKey", created.getPlainKey());
        data.put("plainKeyNotice", "新 AK 只显示这一次，旧的立即失效");
        return Result.success(data);
    }

    @PostMapping("/toggle")
    public Result<Boolean> toggle(@RequestBody Map<String, Object> body) {
        Long id = longNum(body.get("id"));
        boolean enabled = !Boolean.FALSE.equals(body.get("enabled"))
                && !"false".equalsIgnoreCase(str(body.get("enabled")));
        return Result.success(clientService.toggle(id, enabled));
    }

    @PostMapping("/delete")
    public Result<Boolean> delete(@RequestBody Map<String, Object> body) {
        Long id = longNum(body.get("id"));
        return Result.success(clientService.delete(id));
    }

    private Map<String, Object> toView(MsgClientDO c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("appCode", c.getAppCode());
        m.put("appName", c.getAppName());
        m.put("keyPrefix", c.getKeyPrefix());
        m.put("status", c.getStatus());
        m.put("expireTime", c.getExpireTime() == null ? null : c.getExpireTime().format(DATE_TIME));
        m.put("dailyQuota", c.getDailyQuota());
        m.put("quotaUsed", c.getQuotaUsed());
        m.put("quotaDate", c.getQuotaDate());
        m.put("allowedChannels", c.getAllowedChannels());
        m.put("ipWhitelist", c.getIpWhitelist());
        m.put("description", c.getDescription());
        m.put("createdTime", c.getCreatedTime() == null ? null : c.getCreatedTime().format(DATE_TIME));
        m.put("updatedTime", c.getUpdatedTime() == null ? null : c.getUpdatedTime().format(DATE_TIME));
        return m;
    }

    private static LocalDateTime parseDateTime(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String t = text.trim().replace('T', ' ');
        if (t.length() == 16) {
            t = t + ":00";
        }
        try {
            return LocalDateTime.parse(t, DATE_TIME);
        } catch (Exception e) {
            throw new IllegalArgumentException("expireTime 格式不对：" + text + "（应为 yyyy-MM-dd HH:mm:ss）");
        }
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Integer num(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longNum(Object v) {
        if (v == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return Long.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("id 不是数字：" + v);
        }
    }
}
