package com.zifang.z.msg.im.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zifang.z.msg.im.domain.entity.ImMemberDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 成员表 mapper。
 * <p>
 * 两个游标（{@code last_read_seq} / {@code cleared_seq}）的单调性写在 WHERE 里，
 * 而不是"读出来在 Java 里比一比再写回去"：前者是一条语句内的原子判断，
 * 后者在两个并发标已读之间会插入一次读-改-写竞态，把新值盖回旧值。
 */
public interface ImMemberMapper extends BaseMapper<ImMemberDO> {

    /**
     * 已读游标只能前进。
     *
     * @return 1 = 游标前进到 {@code seq}；0 = 传进来的值不比当前游标高（乱序的旧回执），
     *         或这一行根本不是他的会话
     */
    @Update("UPDATE z_msg_im_member SET last_read_seq = #{seq}, updated_time = CURRENT_TIMESTAMP "
            + "WHERE conversation_id = #{conversationId} AND user_id = #{userId} AND last_read_seq < #{seq}")
    int advanceReadSeq(@Param("conversationId") Long conversationId,
                       @Param("userId") Long userId,
                       @Param("seq") long seq);

    /**
     * "清空聊天记录"游标也只能前进：客户端重放一次旧的清空请求，不该把后来新增的消息一起藏掉。
     *
     * @return 1 = 游标推进；0 = 旧值或不是成员
     */
    @Update("UPDATE z_msg_im_member SET cleared_seq = #{seq}, cleared_time = CURRENT_TIMESTAMP, "
            + "updated_time = CURRENT_TIMESTAMP "
            + "WHERE conversation_id = #{conversationId} AND user_id = #{userId} AND cleared_seq < #{seq}")
    int advanceClearedSeq(@Param("conversationId") Long conversationId,
                          @Param("userId") Long userId,
                          @Param("seq") long seq);
}
