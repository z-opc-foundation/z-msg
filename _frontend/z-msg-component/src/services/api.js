/**
 * z-msg API client：走 /msg/** 面（消息中心：inbox / template / delivery / batch）。
 */
import {createRequest} from '@yuku123/z-frontend-common'

const request = createRequest({baseURL: '', tokenKey: 'zmsg_token'})

export default request

export function configureMsg(config) {
    if (config && config.apiBase !== undefined) {
        request.defaults.baseURL = config.apiBase
    }
}

export const msgApi = {
    inbox: (userId, limit) => request.get('/msg/list', {params: {userId, limit}}),
    templatePage: (page, size) => request.post('/msg/template/list', {page, size}),
    deliveryPage: (page, size) => request.post('/msg/delivery/list', {page, size}),
    deliveryStats: () => request.get('/msg/delivery/stats'),
    batchPage: (page, size) => request.post('/msg/batch/list', {page, size}),
}
