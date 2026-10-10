import {Navigate, Route, Routes} from 'react-router-dom'
import {AppLayout} from '../../../../_shared/z-frontend-common-local/dist/z-frontend-common.es.js'
import {menuItems, routeTable} from '@yuku123/z-msg-component/pages'

export default function App() {
    return (
        <Routes>
            <Route path="/" element={<Navigate to="/templates" replace/>}/>
            <Route path="/" element={
                <AppLayout menuItems={menuItems} appTitle="z-msg 消息中心" appShort="MSG" appIcon={{icon: <img src="/icon.png" alt="MSG" style={{width: "100%", height: "100%", objectFit: "cover", borderRadius: 8}}/>, color: '#ec4899', label: 'MSG'}}/>
            }>
                {routeTable.map((r) => (
                    <Route key={r.path} path={r.path} element={<r.Component/>}/>
                ))}
            </Route>
        </Routes>
    )
}
