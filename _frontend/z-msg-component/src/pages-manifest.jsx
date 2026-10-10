import { HomeOutlined, MailOutlined, SendOutlined } from '@ant-design/icons'
import TemplateList from './pages/TemplateList'
import DeliveryLogs from './pages/DeliveryLogs'


export {default as TemplateList} from './pages/TemplateList'
export {default as DeliveryLogs} from './pages/DeliveryLogs'
import HomePage from './pages/HomePage'
import Inbox from './pages/Inbox.jsx'
import BatchTasks from './pages/BatchTasks.jsx'
import MsgApp from './pages/MsgApp.jsx'

/** 菜单 + 路由清单（lead 008 §10/§14/§16 批量落地）。App 壳在 suit 侧组装。 */
export const appMeta = { title: 'z-msg 消息中心', short: 'z-msg' }

export const menuItems = [
    { key: '/z-msg/home', label: '首页', icon: <HomeOutlined /> },
    { key: '/z-msg/templates', label: '消息模板', icon: <MailOutlined /> },
    { key: '/z-msg/delivery', label: '投递记录', icon: <SendOutlined /> },
]

export const routes = [
    { path: '/z-msg/home', Component: HomePage },
    { path: '/z-msg/inbox', title: '收件箱', order: 1, Component: Inbox },
    { path: '/z-msg/templates', title: '消息模板', order: 2, Component: TemplateList },
    { path: '/z-msg/delivery', title: '投递记录', order: 3, Component: DeliveryLogs },
    { path: '/z-msg/batch', title: '批量任务', order: 4, Component: BatchTasks },
    { path: '/z-msg/:rest*', Component: MsgApp },
    { path: '/z-msg/delivery', Component: DeliveryLogs },
]

export { default as HomePage } from './pages/HomePage'
export { default as LoginPage } from './pages/LoginPage'
