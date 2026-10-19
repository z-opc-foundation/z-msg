# 项目总览

## 1. 项目简介

z-msg 是 z-opc-foundation 的**多通道消息 + 实时下发基础设施**：短信 / 邮件 / 站内信 / Webhook /
企微·钉钉·飞书·Slack / 微信公众号，加一层 WebSocket 实时接入，再往上就是聊天室与 IM。

它解决的是「一个业务事件要同时落到短信 / 邮件 / 站内信 / 群机器人，还要有在线的人当场收到红点，
而业务代码不想关心背后是哪家厂商」这件事：`MessageGateway` 收一条 `Message`，按 `channel` 路由到
某个 `ChannelSender`；站内信落库后顺带往 `user:<id>` 推一帧；`z-msg-im` 把「聊天」做成服务端会话 +
`seq` 增量同步 + 已读回执；`z-msg-ws` 是所有实时能力的传输层。切供应商只改
`z-msg.channel.<CH>.provider` 一行配置，业务侧不改。

**Maven 坐标：** `io.github.yuku123:z-msg:${revision}`（当前 `1.2.2`，根 POM `packaging=pom`），
父项目 `io.github.yuku123:z-boot-parent:1.1.0`（`<relativePath/>` 留空；发布件经 flatten `oss` 模式
剥掉 parent，消费者拿到的是自包含 raw pom）。

**技术栈：** Java 8 + Spring Boot 2.7.18 + Maven 多模块（7 个 reactor 模块）；
WebSocket 走 `spring-boot-starter-websocket`；数据访问走 MyBatis-Plus。

## 2. 模块架构

```
z-msg/
├── pom.xml                     # 聚合 POM：父 z-boot-parent:1.1.0，7 个 reactor 模块
├── z-msg-api/                  # 纯 SPI（18 个类，零实现）：MessageGateway / MessageBus / ChannelSender /
│                               #   RealtimePublisher / RealtimeTransport / TopicAuthorizationPolicy / Channels +
│                               #   Message / SmsMessage / EmailMessage / RealtimeMessage / SearchResult 等模型
├── z-msg-core/                 # 默认实现（36 个类，10 个直接子包，domain 下再分 entity/mapper/service）
│                               #   sender（SmtpEmailSender / MockEmailSender / MockSmsSender / WebhookSender）
│                               #   channel（InAppChannel 站内信）/ template（MessageTemplateEngine + 模板版本/i18n）
│                               #   ratelimit（ChannelRateLimiter）/ realtime（RealtimeTicketService 等）
│                               #   bus / domain(entity,mapper,service) / provider / json / config
├── z-msg-channels/             # 真实外部渠道 provider（15 个类）
│                               #   8 支渠道 Sender + AbstractHttpChannelSender 基类 + RecordingMockSender
│                               #   + Signatures 签名 + SimpleHttpClient + ChannelsProperties
├── z-msg-ws/                   # WebSocket 传输层（7 个类）：握手拦截器 / 帧处理 / 会话注册表 /
│                               #   TopicAuthorizer 三态授权 / WsProperties / 自动装配
├── z-msg-im/                   # 聊天室与 IM（27 个类）：会话 / 消息 / 成员 / 已读回执
│                               #   api（3 个 Controller）/ auth / domain(entity,mapper,model,service) / config
├── z-msg-web/                  # REST 层（16 个类）：7 个 Controller + 自动装配 + 鉴权与异常处理
├── z-msg-example/              # 演示宿主（6 个类）：MsgExampleApplication + demo 鉴权 / 通知 / 大厅策略
├── _frontend/                  # 前端工程（仓根下，非 Maven 模块）
├── _doc/                       # 文档目录：001_arch / 003_script
└── LICENSE
```

**模块依赖方向（实测 POM）：** `z-msg-ws` 与 `z-msg-im` 都只依赖 `z-msg-api` + `z-msg-core`，
`z-msg-im` 另依赖 `z-msg-web`（复用鉴权）与 `z-boot-datasource-starter`（数据源），
`z-msg-ws` 另依赖 `spring-boot-starter-websocket` —— 实时层不反向依赖 IM 层。

## 3. 核心功能

