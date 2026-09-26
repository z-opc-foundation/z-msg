package com.zifang.z.msg.im.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zifang.z.msg.im.domain.entity.ImMessageDO;

/**
 * 消息表 mapper。
 * <p>
 * 这里一条手写 SQL 都没有，是刻意的：会话内增量拉取就是
 * {@code WHERE conversation_id = ? AND seq > ? AND seq <= ? ORDER BY seq LIMIT ?}，
 * 用 {@code LambdaQueryWrapper} 表达即可，写成语句反而多一处双方言要维护的地方。
 * <p>
 * 并发正确性由表上的两个唯一索引兜底（{@code uk_im_msg_conv_seq} /
 * {@code uk_im_msg_conv_client}），占号逻辑见
 * {@link com.zifang.z.msg.im.domain.service.ImSequencer}。
 */
public interface ImMessageMapper extends BaseMapper<ImMessageDO> {
}
