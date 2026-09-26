package com.zifang.z.msg.im.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zifang.z.msg.im.domain.entity.ImConversationDO;
import com.zifang.z.msg.im.domain.model.ImConversationView;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 会话表 mapper。
 * <p>
 * 这里只有一条手写 SQL：{@link #casAppend}。它是"会话内 seq 严格递增且不重号"的实现根据
 * ——把 {@code last_msg_seq} 的自增做成一次带条件的原子更新：
 * <ul>
 *   <li>{@code AND last_msg_seq = #{expect}} 是 CAS 谓词，两个线程拿同一个 expect 时
 *       只有一个能改到行（另一个在行锁上排队，等到后谓词已经不成立，影响 0 行）；</li>
 *   <li>水位与"最后一条消息是谁"写在同一条 UPDATE 里，所以 {@code last_msg_preview}
 *       永远对应库里最大的那个 seq，不会出现"水位已前进、摘要还是上一条"的中间态。</li>
 * </ul>
 * 没有用 {@code SELECT ... FOR UPDATE}：那要求一整套事务管理器，而本工程里
 * {@code sqlSessionFactoryMsg} 没有绑定的 {@code PlatformTransactionManager}，
 * 自动提交下 FOR UPDATE 的锁在语句结束就没了，等于没加锁。
 */
public interface ImConversationMapper extends BaseMapper<ImConversationDO> {

    /**
     * 原子占号：把 {@code last_msg_seq} 从 {@code expect} 抬到 {@code expect + 1}，
     * 同时写入本条消息的 id 与摘要。
     *
     * @return 1 = 抢到 {@code expect + 1} 这个 seq；0 = 水位已被别人抬走，调用方要重读重试
     */
    @Update("UPDATE z_msg_im_conversation SET last_msg_seq = #{expect} + 1, "
            + "last_msg_id = #{msgId}, last_msg_preview = #{preview}, updated_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND last_msg_seq = #{expect}")
    int casAppend(@Param("id") Long id,
                  @Param("expect") long expect,
                  @Param("msgId") Long msgId,
                  @Param("preview") String preview);

    /**
     * 成员数按成员表重算，不做 ±1 增量：增量写法在一次加人失败/一次踢人重试之后就会和真实
     * 行数漂移，而 {@code member_count} 是会话列表要展示的数字，宁可每次算准。
     */
    @Update("UPDATE z_msg_im_conversation SET member_count = "
            + "(SELECT COUNT(*) FROM z_msg_im_member WHERE conversation_id = #{id}), "
            + "updated_time = CURRENT_TIMESTAMP WHERE id = #{id}")
    int refreshMemberCount(@Param("id") Long id);

    /**
     * "我的会话列表"的投影：会话上的展示字段 join 上调用者自己的游标。
     * <p>
     * 排序按 {@code c.updated_time}（每条消息落库时由 {@link #casAppend} 一起推进），
     * 不是按成员的 {@code updated_time} —— 后者只反映"我自己动过这个会话"，
     * 别人发新消息不会把它顶到列表最前面。
     * <p>
     * 分页交给 {@code PaginationInnerInterceptor}（{@code MsgAutoConfiguration} 里挂在
     * {@code sqlSessionFactoryMsg} 上），所以这里必须把 {@code IPage} 放在第一位的参数位。
     */
    String MINE_SQL = "SELECT c.id AS conversationId, c.conv_type AS convType, "
            + "c.tenant_code AS tenantCode, c.title AS title, c.avatar AS avatar, "
            + "c.owner_user_id AS ownerUserId, c.last_msg_seq AS lastMsgSeq, "
            + "c.last_msg_id AS lastMsgId, c.last_msg_preview AS lastMsgPreview, "
            + "c.member_count AS memberCount, c.updated_time AS updatedTime, "
            + "c.created_time AS createdTime, m.role AS myRole, "
            + "m.last_read_seq AS myLastReadSeq, m.cleared_seq AS myClearedSeq, m.muted AS muted "
            + "FROM z_msg_im_member m JOIN z_msg_im_conversation c ON c.id = m.conversation_id "
            + "WHERE m.user_id = #{userId} ORDER BY c.updated_time DESC, c.id DESC";

    @Select(MINE_SQL)
    IPage<ImConversationView> selectMinePage(Page<ImConversationView> page,
                                             @Param("userId") Long userId);

    @Select(MINE_SQL)
    java.util.List<ImConversationView> selectMineList(@Param("userId") Long userId);
}
