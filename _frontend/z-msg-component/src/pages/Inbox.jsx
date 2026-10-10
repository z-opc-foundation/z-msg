import {useEffect, useState} from 'react'
import {Alert, Card, Table, Tag} from 'antd'
import {msgApi} from '../services/api'
import {EmptyState, PageHeader} from '@/common/components/ui'

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
            <Card>
                {error ? (
                    <Alert type="error" showIcon
                           message={`站内信读取失败：${error.message || error}`}/>
                ) : (
                    <Table
                        size="small"
                        rowKey="id"
                        loading={loading}
                        dataSource={rows}
                        pagination={{pageSize: 20, showTotal: (t) => `共 ${t} 条`}}
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
                        locale={{emptyText: <EmptyState title="收件箱为空" description="接口返回空列表"/>}}
                    />
                )}
            </Card>
        </div>
    )
}
