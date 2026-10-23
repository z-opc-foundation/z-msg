// ⚠ 必须用**具名**导入拿 axios 实例，不能用默认导入。
//   '@yuku123/z-frontend-common' 的默认导出是「桶对象」
//   {AppLayout, StatusTag, LoginPage, ErrorBoundary, request, authRequest, createRequest}
//   （0.1.1 起的桶契约，老调用方 import zfc from '...' 不断），不是 axios 实例。
//   默认导入拿到的桶上没有 .get/.post ⇒ 一调就抛
//   "TypeError: Xt.get is not a function"（线上 /msg/inbox 白屏就是这个，2026-10-20 实测）。
//   axios 实例在具名导出 `request` 上（= createRequest()）。
import { request } from '@yuku123/z-frontend-common'

/**
 * z-msg (消息中心) 管理接口 — 全部对应主进程 8888 上实测已注册的 /api/msg/**（2026-09-24 经 /actuator/mappings 核对）。
 *
 * 注意两条容易踩的现实：
 *  - 列表类接口是 POST + body，分页参数名是 page/size（不是 z-meta 那套 pageNum/pageSize）
 *  - 站内信接口**不带 userId**（见下面 inbox 的说明）
 */
export const msgApi = {
    /**
     * 收件箱列表。
     *
     * ⚠ **旧契约 `/msg/list?userId=&limit=` 已于 z-msg 1.1.0 下线** ⇒ 继续打必然 404
     *   （2026-10-23 生产实测：收件箱页 404 3/3）。
     * ⚠ **新契约 `/msg/inbox/list` 不接受 userId**：身份由宿主注册的 `MsgPrincipalResolver`
     *   从 SSO 凭证里解出来，服务端在 SQL 的 WHERE 里做归属过滤。
     *   ⇒ **不许把 userId 塞回 query/header**：那是 z-msg 1.0.0「改一下 query 就能读别人收件箱」
     *   的越权成因，1.1.0 起服务端已不再采信客户端自报的身份。
     * ⚠ `size` 缺省为 0，服务端会 clamp 到 `z-msg.inbox.default-page-size`；
     *   这里显式给 50，避免依赖服务端默认。
     */
    inbox: (page = 1, size = 50) => request.get('/msg/inbox/list', {params: {page, size}}),

    templatePage: (page, size) => request.post('/msg/template/list', {page, size}),

    deliveryPage: (page, size) => request.post('/msg/delivery/list', {page, size}),
    deliveryStats: () => request.get('/msg/delivery/stats'),

    batchPage: (page, size) => request.post('/msg/batch/list', {page, size}),
}

/**
 * 当前登录用户 id。
 *
 * ⚠ **已废弃（2026-10-23 起）**：收件箱不再需要客户端提供 userId ——
 *   身份由服务端从 SSO 凭证解析（`GET /api/msg/inbox/list` 不带 userId）。
 *   本函数保留只为不破坏包根导出面（`export * from './services/api.js'`），
 *   **不要再用它给收件箱接口拼参数**。
 *
 * 为什么要读两个键：前端有两条 SSO 入口，只有一条会写 `userInfo`。
 *  - `zteam/utils/ctc-sso.ts` 那条：同时写 `userInfo` 和 `USER_INFO`
 *  - `zteam/utils/sso-auth.ts` 的 `/kapi/sso/getLoginUserInfo`：只写 `USER_INFO`
 *    （`{name, jobNumber, username, staffId}`）。
 * ⚠ 这两个键里的 id 语义**与服务端解析口径不同**：这里读到的是 staffId/jobNumber（工号域），
 *   服务端 `MsgPrincipalResolver` 取的是 SSO 账号 id（数字串）。
 *   ⇒ 本函数的值**不能**当作 z-msg 的 userId 用。
 */
export function currentUserId() {
    const read = (key) => {
        try {
            return JSON.parse(localStorage.getItem(key) || 'null')
        } catch {
            return null
        }
    }
    const ui = read('userInfo')
    const fromUserInfo = ui && (ui.id ?? ui.userId)
    if (fromUserInfo != null && fromUserInfo !== '') return String(fromUserInfo)
    const sso = read('USER_INFO')
    const fromSso = sso && (sso.staffId ?? sso.jobNumber)
    return fromSso == null || fromSso === '' ? null : String(fromSso)
}

export const DELIVERY_STATUS = {
    0: {text: '待发送', color: 'default'},
    1: {text: '成功', color: 'success'},
    2: {text: '失败', color: 'error'},
    3: {text: '重试中', color: 'processing'},
}

export const BATCH_STATUS = {
    0: {text: '待执行', color: 'default'},
    1: {text: '执行中', color: 'processing'},
    2: {text: '已完成', color: 'success'},
    3: {text: '部分失败', color: 'warning'},
    4: {text: '失败', color: 'error'},
}

export const TEMPLATE_STATUS = {
    0: {text: '草稿', color: 'default'},
    1: {text: '启用', color: 'success'},
    2: {text: '停用', color: 'warning'},
}
