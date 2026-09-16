package com.zifang.z.msg.core.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zifang.z.msg.core.domain.entity.MsgDeliveryLogDO;
import com.zifang.z.msg.core.domain.mapper.MsgDeliveryLogMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 投递日志服务 (Phase 2)
 */
@Service
public class MsgDeliveryLogService {

    @Resource
    private MsgDeliveryLogMapper mapper;

    public IPage<MsgDeliveryLogDO> page(int pageNum, int pageSize,
                                        String bizType, String channel, Integer status, Long userId) {
        LambdaQueryWrapper<MsgDeliveryLogDO> qw = new LambdaQueryWrapper<>();
        if (bizType != null && !bizType.isEmpty()) {
            qw.eq(MsgDeliveryLogDO::getBizType, bizType);
        }
        if (channel != null && !channel.isEmpty()) {
            qw.eq(MsgDeliveryLogDO::getChannel, channel);
        }
        if (status != null) {
            qw.eq(MsgDeliveryLogDO::getStatus, status);
        }
        if (userId != null) {
            qw.eq(MsgDeliveryLogDO::getUserId, userId);
        }
        qw.orderByDesc(MsgDeliveryLogDO::getCreatedTime);
        return mapper.selectPage(new Page<>(pageNum, pageSize), qw);
    }

    public Long countSuccess(LocalDateTime from, LocalDateTime to, String channel) {
        LambdaQueryWrapper<MsgDeliveryLogDO> qw = new LambdaQueryWrapper<>();
        qw.eq(MsgDeliveryLogDO::getStatus, 1);
        if (channel != null && !channel.isEmpty()) {
            qw.eq(MsgDeliveryLogDO::getChannel, channel);
        }
        if (from != null) {
            qw.ge(MsgDeliveryLogDO::getCreatedTime, from);
        }
        if (to != null) {
            qw.le(MsgDeliveryLogDO::getCreatedTime, to);
        }
        return mapper.selectCount(qw);
    }

    public Long countFailed(LocalDateTime from, LocalDateTime to, String channel) {
        LambdaQueryWrapper<MsgDeliveryLogDO> qw = new LambdaQueryWrapper<>();
        qw.eq(MsgDeliveryLogDO::getStatus, 2);
        if (channel != null && !channel.isEmpty()) {
            qw.eq(MsgDeliveryLogDO::getChannel, channel);
        }
        if (from != null) {
            qw.ge(MsgDeliveryLogDO::getCreatedTime, from);
        }
        if (to != null) {
            qw.le(MsgDeliveryLogDO::getCreatedTime, to);
        }
        return mapper.selectCount(qw);
    }
}
