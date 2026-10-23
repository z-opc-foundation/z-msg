import {Navigate, Route, Routes} from 'react-router-dom'
import Inbox from './Inbox'
import TemplateList from './TemplateList'
import DeliveryLogs from './DeliveryLogs'
import BatchTasks from './BatchTasks'

/**
 * z-msg 消息中心 — OpsWorkbench 以 /msg/* 通配挂进来
 *
 * ⚠ 收件箱**不再需要 userId prop**：身份由服务端从 SSO 凭证解析
 *   （`GET /api/msg/inbox/list` 不带 userId）。2026-10-23 起本组件不再读
 *   localStorage 的 userId / USER_INFO 来拼参数（那是旧的越权契约）。
 */
export default function MsgApp() {
    return (
        <Routes>
            <Route index element={<Navigate to="inbox" replace/>}/>
            <Route path="inbox" element={<Inbox/>}/>
            <Route path="template" element={<TemplateList/>}/>
            <Route path="delivery" element={<DeliveryLogs/>}/>
            <Route path="batch" element={<BatchTasks/>}/>
        </Routes>
    )
}
