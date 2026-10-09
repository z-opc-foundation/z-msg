/** 投递清单 + 统计：/msg/delivery/list + /msg/delivery/stats。 */
import {useEffect, useState} from 'react'
import {Alert, Button, Card, Col, Row, Space, Statistic, Table, Tag, Typography} from 'antd'
import {ReloadOutlined} from '@ant-design/icons'
import {msgApi} from '../services/api'

const {Title, Paragraph} = Typography

function statusTag(s) {
    const map = {SENT: 'green', PENDING: 'gold', FAILED: 'red', CANCELED: 'default'}
    return <Tag color={map[s] || 'default'}>{s || '—'}</Tag>
}

export default function DeliveryLogs() {
    const [rows, setRows] = useState([])
    const [stats, setStats] = useState(null)
    const [page, setPage] = useState(1)
    const [size, setSize] = useState(20)
    const [total, setTotal] = useState(0)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState(null)

    const fetch = async () => {
        setLoading(true)
        try {
            const [r, s] = await Promise.all([msgApi.deliveryPage(page, size), msgApi.deliveryStats().catch(() => null)])
            const data = r?.records || r?.list || r?.data || []
            setRows(Array.isArray(data) ? data : [])
            setTotal(r?.total || data.length)
            setStats(s)
            setError(null)
        } catch (e) {
            setError(e?.message || String(e))
        } finally { setLoading(false) }
    }

    useEffect(() => { fetch() }, [page, size])

    const columns = [
        {title: 'ID', dataIndex: 'id', key: 'id', width: 80},
        {title: '收件人', dataIndex: 'recipient', key: 'recipient', width: 160, ellipsis: true},
        {title: '渠道', dataIndex: 'channel', key: 'channel', width: 100},
        {title: '模板', dataIndex: 'templateCode', key: 'tpl', width: 140, ellipsis: true},
        {title: '状态', dataIndex: 'status', key: 'status', width: 100, render: statusTag},
        {title: '重试次数', dataIndex: 'retryCount', key: 'retry', width: 100},
        {title: '最后错误', dataIndex: 'lastError', key: 'err', ellipsis: true},
        {title: '时间', dataIndex: 'createdAt', key: 'when', width: 160,
            render: (ts) => ts ? new Date(ts).toLocaleString('zh-CN') : '—'},
    ]

    return (
        <div>
            <Space style={{marginBottom: 16}}>
                <Title level={4} style={{margin: 0}}>投递清单</Title>
                <Button icon={<ReloadOutlined/>} onClick={fetch} loading={loading}>刷新</Button>
            </Space>
            <Paragraph type="secondary">消息投递记录（/msg/delivery/list）+ 全局统计（/msg/delivery/stats）。</Paragraph>

            {error && <Alert type="error" showIcon style={{marginBottom: 16}} message="后端未连接" description={error}/>}

            {stats && (
                <Row gutter={16} style={{marginBottom: 16}}>
                    <Col span={8}><Card><Statistic title="已发送" value={stats.sent || 0} valueStyle={{color: '#3f8600'}}/></Card></Col>
                    <Col span={8}><Card><Statistic title="待投递" value={stats.pending || 0} valueStyle={{color: '#faad14'}}/></Card></Col>
                    <Col span={8}><Card><Statistic title="失败" value={stats.failed || 0} valueStyle={{color: '#cf1322'}}/></Card></Col>
                </Row>
            )}

            <Card>
                <Table rowKey={(r, i) => r.id || i} dataSource={rows} columns={columns}
                       loading={loading} size="small" scroll={{x: 1000}}
                       pagination={{current: page, pageSize: size, total, showSizeChanger: true,
                                    onChange: (p, s) => { setPage(p); setSize(s) }}}/>
            </Card>
        </div>
    )
}
