import {useEffect, useState} from 'react'
import {Alert, Card, Tag} from 'antd'
import {msgApi} from '../services/api'
import {ListTable, PageHeader} from '@yuku123/z-frontend-common/ui'

const READ = {0: {text: '未读', color: 'blue'}, 1: {text: '已读', color: 'default'}}

/**
 * 收件箱列表的两种响应形态都收下：
 *  - `Result<IPage<InAppMessage>>`（z-msg 1.1.0 起的正式契约）⇒ 取 `records`
 *  - 裸数组（万一宿主挂的是老版本 z-msg）⇒ 直接当 rows
 * 认不出记录集时一律给空数组 ⇒ 空态由基座 EmptyState 承接，不报错页。
 */
function pickRecords(data) {
    if (Array.isArray(data)) return data
    if (data && Array.isArray(data.records)) return data.records
    return []
}

/**
 * 收件箱。
 *
 * ⚠ **本页不传 userId**：z-msg 1.1.0 起收件箱契约是 `GET /api/msg/inbox/list`，
 *   身份由服务端从 SSO 凭证解析（宿主注册的 `MsgPrincipalResolver`），
 *   归属过滤在服务端 SQL 的 WHERE 里。
 *   旧契约 `GET /api/msg/list?userId=&limit=` 已下线（生产实测 404 3/3），
 *   且「客户端自报 userId」正是 z-msg 1.0.0 越权读别人收件箱的成因 ⇒ **不得回退**。
 */
export default function Inbox() {
    const [rows, setRows] = useState([])
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState(null)

    useEffect(() => {
        let cancelled = false
        setLoading(true)
        msgApi.inbox(1, 50)
            .then((data) => {
                if (!cancelled) {
                    setRows(pickRecords(data))
                    setError(null)
                }
            })
            .catch((e) => {
                if (!cancelled) {
                    setRows([])
                    setError(e)
                }
            })
            .finally(() => {
                if (!cancelled) setLoading(false)
            })
        return () => {
            cancelled = true
        }
    }, [])

    return (
        <div>
            <PageHeader title="站内信"
                        description="当前登录用户的收件箱（数据源 GET /api/msg/inbox/list，身份由服务端从 SSO 凭证解析）"/>
            {error ? (
                <Card>
                    <Alert type="error" showIcon
                           message={`站内信读取失败：${error.message || error}`}/>
                </Card>
            ) : (
                /* 收件箱一次取回最近 50 条、翻页在前端做 ⇒ 走基座 ListTable：
                   分页/过滤/空态三件套由基座统一承接。
                   ⚠ 列里**没有 content**：新契约的服务端投影刻意不返回正文
                     （省掉一列 BIGTEXT，换来的是收件箱页不再渲染空列）。 */
                <ListTable
                    rows={rows}
                    rowKey="id"
                    loading={loading}
                    searchKeys="title,event_type,msg_type"
                    searchPlaceholder="按标题 / 事件类型 / 消息大类过滤"
                    emptyTitle="收件箱为空"
                    emptyDescription="接口返回空列表"
                    columns={[
                        {title: '标题', dataIndex: 'title', key: 'title'},
                        {
                            title: '消息大类', dataIndex: 'msg_type', key: 'msg_type', width: 120,
                            render: (v) => (v ? <Tag color="geekblue">{v}</Tag> : '-')
                        },
                        {
                            title: '事件类型', dataIndex: 'event_type', key: 'event_type', width: 150,
                            render: (v) => <Tag>{v}</Tag>
                        },
                        {
                            title: '已读', dataIndex: 'is_read', key: 'is_read', width: 90,
                            render: (v) => {
                                const s = READ[v] || {text: `未知(${v})`, color: 'default'}
                                return <Tag color={s.color}>{s.text}</Tag>
                            }
                        },
                        {title: '时间', dataIndex: 'created_time', key: 'created_time', width: 180},
                    ]}
                />
            )}
        </div>
    )
}
