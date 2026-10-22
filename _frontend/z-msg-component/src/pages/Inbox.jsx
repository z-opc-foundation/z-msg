import {useEffect, useState} from 'react'
import {Alert, Card, Tag} from 'antd'
import {msgApi} from '../services/api'
import {ListTable, PageHeader} from '@yuku123/z-frontend-common/ui'

const READ = {0: {text: '未读', color: 'blue'}, 1: {text: '已读', color: 'default'}}

export default function Inbox({userId}) {
    const [rows, setRows] = useState([])
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState(null)

    useEffect(() => {
        if (!userId) return
        let cancelled = false
        setLoading(true)
        msgApi.inbox(userId, 50)
            .then((data) => {
                if (!cancelled) {
                    setRows(data || [])
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
    }, [userId])

    if (!userId) {
        return (
            <div>
                <PageHeader title="站内信"/>
                <Alert type="warning" showIcon message="未取到当前登录用户 id"
                       description="GET /api/msg/list 需要 userId。本页依次读 localStorage 的 userInfo 与 USER_INFO（staffId/jobNumber），两个都没有才会走到这里 —— 请重新登录。"/>
            </div>
        )
    }

    return (
        <div>
            <PageHeader title="站内信"
                        description={`当前用户 ${userId} 的收件箱（数据源 GET /api/msg/list?userId=${userId}）`}/>
            {error ? (
                <Card>
                    <Alert type="error" showIcon
                           message={`站内信读取失败：${error.message || error}`}/>
                </Card>
            ) : (
                /* 收件箱一次取回最近 50 条、翻页在前端做 ⇒ 走基座 ListTable：
                   分页/过滤/空态三件套由基座统一承接。 */
                <ListTable
                    rows={rows}
                    rowKey="id"
                    loading={loading}
                    searchKeys="title,content,event_type"
                    searchPlaceholder="按标题 / 内容 / 事件类型过滤"
                    emptyTitle="收件箱为空"
                    emptyDescription="接口返回空列表"
                    columns={[
                        {title: '标题', dataIndex: 'title', key: 'title'},
                        {title: '内容', dataIndex: 'content', key: 'content', ellipsis: true},
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