### 3.1 统一网关与渠道路由（`z-msg-core`）

`DefaultMessageGateway` 收一条 `Message` → `ChannelRouter` 按 `channel` 选路 → `SenderRegistry`
解析出 `ChannelSender` → 发送并回 `MessageSendResult`。`DefaultMessageBus` 负责业务事件的
消息总线侧。`ChannelRateLimiter` 按渠道限流。切供应商改 `z-msg.channel.<CH>.provider` 一行配置。

### 3.2 站内信与模板（`z-msg-core`）

- 站内信：`InAppChannel` + `InAppMessage` + `MsgInboxService` 落库，读侧在 `z-msg-web` 的
  `MsgInboxController`（`/api/msg/inbox`）。
- 模板：`MessageTemplateEngine` + `MsgTemplateService` + 模板版本与 i18n 两张表
  （`MsgTemplateDO` / `MsgTemplateVersionDO` / `MsgTemplateI18nDO`）。

### 3.3 批量发送与投递日志

`MsgBatchSendService` + `MsgBatchTaskDO` / `MsgBatchTaskMapper` 承接批量任务；
`MsgDeliveryLogService` + `MsgDeliveryLogDO` 记录每次投递结果，读侧 `MsgDeliveryLogController`
（`/api/msg/delivery`）。

### 3.4 外部渠道（`z-msg-channels`）

8 支真实渠道 Sender：`AliyunSmsSender`、`TencentSmsSender`、`JPushSender`、`DingTalkRobotSender`、
`WecomRobotSender`、`FeishuRobotSender`、`SlackBotSender`、`WeixinMpSender`，
外加 `RecordingMockSender`（录制回放）与 `AbstractHttpChannelSender` 基类。
`Signatures` 承担各渠道的签名算法。

### 3.5 WebSocket 实时接入（`z-msg-ws`）

端点默认 **`/api/msg/ws`**（`WsProperties.path`）。浏览器侧先拿一次性短期 ticket 再连
`ws://host:port/api/msg/ws?token=<ws-ticket>`（`RealtimeTicketService` 签发，
`z-msg.realtime.ticket-ttl-seconds` 默认 60 秒；配成 0 或负数会被兜回 60，不允许出现永不过期的握手凭据）。`TopicAuthorizer` 做 topic 三态授权；`WsSessionRegistry` 管在线连接。
资源上限由配置钉住：单连接 topic 上限、每用户会话上限、空闲超时、单帧文本字节上限。

### 3.6 聊天室与 IM（`z-msg-im`）

会话（`ImConversationDO` / `ImConversationService`）、消息（`ImMessageDO` / `ImMessageService`）、
成员（`ImMemberDO`）、已读回执（`ImReadReceiptDO` / `ImReadService`）四组实体；
`ImSequencer` 负责 `seq` 增量同步；`ImTopicAuthorizationPolicy` 落 topic 授权策略。
三个 Controller 分别在 `/api/msg/im/conversation`、`/api/msg/im/message`、`/api/msg/im/read`。

### 3.7 REST 面（`z-msg-web`）

7 个 Controller，路由前缀实测：`/api/msg`（`MessageController`）、`/api/msg/batch`、
`/api/msg/channel`、`/api/msg/delivery`、`/api/msg/inbox`、`/api/msg/preference`、`/api/msg/template`。
`MsgPrincipalResolver` / `TrustedHeaderPrincipalResolver` 解析调用方身份，
`MsgWebExceptionHandler` 统一异常信封。

## 4. 对外集成 / 上下游

**对外 HTTP 边界：** REST 面在 `/api/msg/*` 与 `/api/msg/im/*`（`z-msg-web` + `z-msg-im`），
WebSocket 端点在 `/api/msg/ws`（`z-msg-ws`）。库本身不固定端口，端口与 context-path 随宿主应用。

**演示宿主端口：** `z-msg-example` 监听 **`18099`**（`z-msg-example/src/main/resources/application.yml`），
可直接开两个浏览器标签页当两个用户进聊天室。

