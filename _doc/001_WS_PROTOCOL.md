# z-msg WebSocket 实时协议（1.2.0）

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
  第二次拿同一张票来 → 401（这条从 **1.2.0** 起在发布件里；1.1.0 那一版 TTL 内可重放，
  实测差别是 `MsgHandshakeInterceptor` 在 1.1.0 调 `RealtimeTicketService#verify`、在 1.2.0 调 `#consume`）。
  所以重连必须重新调 `/inbox/ws-token`，不能缓存 token 复用。过期按毫秒比较，不做秒级截断。
  记账在进程内存里：重启后旧票在 TTL 内还能再用一次，多实例则每台各记一次（详见 README §9）。
- 浏览器不能给 `WebSocket` 加请求头，所以身份必须进 URL；正因为它会进 access log，
  这里绝不能换成长期 JWT。服务端任何日志都不打 token 原文。
- 同一张票也可以**在已打开的连接里**花掉（`op=auth`，见 §2），走的是同一本 `nonce` 账：
  一张票无论走握手还是走帧，都只兑现一次。帧内的 token 反而比 URL 干净——它不会落进 access log。
  **这条是主干行为，`op=auth` 不在任何已发布构件里（1.2.0 及以前没有这个 op），默认关闭。**

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
| `auth` | `{"op":"auth","clientMsgId":"a1","token":"<新 ticket>"}` | 在**不重连**的前提下重新证明身份／换到另一个身份。**默认关**，要 `z-msg.ws.inband-auth-enabled=true`；未发布（1.2.0 及以前只有上面四个 op） |

`subscribe`/`unsubscribe` 的 `topics` 为空 → `WS_BAD_FRAME`；未知 `op` → `WS_OP_UNSUPPORTED`；
非 JSON 文本 → `WS_BAD_FRAME`。**这些错误都不会关掉连接**，下一帧照旧能发能收。

### `op=auth`：长连接上换身份（主干，默认关）

握手之后连接的身份本来是不变的，于是"重连一次"成了唯一能重新证明身份的途径——
移动端切账号、多账号共用一条 socket 的宿主都得断线重连、重新拉一遍 topic。
`op=auth` 把这条路挪进帧里，语义与握手完全一致：**只认票，不认帧里自称的 userId**。

```jsonc
// 请求
{"op":"auth","clientMsgId":"a1","token":"z-msg-ws|7102|1790...|nonce.NaMd..."}
// 同身份续期（changed=false，没有 previousUserId / revoked）
{"op":"ack","clientMsgId":"a1","payload":"{\"changed\":false,\"userId\":7101,\"topics\":[\"user:7101\",\"sys:broadcast\",\"room:lobby\"]}"}
// 换成另一个身份
{"op":"ack","clientMsgId":"a1","payload":"{\"changed\":true,\"userId\":7102,\"previousUserId\":7101,\"revoked\":[\"user:7101\"],\"topics\":[\"sys:broadcast\",\"room:lobby\",\"user:7102\"]}"}
```

四条判据，每条都有用例钉着（`MsgWsInbandAuthTest` / `WsSessionRegistryRebindTest`）：

- **失败必须是无害的**：票不对／过期／已被用过 → `WS_AUTH_FAILED`（与握手 401 同一把尺，
  三种情况**刻意不区分**），连接不断、身份不动、订阅不动。开关没开 → `WS_AUTH_DISABLED`，
  而不是静默忽略：客户端以为换了身份其实没换，比直接报错难查十倍。
- **旧身份的收件箱必须跟着断**：`user:<旧id>` 既在"按用户"索引里也在"按 topic"索引里，
  只搬其中一个的话，别人发给旧用户的红点会继续投进这条自称新身份的连接。
- **换完要按新身份重新裁决每一条已有订阅**：新身份不配持有的 topic 会被退订并原样列进
  `revoked`。反过来说清楚**做不到**的那半：服务端没有"某用户该有哪些频道"的可枚举清单
  （那要改 §4 的策略契约），所以**换回原身份时只有自己的收件箱会自动补回来**，
  其余频道客户端要重新 `subscribe`——`revoked` 就是给客户端留的这份对账清单。
- **`max-sessions-per-user` 在新用户名下重跑**：换身份等于在新用户名下多出一条连接，
  超限时要挤掉那一名下最早的那条，而不是让一个人靠换身份攒出无上限的连接数。

服务端日志记 `id / 旧id->新id / 复核后取消订阅数 / 在线数`，**不记 token**。

### 服务端 → 客户端

