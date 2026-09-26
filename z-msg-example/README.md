# z-msg-example

一个**真的能跑起来**的宿主：大厅聊天室 + 站内信。它不进发布清单（`deploy_maven_center.sh` 不带它），
唯一职责是把"接 z-msg 到底要写多少代码"这个问题变成一个可执行答案。

## 跑起来

```bash
cd z-msg
mvn -B -o -DskipTests install                    # 把本地各模块装进 ~/.m2，只需一次
mvn -B -o -pl z-msg-example spring-boot:run      # 起示例（端口 18099）
```

打开 <http://localhost:18099/> **两个标签页**，一个点「1001 张三」，一个点「1002 李四」：

- 在任一边打字回车 → 另一边立刻出现这条消息（走的是 `{"op":"publish"}` 帧，不是 HTTP 轮询）；
- 点「发给对方」→ 对面的红点当场 +1，收件箱列表里多一行，且**同时**收到 `kind=inbox` 的实时帧；
- 点收件箱里任意一条 → 红点 -1；
- 页头那行是服务端在 `ready` 帧里报回来的 `ops`/`limits` 与页面实际采用的心跳间隔
  （心跳取 `idleTimeoutSeconds` 的一半、夹在 2s—30s）。把 `z-msg.ws.idle-timeout-seconds`
  改成 10 再起宿主，这行会显示 5s 且连接在"空闲 10 秒就掐"的服务端上活下来；改成 0 则这一项
  整个不出现、页面落回 20s 兜底。对着已发布的 1.2.0 起宿主时它会直说"服务端未自描述"。

同一个标签页里连点两个身份也是允许的：心跳与在线状态按**最新**那条连接走（旧的会被关掉，
但它那句迟到的 `close` 不再能把新连接的心跳清掉——这一条是改之前实测到的现象）。

页最下面那块「帧日志」是原始帧，用来确认你看到的一切都是走 WebSocket 的。

## 宿主侧代码一共四个文件

| 文件 | 干什么 | 生产要不要改 |
|---|---|---|
| `MsgExampleApplication` | 声明 `dataSourceMsg`（演示用 H2） | **要**：删掉这个方法，改配 `z.base.db.msg.*` |
| `demo/DemoSessionPrincipalResolver` | 实现 `MsgPrincipalResolver`：身份从服务端 session 解 | **要**：换成 z-ctc 的 JWT 解析，接口不变 |
| `chat/LobbyChatPolicy` | 实现 `TopicAuthorizationPolicy`：放开 `room:lobby` 的发言 | 一般不用改；群聊的授权由 `z-msg-im` 按成员表判 |
| `inbox/DemoNotifyController` | 站内信：一句 `gateway.send(IN_APP)` | 不用改，这就是业务代码本体 |

其余（握手、帧编解码、topic 授权串联、站内信落库后推在线连接、未读数、限额与挤占）
都是把 `z-msg-web` + `z-msg-ws` 放进 classpath 就有的，配置见 `src/main/resources/application.yml`。

## 故意留下的"演示专属"

- **登录是假的**：`POST /demo/login?as=1001` 谁都拦不住，只因为这是演示。真系统里这一步是宿主的认证。
  它仍然演示了正确的形状 —— 身份落在服务端 session，`/api/msg/inbox/*` 与 `/api/msg/inbox/ws-token`
  只认这张 cookie，query 里自称的 `userId` 一律不算（`protectedEndpointsTrustTheSessionNotAClientClaimedUserId` 钉着这条）。
- **聊天载荷里的 `from` 是客户端自报的**：`z-msg-ws` 只判"能不能往这个 topic 发帧"，不改写帧内容。
  要服务端署名的聊天室走 `z-msg-im`：发言落库时 `sender_user_id` 与 `seq` 都由服务端写。
- **前端按载荷里的 `userId` 去重**：大厅里自己发的那条，服务端会原样回给包括发送者在内的所有订阅者，
  所以 `index.html` 里对自己那条只更新"已送达"状态（靠 ack 帧回带的 `clientMsgId` 对号），不重复画气泡。
- **内存库**：重启就空。表结构用的是 `z-msg-core` / `z-msg-im` 打进 jar 的那两份 `*-schema-h2.sql`，
  不是示例专用的一份 —— 演示跑的 DDL 和 CI 里 `SchemaParityTest` 守的是同一份。

## 它凭什么说"五分钟"

`MsgExampleApplicationTest` 不 mock 任何传输层，也不往上下文里手塞任何 z-msg bean：
真 Tomcat + 真 H2 + 两条真 WebSocket，用例包括

- 1001 发的话出现在 1002 的帧里，反向同样走得通；
- 没策略表态的 `room:4242` 被 `WS_TOPIC_FORBIDDEN` 拒掉，而**同一条连接**在大厅里发言要成功（负向断言的正向对照）；
- 站内信同时命中实时帧、未读数 0→1、列表里的 `isRead=0`，标已读后回到 0；
- 未登录调 `/api/msg/inbox/unread-count`、`/api/msg/inbox/ws-token` 一律 401，登录后同一个请求 200；
- 首页真的被伺服出来（`GET /` 含"大厅聊天室"），且 `/api/msg/channel/list` 里 `IN_APP` 是真通道。

```bash
cd z-msg && mvn -B -o -pl z-msg-example test
```

## 连不上时先对照这张表

| 现象 | 原因 | 出处 |
|---|---|---|
| 握手 404 | `z-msg.ws.enabled=false`，或 z-msg-ws 不在 classpath | `_doc/001_WS_PROTOCOL.md` §1（握手） |
| 握手 HTTP 503 / 换票业务 code 503 | 没配 `z-msg.realtime.ticket-secret`（fail closed，不签弱密钥票） | 同上 |
| 握手 401 | 票缺失/形状不对/签名对不上/过期 | 同上 |
| HTTP 全 401 | 宿主没注册 `MsgPrincipalResolver`，且 `z-msg.web.trusted-header-enabled=false` | `z-msg-web` 的 `TrustedHeaderPrincipalResolver` 注释 |
| 连上了但收不到聊天 | 该 topic 没有策略放行，或没订阅成功 | `_doc/001_WS_PROTOCOL.md` §4 |
