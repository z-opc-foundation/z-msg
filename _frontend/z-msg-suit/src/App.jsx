import {Navigate, Route, Routes} from 'react-router-dom'
import {AppLayout} from '@yuku123/z-frontend-common'
import {menuItems, routeTable} from '@yuku123/z-msg-component/pages'

export default function App() {
    return (
        <Routes>
            <Route path="/" element={<Navigate to="/templates" replace/>}/>
            <Route path="/" element={
                <AppLayout menuItems={menuItems} appTitle="z-msg 消息中心" appShort="MSG"/>
            }>
                {routeTable.map((r) => (
                    <Route key={r.path} path={r.path} element={<r.Component/>}/>
                ))}
            </Route>
        </Routes>
    )
}
