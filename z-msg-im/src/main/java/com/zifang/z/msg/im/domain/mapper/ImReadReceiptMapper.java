package com.zifang.z.msg.im.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zifang.z.msg.im.domain.entity.ImReadReceiptDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 已读回执表 mapper。
 * <p>
 * upsert 由服务层用"条件更新 → 0 行则插入 → 撞唯一索引再更新一次"三步实现，
 * 没有用 {@code ON DUPLICATE KEY UPDATE}：那是 MySQL 方言，H2 虽然在 MySQL 模式下认，
 * 但它的 affected-rows 语义和 MySQL 不一致，测试会绿在生产会偏。
 * 唯一索引 {@code uk_im_receipt_conv_user} 是这三步的收敛保证。
 */
public interface ImReadReceiptMapper extends BaseMapper<ImReadReceiptDO> {

    /**
     * 回执游标同样只前进不回退。
     *
     * @return 1 = 推进；0 = 行不存在，或带来的值不比现有值高
     */
    @Update("UPDATE z_msg_im_read_receipt SET last_read_seq = #{seq}, updated_time = CURRENT_TIMESTAMP "
            + "WHERE conversation_id = #{conversationId} AND user_id = #{userId} AND last_read_seq < #{seq}")
    int advanceReadSeq(@Param("conversationId") Long conversationId,
                       @Param("userId") Long userId,
                       @Param("seq") long seq);
}
