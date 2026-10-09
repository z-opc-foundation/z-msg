import {MailOutlined, SendOutlined} from '@ant-design/icons'
import TemplateList from './pages/TemplateList'
import DeliveryLogs from './pages/DeliveryLogs'

export const menuItems = [
    {key: '/templates', icon: <MailOutlined/>, label: '消息模板'},
    {key: '/delivery', icon: <SendOutlined/>, label: '投递记录'},
]

const routeTable = [
    {path: 'templates', Component: TemplateList},
    {path: 'delivery', Component: DeliveryLogs},
]
export {routeTable}
export {default as TemplateList} from './pages/TemplateList'
export {default as DeliveryLogs} from './pages/DeliveryLogs'