**上游依赖：** `z-msg-api` / `z-msg-core` / `z-msg-web` 为仓内模块；第三方件走 `spring-boot-starter-websocket`、
MyBatis-Plus、`z-boot-datasource-starter`、`z-util-core`。本仓**不直连**任何外部厂商 SDK ——
所有渠道都是 HTTP 调用 + 自实现签名。

**下游消费方：** 业务应用引 `z-msg-api` 依赖的任一模块（web / ws / im / channels）。
注意 `z-boot-msg-starter` 只聚合了 `z-msg-web` 一支坐标，WebSocket / 聊天室 / 外部渠道三件
需要直接引 `z-msg-ws` / `z-msg-im` / `z-msg-channels`。

**发布口径：** `z-msg`（parent pom）/ `-api` / `-core` / `-web` / `-ws` / `-im` / `-channels` 共 8 支坐标
已上 Maven Central；`z-msg-example` 不进发布清单。

**运维脚本：** `_doc/003_script/deploy_maven_center.sh`（`publish` / `verify` / `gpg-init` / `readme`）、
`_doc/003_script/install-settings.sh`（把 `CENTRAL_USERNAME` / `CENTRAL_TOKEN` 写进 `~/.m2/settings.xml`）。

## 5. 技术特点

1. **SPI 与实现彻底分层：** `z-msg-api` 18 个类全是接口与模型、零实现，业务方只依赖它；
   换厂商、换传输都不需要业务改代码。
2. **实时层与业务层互不反向依赖：** `z-msg-ws` 不依赖 `z-msg-im`，`z-msg-im` 只依赖 `z-msg-api` / `-core` / `-web`
   —— 纯实时场景不必拖进 IM 的会话模型。
3. **握手凭据是一次性短期 ticket：** 不在握手 URL 里直接传长期 token；`RealtimeTicketService` 签发
   60 秒有效的 ticket，握手拦截器只认 ticket 不认明文密钥。
4. **资源上限全部显式钉住：** 每用户会话数、单连接 topic 数、空闲超时、单帧字节数都是 `WsProperties` 里的
   配置项并有默认值，避免一条恶意连接吃掉整台机器。
5. **离线可用是设计目标：** `MockEmailSender` / `MockSmsSender` / `RecordingMockSender` 与真实渠道同接口，
   没有厂商凭据也能跑通全链路（`z-msg-example` 就是这样起来的）。
6. **IM 的实时下发只依赖 API 层的 `RealtimePublisher`：** `z-msg-im` 不直接碰 WebSocket 实现，
   换传输实现不影响会话与回执逻辑。
7. **协议细节另有逐帧规范：** WebSocket 握手 / 帧格式 / `op=auth` 带内换票 / topic 命名 / 三态授权 /
   错误码 / 资源上限 / 断线并发见 `_doc/001_arch/04-protocol.md`。
8. **发布件自包含：** flatten `oss` 模式剥掉 parent，消费者拿到的是不含 `z-boot-parent` 的 raw pom，
   不必担心 parent 在中央仓缺失。

## 6. 目录结构

本仓 Java 包根为 `com.zifang.z.msg`，按 Maven 模块切分：

```
com.zifang.z.msg
├── api/                # z-msg-api：SPI 接口 + 模型，18 个类在同一个包里
├── core/               # z-msg-core
│                       #   sender / channel / template / ratelimit / realtime / bus / provider / json / config
│                       #   domain/{entity,mapper,service}
├── channels/           # z-msg-channels：8 支渠道 Sender + 抽象基类 + 录制 mock + 签名 + HTTP 客户端
├── ws/                 # z-msg-ws：auth / config / handler / handshake / session
├── im/                 # z-msg-im：api / auth / config / domain/{entity,mapper,model,service}
├── web/                # z-msg-web：api / auth / config / host
└── example/            # z-msg-example：演示宿主
```

**规模（`src/main/java` 实测）：** core 36 文件 / 5253 行，im 27 文件 / 3324 行，
api 18 文件 / 1713 行，web 16 文件 / 1099 行，channels 15 文件 / 2529 行，
ws 7 文件 / 1494 行，example 6 文件 / 338 行。

**测试：** `src/test` 共 55 个 Java 文件，覆盖 ws 握手与禁用态、IM 会话与回执、web 收件箱契约、
core 模板与批量等。
