package com.zifang.z.msg.core.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zifang.z.msg.core.domain.entity.MsgTemplateDO;
import com.zifang.z.msg.core.domain.entity.MsgTemplateVersionDO;
import com.zifang.z.msg.core.domain.mapper.MsgTemplateMapper;
import com.zifang.z.msg.core.domain.mapper.MsgTemplateVersionMapper;
import com.zifang.z.msg.core.template.MessageTemplateEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 模板服务 (Phase 1 + Phase 3)
 * <p>
 * 增删改查 + 版本快照。每次 update 自动 append 一行 z_msg_template_version。
 */
@Service
public class MsgTemplateService {

    private static final Logger log = LogManager.getLogger(MsgTemplateService.class);

    @Resource
    private MsgTemplateMapper templateMapper;

    @Resource
    private MsgTemplateVersionMapper versionMapper;

    @Resource
    private MessageTemplateEngine templateEngine;

    public IPage<MsgTemplateDO> page(int pageNum, int pageSize, String bizType, String channel) {
        LambdaQueryWrapper<MsgTemplateDO> qw = new LambdaQueryWrapper<>();
        if (bizType != null && !bizType.isEmpty()) {
            qw.eq(MsgTemplateDO::getBizType, bizType);
        }
        if (channel != null && !channel.isEmpty()) {
            qw.eq(MsgTemplateDO::getChannel, channel);
        }
        qw.orderByDesc(MsgTemplateDO::getUpdatedTime);
        return templateMapper.selectPage(new Page<>(pageNum, pageSize), qw);
    }

    public MsgTemplateDO getById(Long id) {
        return templateMapper.selectById(id);
    }

    @Transactional
    public MsgTemplateDO create(MsgTemplateDO t) {
        if (t.getStatus() == null) {
            t.setStatus(1);
        }
        if (t.getVersion() == null) {
            t.setVersion(1);
        }
        t.setCreatedTime(LocalDateTime.now());
        t.setUpdatedTime(LocalDateTime.now());
        templateMapper.insert(t);
        saveVersion(t, "INITIAL");
        templateEngine.refreshCache();
        return t;
    }

    @Transactional
    public MsgTemplateDO update(Long id, MsgTemplateDO t, String changeNote, String operator) {
        MsgTemplateDO old = templateMapper.selectById(id);
        if (old == null) {
            throw new RuntimeException("模板不存在: id=" + id);
        }
        old.setSubject(t.getSubject());
        old.setContent(t.getContent());
        old.setStatus(t.getStatus() != null ? t.getStatus() : old.getStatus());
        old.setVersion(old.getVersion() + 1);
        old.setUpdatedTime(LocalDateTime.now());
        old.setUpdatedBy(operator);
        templateMapper.updateById(old);
        saveVersion(old, changeNote);
        templateEngine.refreshCache();
        log.info("[MsgTemplate] updated id={} version={} operator={}", id, old.getVersion(), operator);
        return old;
    }

    @Transactional
    public boolean delete(Long id) {
        templateMapper.deleteById(id);
        templateEngine.refreshCache();
        return true;
    }

    /**
     * 审批通过：status → 启用(1)，记录审批版本快照
     */
    @Transactional
    public MsgTemplateDO approve(Long id, String approver, String remark) {
        MsgTemplateDO t = templateMapper.selectById(id);
        if (t == null) {
            throw new RuntimeException("模板不存在: id=" + id);
        }

        t.setStatus(1);
        t.setVersion(t.getVersion() + 1);
        t.setUpdatedTime(LocalDateTime.now());
        t.setUpdatedBy(approver);
        templateMapper.updateById(t);
        saveVersion(t, "APPROVED: " + (remark != null ? remark : ""));
        templateEngine.refreshCache();
        log.info("[MsgTemplate] approved id={} version={} approver={}", id, t.getVersion(), approver);
        return t;
    }

    /**
     * 审批拒绝：status → 停用(2)，记录拒绝原因
     */
    @Transactional
    public MsgTemplateDO reject(Long id, String approver, String reason) {
        MsgTemplateDO t = templateMapper.selectById(id);
        if (t == null) {
            throw new RuntimeException("模板不存在: id=" + id);
        }
        t.setStatus(2);
        t.setUpdatedTime(LocalDateTime.now());
        t.setUpdatedBy(approver);
        templateMapper.updateById(t);
        saveVersion(t, "REJECTED: " + (reason != null ? reason : ""));
        templateEngine.refreshCache();
        log.info("[MsgTemplate] rejected id={} approver={} reason={}", id, approver, reason);
        return t;
    }

    private void saveVersion(MsgTemplateDO t, String note) {
        MsgTemplateVersionDO v = new MsgTemplateVersionDO();
        v.setTemplateId(t.getId());
        v.setVersion(t.getVersion());
        v.setSubject(t.getSubject());
        v.setContent(t.getContent());
        v.setChangeNote(note);
        v.setCreatedTime(LocalDateTime.now());
        v.setCreatedBy(t.getUpdatedBy());
        versionMapper.insert(v);
    }
}
