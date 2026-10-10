import {Progress, Tag} from 'antd'
import {msgApi, BATCH_STATUS} from '../services/api'
import {PagedTable} from '@/common/components/ui'

export default function BatchTasks() {
    return (
        <PagedTable
            title="批量发送任务"
            description="批量任务的进度与失败数（数据源 POST /api/msg/batch/list）"
            load={(page, size) => msgApi.batchPage(page, size)}
            columns={[
                {title: '业务类型', dataIndex: 'biz_type', key: 'biz_type', width: 150},
                {title: '渠道', dataIndex: 'channel', key: 'channel', width: 110, render: (v) => <Tag>{v}</Tag>},
                {
                    title: '进度', key: 'progress', width: 160,
                    render: (_, r) => {
                        const total = Number(r.total_count) || 0
                        const done = (Number(r.success_count) || 0) + (Number(r.failed_count) || 0)
                        return <Progress percent={total ? Math.round(done * 100 / total) : 0} size="small"/>
                    }
                },
                {title: '总数', dataIndex: 'total_count', key: 'total_count', width: 70},
                {
                    title: '成功', dataIndex: 'success_count', key: 'success_count', width: 70,
                    render: (v) => <span style={{color: '#3f8600'}}>{v}</span>
                },
                {
                    title: '失败', dataIndex: 'failed_count', key: 'failed_count', width: 70,
                    render: (v) => (Number(v) > 0
                        ? <span style={{color: '#cf1322'}}>{v}</span>
                        : v)
                },
                {
                    title: '状态', dataIndex: 'status', key: 'status', width: 110,
                    render: (v) => {
                        const s = BATCH_STATUS[v] || {text: `未知(${v})`, color: 'default'}
                        return <Tag color={s.color}>{s.text}</Tag>
                    }
                },
                {title: '失败原因', dataIndex: 'error_message', key: 'error_message', ellipsis: true, render: (v) => v || '-'},
                {title: '创建时间', dataIndex: 'created_time', key: 'created_time', width: 180},
                {title: '结束时间', dataIndex: 'finished_time', key: 'finished_time', width: 180, render: (v) => v || '-'},
            ]}
        />
    )
}