```jsonc
{"op":"ready","ts":...,"payload":"{\"connectionId\":\"...\",\"userId\":7101,\"topics\":[\"user:7101\",\"sys:broadcast\",\"room:lobby\"],\"ops\":[\"ping\",\"subscribe\",\"unsubscribe\",\"publish\"],\"limits\":{\"maxTopicsPerConnection\":64,\"maxSessionsPerUser\":8,\"idleTimeoutSeconds\":120,\"maxTextMessageBytes\":262144}}"}
{"op":"message","kind":"inbox","topic":"user:7101","seq":1,"ts":...,"payload":"{...}"}
{"op":"ack","clientMsgId":"s1","payload":"{\"subscribed\":[\"room:7001\"],\"topics\":[...]}" }
{"op":"error","clientMsgId":"p1","errorCode":"WS_TOPIC_FORBIDDEN","errorMessage":"..."}
```

连接建立时**自动订阅**这三类，前端不必先发 `subscribe` 就能收红点：
`user:<自己>`、`sys:broadcast`、以及 `z-msg.ws.public-topics` 里显式列出的 topic。

### `ready` 的 `ops` 与 `limits`：服务端把自己**真的在执行**的那份报出来

（**主干新增，不在任何已发布构件里**：1.2.0 及以前的 `ready` 只有
`connectionId` / `userId` / `topics` 三个键。加法语义，老前端多读不认识的键没有影响。）

- `ops`：服务端认的客户端帧。它和分发处读的是同一个开关，所以 `auth` **只在
  `z-msg.ws.inband-auth-enabled=true` 时出现**；前端不必再猜"这个环境有没有带内换票"。
  注意它说的是"这张帧有人处理"，不是"你有权限发"——`publish` 永远在清单里，
  放不放行仍由 §4 的策略逐条三态判。
- `limits`：**只报服务端真的会执行的那些**。`maxTopicsPerConnection` / `maxSessionsPerUser` /
  `idleTimeoutSeconds` 三项在配成 0 或负数（=不限，或"别动容器默认值"）时**整个键缺席**，
  而不是报一个 0：前端把 `"maxTopicsPerConnection": 0` 读成"一条都不许订"，
  而那项的真实语义是"没有这道闸"。`maxTextMessageBytes` 总会报，因为容器 buffer 一定按它设。
- 这些数与那道闸**同源**：值取自注册表、handler 与容器 bean 共用的同一个 `WsProperties` 实例，
  由 `MsgWsReadySelfDescriptionTest` 拿"报的 4 就是拒第 5 条的那个数"对撞出来；
  `MsgWsReadyOptOutsTest` 钉反面（报"缺席"的三项确实不拦：一次订 8 条全过、同一用户连开 3 条都在）。
- 前端因此可以按 `idleTimeoutSeconds` 定心跳间隔（要明显小于它），按
  `maxTopicsPerConnection` 预算订阅数，而不是抄本文档里的默认值。
  仓库里那份参考实现是 `z-msg-example/src/main/resources/static/index.html` 的 `heartbeatMs`：
  取它的一半、夹在 2s—30s；键缺席（那道限流没启用）或整个 `limits` 缺席（对着 1.2.0 的服务端）
  都落回 20s。三档都在真浏览器里量过：`idle-timeout-seconds=120` ⇒ 心跳 30s（实测三次 tick
  相隔 30.000s）、`=10` ⇒ 心跳 5s（这条连接在服务端"空闲 10 秒就掐"的配置下活了下来）、
  `=0` ⇒ 报出的限额里确实没有这一项，且落回 20s。
- 心跳要挂在连接对象上，不要挂在一个全局句柄上：换身份（以及任何重连）时旧连接的 `close`
  会晚于新连接建立才到，共用句柄的话它 `clearInterval` 清掉的正是新连接那一份。
  改之前实测到的现象是：切换身份之后还在跳的那份心跳，相位仍是**第一条**连接的 20s，
  即页面上已经没有任何属于当前连接的心跳了——只是被一条孤儿 interval 掩盖着，界面也一度
  被旧连接的收尾打回"已断开"。现在按连接编号，只有最新那条允许动 UI 与心跳。
- 有守卫要求"分发处的 `op` 分支 == 报出去的清单"双向一致（`MsgWsOpCensusTest`）：
  加一张新帧却忘了登记进 `ready.ops`，红的是那一条，因为客户端永远发现不了没报的能力。

站内信（`kind=inbox`）的 payload 字段：
`id`、`msgId`、`eventType`、`msgType`、`title`、`content`、`linkUrl`、`priority`、
`createdTime`、`unread=true`。前端拿 `id` 去 `/api/msg/inbox/read?id=` 标已读。
`z_msg_message.id` 是自增主键，位数远小于 2^53，所以这一族的 `id` 出的是数字。

