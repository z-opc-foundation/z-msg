package com.zifang.z.msg.client;

/**
 * z-msg 客户端工厂。
 * <pre>
 *   // AK 客户端身份（别的板块 / 脚本 / 外部系统）
 *   ZMsgClient client = ZMsgClientFactory.remote("http://z-msg:8888", "msga....");
 *
 *   // 平台用户身份（页面上的操作）
 *   ZMsgClient client = ZMsgClientFactory.remote("http://z-msg:8888");
 *   client.send(request, userToken);
 * </pre>
 */
public final class ZMsgClientFactory {

    private ZMsgClientFactory() {
    }

    /**
     * @param baseUrl 消息中心 base URL，集群内即 {@code http://z-msg.z-msg.svc:8888}
     */
    public static ZMsgClient remote(String baseUrl) {
        return new RemoteZMsgClient(baseUrl);
    }

    /**
     * @param baseUrl 消息中心 base URL
     * @param apiKey  客户端 AK（明文只此一次可见，服务端只留摘要）
     */
    public static ZMsgClient remote(String baseUrl, String apiKey) {
        return new RemoteZMsgClient(baseUrl, apiKey);
    }
}
