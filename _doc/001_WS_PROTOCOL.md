# z-msg WebSocket 实时协议（1.1.0）

一条连接同时承载**站内信红点**、**聊天室/IM 帧**和**业务自定义事件**。
宿主侧接入只需要两件事：把 `z-msg-ws` 放进 classpath、配一把 `z-msg.realtime.ticket-secret`。
不需要写 handler，不需要 `@EnableWebSocket`，不需要注册 transport。

本文档描述的是 `z-msg-ws` 的 `MsgWebSocketHandler` + `MsgHandshakeInterceptor` +
`TopicAuthorizer` **当前实现**的行为，每条都对应 `MsgWsEndToEndTest` /
`MsgWsAuthorizationPolicyTest` 里的断言；改代码请连同这两类一起改。

---

## 1. 握手

```
GET ws://<host>/api/msg/ws?token=<ws-ticket>
```

ticket 由已认证的 HTTP 会话去换：

```
GET /api/msg/inbox/ws-token            （身份取自宿主自己的登录态）
→ {"code":200,"success":true,"data":{"token":"z-msg-ws|7101|1790...|nonce.NaMd...","expiresInSeconds":60}}
```

- **只认 `token` 一个参数**。`?userId=1001` 这种"自称是谁"的参数一律不接受——
  1.0.0 的越权面就是这么来的。
- 同一个 key 出现两次（`?token=a&token=b`）**整体判非法**，不是挑一个用。
- ticket 是 HMAC-SHA256 自签名的短时凭据，格式 `base64url(载荷).base64url(签名)`，
  载荷为 `z-msg-ws|<userId>|<到期毫秒>|<nonce>`。**一张票只换一条连接**：握手按 `nonce` 记账，
  第二次拿同一张票来 → 401（这条在主干代码里，**已发布的 1.1.0 还没有**，那一版 TTL 内可重放）。
  所以重连必须重新调 `/inbox/ws-token`，不能缓存 token 复用。过期按毫秒比较，不做秒级截断。
  记账在进程内存里：重启后旧票在 TTL 内还能再用一次，多实例则每台各记一次（详见 README §9）。
- 浏览器不能给 `WebSocket` 加请求头，所以身份必须进 URL；正因为它会进 access log，
  这里绝不能换成长期 JWT。服务端任何日志都不打 token 原文。

状态码（`MsgWsFailClosedTest` / `MsgWsEndToEndTest` 各钉了一支）：

| 情况 | 状态码 |
| --- | --- |
| 未配 `z-msg.realtime.ticket-secret` | 503（fail closed，绝不签弱密钥票） |
| 缺 token / token 形状不对 / 签名对不上 | 401 |
| 这张票已经开过一条连接（重放） | 401，与上一行**刻意不区分**：分辨它们等于告诉探测者这张票是真的 |
| ticket 有效 | 101 升级，并立刻下发一帧 `ready` |
| `z-msg.ws.enabled=false` | 404（端点根本不存在） |

## 2. 帧格式

一行一帧，UTF-8 JSON。字段集合就是 `RealtimeMessage`：

| 字段 | 出现于 | 说明 |
| --- | --- | --- |
| `op` | 全部 | 帧类型，见下表 |
| `kind` | `message` | 业务语义：`inbox` / `chat` / `typing` / `presence` / `read` / `sys` |
| `topic` | `message`、部分 `ack`/`error` | 见 §3 |
| `seq` | `message` | topic 内单调递增；控制帧为 0 |
| `ts` | 全部 | 服务端 epoch **毫秒** |
| `payload` | `ready`、`message`、`ack` | **JSON 字符串**（传输层不理解其结构，所以要对它再解析一次） |
| `clientMsgId` | 客户端可带；服务端原样回带 | 用来对号：气泡从"发送中"改成"已送达" |
| `errorCode` / `errorMessage` | `error` | 见 §5 |

### 客户端 → 服务端

| op | 载荷 | 服务端反应 |
| --- | --- | --- |
| `ping` | `{"op":"ping","clientMsgId":"p1"}` | 回 `pong`（带同一个 `clientMsgId`），并刷新连接活跃时间 |
| `subscribe` | `{"op":"subscribe","clientMsgId":"s1","topics":["room:7001"]}` | 逐个授权后回 `ack`；`topics` 也可以是单个字符串 |
| `unsubscribe` | 同上，`op` 换成 `unsubscribe` | 回 `ack`，`payload.unsubscribed` + 当前 `payload.topics` |
| `publish` | `{"op":"publish","clientMsgId":"p1","topic":"room:7001","kind":"chat","payload":{...}}` | **默认全部拒绝**，除非有模块注册了 `TopicAuthorizationPolicy`（§4） |

`subscribe`/`unsubscribe` 的 `topics` 为空 → `WS_BAD_FRAME`；未知 `op` → `WS_OP_UNSUPPORTED`；
非 JSON 文本 → `WS_BAD_FRAME`。**这些错误都不会关掉连接**，下一帧照旧能发能收。

### 服务端 → 客户端

```jsonc
{"op":"ready","ts":...,"payload":"{\"connectionId\":\"...\",\"userId\":7101,\"topics\":[\"user:7101\",\"sys:broadcast\",\"room:lobby\"]}"}
{"op":"message","kind":"inbox","topic":"user:7101","seq":1,"ts":...,"payload":"{...}"}
{"op":"ack","clientMsgId":"s1","payload":"{\"subscribed\":[\"room:7001\"],\"topics\":[...]}" }
{"op":"error","clientMsgId":"p1","errorCode":"WS_TOPIC_FORBIDDEN","errorMessage":"..."}
```

连接建立时**自动订阅**这三类，前端不必先发 `subscribe` 就能收红点：
`user:<自己>`、`sys:broadcast`、以及 `z-msg.ws.public-topics` 里显式列出的 topic。