### IM 的 `kind=chat` / `kind=read`：id 一律字符串

`z-msg-im` 落库后的帧（`ImMessageService#payloadOf`）：

| 字段 | 线上形状 | 为什么 |
| --- | --- | --- |
| `id`、`conversationId`、`senderUserId` | **字符串** | 19 位雪花超出 JS Number 的 53 bit，见下面那段 |
| `seq` | 数字 | 会话内从 1 起的游标，客户端要拿它做加减与比较 |
| `msgType`、`content` | 字符串 | `TEXT` / `IMAGE` / `FILE` / `AUDIO` / `SYS` |
| `clientMsgId`、`atUserIds`、`replyToSeq` | 有值才出现 | `atUserIds` 是升序逗号分隔的**字符串**，不是 JSON 数组 |
| `createdTime` | 数字 | epoch 毫秒 |

`kind=read`（`publishReadFrame`，走 `room:`）：`conversationId`、`userId` 字符串，
`lastReadSeq` 数字。

**为什么 id 出字符串而不是数字**：会话/消息 id 是 19 位十进制雪花，而 JS 的 `Number`
只有 53 bit（安全上界 `9007199254740991`，16 位）。浏览器 `JSON.parse` 会当场把
`2103885891501236225` 舍成 `2103885891501236200`——前端拿着这个数去拼 `room:<id>`
或回填 `/api/msg/im/message/send`，症状是 **`403 无权访问会话 <一个服务端从未存在过的 id>`**，
而这条会话是一秒前它自己刚建起来的。现象与权限配置错一模一样，排查方向从一开始就是错的，
所以正确性必须由服务端在线上形状上保证，而不是写在文档里要求前端"记得用 BigInt"。
REST 侧同理：`@JsonSerialize(using = ToStringSerializer.class)` 只钉在 id 字段，
不靠宿主的 Jackson 全局开关（那条开关的范围是整个应用的响应体）。钉的位置是 6 个类共 16 个字段
（四张表的实体 + 会话视图 `ImConversationView` + 未读汇总 `ImUnread`）加两处手工拼的帧载荷，
**不是"给实体打了标就完事"**：`ImUnread` 是 `ImReadService#unreadSummary` 用 `ImUnread.of(...)`
现装的另一份 DTO，实体上的注解结构上覆盖不到它，只有 `unread/summary` 的线上形状能钉住它。

两个由这条形状带来的便利：`atUserIds` 与 `room:` 的标识部分本来就是字符串，
拼 topic 时**直接用手里的字符串**，一位都不会错。

单聊默认同时投两条 topic（`room:<会话>` 与 `user:<对面那一位>`，见
`z-msg.im.user-side-push`），两条帧的 payload 完全相同 —— **客户端按
`conversationId + seq` 去重**，否则会画两个气泡；`z-msg-example` 的私聊面板就是这么做的。

> **这条形状是主干改动，不在任何已发布构件里**：1.2.0 及以前 IM 的 id 在 REST 与实时帧上
> 都是数字。改动破坏线格式（对按数字读的客户端），因此不能反向移植进 1.2.0；
> 钉住它的是 `ImSpringTestSupport#idOf`（判"必须是字符串"）与
> `ImRestApiTest#conversationIdSurvivesAJavaScriptStyleRoundTrip`（判前端"拿到什么回填什么"这条
> 自然路径真的走得通）。三支变异都验过有牙、按 `md5` 逐字节还原：摘实体标 ⇒ 3 条 REST 红，
> 把帧载荷的 `asText()` 换回裸 `Long` ⇒ 2 条帧断言红，摘 `ImUnread` 的标 ⇒ 汇总那 1 条红
> （读数与还原记录见 `README.md` §13）。`seq` 一类游标字段仍是数字，别顺手一起改。

## 3. topic 命名

`RealtimeTopics`：`前缀:标识`，解析一律按第一个冒号切分。除 `biz:` 天生带一段分组
（`biz:<group>:<key>`，此时 `keyOf` 会带回整段 `group:key`）之外，标识内不允许再出现冒号。

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
| `WS_AUTH_DISABLED` | 收到 `op=auth` 但 `z-msg.ws.inband-auth-enabled` 没开（默认就是这一支）；`errorMessage` 点名那个配置 |
| `WS_AUTH_FAILED` | `op=auth` 的票签名不对／已过期／已被用过（三者不区分，同握手 401）；连接与身份都不动 |

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
后四项里被真正执行的那些还会出现在 `ready.limits`（见 §2）：宿主改了 yml，前端不用跟着改常量。

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
