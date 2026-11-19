package com.zifang.z.msg.core.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zifang.z.meta.api.annotation.BizMethod;
import com.zifang.z.msg.core.domain.entity.MsgClientDO;
import com.zifang.z.msg.core.domain.mapper.MsgClientMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户端凭据服务：对外调用方（别的模块、脚本、外部系统）用 app + AK 调消息服务。
 * <p>
 * <b>AK 明文只出现一次</b>：创建时生成并返回明文，库里只留 sha-256 摘要与末 8 位前缀。
 * 丢了就重建（{@link #rotate}），不提供找回 —— 能找回的凭据等于没有凭据。
 * <p>
 * <b>配额落在本表两列上</b>（{@code quota_date} + {@code quota_used}）而不是另开一张计数表：
 * 配额是「每客户端每天多少条」，跨天清零，跟客户端行天然一对一；另开表只会让
 * 「客户端被删了但计数还在」这种孤儿行出现。
 */
@Service
public class MsgClientService {

    /** AK 前缀，便于在日志/密钥库里一眼认出这是消息服务的凭据。 */
    public static final String KEY_PREFIX = "msga";

    @Resource
    private MsgClientMapper clientMapper;

    private final SecureRandom random = new SecureRandom();

    /**
     * 创建客户端。返回的 {@link CreatedClient#getPlainKey()} 是明文 AK，只此一次可见。
     */
    @BizMethod(name = "创建客户端。返回的 CreatedClient#getPlainKey() 是明文 AK，只此一次可见", module = "z-msg", description = "创建客户端。返回的 CreatedClient#getPlainKey() 是明文 AK，只此一次可见。", operation = BizMethod.Operation.CREATE)
    public CreatedClient create(String appCode, String appName, Integer dailyQuota,
                                String allowedChannels, String ipWhitelist,
                                LocalDateTime expireTime, String description) {
        if (appCode == null || appCode.trim().isEmpty()) {
            throw new IllegalArgumentException("appCode 不能为空");
        }
        String plainKey = generatePlainKey();
        MsgClientDO entity = new MsgClientDO();
        entity.setAppCode(appCode.trim());
        entity.setAppName(appName == null || appName.trim().isEmpty() ? appCode.trim() : appName.trim());
        entity.setKeyHash(sha256(plainKey));
        entity.setKeyPrefix(keyPrefix(plainKey));
        entity.setStatus(1);
        entity.setExpireTime(expireTime);
        entity.setDailyQuota(dailyQuota == null || dailyQuota <= 0 ? 0 : dailyQuota);
        entity.setAllowedChannels(trimToNull(allowedChannels));
        entity.setIpWhitelist(trimToNull(ipWhitelist));
        entity.setDescription(trimToNull(description));
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedTime(now);
        entity.setUpdatedTime(now);
        clientMapper.insert(entity);
        return new CreatedClient(entity, plainKey);
    }

    /**
     * 用明文 AK 找客户端。摘要比对，不是明文比对。
     */
    @BizMethod(name = "用明文 AK 找客户端。摘要比对，不是明文比对", module = "z-msg", description = "用明文 AK 找客户端。摘要比对，不是明文比对。", operation = BizMethod.Operation.QUERY)
    public MsgClientDO findByPlainKey(String plainKey) {
        if (plainKey == null || plainKey.trim().isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<MsgClientDO> qw = new LambdaQueryWrapper<>();
        qw.eq(MsgClientDO::getKeyHash, sha256(plainKey.trim())).last("LIMIT 1");
        return clientMapper.selectOne(qw);
    }

    @BizMethod(name = "findById", module = "z-msg", description = "", operation = BizMethod.Operation.QUERY)
    public MsgClientDO findById(Long id) {
        return id == null ? null : clientMapper.selectById(id);
    }

    @BizMethod(name = "list", module = "z-msg", description = "", operation = BizMethod.Operation.QUERY)
    public List<MsgClientDO> list() {
        LambdaQueryWrapper<MsgClientDO> qw = new LambdaQueryWrapper<>();
        qw.orderByDesc(MsgClientDO::getId);
        return clientMapper.selectList(qw);
    }

    /**
     * 轮换 AK：换一把新的，明文同样只返回一次。旧的立即失效。
     */
    @BizMethod(name = "轮换 AK：换一把新的，明文同样只返回一次。旧的立即失效", module = "z-msg", description = "轮换 AK：换一把新的，明文同样只返回一次。旧的立即失效。", operation = BizMethod.Operation.ACTION)
    public CreatedClient rotate(Long id) {
        MsgClientDO entity = findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("客户端不存在：" + id);
        }
        String plainKey = generatePlainKey();
        entity.setKeyHash(sha256(plainKey));
        entity.setKeyPrefix(keyPrefix(plainKey));
        entity.setUpdatedTime(LocalDateTime.now());
        clientMapper.updateById(entity);
        return new CreatedClient(entity, plainKey);
    }

    @BizMethod(name = "update", module = "z-msg", description = "", operation = BizMethod.Operation.UPDATE)
    public boolean update(Long id, String appName, Integer dailyQuota, String allowedChannels,
                          String ipWhitelist, LocalDateTime expireTime, String description) {
        MsgClientDO entity = findById(id);
        if (entity == null) {
            return false;
        }
        if (appName != null && !appName.trim().isEmpty()) {
            entity.setAppName(appName.trim());
        }
        entity.setDailyQuota(dailyQuota == null || dailyQuota <= 0 ? 0 : dailyQuota);
        entity.setAllowedChannels(trimToNull(allowedChannels));
        entity.setIpWhitelist(trimToNull(ipWhitelist));
        entity.setExpireTime(expireTime);
        entity.setDescription(trimToNull(description));
        entity.setUpdatedTime(LocalDateTime.now());
        return clientMapper.updateById(entity) > 0;
    }

    @BizMethod(name = "toggle", module = "z-msg", description = "", operation = BizMethod.Operation.ACTION)
    public boolean toggle(Long id, boolean enabled) {
        MsgClientDO entity = findById(id);
        if (entity == null) {
            return false;
        }
        entity.setStatus(enabled ? 1 : 0);
        entity.setUpdatedTime(LocalDateTime.now());
        return clientMapper.updateById(entity) > 0;
    }

    @BizMethod(name = "delete", module = "z-msg", description = "", operation = BizMethod.Operation.DELETE)
    public boolean delete(Long id) {
        return id != null && clientMapper.deleteById(id) > 0;
    }

    /**
     * IP 白名单。空 = 不限。
     */
    @BizMethod(name = "IP 白名单。空 = 不限", module = "z-msg", description = "IP 白名单。空 = 不限。", operation = BizMethod.Operation.ACTION)
    public boolean verifyIp(MsgClientDO client, String remoteIp) {
        String raw = client == null ? null : client.getIpWhitelist();
        if (raw == null || raw.trim().isEmpty()) {
            return true;
        }
        if (remoteIp == null || remoteIp.trim().isEmpty()) {
            return false;
        }
        for (String item : raw.split(",")) {
            String t = item.trim();
            if (t.equals(remoteIp.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 通道白名单。空 = 全部通道。
     */
    @BizMethod(name = "通道白名单。空 = 全部通道", module = "z-msg", description = "通道白名单。空 = 全部通道。", operation = BizMethod.Operation.ACTION)
    public boolean verifyChannel(MsgClientDO client, String channel) {
        String raw = client == null ? null : client.getAllowedChannels();
        if (raw == null || raw.trim().isEmpty()) {
            return true;
        }
        if (channel == null) {
            return false;
        }
        for (String item : raw.split(",")) {
            if (item.trim().equalsIgnoreCase(channel.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 记一次配额并判断是否超限。跨天自动把计数清零。
     *
     * @return true 表示这次还在配额内
     */
    @BizMethod(name = "记一次配额并判断是否超限。跨天自动把计数清零", module = "z-msg", description = "记一次配额并判断是否超限。跨天自动把计数清零。", operation = BizMethod.Operation.ACTION)
    public boolean consumeQuota(MsgClientDO client) {
        Integer quota = client == null ? null : client.getDailyQuota();
        if (quota == null || quota <= 0) {
            return true;
        }
        String today = LocalDate.now().toString();
        Integer used = client.getQuotaUsed();
        if (today.equals(client.getQuotaDate()) && used != null && used >= quota) {
            return false;
        }
        int current = today.equals(client.getQuotaDate()) && used != null ? used : 0;
        if (current + 1 > quota) {
            return false;
        }
        client.setQuotaDate(today);
        client.setQuotaUsed(current + 1);
        client.setUpdatedTime(LocalDateTime.now());
        clientMapper.updateById(client);
        return true;
    }

    private String generatePlainKey() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(KEY_PREFIX);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String keyPrefix(String plainKey) {
        return plainKey.length() <= 8 ? plainKey : plainKey.substring(plainKey.length() - 8);
    }

    static String sha256(String text) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 创建结果：DO + 只此一次可见的明文 AK。
     */
    public static class CreatedClient {
        private final MsgClientDO client;
        private final String plainKey;

        public CreatedClient(MsgClientDO client, String plainKey) {
            this.client = client;
            this.plainKey = plainKey;
        }

        public MsgClientDO getClient() {
            return client;
        }

        public String getPlainKey() {
            return plainKey;
        }
    }
}



