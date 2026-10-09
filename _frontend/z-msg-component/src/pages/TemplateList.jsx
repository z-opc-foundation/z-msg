/** 模板清单：/msg/template/list 分页（站内信 / 邮件 / 短信模板）。 */
import {useEffect, useState} from 'react'
import {Alert, Button, Card, Space, Table, Tag, Typography} from 'antd'
import {ReloadOutlined} from '@ant-design/icons'
import {msgApi} from '../services/api'

const {Title, Paragraph} = Typography

function channelTag(ch) {
    const map = {IN_APP: 'blue', EMAIL: 'purple', SMS: 'green', WEBHOOK: 'orange'}
    return <Tag color={map[ch] || 'default'}>{ch || '—'}</Tag>
}

export default function TemplateList() {
    const [rows, setRows] = useState([])
    const [page, setPage] = useState(1)
    const [size, setSize] = useState(20)
    const [total, setTotal] = useState(0)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState(null)

    const fetch = async () => {
        setLoading(true)
        try {
            const r = await msgApi.templatePage(page, size)
            const data = r?.records || r?.list || r?.data || []
            setRows(Array.isArray(data) ? data : [])
            setTotal(r?.total || data.length)
            setError(null)
        } catch (e) {
            setError(e?.message || String(e))
        } finally { setLoading(false) }
    }

    useEffect(() => { fetch() }, [page, size])

    const columns = [
        {title: 'code', dataIndex: 'code', key: 'code', width: 160},
        {title: '名称', dataIndex: 'name', key: 'name', width: 200, ellipsis: true},
        {title: '渠道', dataIndex: 'channel', key: 'channel', width: 100, render: channelTag},
        {title: '标题模板', dataIndex: 'titleTemplate', key: 'title', ellipsis: true},
        {title: '内容模板', dataIndex: 'contentTemplate', key: 'content', ellipsis: true},
        {title: '启用', dataIndex: 'enabled', key: 'enabled', width: 80,
            render: (v) => v ? <Tag color="green">启用</Tag> : <Tag color="default">停用</Tag>},
    ]

    return (
        <div>
            <Space style={{marginBottom: 16}}>
                <Title level={4} style={{margin: 0}}>消息模板</Title>
                <Button icon={<ReloadOutlined/>} onClick={fetch} loading={loading}>刷新</Button>
            </Space>
            <Paragraph type="secondary">站内信 / 邮件 / 短信模板清单（/msg/template/list）。</Paragraph>
            {error && <Alert type="error" showIcon style={{marginBottom: 16}} message="后端未连接" description={error}/>}
            <Card>
                <Table rowKey={(r, i) => r.id || r.code || i} dataSource={rows} columns={columns}
                       loading={loading} size="small"
                       pagination={{current: page, pageSize: size, total, showSizeChanger: true,
                                    onChange: (p, s) => { setPage(p); setSize(s) }}}/>
            </Card>
        </div>
    )
}
