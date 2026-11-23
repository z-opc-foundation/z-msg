import {useCallback, useEffect, useMemo, useRef, useState} from 'react'
import {Alert, Button, Card, Col, Form, Input, Row, Select, Space, Tag, Typography} from 'antd'
import {SendOutlined} from '@ant-design/icons'
import {msgApi} from '../services/api'

const {Text} = Typography

/**
 * 站内信的收件人字段是 userId（数字），其余通道是「地址」（手机/邮箱/机器人 webhook）。
 * 这个差别由服务端校验决定（receiver 与 userId 至少给一个），表单跟着变，免得填错再吃一次 400。
 */
const IN_APP = 'IN_APP'

/** 通道中文名：服务端只给通道码，界面上给个人话。 */
const CHANNEL_LABEL = {
    IN_APP: '站内信',
    SMS: '短信',
    EMAIL: '邮件',
    WEBHOOK: 'Webhook',
    IM_WECOM: '企业微信',
    IM_DINGTALK: '钉钉',
    IM_FEISHU: '飞书',
    IM_WEIXIN_MP: '微信公众号',
    IM_SLACK: 'Slack',
    PUSH_FCM: 'FCM 推送',
    PUSH_APNS: 'APNs 推送',
    PUSH_JPUSH: '极光推送',
    PUSH_WEB: 'Web 推送',
    REALTIME: '实时通道',
}

