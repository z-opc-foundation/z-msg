package com.zifang.z.msg.core.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zifang.z.msg.core.domain.entity.MsgUserPreferenceDO;
import com.zifang.z.msg.core.domain.mapper.MsgUserPreferenceMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 用户渠道偏好服务 (Phase 2)
 * <p>
 * - getAllowedChannels(userId, bizType): 返回该用户允许的渠道集合。
 * 查找顺序: user+bizType 精确 > user+"*" 全局默认 > null (全部允许)。
 * 同时支持 quiet hours 静默时段判断。
 */
@Service
public class MsgUserPreferenceService {

    @Resource
    private MsgUserPreferenceMapper preferenceMapper;

    /**
     * 获取用户允许的渠道集合。
     * 返回 null 表示不限 (全部允许);返回空集合表示全部屏蔽。
     */
    public Set<String> getAllowedChannels(Long userId, String bizType) {
        if (userId == null) {
            return null;
        }
        // 1. 精确匹配
        LambdaQueryWrapper<MsgUserPreferenceDO> exact = new LambdaQueryWrapper<>();
        exact.eq(MsgUserPreferenceDO::getUserId, userId)
                .eq(MsgUserPreferenceDO::getBizType, bizType)
                .last("LIMIT 1");
        MsgUserPreferenceDO pref = preferenceMapper.selectOne(exact);
        if (pref != null) {
            return parseChannels(pref.getChannels());
        }
        // 2. 全局默认
        LambdaQueryWrapper<MsgUserPreferenceDO> global = new LambdaQueryWrapper<>();
        global.eq(MsgUserPreferenceDO::getUserId, userId)
                .eq(MsgUserPreferenceDO::getBizType, "*")
                .last("LIMIT 1");
        pref = preferenceMapper.selectOne(global);
        if (pref != null) {
            return parseChannels(pref.getChannels());
        }
        return null;
    }

    /**
     * 判断是否处于静默时段。
     * quietStart..quietEnd 区间内不发送。
     * 跨午夜的情况 (23:00-07:00) 不支持 (简单实现)。
     */
    public boolean isInQuietHours(Long userId, String bizType) {
        MsgUserPreferenceDO pref = lookup(userId, bizType);
        if (pref == null) {
            pref = lookup(userId, "*");
        }
        if (pref == null || pref.getQuietStart() == null || pref.getQuietEnd() == null) {
            return false;
        }
        try {
            String[] s = pref.getQuietStart().split(":");
            String[] e = pref.getQuietEnd().split(":");
            int start = Integer.parseInt(s[0]) * 60 + Integer.parseInt(s[1]);
            int end = Integer.parseInt(e[0]) * 60 + Integer.parseInt(e[1]);
            java.time.LocalTime now = java.time.LocalTime.now();
            int nowMin = now.getHour() * 60 + now.getMinute();
            return nowMin >= start && nowMin <= end;
        } catch (Exception ex) {
            return false;
        }
    }

    public MsgUserPreferenceDO lookup(Long userId, String bizType) {
        LambdaQueryWrapper<MsgUserPreferenceDO> qw = new LambdaQueryWrapper<>();
        qw.eq(MsgUserPreferenceDO::getUserId, userId)
                .eq(MsgUserPreferenceDO::getBizType, bizType)
                .last("LIMIT 1");
        return preferenceMapper.selectOne(qw);
    }

    @org.springframework.transaction.annotation.Transactional
    public MsgUserPreferenceDO upsert(MsgUserPreferenceDO p) {
        MsgUserPreferenceDO old = lookup(p.getUserId(), p.getBizType());
        if (old != null) {
            p.setId(old.getId());
            p.setUpdatedTime(LocalDateTime.now());
            preferenceMapper.updateById(p);
            return p;
        }
        p.setCreatedTime(LocalDateTime.now());
        p.setUpdatedTime(LocalDateTime.now());
        preferenceMapper.insert(p);
        return p;
    }

    public boolean delete(Long id) {
        return preferenceMapper.deleteById(id) > 0;
    }

    private Set<String> parseChannels(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return null;
        }
        Set<String> set = new HashSet<>();
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                set.add(t.toUpperCase());
            }
        }
        return set.isEmpty() ? Collections.emptySet() : set;
    }
}
