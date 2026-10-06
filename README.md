# z-msg

> **多通道消息 + 实时下发基础设施** — 短信 / 邮件 / 站内信 / Webhook / 企微·钉钉·飞书·Slack / 微信公众号，
> 加一层 WebSocket 实时接入，再往上就是聊天室和 IM。
> Java 8 + Spring Boot 2.7.18，切供应商只改一行 yml，业务代码零修改。

[![Maven Central](https://img.shields.io/badge/Maven%20Central-api%2Fcore%2Fweb%2Fws%2Fim%2Fchannels%201.2.2-blue?logo=apache-maven)](https://central.sonatype.com/search?q=g:io.github.yuku123+a:z-msg)
[![Java](https://img.shields.io/badge/Java-8%2B-orange)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-6DB33F)](https://spring.io)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

它解决的是"一个业务事件要同时落到短信/邮件/站内信/群机器人，还要有在线的人当场收到红点，
而业务代码不想关心背后是哪家厂商"这件事：`MessageGateway` 收一条 `Message`，按 `channel` 路由到
某个 `ChannelSender`；站内信落库后顺带往 `user:<id>` 推一帧；`z-msg-im` 把"聊天"这件事做成服务端
会话 + `seq` 增量同步 + 已读回执；`z-msg-ws` 是所有实时能力的传输层。切供应商只改 `z-msg.channel.<CH>.provider`
一行 yml，业务侧一行不改。

---

## 📋 基本信息

| 字段 | 值 |
|------|-----|
| **仓库** | `z-msg` |
| **Maven 坐标** | `io.github.yuku123:z-msg:${revision}`（聚合 POM） |
| **当前版本** | `1.2.2`（根 POM `<revision>`，CI-friendly versions + flatten-maven-plugin） |
| **父项目** | `io.github.yuku123:z-boot-parent:1.0.21`（`<relativePath/>` 留空，parent 在 repo1 不在磁盘；**发布件经 flatten `oss` 模式剥掉 parent，消费者拿到的是自包含 raw pom**） |
| **Maven Central** | 已发布：`z-msg`(parent) / `z-msg-api` / `z-msg-core` / `z-msg-web` / `z-msg-ws` / `z-msg-im` / `z-msg-channels` 的 **1.2.0 / 1.2.1 / 1.2.2** 均可从 repo1 拉取；`z-msg-example` 各版均 404（见下「发布状态」） |
| **默认端口** | 库本身无固定端口；演示宿主 `z-msg-example` 监听 `18099`；WS 端点路径 `/api/msg/ws` |
| **运行口径** | Java 8 · Spring Boot 2.7.18（口径由 `z-boot-parent` → `z-boot-dependencies` 地板供给） |
| **最近更新** | 2026-09-30 |

---

## 📦 发布状态（照实说，2026-09-30 实测）

对 `https://repo1.maven.org/maven2/io/github/yuku123/<artifactId>/<version>/<artifactId>-<version>.pom`
逐个发 HEAD，八个坐标 × 三个版本实测：

| 构件 | 1.2.0 | 1.2.1 | 1.2.2（当前 `${revision}`） |
|---|---|---|---|
| parent `z-msg` | 200 | 200 | **200** |
| `z-msg-api` / `z-msg-core` / `z-msg-web` | 200 | 200 | **200** |
| `z-msg-channels` / `z-msg-ws` / `z-msg-im` | 200 | 200 | **200** |
| `z-msg-example` | 404 | 404 | **404：不进发布清单** |

**引 1.2.2 就能拿到全部东西**：

```xml
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-msg-im</artifactId>
    <version>1.2.2</version>
</dependency>
```

> ✅ **历史上那些"只在主干、尚未发布"的改动，如今都已随 1.2.1、1.2.2 上了中央仓库**：
> `op=auth` 带内换票（§4）、`ready` 的 `ops`/`limits` 自描述（§4）、腾讯云短信 `TencentSmsSender`
> 与极光推送 `JPushSender`（§7）、IM 的线格式（§6：id 由数字改字符串、`kind=chat` 帧的 `createdTime`
> 由 epoch 毫秒改 ISO 字符串）。这些提交都在 `1.2.1` 的发布 commit 之前合入，`1.2.1`/`1.2.2` 的构件字节里都有。
> 唯一仍在"主干但未另发版本号"的，是 parent 从 `1.0.19` 抬到 `1.0.21` 这一刀（`ecf405f`，未 bump revision）——
> 它只动构建口径、不动发布形状（flatten 后仍是自包含 pom），对消费者无感。

> ⚠️ **`z-boot-msg-starter` 只能带你走一半。** 2026-09-30 实测 repo1：starter 最新是 `1.0.21`，
> 它的 pom 里的 z-msg 依赖**只有一条** — `z-msg-web:1.2.2`。这一版 pin 对了（收件箱读侧的归属校验、
> `/history` 的同步判定量都拿得到）。但 **`ws` / `im` / `channels` 三个坐标一个都不在聚合里**，
> 所以下面"这层能替你干什么"表里的 WebSocket、聊天室、微信式 IM、外部渠道那四行，
> 必须直接引 `z-msg-ws`、`z-msg-im`、`z-msg-channels` 才拿得到——starter 的聚合范围兜不住这三件事。

---

## 🎯 这层能替你干什么

| 你想要的 | 引哪个模块 | 你要写的代码 |
|---|---|---|
| 一句 `gateway.send(...)` 把一条业务事件投到 N 个渠道 | `z-msg-core` | 0 行（配置驱动） |
| 系统站内信：落库 + 未读红点 + 列表/详情/已读/删除 | `z-msg-core` + `z-msg-web` | 1 行 `send(IN_APP)` |
| WebSocket 接入（握手鉴权、订阅、多端、心跳、背压上限） | `z-msg-ws` | 1 个 `MsgPrincipalResolver` bean |
| 五分钟搭出聊天室 | `z-msg-ws` + 1 个授权策略 | 一个 `TopicAuthorizationPolicy` |
| 微信式 IM（会话/成员/seq/已读回执/未读汇总/离线增量） | `z-msg-im` | 0 行（引 jar 即自动装配） |
| 对接企微 / 钉钉 / 飞书 / Slack / 公众号 / 阿里云与腾讯云短信 / 极光推送 | `z-msg-channels` | 0 行（一段 yml） |

---

## 🏗️ 项目结构

7 个 Maven 模块 + 1 个聚合 parent（reactor 里共 8 个 `io.github.yuku123` 坐标）。

```
z-msg/
├── pom.xml                  # 聚合 parent：继承 z-boot-parent:1.0.21，${revision}=1.2.2 + flatten(oss)
├── _doc/001_arch/04-protocol.md  # 实时协议逐帧规范（本仓唯一 _doc 文件）
├── z-msg-api/               # 纯 SPI 与常量，零实现零 Spring
├── z-msg-core/              # 默认 provider（mock/SMTP）+ ChannelRouter + 限流 + 重试 + 模板 + 站内信 + 票签发 + 7 表 DDL
├── z-msg-channels/          # 真实外部厂商 provider
├── z-msg-web/               # REST Controller + MsgAutoConfiguration + 身份接缝 + 管理面闸门 AdminEndpointGate
├── z-msg-ws/                # WebSocket 实时层：短期票握手 + 帧协议（ready 自描述、带内换票）+ topic 订阅 + 三态授权 + 在线注册表
├── z-msg-im/                # 会话/成员/消息/已读回执域，seq 分配与增量同步，自带 4 张表与 REST
├── z-msg-example/           # 演示宿主：大厅聊天室 + 站内信（maven.deploy.skip=true，不进中央仓库）
├── _doc/003_script/deploy_maven_center.sh   # 一键发布 Maven Central（publish/verify/gpg-init/readme），仓库根
└── _doc/003_script/install-settings.sh      # 把 Central 凭证写入 ~/.m2/settings.xml，仓库根
```

| 模块 | 职责 |
|---|---|
| `z-msg-api` | 纯 SPI 与常量：`MessageGateway` / `Message` / `Channels` / `ChannelSender` / `RealtimePublisher` / `RealtimeMessage` / `RealtimeTopics` / `TopicAuthorizationPolicy`，零实现零 Spring |
| `z-msg-core` | 默认 provider（mock / SMTP）、`ChannelRouter`、限流、重试、模板、投递日志、批量、站内信读写侧、`RealtimeTicketService` 签发与校验、7 张表的 DDL |
| `z-msg-channels` | 真实外部渠道 provider：钉钉/企微/飞书/Slack 群机器人、公众号模板消息、阿里云与腾讯云短信、极光推送、录制 mock；全部 JDK `HttpURLConnection`，不引厂商 SDK |
| `z-msg-web` | REST Controller + `MsgAutoConfiguration`（`spring.factories`）+ 身份接缝 `MsgPrincipalResolver` + 管理面闸门 `AdminEndpointGate` |
| `z-msg-ws` | WebSocket 实时接入层：短期票握手、帧协议（含 `ready` 自描述与带内换票 `op=auth`）、topic 订阅、三态授权、在线注册表 |
| `z-msg-im` | 会话/成员/消息/已读回执域，`seq` CAS 分配与增量同步，自带 4 张表与 18 个 REST 端点 |
| `z-msg-example` | 能跑的大厅聊天室 + 站内信宿主（`maven.deploy.skip=true`，且根 POM central profile 的 `<excludeArtifacts>` 把它挡在 bundle 外），顺带承载端点普查 |

> `z-msg-spring-boot-starter` 已不在本仓——它下沉到 `z-boot/z-boot-integration-starters/z-boot-msg-starter`。
> 深入文档各就各位，本 README 只讲清边界与入口：
> - 实时协议逐帧说明 → [`_doc/001_arch/04-protocol.md`](_doc/001_arch/04-protocol.md)
> - 渠道 / 签名 / 超时 / 待核对项 → [`z-msg-channels/README.md`](z-msg-channels/README.md)
> - 五分钟跑起来、宿主四个文件 → [`z-msg-example/README.md`](z-msg-example/README.md)

---

## 🔧 技术栈

| 层级 | 技术（均来自 POM / 地板实测） |
|------|------|
| 语言 / 运行时 | Java 8（全组织口径 1.8） |
| 框架 | Spring Boot 2.7.18（由 `z-boot-parent:1.0.21` → `z-boot-dependencies` 地板统一供给，模块 POM 不再字面钉） |
| 持久层 | MyBatis-Plus 3.5.7（地板 `mybatis-plus.version`）+ z-boot-datasource-starter |
| 数据库 | MySQL 8（生产，走 `z.base.db.msg.*`）/ H2（测试与演示内存库） |
| 实时 | Spring `spring-boot-starter-websocket` 标准 `TextWebSocketHandler`（不引额外 IO 框架） |
| JSON | Jackson，统一收口在 `MsgJson`（见 §4），`findAndRegisterModules()` 探测式注册 jsr310 |
| 日志 | SLF4J 1.7 + **Log4j2 2.20.0**（本仓刻意把 log4j 四件直接钉在 2.20.0，与地板 2.25.4 不同值 = 零漂移选择；`maven-enforcer-plugin` 在 validate 阶段 ban 掉 `log4j-to-slf4j` / `log4j-slf4j2-impl` / `spring-boot-starter-logging` / logback） |
| 其它 | netty 4.1.138.Final（收口 CVE 后重发）、`z-util-core:1.0.13`（排掉 `log4j-slf4j2-impl`）、Maven 3.6+ |

---

## 🚀 快速开始

### 依赖与最小配置

`z-msg-web` / `z-msg-ws` / `z-msg-im` / `z-msg-channels` 各带一份 `META-INF/spring.factories`，
放进 classpath 就装配，宿主不需要 `@Import`。`z-msg-core` 没有自己那份——它的
`MsgCoreConfiguration`（provider 注册表、限流器、实时发布器、线程池）由 web 的
`@ComponentScan({"com.zifang.z.msg.core", "com.zifang.z.msg.web"})` 带进来；只想引 core 不引 web
的宿主，自己 `@Import(MsgCoreConfiguration.class)`。

```yaml
z-msg:
  enabled: true
  realtime:
    ticket-secret: ${MSG_TICKET_SECRET}   # 不配就 fail closed：换票回业务 code 503，握手 HTTP 503
  sms:
    provider: aliyun                      # 缺省 mock
  email:
    provider: smtp                        # 缺省 mock
  channel:
    im-dingtalk: { provider: robot, token: ${DING_TOKEN}, secret: ${DING_SECRET} }

# 数据源走 z-boot 的 ModuleDataSourceTemplate：z-msg 只用自己的库，键名是 z.base.db.msg.*（不是 z.msg.*）
z:
  base:
    db:
      msg: { host: 127.0.0.1, port: 3306, database: msg, username: ${MSG_DB_USER}, password: ${MSG_DB_PASSWORD} }
```

**凭据必须经环境变量注入，禁止写进 yml / jar / 镜像层**（演示宿主 `application.yml` 里那把
`ticket-secret` 是 `demo-only-…` 占位，生产必须覆盖、不要复用）：

| 环境变量 | 用途 |
|----------|------|
| `MSG_TICKET_SECRET` | 实时短期票 HMAC 密钥；空则握手 fail-closed |
| `MSG_DB_USER` / `MSG_DB_PASSWORD` | z-msg 独立库数据源 |
| `DING_TOKEN` / `DING_SECRET` 等 | 各厂商 channel 参数，逐键见 [`z-msg-channels/README.md`](z-msg-channels/README.md) |
| `CENTRAL_USERNAME` / `CENTRAL_TOKEN` | 发布 Maven Central 用（`_doc/003_script/install-settings.sh` 写入 `~/.m2/settings.xml`） |

`dataSourceMsg` 带 `@ConditionalOnMissingBean(name="dataSourceMsg")`，宿主可以自己顶一个
（演示宿主就是这么塞 H2 的），但顶完必须保证 `sqlSessionFactoryMsg` 仍指向它——见「装配契约」。

### 本地跑起来（无需 MySQL）

```bash
mvn -B -o -DskipTests install                 # 装进 ~/.m2，一次即可
mvn -B -o -pl z-msg-example spring-boot:run   # 演示宿主，端口 18099
```

打开 <http://localhost:18099/> 两个标签页，分别点「1001 张三」「1002 李四」——
聊天与站内信都是真的跨连接到达，页底的帧日志可逐帧核对。演示用内存 H2，数据源在
`MsgExampleApplication#dataSourceMsg` 里顶掉。
（同一 workspace 里若 18099 被别的进程占了，用 `--server.port=` 换。）

### 编译与全量校验

```bash
mvn -B -o clean verify
```

第三方版本一律由 `z-boot-parent` → `z-boot-dependencies`（地板）+ `z-boot-fleet`（兄弟仓权威表）
供给，模块 POM 里不应再出现字面版本钉；若报找不到版本，先确认本地/镜像能解析到
`io.github.yuku123:z-boot-parent:1.0.21`。

---

## 🧩 装配契约（改坏就全盘坏）

| 契约 | 内容 |
|---|---|
| `dataSourceMsg` / `sqlSessionFactoryMsg` | **bean 名是契约**。core 的 mapper 与 im 的 mapper 都 `sqlSessionFactoryRef="sqlSessionFactoryMsg"`，两套 mapper 必须落在同一个 SqlSessionFactory 上——`ImAutoConfigurationTest` 直接断言这一点。 |
| 两套 `@MapperScan` | web 的那份只覆盖 `com.zifang.z.msg.core.domain.mapper`；`z-msg-im` 靠自己的 `spring.factories` + 自己的 `@MapperScan` 自注册。**删掉那行 spring.factories 注册，im 全部 bean 消失**（`ImDisabledAutoConfigurationTest` 钉着）。 |
| `msgMybatisPlusInterceptor` | 分页插件 bean 名同样是契约，宿主已有 MP 配置时要顶掉而不是并存。 |
| 条件装配顺序 | `MsgImAutoConfiguration` 用 `@ConditionalOnClass(name=...)` + 属性开关，**不用** `@ConditionalOnBean`：实测自动配置类上 `@ConditionalOnBean(name="sqlSessionFactoryMsg")` 永远不匹配。已知不完美：守的是"类在不在"，不是"bean 在不在"，类注释里写着。 |
| 表结构 | 随 jar 走：`classpath:z-msg/sql/schema-h2.sql`、`classpath:z-msg/sql/im-schema-h2.sql`（MySQL 版同目录）。 |
| 扫描范围收窄 | web 的 `@ComponentScan` 只覆盖 `core` + `web` 两个包，**不是** `com.zifang.z.msg` 全包：宿主把演示/实验模块放进 classpath 时，它们的 bean 不会被扫进生产。 |

---

## 📨 系统站内信

写侧是业务代码本体，一句：

```java
@Resource private MessageGateway gateway;

public void onOrderShipped(long userId, String orderNo) {
    gateway.send(Message.builder()
            .channel(Channels.IN_APP)
            .userId(Long.valueOf(userId))
            .bizType("ORDER_SHIPPED")
            .msgType("NOTICE")                 // 自由字符串，DB 默认 'NOTICE'；收件箱按它分 tab
            .subject("订单已发货")
            .content("订单 " + orderNo + " 已交付承运商")
            .linkUrl("/orders/" + orderNo)
            .build());
}
```

`IN_APP` 的 provider 是 `db`（实现类 `InAppChannel`）：落库之后**同一次调用**里顺带往 `user:<id>`
推一帧 `kind=inbox`，在线的人红点当场加一，不在线的什么都不缺
（`z-msg.inbox.push-realtime=false` 可关）。实时只是增强，不是前提：没有 transport 时
`publish` 返回 0 且不抛异常。

读侧三件事是刻意做的，别绕过去：

- **`userId` 不是入参**。所有读侧方法只吃 `MsgPrincipalResolver` 解出来的当前登录人，WHERE 里恒定
  `user_id = 当前人`，越权不是"忘了判"而是"写不出"。
- **列表不带正文**。`content` 是 CLOB，列表只取展示列（`MsgInboxService#pageMine` 的显式投影），
  点进去再走 `/inbox/detail?id=`。
- **过期与软删不计红点**。`unread-count` 与 `list` 共用同一套 alive 过滤；删除一律 `deleted=1`，
  `/delete-all` 不再是物理删。

---

## 🔌 WebSocket 接入（帧线格式）

浏览器的 `WebSocket` 构造方法带不上自定义头（cookie 会带，但 JWT 走 `Authorization` 头），
所以握手身份走**短期票**：

```
GET /api/msg/inbox/ws-token        ← 已认证的 HTTP（session / JWT 由宿主 resolver 解）
   → { success: true, data: { token: "<HMAC>", expiresInSeconds: 60 } }
WS  /api/msg/ws?token=<token>      ← 握手只认这张票，签名/受众/过期任一不过 → 401
```

一张票只换一条连接，所以**重连要重新换票**（缓存 token 复用会撞 401）。握手与帧内换票走的是
同一本一次性账（`RealtimeTicketService#consume`）。

**帧线格式（`RealtimeMessage` + `MsgJson`，实现为准）：**

- 一帧 = **一条 WebSocket 文本消息**，内容是一个 JSON 对象；序列化统一走 `z-msg-core` 的 `MsgJson`
  （Jackson `ObjectMapper`：`NON_NULL` 输出、`FAIL_ON_UNKNOWN_PROPERTIES=false`、`findAndRegisterModules()`）。
- `payload` 字段本身是**一个 JSON 字符串**（外层帧再解析一次才是业务结构）。因为它是 String 字段，
  Jackson 在写外层帧时会把它内部的换行/控制字符转义成 `\n`——所以**聊天正文里带裸换行不会把整条帧截断**：
  WebSocket 帧由传输层按长度分帧，不是按行分帧，外层 JSON 又已转义控制字符，两条都保住了帧边界。
- 入站解析用 `MsgJson.toMap(...)`，**容错**：非 JSON、缺 `op`、解析异常都返回 `null` →
  服务端回一帧 `WS_BAD_FRAME`，**连接不关、不动身份、不动订阅**，下一帧照旧能发能收。
- 字段：`op` / `kind` / `topic` / `from` / `clientMsgId` / `seq`(long，控制帧 0) / `ts`(服务端 epoch 毫秒) /
  `payload`(JSON 字符串) / `errorCode` / `errorMessage`。

连上后服务端先推一帧 `ready`（含自动订阅好的 `user:<自己>`、`sys:broadcast` 与配置声明的公共 topic），
之后客户端帧是 `subscribe` / `unsubscribe` / `publish` / `ping` / `auth` 五张，服务端帧是
`pong` / `ready` / `message` / `ack` / `error`。逐字段、错误码、资源上限、断线与并发语义都在
[`_doc/001_arch/04-protocol.md`](_doc/001_arch/04-protocol.md)。

`ready` 除了 `connectionId` / `userId` / `topics`，还报出 `ops`（服务端认哪几张帧）与 `limits`
（当前真正生效的资源上限）。它要顶掉的是前端抄默认值这件事：`idle-timeout-seconds` 从 120 改成 31
之后，症状是"连上没多久就断线，浏览器侧看不出原因"。两条口径：`auth` 只在 `inband-auth-enabled=true`
时出现在 `ops` 里（报的是"有人处理"，不是"你有权限发"，`publish` 一直在清单里而放行仍由策略判）；
配成 0/负数的那三项上限**整个键缺席**而不是报 0，因为 `"maxTopicsPerConnection": 0` 会被读成
"一条都不许订"，而它的真意是"没有这道闸"。

`op=auth` 是**默认关闭**的一张帧（`z-msg.ws.inband-auth-enabled=true` 才认）。它解决的是"票 60s 过期，
而连接一旦建立就再也不看票"：切账号、多账号共用一条 socket 这类宿主，以前只能断线重连。

```js
ws.send(JSON.stringify({op: 'auth', clientMsgId: 'a1', token: await freshTicket()}));
// → ack: {changed:true, userId:7102, previousUserId:7101, revoked:["user:7101"], topics:[...]}
```

用的仍是同一本一次性账（`RealtimeTicketService#consume`）：一张票无论走握手还是走帧，只兑现一次；
失败只回 `WS_AUTH_FAILED`，**不断连接、不动身份、不动订阅**。换身份时服务端按**新身份**复核每一条
已有订阅，不配持有的直接退订并列进 `revoked`——反过来做不到的那半：服务端没有"某用户该有哪些频道"
的可枚举清单，所以**换回原身份只有自己的收件箱会自动补回来**，其余频道客户端要按 `revoked` 重新
`subscribe`。日志侧有守卫：`ticketNeverReachesTheLogsButTheRebindLineDoes` 断言票的签名段零命中。

多端同时在线由 `WsSessionRegistry` 按用户记账，超了挤掉最老的那条（`max-sessions-per-user=8`）；
每连接 topic 上限 64 由 handler 拒绝越界的 `subscribe`。空闲 120s 与单帧 256 KiB 是设进容器的
`setMaxSessionIdleTimeout` / `setMaxTextMessageBufferSize`，属容器行为。
服务端往同一条连接写帧时加锁串行（`MsgWsSession#sendLock`）；写失败（对端已关、容器已 close）不抛给
业务方，而是把该连接从注册表摘掉。

---

## 💬 五分钟聊天室

大厅只有一个 topic 和一段策略。**写侧默认全拒**——`op=publish` 是"让服务端代发"的口子，
没有策略放行时任何 topic 都不能发，所以搭聊天室要显式说清放开哪一个：

```java
@Component
public class LobbyChatPolicy implements TopicAuthorizationPolicy {

    private final WsProperties wsProperties;                       // 判据取自配置，不再抄一遍 "room:lobby"

    public LobbyChatPolicy(WsProperties wsProperties) { this.wsProperties = wsProperties; }

    @Override public boolean supports(String topic) {              // 只管配置里声明为公共的那些 room:
        return RealtimeTopics.isRoom(topic) && wsProperties.getPublicTopics().contains(topic);
    }
    @Override public Boolean allowSubscribe(Long userId, String topic) { return userId == null ? null : Boolean.TRUE; }
    @Override public Boolean allowPublish(Long userId, String topic)   { return userId == null ? null : Boolean.TRUE; }
    @Override public int order() { return 10; }
}
```

```yaml
z-msg:
  ws:
    public-topics: [room:lobby]
```

三态（`TRUE` 放行 / `FALSE` 拒绝 / `null` 不表态）而不是布尔，是因为一条连接上会同时挂着
自己的收件箱、群聊、租户公告，没有哪个模块能单独判完；任一策略 `FALSE` 即拒，全部不表态才落到
传输层默认（只允许订 `user:<自己>`、`sys:broadcast` 和配置里声明的公共 topic；写侧一律拒绝）。
不认识的 topic 一律返回 `null` 而不是 `FALSE`——返回 `FALSE` 会把 `z-msg-im` 那种按成员表判其它房间
的策略一起否掉。判据取 `WsProperties.getPublicTopics()` 而不是硬写 `"room:lobby"`：宿主改了 yml 而
策略还认老 topic 的话，症状是"连上了但没人说话"，最难查。

跑起来的完整版本在 `z-msg-example`：两条真 WebSocket、两个登录身份、A 打字 B 立刻看到，
由 `MsgExampleApplicationTest` 在无 mock 的情况下钉住。演示页头显示 `ready` 报回来的 `ops`/`limits`，
心跳间隔按 `idleTimeoutSeconds` 推（推导规则见 `_doc/001_arch/04-protocol.md` §2）。

---

## 🗨️ 微信式 IM

`z-msg-im` 就是"聊天"这件事的域模型，引 jar 即得 18 个 REST 端点 + 实时行为：

| 能力 | 靠什么做到 |
|---|---|
| 单聊幂等、群聊、成员与角色 | `single(me, peer, tenant)` 靠 `uk_im_conv_pair` 保证两人只会有一个会话；成员/角色/禁言走成员表 |
| 消息有序、可增量同步 | 会话内 `seq` 由服务端 CAS 分配（`uk_im_msg_conv_seq`），客户端只按 `sinceSeq` 拉增量；`/history` 连判定量一起给（`hasMore`/`nextSinceSeq`/`headSeq`/`minVisibleSeq`） |
| 重发不产生两条 | `clientMsgId` 唯一键 `uk_im_msg_conv_client`，命中时返回既有那行（`seq` 不变） |
| 已读 / 未读 / 回执 | `last_read_seq` + `uk_im_receipt_conv_user`，未读汇总按会话返回 |
| 服务端署名 | 发言落库时 `sender_user_id` 与 `seq` 都由服务端写——这是它和"大厅自报 from"的区别 |
| 别人订不到你的房间 | `ImTopicAuthorizationPolicy` 按成员表判 `room:`，非成员 `FALSE`，且**不影响**大厅放行 |
| 前端拿到的 id 是准的 | 会话/消息/用户引用的 id 线上一律**字符串**（REST 与实时帧同一条规则）——19 位雪花出成 JSON 数字，浏览器 `JSON.parse` 会舍掉末位 |
| 实时那条与补拉那条是同一个气泡 | 帧 payload 的 `createdTime` 与 REST 那一行**同形**（ISO 字符串、截到秒），去重键仍按 `conversationId + seq` |

**为什么 id 出字符串（这条线格式已随 1.2.1/1.2.2 发布）**：id 一旦被 JS 舍入，前端拿舍过的值去拼
`room:<id>` 或回填 `message/send`，收到的是 `403 无权访问会话 <一个服务端从未有过的 id>`，而这条会话
正是它一秒前自己建的——症状与权限配错完全一样。落点是 `@JsonSerialize(using = ToStringSerializer.class)`
钉在 6 个类共 16 个字段（四张表实体 `ImConversationDO`/`ImMemberDO`/`ImMessageDO`/`ImReadReceiptDO`、
会话视图 `ImConversationView`、未读汇总 `ImUnread`）加 `ImMessageService` 两处手工拼的帧载荷
（`payloadOf`、`publishReadFrame`）走 `asText()`。`ImUnread` 那一处是"给实体打标"结构上覆盖不到的：
它是 `ImReadService` 现装的另一份 DTO，只由 `unread/summary` 的线上形状钉着。`seq` 一类游标仍是数字。

**同一段里第二条形状**：帧的 `createdTime` 也收成字符串，和 REST 那一行、站内信帧一致（原为 epoch 毫秒）。
因为 `MsgJson` 那个私有 mapper **没关** `WRITE_DATES_AS_TIMESTAMPS`、jsr310 靠宿主带，把裸 `LocalDateTime`
丢进 map 的话线格式就由宿主依赖表决定，所以服务端自己 `toString()`；截到秒是因为 MySQL 的 `created_time
TIMESTAMP` 只存整秒，而帧里那份是 insert 之前的内存值。比的时候按**"两边各截到秒后相等"**（MySQL 读回整秒、
H2 存微秒带尾巴）。外层帧的 `ts` 仍是 epoch 毫秒（传输层绝对时刻，不是一回事）。

`ImMessageService#send` 的完整签名：

```java
ImMessageDO send(Long conversationId, Long senderUserId, String msgType, String content,
                 String clientMsgId, List<Long> atUserIds, Long replyToSeq);
```

`POST /api/msg/im/message/history` 回的是一个**对象**，不是消息数组（对象形状从 1.2.0 起对外）：

```json
{ "rows": [ { "seq": 7, "content": "…" } ],
  "nextSinceSeq": 7, "hasMore": true, "headSeq": 9, "minVisibleSeq": 1 }
```

增量同步因此只剩一段没有分支的循环：

```js
let cursor = 0;
for (;;) {
  const { data: p } = await post('/api/msg/im/message/history',
    { conversationId, sinceSeq: cursor, size: 200 });
  append(p.rows);
  cursor = p.nextSinceSeq;
  if (!p.hasMore) break;
}
// 追平后若 cursor < p.headSeq，而中间那几个号既 >= p.minVisibleSeq 又不在 rows 里，
// 那是库里就没有那个号（占号成功而插入失败的代价），不是客户端漏了。
```

后三个字段的来历，都是客户端自己算不出来的那类：

- `hasMore` 靠**多读一条**探出来（服务端读 `cap + 1` 条、只回 `cap` 条），所以"这一页正好回满"和
  "后面还有"是两件分得开的事；请求里的 `size` 会被 `z-msg.im.max-page-size` 夹掉。
- `nextSinceSeq` 是本次下限之后真正交出去的最后一条；空页时它就是本次生效的下限。
- `minVisibleSeq = max(请求的 sinceSeq, 本人的 cleared_seq) + 1`：被"清空聊天记录"挡掉的那一段和
  "库里本来就没有"的那一段靠它区分。它是**从本次请求下界推出来的**，不是会话绝对下界。`headSeq` 是
  读取时刻的会话水位（`last_msg_seq`）。

单聊默认同时投两条 topic（`room:<会话>` 与 `user:<对面那一位>`，`z-msg.im.user-side-push`），
两条帧 payload 相同 —— **客户端按 `conversationId + seq` 去重**，否则会画两个气泡。

---

## 📡 外部渠道对接

`z-msg-channels` 把 `ChannelSender` SPI 落到真实厂商，全部 JDK `HttpURLConnection`，
不引厂商 SDK、不引 OkHttp，每家都有本地 stub server 的离线测试。

| channel | provider | 实现类 | 状态 |
|---|---|---|---|
| `SMS` | `aliyun` | `AliyunSmsSender` | ✅ RPC 签名向量离线钉死 |
| `SMS` | `tencent` | `TencentSmsSender` | ✅ TC3-HMAC-SHA256，签名覆盖"线上真发的那几个字节"；仅离线固定向量与 stub 背书 |
| `EMAIL` | `smtp` | `SmtpEmailSender`（core） | ✅ |
| `IN_APP` | `db` | `InAppChannel`（core） | ✅ |
| `WEBHOOK` | `http` | `WebhookSender` | ✅ |
| `IM_WECOM` / `IM_DINGTALK` / `IM_FEISHU` | `robot` | 群机器人三家 | ✅ |
| `IM_SLACK` | `bot` | `SlackBotSender` | ✅ |
| `IM_WEIXIN_MP` | `mp` | `WeixinMpSender` | ✅ token 缓存 + 过期重取 |
| `PUSH_JPUSH` | `jpush` | `JPushSender` | ✅ v3 push，Basic 认证；仅离线 stub 背书 |
| `IM_WEIXIN_MINI` / `PUSH_FCM` / `PUSH_APNS` / `PUSH_WEB` / `REALTIME` | — | **常量有、厂商 sender 无** | ⚠️ 落到 `MockFallbackSender` |

最后那行是这个表存在的原因：`GET /api/msg/channel/list` 每条通道返回
`real` / `ready` / `enabled` / `providers` / `activeProvider` / `configuredProvider` / `fallbackChannels`，
`GET /api/msg/channel/detail?channel=` 再补 `selected`（选中的实现类）与 `selectedIsMock`。
**假成功在自省接口里是看得见的**（`real:false`、`selectedIsMock:true`），
`channelIntrospectionLabelsMockProvidersHonestly` 钉着这条。各家的未支持项与待核对点（含飞书签名形状、
极光透传消息）逐条列在 [`z-msg-channels/README.md`](z-msg-channels/README.md)。

一个业务事件同时投多通道，走 `fanOut`（偏好过滤 + 静默时段 + 限流 + 模板渲染 + 重试 + 投递日志）：

```java
Map<String, String> receivers = new HashMap<String, String>();
receivers.put(Channels.SMS, "13800000000");
receivers.put(Channels.EMAIL, "a@b.com");
receivers.put(Channels.IM_DINGTALK, "robot-key");

FanOutResult r = gateway.fanOut("ORDER_SHIPPED", Long.valueOf(userId), receivers, params, "zh_CN");
```

---

## 🧭 REST 端点全表

响应统一 `com.zifang.util.core.meta.Result`：`{ success, code, message, data }`（字段名是 `message`，不是 `msg`）。

**站内信**（`/api/msg/inbox/...`，全部要求已认证，未认证 401）：

| 方法 | 路径 |
|---|---|
| GET | `/list?page=&size=&unreadOnly=&msgType=` |
| GET | `/unread-count` |
| GET | `/detail?id=` |
| POST | `/read?id=` |
| POST | `/read-all` |
| DELETE | `/delete?id=` |
| DELETE | `/delete-all` |
| GET | `/ws-token` |

**IM**（`/api/msg/im/...`，全 POST，全要求已认证）：

| 前缀 | 端点 |
|---|---|
| `conversation` | `/list` `/single` `/group` `/members` `/member/add` `/member/remove` `/member/role` `/leave` `/mute` `/clear` |
| `message` | `/send` `/history` `/at` `/head` |
| `read` | `/mark` `/unread` `/summary` `/receipts` |

`data` 的形状按端点分三类，别一律当数组接：`conversation/list` 是 `IPage`（行在 `records`，`total` 是真的）、
`message/history` 是对象（行在 `rows`，另带四个判定量）、只有 `conversation/members` 与 `read/receipts` 回裸数组。
其余：`send`/`at` 回消息行、`message/head`·`conversation/clear`·`read/unread` 回数字、`mute`/`leave` 回布尔。

**偏好 / 通道 / 事件 / 管理面**：

| 方法 | 路径 | 认证 |
|---|---|---|
| POST | `/api/msg/preference/upsert` | ✅ 只作用在当前人 |
| GET | `/api/msg/preference/my` | ✅ |
| DELETE | `/api/msg/preference/{id}` | ✅ |
| GET | `/api/msg/channel/list` | ❌ 无需身份；实测不回传任何密钥 |
| GET | `/api/msg/channel/detail?channel=` | ❌ |
| POST | `/api/msg/publish?eventType=&userId=` | ❌ **默认关**（`z-msg.web.publish-endpoint-enabled=false`）。关着时实测 HTTP 200 + 业务 `code:403`，不是 HTTP 403 |
| POST | `/api/msg/template/list` · `/api/msg/template`（建）· `/{id}`（GET/PUT/DELETE）· `/{id}/approve` · `/{id}/reject` · `/cache/refresh` | ❌ **默认关**：8 个映射一律真 HTTP 403 |
| POST | `/api/msg/batch/submit` · `/list` · `/{id}/cancel`，GET `/api/msg/batch/{id}` | ❌ 同上 |
| POST | `/api/msg/delivery/list` | ❌ 同上 |
| GET **和** POST | `/api/msg/delivery/stats` | ❌ 同上。GET 由 `MessageController` 声明、POST 由 `MsgDeliveryLogController` 声明；闸门按**路径**判，不会因为"不长在它自己的 controller 里"而漏出去 |

`/api/msg/send` 不存在。管理面那 12 条路径不是手抄的：`MsgAdminSurfaceCensusTest` 在示例宿主（web + im 全量装配）
里把活的 handler mapping 逐条过一遍闸门判定，与两份钉死的清单对账——少拦一条或多放一条，测试就红。

---

## 🔐 安全边界（照实说）

**默认关的东西**，每一条都是因为开着时"不带身份就能用"：

| 开关 | 默认 | 打开的代价 |
|---|---|---|
| `z-msg.web.trusted-header-enabled` | `false` | 打开后 `X-Msg-User-Id` 就是身份——**必须**确认上游网关会覆盖/剥离客户端自带的同名头 |
| `z-msg.web.publish-endpoint-enabled` | `false` | 服务间事件投递端点，开了要自己保证调用方可信。关着时是 HTTP 200 + 业务 `code:403` |
| `z-msg.web.admin-endpoints-enabled` | `false` | 模板/批量/投递日志三组管理面。关着时是**真 HTTP 403** + `Result` 正文（这些请求根本没进 controller） |
| `z-msg.realtime.ticket-secret` | `""` | 空则 fail closed，不签弱密钥票：`/ws-token` 回业务 `code:503`（HTTP 仍 200），WS 握手由拦截器给真 HTTP 503 |
| `z-msg.ws.public-topics` | `[]` | 只影响"订得到哪些非本人 topic"，全部不表态时按拒绝处理 |

`z-msg.ws.allowed-origins` 是**例外**：默认 `[]` 的含义是"不限 Origin"，不是"全部拒绝"——代码只在列表非空时
调用 `setAllowedOrigins`。理由（`WsProperties` 里写着）：握手凭据是一次性短期票，必须由已认证的 HTTP 会话去
`/inbox/ws-token` 换，跨站页面读不到那个响应；能拿到票的人本来就在登录态里。**如果宿主改成长期 token 走 query，
请务必显式配上自己的域名。**

**管理面为什么是开关而不是鉴权**：库里没有"管理员"这个概念（角色归宿主），所以 1.1.0 收口的形状是默认全关，
`AdminEndpointGate` 一个拦截器按路径挡，宿主在自己的认证边界内打开一行 yml 就能用。**代价**：z-opc 的模板管理页 /
批量任务页 / 投递日志页要额外一行 `z-msg.web.admin-endpoints-enabled=true`（+ 自己前面的登录墙）。

**仍然没做的**（开关不等于鉴权）：管理面**没有角色判定**，开了开关就是全开；
`MsgDeliveryLogController#list` 在开关打开后仍按**请求体里客户端自报的 `userId`** 过滤——那是 PII，
开关只是让它默认不存在。

**票的一次性消费**：握手走 `RealtimeTicketService#consume`，一张票只换得到第一条连接，第二次握手 401——
因为票进过 URL 就会被 access log、代理日志、浏览器历史原样留下来。`verify` 保持纯验签、可重复调用，它
**不是**安全闸门。边界照实说：记账在**进程内存**里，所以 ① 重启后旧票在 TTL 内还能再用一次 ② 多实例各记各的 ③
窗口内成功握手超过 2 万条时开始丢弃最早过期的记录（降级、但会 warn）。要跨实例严格一次得把 jti 放进共享存储
（Redis 之类），z-msg 不替你引这个依赖。

**出站幂等**：`Message.idempotencyKey` 的「同 channel + bizType + key 只投递一次」现在是**跨节点**成立的，
仲裁点是 `z_msg_delivery_log.idempotency_key` + `uk_channel_biz_dedup` 唯一索引。

`ChannelRouter` 有两道：

| 层 | 载体 | 作用 |
|---|---|---|
| 本机快路径 | `ChannelRouter.issued`（`ConcurrentHashMap`） | 省掉一次数据库往返，窗口 24h |
| 跨节点（权威） | 发送**前**插一行 `status=0` 的占位行，撞唯一索引即判 `DUPLICATED` | 多实例部署时同一份消息只发一次 |

**占位必须在发送之前**。只在写日志那一刻靠唯一索引去重是没用的：索引生效时消息早就发出去了。
「占位行即最终行」——送达时就地 `UPDATE` 那一行（不另插），没送达就把它 `UPDATE` 成失败并把幂等位
`SET ... = null` 还回去，所以投递日志里同一次尝试只有一行。

落位时机上，幂等位只在**消息真的送出去了**（`status` 为 success/mock）时才由那一行持有：厂商故障、限流、
偏好屏蔽、静默时段一律写 NULL。唯一索引允许多个 NULL，所以这些行彼此不撞，也不会把消息钉死。

几条照实说的边界：

- **去重令牌带 `userId` 和 `receiver`**，不是裸 key。`Message.idempotencyKey` 的 Javadoc 写的是
  「同 channel + bizType + key」，比实现窄——照字面收窄的话，"一份日报发给 1000 个用户"只会发出去 1 条。
  同一个理由在 `InAppChannel` 判站内信幂等时也写过（按 `(userId, dedupKey)` 而不是 `dedupKey` 单列）。
- **`bizType` 为空时退回仅本机去重**。唯一索引里 `biz_type` 可空，它为 NULL 时整行不参与去重，占位白占。
- **`uk_channel_biz_dedup` 没有时间维度，已送达的幂等位是永久的**（本机 map 是 24h）。最终行为以索引为准，
  所以同一个键过了 24h 重放仍然会被拦下。
- **进程在发送中途挂掉会留下 `status=0` 的占位行**，那一行会一直占着这个键直到被人清掉。
  z-msg 不自带清理任务；要扫的话按 `status = 0 AND created_time < ?` 挑出来处理。
- **占位这一步本身不可用时是 fail-open 的**（库还没升级、连接断了、权限不足）：照常投递，只是退回
  「仅本机去重」——幂等是增强项，不能因为它坏了就把消息一起发不出去。

**存量库要先升级**：`schema-*.sql` 都是 `CREATE TABLE IF NOT EXISTS`，对已经建好的表不生效。
手工执行 [`z-msg/sql/upgrade-delivery-log-idemotency.sql`](z-msg-core/src/main/resources/z-msg/sql/upgrade-delivery-log-idemotency.sql)
（加列 → 建唯一索引，含预检 SQL 与回滚语句）。

---

## ⚙️ 配置全表

`z-msg.*`（core，`MessageProperties`）：

| 键 | 默认 | 说明 |
|---|---|---|
| `enabled` | `true` | 总开关，关掉全部撤回 |
| `sms.provider` | `mock` | `aliyun` / `tencent` / `mock` |
| `sms.default-sign` | `【z-opc】` | |
| `email.provider` | `mock` | `smtp` / `mock` |
| `email.default-from` | `no-reply@z-opc.com` | |
| `email.smtp-host` / `smtp-port` / `smtp-username` / `smtp-password` | — | |
| `template.<code>` | 空 map | 模板串 |
| `channel.<CH>.provider` | 无 | 选厂商实现，见 §外部渠道 |
| `channel.<CH>.enabled` | `true` | |
| `channel.<CH>.fallback-channels` | `[]` | 该通道不可用时的降级顺序 |
| `rate-limit.enabled` | `true` | |
| `rate-limit.default-permits-per-second` | `50` | |
| `rate-limit.per-channel.<CH>` | 无 | 按通道覆盖 |
| `rate-limit.per-user-permits-per-minute` | `20` | |
| `retry.max-attempts` | `2` | |
| `retry.backoff-ms` | `200,1000,3000` | |
| `retry.non-retryable` | 内置列表 | 业务拒绝不重试 |
| `retry.max-blocking-ms` | `1500` | 同步调用最长被拖住的时间 |
| `inbox.push-realtime` | `true` | 落库后顺带推在线连接 |
| `inbox.default-page-size` / `max-page-size` | `20` / `200` | |
| `inbox.expire-days` | `0` | 0 = 不过期 |
| `realtime.ticket-secret` | `""` | 空 = fail closed |
| `realtime.ticket-ttl-seconds` | `60` | 也是消费记录的存活时长 |
| `realtime.ticket-audience` | `z-msg-ws` | |

`z-msg.web.*`：`trusted-header-enabled=false`、`trusted-header-name=X-Msg-User-Id`、
`publish-endpoint-enabled=false`、`admin-endpoints-enabled=false`（判定只在 `AdminEndpointGate.isAdminPath` 一处：
按路径段对齐，`/api/msg/templates` 不算管理面）。

`z-msg.ws.*`：`enabled=true`、`path=/api/msg/ws`、`allowed-origins=[]`、`public-topics=[]`、
`idle-timeout-seconds=120`、`max-text-message-bytes=262144`、`max-sessions-per-user=8`、
`max-topics-per-connection=64`、`inband-auth-enabled=false`（开了才认 `op=auth`）。

`z-msg.im.*`：`enabled=true`、`default-page-size=50`、`max-page-size=200`、`max-members-per-conversation=500`、
`max-content-length=4000`、`seq-cas-max-attempts=200`、`seq-cas-backoff-ms=1`、`publish-realtime=true`、
`publish-read-receipt=true`、`user-side-push=true`、`user-side-push-max-members=200`。

`z-msg.channel.<CH>.*` 的厂商键（`token` / `secret` / `app-id` / `app-secret` / `access-key-id` /
`access-key-secret` / `sdk-app-id` / `app-key` / `master-secret` / `sign-name` / `template-code` / `region` /
`signature-version` / `slack-channel` / `url` / `base-url` / `connect-timeout-ms` / `read-timeout-ms` / `retries` /
`mock`，另加 `provider` 这个选择位）逐个"在哪被读"见 [`z-msg-channels/README.md`](z-msg-channels/README.md)。

---

## 🗄️ 数据库

MySQL 与 H2 各一份，同源由 `SchemaParityTest` 真跑执行验证：

| 脚本 | 内容 |
|---|---|
| `z-msg/sql/schema-mysql.sql` / `schema-h2.sql`（在 `z-msg-core` 的 jar 里） | 7 张表：`z_msg_message`、`z_msg_template`、`z_msg_template_i18n`、`z_msg_template_version`、`z_msg_delivery_log`、`z_msg_batch_task`、`z_msg_user_preference`。`z_msg_delivery_log` 上有唯一键 `uk_channel_biz_dedup`（跨节点出站幂等的仲裁点）；**存量库要走 `upgrade-delivery-log-idemotency.sql` 手工升级** |
| `z-msg/sql/upgrade-delivery-log-idemotency.sql`（在 `z-msg-core` 的 jar 里） | 存量库的增量升级：给 `z_msg_delivery_log` 加 `idempotency_key` 列 + 建 `uk_channel_biz_dedup`。含预检 SQL（存量重复令牌会挡住建索引）与回滚语句 |
| `z-msg/sql/im-schema-mysql.sql` / `im-schema-h2.sql`（在 `z-msg-im` 的 jar 里） | 4 张表：会话、成员、消息、已读回执；唯一键 `uk_im_conv_pair`、`uk_im_member_conv_user`、`uk_im_msg_conv_seq`、`uk_im_msg_conv_client`、`uk_im_receipt_conv_user` |

上表那些"幂等/有序/不重复"的承诺，落点全在这几个唯一键上。

---

## 🧪 测试

```bash
mvn -B -o clean verify       # 8 模块全量校验
```

各模块都自带真起 Spring 上下文 / 真 Tomcat 的端到端用例，钉住的关键几例：

| 模块 | 关键几例 |
|---|---|
| core | `SchemaParityTest`（MySQL/H2 两份 DDL 真跑、同表同列）、`SenderRegistryProviderDefaultTest`（缺 provider 兜底成 mock 且被如实标记）、`ChannelRouterIdempotencyTest`（出站幂等的落位时机：主通道成功要占位、**降级送达同样要占位**否则用户收两条、投递失败绝不占位否则重放被吞——三条里后两条首跑都是红的）、`ChannelRouterIdemSlotTest`（**跨节点**那半：别的节点已占位时一个字节都不发、占位行就地转正不另插一行、没送达把幂等位 `SET = null` 还回去、占位这步坏掉不拦投递、降级成功时位归降级那一行、`bizType` 为空时不占位、超长令牌折叠到 `VARCHAR(191)` 且不撞车——7 条）、`DeliveryLogIdemIndexH2Test`（`uk_channel_biz_dedup` 在**真 H2** 上的行为：重复的非 NULL 令牌必须被挡住、多个 NULL 必须共存、还回去之后同一个令牌要能重新占住、`biz_type` 为 NULL 的行不参与去重——4 条）、`RealtimeTicketServiceTest`（签发/验签/过期/篡改 + 一次性消费：`consume` 只放行第一次、`verify` 仍可重复且**不是**安全闸门、2 万条上限真把住且超量降级） |
| web | `MsgInboxApiTest`（匿名 401 而登录 200、`oneUserCannotSeeOrMutateAnotherUsersInbox`、过期项既不列表也不计红点、分页真截断且 `total` 是真的、同 `idempotencyKey` 不重复入库、自省接口如实标 mock、`ws-token` 绑当前人）、`MsgAdminEndpointGateTest` / `MsgAdminEndpointEnabledTest`（缺省时管理面一律 HTTP 403 且正文点名开关、非管理面照常答、开关打开后同一批全 200——后者是前者的对照） |
| ws | 握手 fail-closed、`enabled=false` 全撤、端到端（多的一例：同一张票第二次握手 401 且换新票同用户照样连得上）、授权策略、注册表索引（8 持票 × 4 加入 × 20000 代对撞）、带内换票 `op=auth` 8 例（同身份续期与重放必拒、坏票不改任何状态、非本人频道按新身份复核并退订、票签名段进不了日志）+ 默认关时回 `WS_AUTH_DISABLED` 1 例、`ready` 自描述 7 例（`ops`/`limits` 与那道闸同源、"不限"三项整键缺席、分发分支与清单双向一致的源码普查） |
| im | 自动装配（im 的 mapper 与 core 的 mapper 落在同一个 `sqlSessionFactoryMsg`）、禁用路径（读 `ConditionEvaluationReport` 点名 `im.enabled`）、三态授权、两条真 socket 的实时、REST（`conversationIdSurvivesAJavaScriptStyleRoundTrip`：把响应里 19 位字符**原样**回填 `message/send`/`history`/`unread/summary` 再比一次）、服务层（增量同步判定量：`hasMore` 只来自多读的那一条、照 `nextSinceSeq` 翻到底一条不多不少、`minVisibleSeq` 报真生效的游标） |
| channels | 每家 sender 都配本地 stub server 离线测；阿里云 RPC 签名与腾讯云 TC3-HMAC-SHA256 有固定向量钉死，凭据不上日志 |
| example | 大厅 A 发言进 B 的帧、未放行 topic 被拒而大厅同连接可发、站内信三处同时命中、未登录 401、首页真伺服、同 cookie jar 两个标签页仍是两个人、私聊面板那条端到端（真 Tomcat + 真 H2 + 两条真 socket）、`MsgAdminSurfaceCensusTest`（全量装配宿主里逐条过闸门，钉住"拦下 / 放行"两份清单） |

> 主干工作树上一次全量 `mvn -B -o clean verify`（2026-09-27 那棵树）为 **224 例、0 失败 0 跳过**；
> 本次改动后 `mvn -o clean test`（2026-10-06，JDK 8，8 模块全跑）为 **238 例、0 失败 0 错误 0 跳过**
> （227 → 238，多出的 11 例即 `ChannelRouterIdemSlotTest` 7 条 + `DeliveryLogIdemIndexH2Test` 4 条；
> 更早一轮多出的 3 例是 `ChannelRouterIdempotencyTest`；`z-msg-ws` / `z-msg-im` / `z-msg-example` 三个模块
> 此前在本机因 `~/.m2` 缓存缺 `central=` 记录而无法离线解析，一直是被 SKIPPED 的，这轮才真跑到）
> （core 40 / channels 82 / web 13 / ws 39 / im 56 / example 8）。此后仅有 pom/依赖口径改动与一支
> charset 测试修补（`9ca2948`），未重跑，例数不变。`1.2.1`/`1.2.2` 的发布树是在含 op=auth、`ready`
> 自描述、IM 字符串 id、`createdTime` ISO、腾讯云·极光 sender 那批守卫之后重发的；本 README 未对
> 发布件精确到个位数地重测，具体跑绿以本地 `mvn verify` 为准。

这些不是"跑过一遍绿了"就完事：每条新增守卫都做过变异验证（摘掉守卫必须变红，再按 `md5` 逐字节还原复跑）。

---

## 🔁 迁移摘要

**破坏性变更集中在读侧身份与线格式。**

| 旧 | 现 | 迁移动作 |
|---|---|---|
| 1.0.0 收件箱 `list(Long,Integer)`/`unreadCount(Long)`/`detail(Long)`… 归属人来自调用方 | 1.1.0 起一律从 `MsgPrincipalResolver` 取当前人 | 删掉 `userId` 入参；`/api/msg/list?userId=` → `/api/msg/inbox/list?page=&size=`，行读 `data.records` |
| `POST /api/msg/im/message/history` → `data` 是裸数组（1.1.0 及更早） | **1.2.0** 起 `data` 是对象 `{rows,nextSinceSeq,hasMore,headSeq,minVisibleSeq}` | 行从 `data` 改读 `data.rows`；翻页用 `nextSinceSeq`，别自己拼 `rows[last].seq`；`size` 条判断换成 `hasMore` |
| IM id 出数字（1.2.0 及更早）、`kind=chat` 的 `createdTime` 出 epoch 毫秒 | **1.2.1/1.2.2** 起 id 出字符串、`createdTime` 出 ISO 字符串（REST 与帧同形） | 把这些 id 当字符串用，别再 `Number()`（`Number("2103885891501236225")` 会舍成 `…200`）；帧里时间直接 `String` 用；要绝对时刻认外层 `ts`（仍是数字）。`seq` 一类游标仍是数字 |
| `z.msg.*` 前缀（1.0.0 README 写的） | 从来不是真实前缀 | 根前缀一直是 `z-msg` |
| `/api/msg/template/**` · `/batch/**` · `/delivery/**`、`GET /delivery/stats` | 路径与请求体**不变**，但默认一律真 HTTP 403 | 宿主加一行 `z-msg.web.admin-endpoints-enabled=true` 就回到旧行为 |

`z-msg-example` 曾被**误发**为 `z-msg-example:1.1.0`（版本号不可复用、删不掉）；从 1.2.0 起根 POM central
profile 的 `<excludeArtifacts>` 把它挡在 bundle 外，`repo1` 上 1.2.0/1.2.1/1.2.2 三版均实测 404。
一条坑：**`mvn -o deploy -Pcentral` 会静默不发**——central-publishing 的 `publish` 要求联网，离线时每模块打一行
"requires online mode … skipping" 后照样 `BUILD SUCCESS`。判"发出去了"只认 repo1 的 HEAD 读数，别认 Maven 那三个字。

---

## 🔗 相关项目

| 项目 | 关系 |
|---|---|
| `z-boot` | BOM 与 starter 聚合（本仓 parent `z-boot-parent:1.0.21`；`z-boot-msg-starter` 最新 `1.0.21` 实测 pin `z-msg-web:1.2.2`，聚合里只有 `z-msg-web` 这一条，`ws`/`im`/`channels` 要直接引，见开头那条警告） |
| `z-ctc` | 宿主认证来源：`MsgPrincipalResolver` 生产实现接它的 JWT |
| `z-mq` | 同系列消息队列；z-msg 刻意不依赖它。`RealtimeTransport` 本仓库只有一个实现（`WsSessionRegistry`，单机内存），多节点部署要宿主自己接一层桥 |
| `z-opc` | 下游消费者（站内信、模板管理页、演示宿主前端） |

## 📄 License

[MIT License](LICENSE)（根 POM `<licenses>` 声明 MIT License，`LICENSE` 文件为 MIT 正文）。

---

_Maintained by the z-opc-foundation organization._

---

## 文档目录

本仓文档未完全收口到规范的 `_doc/{001_arch,002_deploy,003_script,004_skill}` 分层目录：目前 `_doc/` 下只有一个
**平铺的**实时协议文件（文件名也不带 `_doc` 规范的编号前缀）。下面链接的都是实测真实存在的文件：

- [`_doc/`](_doc/) — 文档目录（当前非分层，只有一个文件）
  - [`001_WS_PROTOCOL.md`](_doc/001_arch/04-protocol.md) — z-msg WebSocket 实时协议逐帧规范（握手 / 帧格式 /
    `op=auth` 带内换票 / topic 命名 / 三态授权 / 错误码 / 资源上限 / 断线并发 / 最小前端）。该文件对"哪些改动
    已发布"的旁注以本 README「发布状态」为准：`op=auth`、`ready` 自描述、IM 字符串 id、`createdTime` ISO
    均已随 1.2.1/1.2.2 上中央仓库。

其它文档按模块就近放置（不在 `_doc/` 下）：

- [`z-msg-channels/README.md`](z-msg-channels/README.md) — 渠道 provider / 签名 / 超时 / 未支持与待核对项
- [`z-msg-example/README.md`](z-msg-example/README.md) — 五分钟跑起来、宿主四个文件

发布脚本**平铺在仓库根**（未收口到 [`_doc/003_script/`](_doc/003_script/)，如实写明）：

- [`_doc/003_script/deploy_maven_center.sh`](_doc/003_script/deploy_maven_center.sh) — 一键发布 Maven Central（子命令 `publish` / `verify` / `gpg-init` / `readme`）
- [`_doc/003_script/install-settings.sh`](_doc/003_script/install-settings.sh) — 把 Central 凭证（`CENTRAL_USERNAME` / `CENTRAL_TOKEN`）写入 `~/.m2/settings.xml`