export default function SendMessage() {
    const [form] = Form.useForm()
    const [channels, setChannels] = useState([])
    const channel = Form.useWatch('channel', form) ?? IN_APP
    const [loadingChannels, setLoadingChannels] = useState(true)
    const [sending, setSending] = useState(false)
    const sendingRef = useRef(false)
    const [result, setResult] = useState(null)
    const [error, setError] = useState(null)

    const [channelError, setChannelError] = useState(null)
    const channelRequest = useRef(0)
    const loadChannels = useCallback(async () => {
        const version = ++channelRequest.current
        setLoadingChannels(true)
        setChannelError(null)
        try {
            const rows = await msgApi.channels()
            if (!Array.isArray(rows)) throw new Error('通道列表响应格式错误')
            if (version === channelRequest.current) setChannels(rows)
        } catch (e) {
            if (version === channelRequest.current) setChannelError(e)
        } finally {
            if (version === channelRequest.current) setLoadingChannels(false)
        }
    }, [])

    useEffect(() => {
        loadChannels()
        return () => { ++channelRequest.current }
    }, [loadChannels])

    const channelMeta = useMemo(
        () => channels.find((c) => c.channel === channel),
        [channels, channel],
    )

    /** 只有真通道才标绿；mock 兜底的发出去「成功」但对方收不到，必须让人看见。 */
    const options = useMemo(() => {
        const known = channels.length > 0
            ? channels.map((c) => c.channel)
            : Object.keys(CHANNEL_LABEL)
        return known.map((c) => ({
            value: c,
            label: (
                <Space size={6}>
                    <span>{CHANNEL_LABEL[c] || c}</span>
                    <Text type="secondary" style={{fontSize: 12}}>{c}</Text>
                    {channels.length > 0 && (
                        <Tag color={channels.find((x) => x.channel === c)?.real ? 'green' : 'default'}>
                            {channels.find((x) => x.channel === c)?.real ? '真通道' : '仅 mock'}
                        </Tag>
                    )}
                </Space>
            ),
        }))
    }, [channels])

    const isInApp = channel === IN_APP

    const handleSend = async () => {
        // 同步锁覆盖校验等待期，避免连续提交在下一次渲染前同时进入。
        if (sendingRef.current || channelError) return
        sendingRef.current = true
        setSending(true)
        setResult(null)
        setError(null)
        try {
            const values = await form.validateFields()
            // 字段名用 snake_case：宿主（z-team）的全局 Jackson 策略是 SNAKE_CASE，
            //   camelCase 的键会被**静默丢掉**（不报错，字段变 null）。
            //   服务端 DTO 同时接受 camelCase（@JsonAlias），但客户端按平台口径发 snake_case。
            const payload = {
                channel: values.channel,
                subject: values.subject,
                content: values.content,
                link_url: values.linkUrl || undefined,
                biz_type: values.bizType || undefined,
                idempotency_key: values.idempotencyKey || undefined,
            }
            if (values.channel === IN_APP) {
                payload.user_id = Number(values.receiver)
            } else {
                payload.receiver = values.receiver
            }
            const data = await msgApi.send(payload)
            setResult(data)
            form.resetFields()
        } catch (e) {
            if (!e?.errorFields) setError(e)
        } finally {
            sendingRef.current = false
            setSending(false)
        }
    }

    return (
        <div>
            {channelError && <Alert type="error" showIcon style={{marginBottom: 12}}
                title="通道加载失败" description={channelError.message || String(channelError)}
                action={<Button onClick={loadChannels}>重试</Button>}/>}

            <Card size="small" title="发消息" loading={loadingChannels}>
                <Form form={form} layout="vertical" onFinish={handleSend} disabled={sending}
                      initialValues={{channel: IN_APP, bizType: 'MANUAL_SEND'}}>
                    <Row gutter={16}>
                        <Col xs={24} md={8}>
                            <Form.Item name="channel" label="通道" rules={[{required: true}]}>
                                <Select options={options}/>
                            </Form.Item>
                        </Col>
                        <Col xs={24} md={8}>
                            <Form.Item
                                name="receiver"
                                label={isInApp ? '收件人用户 ID' : '收件地址'}
                                rules={[
                                    {required: true, message: isInApp ? '请填用户 ID' : '请填收件地址'},
                                    ...(isInApp
                                        ? [{pattern: /^\d+$/, message: '站内信收件人是数字用户 ID'}]
                                        : []),
                                ]}
                            >
                                <Input
                                    placeholder={isInApp ? '如 1001' : '如 13800000000 / a@example.com'}/>
                            </Form.Item>
                        </Col>
                        <Col xs={24} md={8}>
                            <Form.Item name="subject" label="标题">
                                <Input placeholder="可空"/>
                            </Form.Item>
                        </Col>
                    </Row>

                    <Form.Item name="content" label="正文" rules={[{required: true, message: '请填正文'}]}>
                        <Input.TextArea rows={5} placeholder="要发给对方的内容"/>
                    </Form.Item>

                    <Row gutter={16}>
                        <Col xs={24} md={8}>
                            <Form.Item name="linkUrl" label="跳转链接">
                                <Input placeholder="可空，如 https://..."/>
                            </Form.Item>
                        </Col>
                        <Col xs={24} md={8}>
                            <Form.Item name="bizType" label="业务类型">
                                <Input placeholder="留空则归档为 DIRECT_SEND"/>
                            </Form.Item>
                        </Col>
                        <Col xs={24} md={8}>
                            <Form.Item name="idempotencyKey" label="幂等键">
                                <Input placeholder="可空；同键重复发送会被服务端判重"/>
                            </Form.Item>
                        </Col>
                    </Row>

                    {channelMeta && !channelMeta.real && (
                        <Alert type="warning" showIcon style={{marginBottom: 12}}
                               message={`${CHANNEL_LABEL[channel] || channel} 当前只有 mock 兜底 —— 发送会返回成功，但不会真的到达对方`}/>
                    )}

                    <Button type="primary" htmlType="submit" icon={<SendOutlined/>} loading={sending}
                            disabled={loadingChannels || Boolean(channelError)}>
                        发送
                    </Button>
                </Form>
            </Card>

            {error && (
                <Alert type="error" showIcon style={{marginTop: 12}}
                       message="发送失败"
                       description={error?.message || String(error)}/>
            )}
            {result && (
                <Alert type="success" showIcon style={{marginTop: 12}}
                       message="已发出"
                       description={
                           <Space direction="vertical" size={2}>
                               <span>消息 ID：<Text code>{result.msgId}</Text></span>
                               <span>通道：{result.channel} · 投递方：{result.provider || '—'} · 业务类型：{result.bizType || '—'}</span>
                           </Space>
                       }/>
            )}
        </div>
    )
}
