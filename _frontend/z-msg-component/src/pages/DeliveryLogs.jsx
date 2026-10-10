import {useEffect, useState} from 'react'
import {Alert, Col, Row, Statistic, Tag} from 'antd'
import {msgApi, DELIVERY_STATUS} from '../services/api'
import {PagedTable} from '@/common/components/ui'

function StatsBanner() {
    const [stats, setStats] = useState(null)
    const [error, setError] = useState(null)

    useEffect(() => {
        let cancelled = false
        msgApi.deliveryStats()
            .then((d) => {
                if (!cancelled) setStats(d)
            })
            .catch((e) => {
                if (!cancelled) setError(e)
            })
        return () => {
            cancelled = true
        }
    }, [])

    if (error) {
        return <Alert type="warning" showIcon style={{marginBottom: 12}}
                      message={`投递统计读取失败：${error.message || error}`}/>
    }
    if (!stats) return null

    return (
        <Row gutter={16} style={{marginBottom: 12}}>
            <Col span={6}><Statistic title="投递总数" value={stats.total ?? 0}/></Col>
            <Col span={6}><Statistic title="成功" value={stats.success ?? 0} valueStyle={{color: '#3f8600'}}/></Col>
            <Col span={6}><Statistic title="失败" value={stats.failed ?? 0} valueStyle={{color: '#cf1322'}}/></Col>
            <Col span={6}>
                <Statistic title="成功率"
                           value={Number(stats.total) > 0
                               ? (Number(stats.success) * 100 / Number(stats.total)).toFixed(1)
                               : '0.0'} suffix="%"/>
            </Col>
        </Row>
    )
}

export default function DeliveryLogs() {
    return (
        <PagedTable
            title="投递日志"
            description="每条消息按渠道投递的结果与失败原因（数据源 POST /api/msg/delivery/list + GET /api/msg/delivery/stats）"
            load={(page, size) => msgApi.deliveryPage(page, size)}
            extra={<StatsBanner/>}
            columns={[
                {title: '业务类型', dataIndex: 'biz_type', key: 'biz_type', width: 140},
                {title: '渠道', dataIndex: 'channel', key: 'channel', width: 110, render: (v) => <Tag>{v}</Tag>},
                {title: '接收方', dataIndex: 'receiver', key: 'receiver', width: 140},
                {title: '主题', dataIndex: 'rendered_subject', key: 'rendered_subject', width: 180, ellipsis: true},
                {
                    title: '状态', dataIndex: 'status', key: 'status', width: 100,
                    render: (v) => {
                        const s = DELIVERY_STATUS[v] || {text: `未知(${v})`, color: 'default'}
                        return <Tag color={s.color}>{s.text}</Tag>
                    }
                },
                {
                    title: '失败原因', key: 'reason', ellipsis: true,
                    render: (_, r) => r.error_code
                        ? <span style={{color: '#cf1322'}}>{r.error_code}{r.error_message ? ` · ${r.error_message}` : ''}</span>
                        : '-'
                },
                {title: '重试', dataIndex: 'retry_count', key: 'retry_count', width: 70},
                {title: '时间', dataIndex: 'created_time', key: 'created_time', width: 180},
            ]}
        />
    )
}
