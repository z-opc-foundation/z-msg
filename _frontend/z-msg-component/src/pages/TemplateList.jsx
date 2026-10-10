import {Tag} from 'antd'
import {msgApi, TEMPLATE_STATUS} from '../services/api'
import {PagedTable} from '@/common/components/ui'

export default function TemplateList() {
    return (
        <PagedTable
            title="消息模板"
            description="按渠道/业务类型维护的消息模板（数据源 POST /api/msg/template/list）"
            load={(page, size) => msgApi.templatePage(page, size)}
            columns={[
                {title: '业务类型', dataIndex: 'biz_type', key: 'biz_type', width: 160},
                {
                    title: '渠道', dataIndex: 'channel', key: 'channel', width: 120,
                    render: (v) => <Tag color="geekblue">{v}</Tag>
                },
                {title: '主题', dataIndex: 'subject', key: 'subject', width: 220, ellipsis: true},
                {title: '正文模板', dataIndex: 'content', key: 'content', ellipsis: true},
                {title: '版本', dataIndex: 'version', key: 'version', width: 80},
                {
                    title: '状态', dataIndex: 'status', key: 'status', width: 90,
                    render: (v) => {
                        const s = TEMPLATE_STATUS[v] || {text: `未知(${v})`, color: 'default'}
                        return <Tag color={s.color}>{s.text}</Tag>
                    }
                },
                {title: '更新时间', dataIndex: 'updated_time', key: 'updated_time', width: 180},
            ]}
        />
    )
}
