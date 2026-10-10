import {Navigate, Route, Routes} from 'react-router-dom'
import Inbox from './Inbox'
import TemplateList from './TemplateList'
import DeliveryLogs from './DeliveryLogs'
import BatchTasks from './BatchTasks'
import {currentUserId} from '../services/api'

/** z-msg 消息中心 — OpsWorkbench 以 /msg/* 通配挂进来 */
export default function MsgApp() {
    const userId = currentUserId()
    return (
        <Routes>
            <Route index element={<Navigate to="inbox" replace/>}/>
            <Route path="inbox" element={<Inbox userId={userId}/>}/>
            <Route path="template" element={<TemplateList/>}/>
            <Route path="delivery" element={<DeliveryLogs/>}/>
            <Route path="batch" element={<BatchTasks/>}/>
        </Routes>
    )
}
