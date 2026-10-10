import request from '@/common'

/**
 * z-msg (消息中心) 管理接口 — 全部对应主进程 8888 上实测已注册的 /api/msg/**（2026-09-24 经 /actuator/mappings 核对）。
 *
 * 注意两条容易踩的现实：
 *  - 列表类接口是 POST + body，分页参数名是 page/size（不是 z-meta 那套 pageNum/pageSize）
 *  - 站内信接口要求 userId，取当前登录用户
 */
export const msgApi = {
    inbox: (userId, limit) => request.get('/msg/list', {params: {userId, limit}}),

    templatePage: (page, size) => request.post('/msg/template/list', {page, size}),

    deliveryPage: (page, size) => request.post('/msg/delivery/list', {page, size}),
    deliveryStats: () => request.get('/msg/delivery/stats'),

    batchPage: (page, size) => request.post('/msg/batch/list', {page, size}),
}

/**
 * 当前登录用户 id。
 *
 * 为什么要读两个键：前端有两条 SSO 入口，只有一条会写 `userInfo`。
 *  - `zteam/utils/ctc-sso.ts` 那条：同时写 `userInfo` 和 `USER_INFO`
 *  - `zteam/utils/sso-auth.ts` 的 `/kapi/sso/getLoginUserInfo`：只写 `USER_INFO`
 *    （`{name, jobNumber, username, staffId}`），此时单读 `userInfo` 会拿到 null，
 *    本页就变成"未取到当前登录用户 id"且**一个请求都不发**（2026-09-26 探针实测 calls=0）。
 * 实测 `z_msg_message.user_id` 与 `USER_INFO.staffId` 同源（staffId=1 ⇒ userId=1 回 4 行）。
 * request 拦截器注入的 `X-User-Id` 仍只认 `userInfo`，所以这里显式传参，不依赖那个头。
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
