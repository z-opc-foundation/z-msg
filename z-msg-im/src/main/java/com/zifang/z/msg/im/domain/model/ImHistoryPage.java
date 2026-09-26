package com.zifang.z.msg.im.domain.model;

import com.zifang.z.msg.im.domain.entity.ImMessageDO;

import java.util.Collections;
import java.util.List;

/**
 * {@code POST /api/msg/im/message/history} 的返回体。
 * <p>
 * 1.1.0 回的是一个裸数组，客户端拿到之后只能猜三件事，而这三件事在库里都没有：
 * <ol>
 *   <li><b>还有没有</b>：请求里的 {@code size} 会被服务端按 {@code z-msg.im.max-page-size}
 *       悄悄夹掉，"正好回了 size 条"和"回满了但后面还有"长得一模一样；</li>
 *   <li><b>下次从几开始</b>：靠 {@code rows.get(size-1).seq}，空页就得自己记住上次的游标；</li>
 *   <li><b>少掉的那段是我的问题还是数据的问题</b>：{@code seq} 允许有空洞（占号 CAS 成功而随后的
 *       INSERT 失败会留下一个没有消息的号），自己 {@code /clear} 过的区间也不该再拉回来。
 *       新设备上这两个信息都不在客户端手里。</li>
 * </ol>
 * 所以这里把判定要的量原样交出去。空洞仍然不由服务端断言真伪——它只保证
 * "客户端能自己算出少了哪一段、以及那一段是不是被自己的可见游标挡掉的"。
 */
public class ImHistoryPage {

    /** 本页消息，按 {@code seq} 升序；条数不超过服务端夹过的那个大写。 */
    private final List<ImMessageDO> rows;
    /** 下次请求该传的 {@code sinceSeq}：非空页就是最后一条的 seq，空页就是本次生效的下限。 */
    private final long nextSinceSeq;
    /** 本页之后是否还有对本人可见的消息（服务端多读一条探出来的，不是拿 size 猜的）。 */
    private final boolean hasMore;
    /** 读取时刻的会话水位（{@code last_msg_seq}）。客户端据此判断"我追平了没有"。 */
    private final long headSeq;
    /** 本次实际生效的可见下界：{@code max(请求的 sinceSeq, 本人的 cleared_seq) + 1}。 */
    private final long minVisibleSeq;

    public ImHistoryPage(List<ImMessageDO> rows, long nextSinceSeq, boolean hasMore,
                         long headSeq, long minVisibleSeq) {
        this.rows = rows == null ? Collections.<ImMessageDO>emptyList() : rows;
        this.nextSinceSeq = nextSinceSeq;
        this.hasMore = hasMore;
        this.headSeq = headSeq;
        this.minVisibleSeq = minVisibleSeq;
    }

    public List<ImMessageDO> getRows() {
        return rows;
    }

    public long getNextSinceSeq() {
        return nextSinceSeq;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public long getHeadSeq() {
        return headSeq;
    }

    public long getMinVisibleSeq() {
        return minVisibleSeq;
    }
}