站内信（`kind=inbox`）的 payload 字段：
`id`、`msgId`、`eventType`、`msgType`、`title`、`content`、`linkUrl`、`priority`、
`createdTime`、`unread=true`。前端拿 `id` 去 `/api/msg/inbox/read?id=` 标已读。

## 3. topic 命名

`RealtimeTopics`：`前缀:标识`，标识内不允许再出现冒号（按第一个冒号切分）。

| 前缀 | 含义 | 默认谁能订 |
| --- | --- | --- |
| `user:<userId>` | 个人收件箱 | 只有本人 |
| `room:<conversationId>` | IM 会话/群 | 没有策略表态时**拒**（由 z-msg-im 按成员表放行） |
| `tenant:<tenantCode>` | 租户公告 | 同上，需策略 |
| `biz:<group>:<key>` | 业务自定义事件 | 同上，需策略 |
| `sys:broadcast` | 全域公告 | 任何已登录连接 |

## 4. 授权：三态而不是布尔

`TopicAuthorizer` 按 `order()` 升序问每一个 `supports(topic)` 为真的 `TopicAuthorizationPolicy`：

1. 任一策略返回 `FALSE` → **立即否决**（FALSE 优先，不会因为别的策略先放行就短路）；
2. 否则有任一 `TRUE` → 放行；
3. 全部返回 `null`（不表态）→ 落到默认策略：
   - **订阅**：自己的 `user:`、`z-msg.ws.public-topics`、`sys:broadcast`，其余拒绝；
   - **发布**：一律拒绝。写侧没有"默认允许"这一档——放开它等于给任意连接一个
     往别人 topic 里塞帧的能力。策略抛异常按"不表态"处理，不会带走整条连接。

实现一个群聊权限只要：

```java
@Bean
TopicAuthorizationPolicy roomPolicy(final ImMembershipService members) {
    return new TopicAuthorizationPolicy() {
        public boolean supports(String topic) { return RealtimeTopics.isRoom(topic); }
        public Boolean allowSubscribe(Long userId, String topic) {
            return members.isMember(RealtimeTopics.keyOf(topic), userId);
        }
        public Boolean allowPublish(Long userId, String topic) {
            return members.canSpeak(RealtimeTopics.keyOf(topic), userId);
        }
    };
}
```

## 5. 错误码

| errorCode | 含义 |
| --- | --- |
| `WS_BAD_FRAME` | 非 JSON、缺 `op`、`topics` 为空 |
| `WS_TOPIC_FORBIDDEN` | 订阅/退订/代发被 §4 拒；`errorMessage` 里点名 topic |
| `WS_TOPIC_LIMIT` | 本帧会让订阅数超过 `max-topics-per-connection`，**整帧不改状态** |
| `WS_OP_UNSUPPORTED` | 未知 `op` |

## 6. 资源上限

| 配置 | 默认 | 兑现位置 |
| --- | --- | --- |
| `z-msg.ws.enabled` | true | false 时本模块一个 bean 都不注册，端点 404 |
| `z-msg.ws.path` | `/api/msg/ws` | 端点路径 |
| `z-msg.ws.public-topics` | 空 | 自动订阅 + 默认放行订阅 |
| `z-msg.ws.allowed-origins` | 空（不限） | 见 `WsProperties` 注释：换成长期 token 时必须显式配上 |
| `z-msg.ws.max-text-message-bytes` | 262144 | 落到容器的 text/binary buffer 上限 |
| `z-msg.ws.idle-timeout-seconds` | 120 | 落到容器 `maxSessionIdleTimeout`；配 0/负数则**不改容器默认**（那是全局值，会连累宿主自己的端点） |
| `z-msg.ws.max-sessions-per-user` | 8 | 超出时挤掉最早那条，并从注册表摘干净 |
| `z-msg.ws.max-topics-per-connection` | 64 | 越界整帧拒绝 |

写在这里但没人读的配置就是骗人的广告——上面每一项都有对应的 bean 或断言在读它。

## 7. 断线与并发

- 服务端往同一条连接写帧时**加锁串行**（`MsgWsSession#sendLock`）：站内信线程、IM 线程、
  心跳可能同时写，`WebSocketSession#sendMessage` 不是线程安全的。
- 写失败（对端已关、容器已 close）不抛给业务方，而是把该连接从注册表摘掉：
  一个浏览器关掉标签页不该带走一次业务投递。
- **`op=publish` 的那条连接自己也会收到这一帧**：`deliverToTopic` 遍历的是 topic 的全部订阅者，
  不摘掉发送者。前端因此要按 `payload.userId` 或 `clientMsgId` 去重，否则每个自己说的话会画两遍气泡。
- 实时推送是站内信的**增强而非前提**：`z-msg.inbox.push-realtime=false` 或宿主没引
  `z-msg-ws` 时，站内信照常落库，前端退回轮询 `/api/msg/inbox/unread-count`。

## 8. 最小前端

```js
// WS_BASE 例如 'ws://localhost:8080'；端点路径由宿主的 z-msg.ws.path 决定，默认 /api/msg/ws
async function connect(onFrame) {
  const resp = await fetch('/api/msg/inbox/ws-token', { headers: loginHeaders() });
  const { data } = await resp.json();          // data = {token, expiresInSeconds}
  const ws = new WebSocket('ws://localhost:8080/api/msg/ws?token=' + data.token);
  ws.onmessage = e => {
    const f = JSON.parse(e.data);
    if (typeof f.payload === 'string') f.payload = JSON.parse(f.payload);
    onFrame(f);                       // ready / message / ack / error / pong
  };
  return ws;
}
```

`ready` 到达才说明这次连接真的可用；把它当成"重连成功"的信号，而不是 `onopen`。
