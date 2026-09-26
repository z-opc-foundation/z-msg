# z-msg

> **多通道消息 + 实时下发基础设施** — 短信 / 邮件 / 站内信 / Webhook / 企微·钉钉·飞书·Slack / 微信公众号，
> 加一层 WebSocket 实时接入，再往上就是聊天室和 IM。
> Java 8 + Spring Boot 2.7.12，切供应商只改一行 yml，业务代码零修改。

[![Maven Central](https://img.shields.io/badge/Maven%20Central-api%2Fcore%2Fweb%2Fws%2Fim%2Fchannels%201.2.0-blue?logo=apache-maven)](https://central.sonatype.com/search?q=g:io.github.yuku123+a:z-msg)
[![Java](https://img.shields.io/badge/Java-8%2B-orange)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.12-6DB33F)](https://spring.io)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

## 发布状态（照实说）

`repo1.maven.org` 实测（对 `.pom` 与 `.jar` 发 HEAD，八个坐标逐个量，2026-09-26 20:09；
parent 是 pom-only，它的 `.jar` 本来就该 404）：

| 构件 | 1.0.0 | 1.1.0 | 1.2.0（本仓库 `${revision}`） |
|---|---|---|---|
| parent `z-msg` | 200 | 200 | **200 已发布** |
| `z-msg-api` / `z-msg-core` / `z-msg-web` | 200 | 200 | **200 已发布** |
| `z-msg-channels` / `z-msg-ws` / `z-msg-im` | 404（1.1.0 才有的模块） | 200 | **200 已发布** |
| `z-msg-example` | 404 | 200 —— 这一件是**误发**的，见 §14 | **404：这一版起真的不发了**（实测） |

所以引 1.2.0 就能拿到全部东西，包括 §9 的票一次性消费与 §6 的 `/history` 同步判定量：

```xml
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-msg-im</artifactId>
    <version>1.2.0</version>
</dependency>
```

1.2.0 相对 1.1.0 的全部内容就是这两处，而且都在发布字节里验到过（拿 1.1.0 的发布 jar 做阴性对照，
`javap` 于 `~/.m2` 与 repo1 各自下载的构件）：`MsgHandshakeInterceptor` 从调 `RealtimeTicketService#verify`
变成调 `#consume`；`ImMessageController#history` 的返回从 `Result<List<ImMessageDO>>` 变成
`Result<ImHistoryPage>`。

> ⚠️ **`repo1` 上最新仍是 1.2.0，而主干已经走到它前面了。** 有四处改动只在 `origin/main` 上、
> 不在任何发布构件里：`op=auth` 带内换票（§4）、`ready` 的 `ops`/`limits` 自描述（§4）、
> 腾讯云短信与极光推送两个 sender（§7，且只经过离线固定向量与 stub，没向厂商真投过一条）、
> **IM 的 id 线格式从数字改成字符串**（§6，这一条是**破坏性**变更，别指望把它塞回 1.2.0）。
> **"主干代码，尚未发布"这一档又回来了**（上一段那个结论只对 1.2.0 那两件事成立）；
> 本文点到这四处的段落都各自带着这句限定，别看代码时以为线上就有。

> ⚠️ **`z-boot-msg-starter` 只能带你走一半。** 2026-09-27 00:00 实测 repo1：starter 最新是 `1.0.16`
> （`1.0.17` 404），它的 pom 里的 z-msg 依赖**只有一条** — `z-msg-web:1.2.0`。
> 这一版 pin 对了：1.2.0 的收件箱读侧（按服务端解出的身份过滤）与 `/history` 的同步判定量都拿得到。
> 但**`ws` / `im` / `channels` 三个坐标一个都不在聚合里**，所以
> 上面表里"聊天室 / 微信式 IM / 对接外部渠道"那三行，必须直接引 `z-msg-ws`、`z-msg-im`、`z-msg-channels`
> 才拿得到——starter 的聚合范围决定它兜不住这三件事。（下面"这层能替你干什么"那张表里，只有前两行
> `gateway.send` / 系统站内信走 starter 就够；WebSocket、五分钟聊天室、微信式 IM、外部渠道那四行
> 都得按表里写的模块直接引。）

---

## 这层能替你干什么

| 你想要的 | 引哪个模块 | 你要写的代码 |
|---|---|---|
| 一句 `gateway.send(...)` 把一条业务事件投到 N 个渠道 | `z-msg-core` | 0 行（配置驱动） |
| 系统站内信：落库 + 未读红点 + 列表/详情/已读/删除 | `z-msg-core` + `z-msg-web` | 1 行 `send(IN_APP)` |
| WebSocket 接入（握手鉴权、订阅、多端、心跳、背压上限） | `z-msg-ws` | 1 个 `MsgPrincipalResolver` bean |
| 五分钟搭出聊天室 | `z-msg-ws` + 1 个授权策略 | 一个 `TopicAuthorizationPolicy` |
| 微信式 IM（会话/成员/seq/已读回执/未读汇总/离线增量） | `z-msg-im` | 0 行（引 jar 即自动装配） |
| 对接企微 / 钉钉 / 飞书 / Slack / 公众号 / 阿里云与腾讯云短信 / 极光推送 | `z-msg-channels` | 0 行（一段 yml） |

---

## 模块结构

8 个 Maven 模块（`${revision}` + flatten，parent 自给自足）。**两把尺要分开读**：
当前工作树 `mvn -B -o clean verify` = **总计 224 例、0 失败 0 跳过**
（core 26 / channels 82 / web 13 / ws 39 / im 56 / example 8）；
而 **repo1 上已发布的 1.2.0 那棵树是 177 例**（channels 57 / ws 19，发布那一场 `mvn -B clean deploy -Pcentral`
**没带** `-DskipTests`，上传出去的字节就是那 177 例跑绿的那批 jar，三方 sha256 对账见 §14）。
差的 47 例全在主干上、还没发布：channels 25（腾讯云 SMS 10 + 极光推送 10 + TC3/Basic 固定向量与
凭据不上日志 5）、ws 13（带内换票 `op=auth` 那一轮，ws 19→32）、ws 7（`ready` 自描述那一轮，32→39）、
im 1（id 线格式那一轮，REST 5→6）、example 1（私聊面板那条端到端，7→8）。
**所以对外仍是 177 的口径。** 1.1.0 发布件是 168 例，
多出来的 6 例是票的一次性消费那一轮（5 支 core + 1 支真握手 ws E2E），3 例是增量同步判定量那一轮（全在 im 服务层）。

| 模块 | 职责 | 测试 |
|---|---|---|
| `z-msg-api` | 纯 SPI 与常量：`MessageGateway` / `Message` / `Channels` / `RealtimePublisher` / `TopicAuthorizationPolicy`，零实现零 Spring | — |
| `z-msg-core` | 默认 provider（mock / SMTP）、`ChannelRouter`、限流、重试、模板、投递日志、批量、站内信读写侧、7 张表的 DDL | 26 |
| `z-msg-channels` | 真实外部渠道 provider：钉钉/企微/飞书/Slack 群机器人、公众号模板消息、阿里云与腾讯云短信、极光推送、录制 mock | 82 |
| `z-msg-web` | REST Controller + `MsgAutoConfiguration`（`spring.factories`）+ 身份接缝 + 管理面闸门 `AdminEndpointGate` | 13 |
| `z-msg-ws` | WebSocket 实时接入层：短期票握手、帧协议（含 `ready` 自描述与带内换票）、topic 订阅、三态授权、在线注册表 | 39 |
| `z-msg-im` | 会话/成员/消息/已读回执域，seq 分配与增量同步，自带 4 张表与 REST | 55 |
| `z-msg-example` | 能跑的大厅聊天室 + 站内信宿主（**1.2.0 起真的不进发布清单**：repo1 实测这一件 404、其余七件 200，见 §14），顺带承载端点普查 | 7 |

深入文档各就各位，本 README 只讲清边界与入口：

- 实时协议逐帧说明 → [`_doc/001_WS_PROTOCOL.md`](_doc/001_WS_PROTOCOL.md)
- 渠道/签名/超时/待核对项 → [`z-msg-channels/README.md`](z-msg-channels/README.md)
- 五分钟跑起来、宿主四个文件 → [`z-msg-example/README.md`](z-msg-example/README.md)

---

## 1. 依赖与最小配置

```xml
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-msg-web</artifactId>   <!-- 站内信 + HTTP 面 -->
    <version>1.2.0</version>
</dependency>
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-msg-ws</artifactId>    <!-- WebSocket 实时 -->
    <version>1.2.0</version>
</dependency>
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-msg-im</artifactId>    <!-- 聊天室 / IM 域 -->
    <version>1.2.0</version>
</dependency>
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-msg-channels</artifactId> <!-- 真实厂商 -->
    <version>1.2.0</version>
</dependency>
```

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

# 数据源走 z-boot 的 ModuleDataSourceTemplate：z-msg 只用自己的库，键名如下
z:
  base:
    db:
      msg: { host: 127.0.0.1, port: 3306, database: msg, username: ${MSG_DB_USER}, password: ${MSG_DB_PASS} }
```

`dataSourceMsg` 带 `@ConditionalOnMissingBean(name="dataSourceMsg")`，宿主可以自己顶一个
（演示宿主就是这么塞 H2 的），但顶完必须保证 `sqlSessionFactoryMsg` 仍指向它——见 §2。

## 2. 装配契约（改坏就全盘坏，写在这是为了别踩）

| 契约 | 内容 |
|---|---|
| `dataSourceMsg` / `sqlSessionFactoryMsg` | **bean 名是契约**。core 的 mapper 与 im 的 mapper 都 `sqlSessionFactoryRef="sqlSessionFactoryMsg"`，两套 mapper 必须落在同一个 SqlSessionFactory 上——`ImAutoConfigurationTest` 直接断言这一点。 |
| 两套 `@MapperScan` | web 的那份只覆盖 `com.zifang.z.msg.core.domain.mapper`；`z-msg-im` 靠自己的 `spring.factories` + 自己的 `@MapperScan` 自注册。**删掉那行 spring.factories 注册，im 全部 bean 消失**，`ImDisabledAutoConfigurationTest` 的对照组钉着这条。 |
| `msgMybatisPlusInterceptor` | 分页插件 bean 名同样是契约，宿主已有 MP 配置时要顶掉而不是并存。 |
| 条件装配顺序 | `MsgImAutoConfiguration` 用 `@ConditionalOnClass(name=...)` + 属性开关，**不用** `@ConditionalOnBean`：实测 `@Bean`-less 的自动配置类上 `@ConditionalOnBean(name="sqlSessionFactoryMsg")` 永远不匹配（`ConfigurationClassPostProcessor` 解析它时还没有任何自动配置 `@Bean` 定义）。已知不完美：它守的是"类在不在"，不是"bean 在不在"，类注释里写着。 |
| 表结构 | 随 jar 走：`classpath:z-msg/sql/schema-h2.sql`、`classpath:z-msg/sql/im-schema-h2.sql`（MySQL 版同目录）。 |
| 扫描范围收窄 | web 的 `@ComponentScan` 只覆盖 `core` + `web` 两个包，**不是** `com.zifang.z.msg` 全包：宿主把演示/实验模块放进 classpath 时，它们的 bean 不会被扫进生产。 |

## 3. 系统站内信

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
（`z-msg.inbox.push-realtime=false` 可关）。
实时只是增强，不是前提：没有 transport 时 `publish` 返回 0 且不抛异常。

读侧 8 个端点见 §8 端点全表。三件事是 1.1.0 刻意做的，别绕过去：

- **`userId` 不是入参**。所有读侧方法只吃 `MsgPrincipalResolver` 解出来的当前登录人，WHERE 里恒定
  `user_id = 当前人`，越权不是"忘了判"而是"写不出"。
- **列表不带正文**。`content` 是 CLOB，列表只取展示列（`MsgInboxService#pageMine` 的显式投影），
  点进去再走 `/inbox/detail?id=`。
- **过期与软删不计红点**。`unread-count` 与 `list` 共用同一套 alive 过滤；删除一律 `deleted=1`，
  `/delete-all` 不再是物理删。

## 4. WebSocket 接入

浏览器的 `WebSocket` 构造方法带不上自定义头（cookie 会带，但 JWT 走的是 `Authorization` 头），
所以握手身份走**短期票**：

```
GET /api/msg/inbox/ws-token        ← 已认证的 HTTP（session / JWT 由宿主 resolver 解）
   → { success: true, data: { token: "<HMAC>", expiresInSeconds: 60 } }
WS  /api/msg/ws?token=<token>      ← 握手只认这张票，签名/受众/过期任一不过 → 401
```

一张票只换一条连接，所以**重连要重新换票**（缓存 token 复用会撞 401；这条从 1.2.0 起是发布件里的
行为，1.1.0 及更早还是 TTL 内可重放，见 §9）。

连上后服务端先推一帧 `ready`（含自动订阅好的 `user:<自己>` 与租户 topic），之后就是
`subscribe` / `unsubscribe` / `publish` / `ping` / `auth` 五张客户端帧和 `pong` / `ready` / `message` /
`ack` / `error` 五张服务端帧。逐字段、错误码、资源上限、断线与并发语义都在
[`_doc/001_WS_PROTOCOL.md`](_doc/001_WS_PROTOCOL.md)；最小前端片段在那份文档的 §8（最小前端）。

`ready` 除了 `connectionId` / `userId` / `topics`，还会报出 `ops`（服务端认哪几张帧）与
`limits`（当前真正生效的资源上限）——**主干新增，1.2.0 及以前的发布件里 `ready` 只有前三个键**。
它要顶掉的是前端抄默认值这件事：`idle-timeout-seconds` 从 120 改成 31 之后，症状是"连上没多久
就断线，而浏览器侧看不出原因"。两条口径要记住：
`auth` 只在 `inband-auth-enabled=true` 时出现在 `ops` 里（报的是"有人处理"，不是"你有权限发"，
`publish` 一直在清单里而放行仍由策略判）；配成 0/负数的那三项上限**整个键缺席**而不是报 0，
因为 `"maxTopicsPerConnection": 0` 会被读成"一条都不许订"，而它的真意是"没有这道闸"。

`op=auth` 是**主干新增、默认关闭**的一张帧（`z-msg.ws.inband-auth-enabled=true` 才认；
1.2.0 及以前的发布件里没有这个 op，回了就是 `WS_OP_UNSUPPORTED`）。它解决的是"票 60s 过期，
而连接一旦建立就再也不看票"：切账号、多账号共用一条 socket 这类宿主，以前只能断线重连。

```js
ws.send(JSON.stringify({op: 'auth', clientMsgId: 'a1', token: await freshTicket()}));
// → ack: {changed:true, userId:7102, previousUserId:7101, revoked:["user:7101"], topics:[...]}
```

用的仍是同一本一次性账（`RealtimeTicketService#consume`）：一张票无论走握手还是走帧，只兑现一次；
失败只回 `WS_AUTH_FAILED`，**不断连接、不动身份、不动订阅**。换身份时服务端按**新身份**复核每一条
已有订阅，不配持有的直接退订并列进 `revoked`——反过来要说清楚做不到的那半：服务端没有
"某用户该有哪些频道"的可枚举清单，所以**换回原身份只有自己的收件箱会自动补回来**，
其余频道客户端要按 `revoked` 重新 `subscribe`。

多端同时在线由 `WsSessionRegistry` 按用户记账，超了就挤掉最老的那条
（`max-sessions-per-user=8`，`moreSocketsThanMaxSessionsPerUserEvictsTheOldest` 真开 3 条 socket 验它）；
每连接 topic 上限 64 由 handler 拒绝越界的 `subscribe`（用例把上限压到 4 验截断）。
空闲 120s 与单帧 256 KiB 是设进容器的 `setMaxSessionIdleTimeout` / `setMaxTextMessageBufferSize`，
属容器行为，没有专门用例钉。

## 5. 五分钟聊天室

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

判据取 `WsProperties.getPublicTopics()` 而不是在策略里硬写 `"room:lobby"`：宿主改了 yml 而策略还认
老 topic 的话，症状是"连上了但没人说话"，最难查。不认识的 topic 一律返回 `null` 而不是 `FALSE`——
返回 `FALSE` 会把 `z-msg-im` 那种按成员表判其它房间的策略一起否掉。

三态（`TRUE` 放行 / `FALSE` 拒绝 / `null` 不表态）而不是布尔，是因为一条连接上会同时挂着
自己的收件箱、群聊、租户公告，没有哪个模块能单独判完；任一策略 `FALSE` 即拒，全部不表态才落到
传输层默认（只允许订 `user:<自己>` 和配置里声明的公共 topic）。

跑起来的完整版本在 `z-msg-example`：两条真 WebSocket、两个登录身份、A 打字 B 立刻看到，
由 `MsgExampleApplicationTest` 在无 mock 的情况下钉住。演示页也是"前端不必猜"这句话的
消费方：页头显示 `ready` 报回来的 `ops`/`limits`，心跳间隔按 `idleTimeoutSeconds` 推
（推导规则与三档实测见 `_doc/001_WS_PROTOCOL.md` §2）。这一段只在主干上——对着已发布的
1.2.0 起宿主时 `ready` 里没有这些键，页面会显示"服务端未自描述"并落回 20s 兜底心跳。

## 6. 微信式 IM

`z-msg-im` 就是"聊天"这件事的域模型，引 jar 即得 18 个 REST 端点 + 实时行为：

| 能力 | 靠什么做到 |
|---|---|
| 单聊幂等、群聊、成员与角色 | `single(me, peer, tenant)` 靠 `uk_im_conv_pair` 保证两人只会有一个会话；成员/角色/禁言走成员表 |
| 消息有序、可增量同步 | 会话内 `seq` 由服务端 CAS 分配（`uk_im_msg_conv_seq`），客户端只按 `sinceSeq` 拉增量；`/history` 连判定量一起给（`hasMore`/`nextSinceSeq`/`headSeq`/`minVisibleSeq`，见下） |
| 重发不产生两条 | `clientMsgId` 唯一键 `uk_im_msg_conv_client`，命中时返回既有那行（`seq` 不变） |
| 已读 / 未读 / 回执 | `last_read_seq` + `uk_im_receipt_conv_user`，未读汇总按会话返回 |
| 服务端署名 | 发言落库时 `sender_user_id` 与 `seq` 都由服务端写——这是它和"大厅自报 from"的区别 |
| 别人订不到你的房间 | `ImTopicAuthorizationPolicy` 按成员表判 `room:`，非成员 `FALSE`，且**不影响**大厅放行 |
| 前端拿到的 id 是准的 | 会话/消息/用户引用的 id 线上一律**字符串**（REST 与实时帧同一条规则）——19 位雪花出成 JSON 数字，浏览器 `JSON.parse` 会舍掉末位 |

**这条形状是主干改动，1.2.0 及以前出的是数字**，属破坏性线格式变更，不能塞回已发布的构件里。
它的理由不是"好看"：id 一旦被舍入，前端拿舍过的值去拼 `room:<id>` 或回填 `message/send`，
收到的是 `403 无权访问会话 <一个服务端从未有过的 id>`，而这条会话正是它一秒前自己建的——
症状与权限配错完全一样。钉住的是 `ImSpringTestSupport#idOf`（判形状）与
`ImRestApiTest#conversationIdSurvivesAJavaScriptStyleRoundTrip`（判"拿到什么就回填什么"这条
前端自然路径真走得通），细节在 `_doc/001_WS_PROTOCOL.md` §2。`seq` 一类游标仍是数字。

标注的位置有 6 个类，不是 1 个：4 张表的实体（`ImConversationDO` / `ImMemberDO` / `ImMessageDO` /
`ImReadReceiptDO`）各 3 个字段、会话视图 `ImConversationView` 3 个、未读汇总 `ImUnread` 1 个，
再加上 `ImMessageService` 里两处手工拼的帧载荷（`payloadOf`、`publishReadFrame`）走 `asText()`。
`ImUnread` 那一处是"给实体打标"这个动作结构上覆盖不到的：它是 `ImReadService` 现装的另一份 DTO，
只由 `unread/summary` 的线上形状钉着（变异验证见 §13）。

`ImMessageService#send` 的完整签名：

```java
ImMessageDO send(Long conversationId, Long senderUserId, String msgType, String content,
                 String clientMsgId, List<Long> atUserIds, Long replyToSeq);
```

`POST /api/msg/im/message/history` 回的是一个**对象**，不是消息数组
（1.1.0 及更早的发布件回的是裸数组 —— 对象形状从 **1.2.0** 起对外，迁移动作见 §14）：

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
// 追平之后若 cursor < p.headSeq，而中间那几个号既 >= p.minVisibleSeq 又不在 rows 里，
// 那是库里就没有那个号（占号成功而插入失败的代价），不是客户端漏了。
```

后三个字段的来历，都是客户端自己算不出来的那类：

- `hasMore` 靠**多读一条**探出来：服务端按夹过的大写读 `cap + 1` 条、只回 `cap` 条，所以"这一页正好回满"
  和"后面还有"是两件分得开的事；请求里的 `size` 会被 `z-msg.im.max-page-size` 夹掉，
  拿回到的条数去和客户端自己给的 `size` 比，在夹了的那一档上永远是对的不上。
- `nextSinceSeq` 是本次下限之后真正交出去的最后一条；空页时它就是本次生效的下限，
  客户端照它传下次不会反复撞同一道墙。
- `minVisibleSeq = max(请求的 sinceSeq, 本人的 cleared_seq) + 1`：被自己的"清空聊天记录"挡掉的那一段，
  和"库里本来就没有"的那一段，靠它区分。它是**从本次请求的下界推出来的，不是会话的绝对下界**：
  拿着 `sinceSeq=1` 去问一个只有 `seq=1` 的会话，回的就是 `minVisibleSeq=2`，
  这句话说的是"本次之后从 2 起看"，不是"1 被服务端藏起来了"。`headSeq` 是读取时刻的会话水位
  （`last_msg_seq`），在拉取之后才读，所以它只会等于或高于本次给出去的最后一条。

## 7. 外部渠道对接

`z-msg-channels` 把 `ChannelSender` SPI 落到真实厂商，全部 JDK `HttpURLConnection`，
不引厂商 SDK、不引 OkHttp，每家都有本地 stub server 的离线测试。

| channel | provider | 实现类 | 状态 |
|---|---|---|---|
| `SMS` | `aliyun` | `AliyunSmsSender` | ✅ RPC 签名向量离线钉死 |
| `SMS` | `tencent` | `TencentSmsSender` | ✅ TC3-HMAC-SHA256，签名覆盖"线上真发的那几个字节" |
| `EMAIL` | `smtp` | `SmtpEmailSender`（core） | ✅ |
| `IN_APP` | `db` | `InAppChannel`（core） | ✅ |
| `WEBHOOK` | `http` | `WebhookSender` | ✅ |
| `IM_WECOM` / `IM_DINGTALK` / `IM_FEISHU` | `robot` | 群机器人三家 | ✅ |
| `IM_SLACK` | `bot` | `SlackBotSender` | ✅ |
| `IM_WEIXIN_MP` | `mp` | `WeixinMpSender` | ✅ token 缓存 + 过期重取 |
| `PUSH_JPUSH` | `jpush` | `JPushSender` | ✅ v3 push，Basic 认证 |
| `IM_WEIXIN_MINI` / `PUSH_FCM` / `PUSH_APNS` / `PUSH_WEB` / `REALTIME` | — | **常量有、厂商 sender 无** | ⚠️ 落到 `MockFallbackSender` |

最后那行是这个表存在的原因：`GET /api/msg/channel/list` 每条通道返回
`real` / `ready` / `enabled` / `providers` / `activeProvider` / `configuredProvider` /
`fallbackChannels`，`GET /api/msg/channel/detail?channel=` 再补 `selected`（选中的实现类）与
`selectedIsMock`。**假成功在自省接口里是看得见的**（`real:false`、`selectedIsMock:true`），
`channelIntrospectionLabelsMockProvidersHonestly` 钉着这条。
SendGrid、FCM/APNs 的形状照 `AliyunSmsSender` / `TencentSmsSender` + 测试模板补即可；
各家的未支持项与待核对点（含飞书签名形状、极光透传消息）逐条列在
[`z-msg-channels/README.md`](z-msg-channels/README.md)。

一个业务事件同时投多通道，走 `fanOut`（偏好过滤 + 静默时段 + 限流 + 模板渲染 + 重试 + 投递日志）：

```java
Map<String, String> receivers = new HashMap<String, String>();
receivers.put(Channels.SMS, "13800000000");
receivers.put(Channels.EMAIL, "a@b.com");
receivers.put(Channels.IM_DINGTALK, "robot-key");

FanOutResult r = gateway.fanOut("ORDER_SHIPPED", Long.valueOf(userId), receivers,
        params, "zh_CN");
```

## 8. REST 端点全表

响应统一 `com.zifang.util.core.meta.Result`：`{ success, code, message, data }`
（字段名是 `message`，不是 `msg`）。

**站内信** — 全部要求已认证，未认证 401：

| 方法 | 路径 |
|---|---|
| GET | `/api/msg/inbox/list?page=&size=&unreadOnly=&msgType=` |
| GET | `/api/msg/inbox/unread-count` |
| GET | `/api/msg/inbox/detail?id=` |
| POST | `/api/msg/inbox/read?id=` |
| POST | `/api/msg/inbox/read-all` |
| DELETE | `/api/msg/inbox/delete?id=` |
| DELETE | `/api/msg/inbox/delete-all` |
| GET | `/api/msg/inbox/ws-token` |

**IM**（`/api/msg/im/...`，全 POST，全要求已认证）：

| 前缀 | 端点 |
|---|---|
| `conversation` | `/list` `/single` `/group` `/members` `/member/add` `/member/remove` `/member/role` `/leave` `/mute` `/clear` |
| `message` | `/send` `/history` `/at` `/head` |
| `read` | `/mark` `/unread` `/summary` `/receipts` |

请求体键名：`conversationId, content, msgType, clientMsgId, atUserIds, replyToSeq, sinceSeq, seq,
size, page, peerUserId, tenantCode, convType, memberUserIds, title, avatar, userIds, role, muted, lastReadSeq`。

`data` 的形状按端点分三类，别一律当数组接：`conversation/list` 是 `IPage`（行在 `records`，`total` 是真的）、
`message/history` 是对象（行在 `rows`，另带四个判定量，见 §6）、
只有 `conversation/members` 与 `read/receipts` 回裸数组。其余：`send`/`at` 回消息行、
`message/head`·`conversation/clear`·`read/unread` 回数字、`mute`/`leave` 回布尔。

**偏好 / 通道 / 事件 / 管理面**：

| 方法 | 路径 | 认证 |
|---|---|---|
| POST | `/api/msg/preference/upsert` | ✅ 只作用在当前人 |
| GET | `/api/msg/preference/my` | ✅ |
| DELETE | `/api/msg/preference/{id}` | ✅ |
| GET | `/api/msg/channel/list` | ❌ 无需身份；实测不回传任何密钥 |
| GET | `/api/msg/channel/detail?channel=` | ❌ |
| POST | `/api/msg/publish?eventType=&userId=` | ❌ **默认关**（`z-msg.web.publish-endpoint-enabled=false`）。关着时实测 HTTP 200 + 业务 `code:403`，不是 HTTP 403 |
| POST | `/api/msg/template/list` · `POST /api/msg/template`（建）· `/api/msg/template/{id}`（GET/PUT/DELETE）· `/{id}/approve` · `/{id}/reject` · `/cache/refresh` | ❌ **默认关**：8 个映射一律真 HTTP 403，见 §9 |
| POST | `/api/msg/batch/submit` · `/api/msg/batch/list` · `/api/msg/batch/{id}/cancel`，GET `/api/msg/batch/{id}` | ❌ 同上 |
| POST | `/api/msg/delivery/list` | ❌ 同上 |
| GET **和** POST | `/api/msg/delivery/stats` | ❌ 同上。两个动词各由一个类声明（`MessageController` 的 GET、`MsgDeliveryLogController` 的 POST），z-opc 前端用的是 GET —— 闸门按**路径**判，所以这一条不会因为"不长在它自己的 controller 里"而漏出去 |

`/api/msg/send` 不存在（旧 README 里那句是假的）。

上面那 12 条管理面路径不是手抄的：`MsgAdminSurfaceCensusTest` 在示例宿主（web + im 全量装配）里
把活的 handler mapping 逐条过一遍闸门判定，与两份钉死的清单对账 —— 少拦一条、或多放一条，测试就红。

## 9. 安全边界（照实说）

**默认关的东西**，每一条都是因为开着时"不带身份就能用"：

| 开关 | 默认 | 打开的代价 |
|---|---|---|
| `z-msg.web.trusted-header-enabled` | `false` | 打开后 `X-Msg-User-Id` 就是身份——**必须**确认上游网关会覆盖/剥离客户端自带的同名头 |
| `z-msg.web.publish-endpoint-enabled` | `false` | 服务间事件投递端点，开了要自己保证调用方可信。关着时是 HTTP 200 + 业务 `code:403` |
| `z-msg.web.admin-endpoints-enabled` | `false` | 模板/批量/投递日志三组管理面。关着时是**真 HTTP 403** + `Result` 正文（这些请求根本没进 controller，给网关和监控看得见的状态码才有用） |
| `z-msg.realtime.ticket-secret` | `""` | 空则 fail closed，不签弱密钥票：`/ws-token` 回业务 `code:503`（HTTP 仍是 200），WS 握手由拦截器给真 HTTP 503 |
| `z-msg.ws.public-topics` | `[]` | 只影响"订得到哪些非本人 topic"，全部不表态时按拒绝处理 |

`z-msg.ws.allowed-origins` 是**例外**：默认 `[]` 的含义是"不限 Origin"，不是"全部拒绝"——
代码只在列表非空时调用 `setAllowedOrigins`。这不是疏漏，`WsProperties` 里写着理由：握手凭据是一次性
短期票（这条保证从 1.2.0 起在发布件里，1.1.0 还没有，见下面"票的一次性消费"），必须由已认证的 HTTP 会话去 `/inbox/ws-token` 换，跨站页面读不到那个响应；能拿到票的人本来
就在登录态里。**如果宿主改成长期 token 走 query，请务必显式配上自己的域名。**

**管理面为什么是开关而不是鉴权**：1.0.x 一直到 1.1.0 之前，`/api/msg/template/**`、`/api/msg/batch/**`、
`/api/msg/delivery/**` 一次 `principalResolver` 都没调用过，`z-msg-web` 里也没有任何
`HandlerInterceptor` / `OncePerRequestFilter`。库里没有"管理员"这个概念（角色归宿主），
所以 1.1.0 收口的形状是：默认全关，`AdminEndpointGate` 一个拦截器按路径挡，宿主在自己的
认证边界内打开一行 yml 就能用。**代价说清楚**：z-opc 的模板管理页 / 批量任务页 / 投递日志页
升级到 1.1.0 后会直接 403，需要在宿主要么补一行 `z-msg.web.admin-endpoints-enabled=true`（+ 自己前面的登录墙），
要么等角色判定这一层落地。

**仍然没做的**（开关不等于鉴权，这条别再当已闭）：

- 管理面**没有角色判定**：开了开关就是全开，"谁能审批模板"仍然只由宿主前置的网关/登录决定；
- `MsgDeliveryLogController#list` 在开关打开后仍按**请求体里客户端自报的 `userId`** 过滤——
  那是 PII，形状与 1.0.x 那个越权读收件箱的洞同一个。开关只是让它默认不存在。

**票的一次性消费**（1.2.0 起是发布件行为；1.1.0 及更早里票在 TTL 内仍可重放）：握手走 `RealtimeTicketService#consume`，一张票只换得到第一条连接，
第二次握手 401 —— 因为票进过 URL 就会被 access log、代理日志、浏览器历史原样留下来。
主干上 `op=auth` 花的是**同一本账**（也走 `consume`），所以"在帧里用掉一张票"不会比握手松一寸；
反过来说，票放在帧里比放在 URL 里干净——它进不了 access log。日志侧另有守卫：
`ticketNeverReachesTheLogsButTheRebindLineDoes` 先证捕到了那条换票日志，再断言票的签名段零命中。
`verify` 保持纯验签、可重复调用，它**不是**安全闸门：验得通只说明"这张票是真的"，不说明"还没人用过"。
边界照实说：记账在**进程内存**里，所以 ① 重启后旧票在 TTL 内还能再用一次 ② 多实例各记各的，
一张票在 N 台节点上各能开一条 ③ 窗口内成功握手超过 2 万条时开始丢弃最早过期的记录（保证降级、但会 warn）。
要跨实例严格一次，得把 jti 放进共享存储（Redis 之类），z-msg 不替你引这个依赖。

**其余已知取舍**：IM 已读游标与消息表是两处写，崩溃窗口内可能短暂偏差。

## 10. 配置全表

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
| `channel.<CH>.provider` | 无 | 选厂商实现，见 §7 |
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
| `realtime.ticket-ttl-seconds` | `60` | 也是消费记录的存活时长：一张票只开一条连接，账在本进程内存里记到过期为止 |
| `realtime.ticket-audience` | `z-msg-ws` | |

`z-msg.web.*`：`trusted-header-enabled=false`、`trusted-header-name=X-Msg-User-Id`、
`publish-endpoint-enabled=false`、`admin-endpoints-enabled=false`（后者挡 §9 那三组管理面，
判定只在 `AdminEndpointGate.isAdminPath` 一处：按路径段对齐，`/api/msg/templates` 不算管理面）。

`z-msg.ws.*`：`enabled=true`、`path=/api/msg/ws`、`allowed-origins=[]`、`public-topics=[]`、
`idle-timeout-seconds=120`、`max-text-message-bytes=262144`、`max-sessions-per-user=8`、
`max-topics-per-connection=64`、`inband-auth-enabled=false`（最后一个是主干新增，
开了才认 `op=auth`，见 §4）。

`z-msg.im.*`：`enabled=true`、`default-page-size=50`、`max-page-size=200`、
`max-members-per-conversation=500`、`max-content-length=4000`、`seq-cas-max-attempts=200`、
`seq-cas-backoff-ms=1`、`publish-realtime=true`、`publish-read-receipt=true`、
`user-side-push=true`、`user-side-push-max-members=200`。

`z-msg.channel.<CH>.*` 的 20 个厂商键（`token` / `secret` / `app-id` / `app-secret` / `access-key-id`
/ `access-key-secret` / `sdk-app-id` / `app-key` / `master-secret` / `sign-name` / `template-code`
/ `region` / `signature-version` / `slack-channel`
/ `url` / `base-url` / `connect-timeout-ms` / `read-timeout-ms` / `retries` / `mock`，另加 `provider`
这个选择位）逐个"在哪被读"见 [`z-msg-channels/README.md`](z-msg-channels/README.md)。

## 11. 数据库

MySQL 与 H2 各一份，同源由 `SchemaParityTest` 真跑执行验证：

| 脚本 | 内容 |
|---|---|
| `z-msg/sql/schema-mysql.sql` / `schema-h2.sql`（在 `z-msg-core` 的 jar 里） | 7 张表：`z_msg_message`、`z_msg_template`、`z_msg_template_i18n`、`z_msg_template_version`、`z_msg_delivery_log`、`z_msg_batch_task`、`z_msg_user_preference` |
| `z-msg/sql/im-schema-mysql.sql` / `im-schema-h2.sql`（在 `z-msg-im` 的 jar 里） | 4 张表：会话、成员、消息、已读回执；唯一键 `uk_im_conv_pair`、`uk_im_member_conv_user`、`uk_im_msg_conv_seq`、`uk_im_msg_conv_client`、`uk_im_receipt_conv_user` |

上表那些"幂等/有序/不重复"的承诺，落点全在这几个唯一键上。

## 12. 跑起来

```bash
cd z-msg
mvn -B -o clean verify                        # 当前树 8 模块、224 例（1.2.0 发布件那棵树 177 例；1.1.0 是 168 例）
mvn -B -o -DskipTests install                 # 装进 ~/.m2，一次即可
mvn -B -o -pl z-msg-example spring-boot:run   # 演示宿主，端口 18099
```

打开 <http://localhost:18099/> 两个标签页，分别点「1001 张三」「1002 李四」——
聊天与站内信都是真的跨连接到达，页底的帧日志可以逐帧核对。
（同一 workspace 里若 18099 被别的进程占了，用 `--server.port=` 换。）

## 13. 测试都钉住了什么

| 模块 | 关键几例 |
|---|---|
| core | `SchemaParityTest`（MySQL/H2 两份 DDL 真跑执行、同表同列）、`SenderRegistryProviderDefaultTest`（缺 provider 时兜底成 mock 且被如实标记）、`RealtimeTicketServiceTest` 11 例（签发/验签/过期/篡改之外，一次性消费那一格钉了五例：`consume` 只放行第一次、`verify` 仍可重复且**不是**安全闸门、按票记账不按用户、过期票不进账、2 万条上限真把住且超量时是降级不是拒登）、`WebhookSenderTest`、`LegacySpiAdapterTest` |
| web | `MsgInboxApiTest` 8 例：匿名 401 而登录 200、`oneUserCannotSeeOrMutateAnotherUsersInbox`、过期项既不列表也不计红点、分页真截断且 `total` 是真的、同 `idempotencyKey` 不重复入库、自省接口如实标 mock、`ws-token` 绑当前人。`MsgAdminEndpointGateTest` 4 例：缺省时 9 条管理面请求（含挂在 `MessageController` 上的 `GET /delivery/stats`）一律 HTTP 403、403 正文里点名那个开关、非管理面端点照常答（且缺身份仍是 401 而不是被闸门顺手改成 403）、前缀匹配按路径段对齐。`MsgAdminEndpointEnabledTest` 1 例：同一批请求打开开关全 200 且返回真分页结构——这一例是前四例的对照，否则"路由压根没挂上"也能让 403 假绿 |
| ws | 握手 fail-closed 2 例 / `enabled=false` 全撤 1 例 / 端到端 11 例（多的一例：同一张票第二次握手 401，且换一张新票同用户照样连得上） / 授权策略 4 例 / 注册表索引 1 例（8 持票 × 4 加入 × 20000 代对撞，并断言 `GENERATIONS*JOINERS` 次订阅全部成立——防止"对撞没跑满"的假绿） / 带内换票 8 例（主干，`inband-auth-enabled=true`：同身份续期与重放必拒、坏票不改任何状态、缺 token 判坏帧、换身份后旧用户红点断流、非本人频道按新身份复核并退订、票的签名段进不了日志）+ 默认关时 1 例（回 `WS_AUTH_DISABLED` 而其余 op 一切照旧）+ 注册表 `rebindIdentity` 4 例 + `ready` 自描述 7 例（`ops`/`limits` 与那道闸同源 3 例、配成"不限"时三项该整键缺席且确实不拦 3 例、分发分支与清单双向一致的源码普查 1 例） |
| im | 自动装配 6 例（含"im 的 mapper 与 core 的 mapper 落在同一个 `sqlSessionFactoryMsg`"）、禁用路径 2 例（读 `ConditionEvaluationReport` 定位到 `OnPropertyCondition` 且点名 `im.enabled`）、三态授权 7 例、两条真 socket 的实时 6 例、REST 6 例（多的一例是 id 线格式：`conversationIdSurvivesAJavaScriptStyleRoundTrip`
——把响应里那串 19 位字符**原样**回填进 `message/send`、`message/history`、`unread/summary` 再比一次，
前端"照着文档做"的路径因此是被钉住的，而不是靠约定）、服务层 29 例（增量同步判定量那一格三例：`hasMore` 只能来自多读的那一条、照 `nextSinceSeq` 翻到底一条不多一条不少、`minVisibleSeq` 报的是真生效的那个游标而不是客户端要的那个） |
| example | 8 例：A 发言进 B 的帧、未放行 topic 被拒而大厅同连接可发、站内信三处同时命中、未登录 401、首页真伺服、同一个 cookie jar 里两个标签页仍是两个人，外加私聊面板那条端到端
（`singleChatPanelPathWorksOnTheDemoHostWithServerSignedIds`：真 Tomcat + 真 H2 + 两条真 socket 走完
"开会话 → 两侧各自订 `room:<id>` → 发言时自称 `senderUserId=999999` 仍被服务端改成登录身份 →
按 topic 数清扇出次数 → 标已读拿到 read 帧 → 拉一页增量"），外加 `MsgAdminSurfaceCensusTest`：在这个全量装配的宿主里把活的 handler mapping 逐条过闸门，钉住"拦下 12 条 / 放行 32 条"两份清单，并先断言普查真的数到了 40+ 个映射 |

这些不是"跑过一遍绿了"就完事：每条新增的守卫都做过变异验证（摘掉守卫必须变红，然后按 `md5`
还原成字节一致再复跑）。管理面闸门这一轮跑了 9 支：摘掉拦截器注册、从清单里少写一个前缀、
前缀匹配退化成裸 `startsWith`、默认值翻成开、判定恒真（拦下所有端点，连带把收件箱与收件箱用例一起打红）、
判定恒假、以及两支专打普查的（删前缀 → 拦下的那份不等；新加一个 `/api/msg/p8probe` controller →
放行的那份不等）。九支全部落到具名用例变红，还原后 `md5` 逐字节一致。
"票的一次性消费"这一轮另跑两支：拦截器把 `consume` 换回 `verify` ⇒ ws 用例红在
`同一张票第二次握手必须 401 ==> expected: <401> but was: <101>`；`putIfAbsent` 退化成
先查后写的 `get`（记不上账）⇒ core 两条红在 `expected: <null> but was: <88>` / `<9>`。
"增量同步判定量"这一轮跑了六支，六支全部落到具名断言变红（还原同样走 `cp` 副本 + `md5` 对账）：
多读的那一条摘掉（`size + 1` 改回 `size`）⇒ `被夹掉之后仍要说清后面还有 ==> expected: <true> but was: <false>`；
`hasMore` 退化成"回满一页就算还有"⇒ `满页不等于还有：hasMore 只能来自多读的那一条 expected: <false> but was: <true>`；
空页的 `nextSinceSeq` 归零 ⇒ 三条红（REST 层 `空页的游标就是本次生效的下限 <2> but was: <0>`、
翻页循环 `expected: <10> but was: <0>`、`游标按生效下限给 <5> but was: <0>`）；
`minVisibleSeq` 少加一 ⇒ 三条红（`<3> but was: <2>`、`<6> but was: <5>`、`没清空过，可见下界就是 1 <1> but was: <0>`）；
`headSeq` 改成从行集里取（也就是"永远看不出洞"）⇒ 两条红（`水位比拿到的最后一条高 1：空洞就此显形 expected: <5> but was: <4>`、
REST 的 `水位仍是清空时的位置 <2> but was: <0>`）；摘掉探针行的截断 ⇒ 三条红，其中一条是**旧的**
`historyIsAscendingIncrementalAndCappedInSql`，说明这一档本来就有守卫在看着。
上面这几轮的每一支注入都是按 `cp` 副本还原、`md5` 与注入前逐字节一致后再复跑全绿的。

"带内换票 `op=auth`"这一轮 7 支，6 支红在具名断言上（同样 `cp` 副本 + `md5` 逐字节还原）：
注册表换身份时不摘旧 `user:<id>` topic ⇒ `WsSessionRegistryRebindTest.rebindMovesTheUserIndexAndKeepsEveryTopicSubscription`；
连身份一起不搬 ⇒ 6 条红；摘掉 `inband-auth-enabled` 那道开关 ⇒ `MsgWsInbandAuthDisabledTest`；
`consume` 换成 `verify`（票可重放）⇒ `freshTicketRenewsTheSameIdentityAndIsUsableOnlyOnce`；
换身份后不按新身份复核订阅 ⇒ `subscriptionTheNewIdentityCannotHoldIsRevokedAndStopsDelivering`；
把票打进日志 ⇒ `ticketNeverReachesTheLogsButTheRebindLineDoes`。
另有两支是**量具自己的对照**，不许当成缺漏：一支摘掉"两次读之间连接被摘走"的早退守卫，
预期就是全绿（那条窗口在本用例形状下够不着，能杀掉它的只有真造出竞态的用例）；
一支故意让日志漏票，用来证明"日志零命中"不是因为 appender 一条都没收到。

"`ready` 自描述"这一轮 8 支（只打 `MsgWebSocketHandler` 一个文件，量具
`~/.cache/zmsg_ready_mutants.py`，读数只吃 surefire XML 的具名失败），7 支红、1 支按设计该活：
limits 里的 topic 上限写死成默认 64 ⇒ `limitsInReadyAreTheSameNumbersTheServerEnforces`；
摘掉"没设过就不报"那道 `> 0`、空闲超时照报 ⇒ `anIdleTimeoutWeNeverSetIsNotAdvertised` 与
`limitsThatAreSwitchedOffAreAbsentAndActuallyNotEnforced` 两支；`auth` 无条件进清单 ⇒
`authIsAbsentFromTheAdvertisedOpsButStillAnswersByName`；整条 `ops` 不发 ⇒
`everyAdvertisedOpReachesItsOwnBranch` 与上面那支一起红；**把闸改成读字面量 64 而 ready 仍报配置值**
（这一支才是"报的数就是那道闸"的正猎物）⇒ `limitsInReadyAreTheSameNumbersTheServerEnforces`；
把"不限"当成 0 报出去 ⇒ `limitsThatAreSwitchedOffAreAbsentAndActuallyNotEnforced`；
**加一张分发了却没登记进清单的帧** ⇒ 只有 `MsgWsOpCensusTest#dispatchedOpBranchesAndTheAdvertisedListAgree`
红——端到端那几支对它全无感觉（那张帧什么都不做，客户端也不知道有它），这正是普查例要存在的理由。
该活的那支是 `LinkedHashMap`→`TreeMap`（只改 JSON 键顺序）：**键顺序不是契约，没人钉是对的**，
留着它是为了证明前面七支红不是因为量具一视同仁地什么都杀。八支跑完 `MsgWebSocketHandler.java`
与注入前逐字节一致（`md5 970a203b…`），随后 `mvn -B -o clean verify` 全量 **222 例 / 0 失败 / 0 跳过**
（那是那一轮的树；当前工作树 224，口径见 §模块结构）。

"id 线格式"这一轮 3 支，全部红在具名断言上（同样 `cp` 副本 + `md5` 逐字节还原后复跑全绿）：
摘掉 `ImConversationDO` 上的 `@JsonSerialize(using = ToStringSerializer.class)` ⇒ im 模块 3 条红
（`ImRestApiTest#selfReportedIdentityInBodyIsIgnoredNotHonoredAndNotFatal`、
`#conversationAndMessageFlowOverHttp`、`#conversationIdSurvivesAJavaScriptStyleRoundTrip` 里那句
"响应体里 id 必须带引号"，红消息直接印出 `实际线上形状是 java.lang.Long` 和整份响应）；
把 `payloadOf` 里三处 `asText(...)` 换回裸 `Long`（帧载荷退回数字）⇒ 实时 2 条红
（`ImRealtimeTwoSocketTest#memberSocketOnRoomTopicReallyReceivesWhatAnotherMemberSent`、
`#singleChatAlsoLandsOnThePeersOwnUserTopicWithoutSubscribing`，都红在 `idOf` 那句形状断言）；
摘掉 `ImUnread` 上的标 ⇒ 1 条红，正是汇总那一段：
`id 必须出字符串…实际 java.lang.Long 2103893504519475201`。
第三支是这一轮里唯一"实体打了标就以为完事"会漏掉的位置：未读汇总的 `items` 是
`ImReadService#unreadSummary` 用 `ImUnread.of(...)` 现装的另一份 DTO，`conversationId` 从
`ImConversationView` 逐字段抄过来——实体上的注解结构上管不到这个新对象，
所以这条红只能由 `unread/summary` 的线上形状来钉。

---

## 14. 迁移：1.0.0 → 1.1.0 → 1.2.0

**破坏性变更集中在读侧身份。** 1.0.0 的 jar 实测（`javap` 于 `z-msg-web:1.0.0`）：
`list(Long,Integer)`、`unreadCount(Long)`、`detail(Long)`、`markRead(Long)`、`delete(Long)`、
`deleteAll(Long)` —— 归属人完全来自调用方，`detail`/`read`/`delete` 连归属参数都没有。
1.1.0 起这些一律从 `MsgPrincipalResolver` 取当前人，客户端自称的 `userId` 不再被读。

| 1.0.0 | 1.1.0 | 迁移动作 |
|---|---|---|
| `GET /api/msg/list?userId=&unreadOnly=` | `GET /api/msg/inbox/list?page=&size=` | 删掉 `userId`；分页响应变 `IPage`，行在 `data.records` |
| `GET /api/msg/unread/{userId}` | `GET /api/msg/inbox/unread-count` | 路径参数消失 |
| `GET /api/msg/detail/{id}` | `GET /api/msg/inbox/detail?id=` | id 改 query；不是自己的会查不到 |
| `POST /api/msg/read/{id}` | `POST /api/msg/inbox/read?id=` | 同上 |
| `POST /api/msg/read-all/{userId}` | `POST /api/msg/inbox/read-all` | |
| `POST /api/msg/delete/{id}` · `POST /api/msg/delete-all/{userId}` | `DELETE /api/msg/inbox/delete?id=` · `DELETE /api/msg/inbox/delete-all` | 动词从 POST 改成 DELETE；删除由物理删改软删 |
| `MessageGateway.sendSms/sendEmail` | 保留，另加 `send(Message)` / `fanOut(...)` | 无（IM/Push/In-app 之前只能绕开 gateway 拿 router，现在不用了） |
| `POST /api/msg/template/**` · `/api/msg/batch/**` · `/api/msg/delivery/**`、`GET /api/msg/delivery/stats` | 路径与请求体**不变**，但默认一律真 HTTP 403 | 宿主加一行 `z-msg.web.admin-endpoints-enabled=true` 就回到旧行为（z-opc 的模板页 / 批量任务页 / 投递日志页要的就是这一条） |
| — | `GET /api/msg/send` 之类不存在；`/api/msg/publish` 默认关 | 需要服务间投递请显式开开关 |

旧前缀写错的地方也一并纠正：README 1.0.0 里的 `z.msg.*` 从来就不是真实前缀，parent 从 1.0.0 起
就是 `z-msg`。

**1.1.0 已发布**（`repo1` 实测 200，签名的公钥指纹 `42DC738C…F3111602D4A8C3BB`，
`z-msg-web-1.1.0.jar` 的 sha256 与本机那次 168 例全绿构建的产物逐字节相同）。
一行需要更正的实话：上表第一列说 `z-msg-example` **不进发布清单**，1.1.0 那次其实进了 ——
`io.github.yuku123:z-msg-example:1.1.0` 现在在中央仓库上，删不掉（版本号不可复用）。
原因不是配置写漏，是写错了地方：`maven.deploy.skip` 对
central-publishing-maven-plugin 0.7.0 无效，它只认自己的 `excludeArtifacts` /
`skipPublishing`；而后者不能用——本模块是 reactor 最后一个，bundle 的创建与上传都发生在
最后一次 publish 执行里，在模块里写 `skipPublishing=true` 的实测结果是"zip 照样打好、
七个该发的一个都发不出去"。所以排除写在根 pom 的 `<excludeArtifacts>`，`z-msg-example` 从 1.2.0 起真的不进清单。

**1.2.0 已发布**（2026-09-26，deploymentId `2abdcb41-9e67-4ab7-94e5-c7cd5df1eb0a`）。这一次的收口证据是量出来的，
不是照着配置推的：

| 量什么 | 读数 |
|---|---|
| repo1 八个坐标（`.pom`、`.jar`、`.pom.asc` 各发 HEAD） | 六个模块 `.pom`/`.jar`/`.pom.asc` 全 **200**；parent `.pom`/`.pom.asc` 200（`.jar` 404 是 pom-only，不是缺件）；`z-msg-example` 三样全 **404** |
| 上传前的 bundle（死端点预演与真发布各产一份，路径 `target/central-publishing/central-bundle.zip`） | **150 个文件、7 个坐标、`example` 命中 0**、内部版本全 1.2.0 |
| 六个 jar 的 sha256 三方对账 | `repo1` == bundle 内字节 == 本机 `target/` 产物，逐字节相同 |
| 签名 | `gpg --verify` 对 repo1 的 `.pom.asc` / `.jar.asc` 都是 Good signature，密钥 `42DC738C0C7FCA6D3D6476ACF3111602D4A8C3BB`（ed25519） |
| 发布那一场构建的测试 | `mvn -B clean deploy -Pcentral` **不带 `-DskipTests`**：177 例、0 失败 0 跳过（所以对外字节与被绿的字节是同一批） |

一条必须写下来的坑：**`mvn -o deploy -Pcentral` 会静默不发**。central-publishing 的 `publish` 目标
要求联网，离线时八个模块各打一行 `Goal publish requires online mode for execution but Maven is
currently offline, skipping`，然后照样 `BUILD SUCCESS`——预演第一次就是这么"全绿"而 `target/central-staging`
是空目录。判"发出去了"只认 repo1 的 HEAD 读数与 bundle 里的文件数，别认 Maven 那三个字。

z-opc 前端要改的只有一处（路径都在 `z-opc/bootstraps/z-opc-main-starter-frontend/`，实测行号）：

- `src/msg/services/api.js:11` 的 `inbox:` 是 `request.get('/msg/list', {params:{userId, limit}})` ——
  `/api/msg/list` 在 1.1.0 已删（实测 404；连 1.0.0 也从来没有 `limit` 这个参数，它一直是白传的）。
  改成 `GET /api/msg/inbox/list?page=&size=`、行读 `data.records`、**不再传 `userId`**；
  配套的两处文案在 `src/msg/pages/Inbox.jsx:43` 与 `:51`。
  收件箱这条路不再需要 `currentUserId()` 拼出来的那个 id（但 `pages/MsgApp.jsx:10` 还在用它显示
  "当前登录人"，别顺手删函数）。

`TemplateList.jsx` / `BatchTasks.jsx` / `DeliveryLogs.jsx` 用的
`POST /api/msg/{template,batch,delivery}/list` 与 `GET /api/msg/delivery/stats` 在 1.1.0 **仍在**，
不用改——但它们是 §9 那个"完全不做身份校验"的管理面，改不改端点都要一并处理。

### 1.1.0 → 1.2.0 的读侧形状变更

下面这一条不在 1.1.0 的发布件里，**从 1.2.0 起对外**（发布件侧的证据：repo1 上 `z-msg-im-1.2.0.jar`
里的 `ImMessageController#history` 返回 `Result<ImHistoryPage>`，而 `z-msg-im-1.1.0.jar` 里是
`Result<List<ImMessageDO>>`，两边都 `javap` 过）：

| 1.1.0 已发布 | 1.2.0 | 迁移动作 |
|---|---|---|
| `POST /api/msg/im/message/history` → `data` 是**消息数组** | `data` 是对象：`{ rows, nextSinceSeq, hasMore, headSeq, minVisibleSeq }` | 行从 `data` 改读 `data.rows`；翻页别再自己拼 `sinceSeq = rows[rows.length-1].seq`，直接用 `nextSinceSeq`；原先"回了 `size` 条就当还有下一批"的判断可以整段删掉，换 `hasMore` |

`ImMessageService#history(...)` 那个返回 `List<ImMessageDO>` 的签名**保留**（它就是
`historyPage(...).getRows()`，同一个查询、同一套可见性口径），所以 Java 侧的宿主不改也能编过、
行为不变；只有走 HTTP 的客户端会看到这个形状变化。

改动代价在本仓库里量过，是零：`z-msg-im` 只被 `z-msg-example` 一个下游 pom 引用；
示例宿主的页面只调 `/inbox`、`/inbox/ws-token` 与 WS，没调过 `/history`；z-opc 前端
（`z-opc/bootstraps/z-opc-main-starter-frontend/src/msg/`，实测 6 个文件含 dist）里
`msg/im` **0 命中**。也就是说目前没有任何已知客户端会因为这一条断掉——但它是破坏性的，
所以只跟着版本号出去，如今是跟着 1.2.0 出去的。

还有一条破坏性的**尚未**跟着版本号出去：IM 的 id 线格式（§6）。它改的是**哪一个**响应，
先把范围量清楚再写下来，别到时候凭印象补——`z-msg-im` 三个 controller 实测 18 条 POST，
形状变的有 11 条：`conversation/single|group|list|members|member/add|member/role`、
`message/send|at|history`（`history` 是 `rows` 里的每一行）、`read/receipts`、`read/summary`
（`items` 里每一行的 `conversationId`）；形状不变的有 7 条，因为它们回的是标量或只有游标/计数：
`conversation/leave|mute|clear|member/remove`、`read/mark|unread`、`message/head`。
字段层面共 16 个注解位（`id` / `conversationId` / `senderUserId` / `ownerUserId` / `lastMsgId` / `userId`）
加 2 处手工拼的帧载荷，**不动**的是 `seq` / `lastReadSeq` / `myLastReadSeq` / `clearedSeq` /
`myClearedSeq` / `replyToSeq` / `unreadCount` / `conversationCount` / `total` / `headSeq` /
`nextSinceSeq` / `minVisibleSeq` / `createdTime`。
JS 客户端的迁移动作只有一句：把这些 id 当字符串用，别再 `Number()` 一次——
`Number("2103885891501236225")` 依旧会舍成 `…200`（这条实测过），形状改了而问题没改。

## 15. 设计参考（GitHub 同类项目）

看过一圈，取的是形状不是代码（stars 为写作时 `gh api` 实测）：

| 项目 | 实测 | 从它那儿拿走的 | z-msg 里落在哪 |
|---|---|---|---|
| [novuhq/novu](https://github.com/novuhq/novu) | 40068★ TS | "一次 API 扇出多通道 + 用户偏好 + 静默时段"作为通知中台的产品形状 | `gateway.fanOut(...)`、`z_msg_user_preference`、`priority` 跳静默/限流的档位 |
| [openimsdk/open-im-server](https://github.com/openimsdk/open-im-server) | 16675★ Go | 会话/成员/**seq** 三件套与"增量同步按 seq 拉"的消息模型 | `z-msg-im` 的 `ImSequencer`、`uk_im_msg_conv_seq`、`POST /api/msg/im/message/history` 的 `sinceSeq` |
| [centrifugal/centrifugo](https://github.com/centrifugal/centrifugo) | 10797★ Go | channel 订阅语义、连接建立即下发 `ready`、服务端代发而非放开客户端直写 | `z-msg-ws` 的 `ready` 帧、`op=publish` + 三态授权 |
| [mrniko/netty-socketio](https://github.com/mrniko/netty-socketio) | 7017★ Java | Java 侧实时接入的常见形态（也说明为什么这里选标准 `TextWebSocketHandler` 而不是再引一个 IO 框架） | `z-msg-ws` 走 Spring 标准 WS，不引额外传输依赖 |
| [ZhongFuCheng3y/austin](https://github.com/ZhongFuCheng3y/austin) | 6083★ Java | 国内渠道清单（邮件/短信/服务号/小程序/企微/钉钉）与"模板 + 厂商参数"的拆分 | `Channels` 常量集、`z-msg.channel.<CH>.*` 那 17 个键 |

差异也说清：novu/austin 是**独立部署的平台**（自带队列与运营台），z-msg 是**嵌进宿主的库**——
它的边界是"不引 MQ、不引厂商 SDK、不加一张自建调度表"；OpenIM 是全栈 IM（含客户端 SDK 与
离线推送），`z-msg-im` 只做服务端会话与实时帧，客户端归宿主。

## 16. 尚未做 / 待议

按性价比排序，都还没有实现：

- 管理面的**角色判定**（见 §9）：1.1.0 落的是"默认关 + 一行 yml 打开"，
  "谁能审批模板、谁能翻别人的投递日志"仍然完全由宿主前置的登录墙决定；
- 把 jti 记账换成共享存储，做到跨实例严格一次（现在是每实例各记，见 §9）；
- `op=auth` 已在主干可用（默认关，见 §4），但**换回原身份时只有自己的收件箱会自动补回来**：
  服务端没有"某用户该有哪些频道"的可枚举清单，要做到自动恢复得给 `TopicAuthorizationPolicy`
  加一个"列出该用户可订 topic"的口子——那是契约变更，没想清楚谁负责保证清单完整之前不开；
- 增量同步的判定量（`hasMore`/`nextSinceSeq`/`headSeq`/`minVisibleSeq`）的 REST 形状已随 1.2.0 发出去（§6、§14），
  但它只让客户端**看得见**洞：占号 CAS 成功而随后的 INSERT 失败时，水位不会让回去，
  那个号就永久空着。写侧的 seq 回收仍然待议（要做就得连"让回去的号可能已经被更晚的写占走"一起想清楚）；
- provider 出网白名单（SSRF 面：`url`/`base-url` 目前由配置决定，scheme 白名单已有，host 未限）；
- 摘要合并窗口与时区感知的静默时段；
- SendGrid / FCM-APNs 的 sender 实现（腾讯云 SMS 与极光推送已在树上：`TencentSmsSender` / `JPushSender`，
  但**只有离线 stub 测试与离线固定向量背书，没真机投过一条**，且尚未发布）。

---

## 技术栈与前置

Java 8、Spring Boot 2.7.12、MyBatis-Plus 3.5.7、`z-boot-dependencies:1.0.8` BOM、
SLF4J + Log4j2、H2（测试与演示）。Maven 3.6+；发布需 `~/.m2/settings.xml` 里有 `<server id="central">`
（跑 `install-settings.sh`）。

## 项目结构

```
z-msg/
├── pom.xml                 # parent：${revision}=1.2.0 + flatten，自给自足
├── _doc/001_WS_PROTOCOL.md # 实时协议逐帧规范
├── z-msg-api/              # 纯 SPI
├── z-msg-core/             # 默认 provider + router + 站内信 + 7 表 DDL
├── z-msg-channels/         # 真实厂商 provider
├── z-msg-web/              # REST + 自动装配 + 身份接缝
├── z-msg-ws/               # WebSocket 实时层
├── z-msg-im/               # 会话 / 消息 / 已读回执
├── z-msg-example/          # 演示宿主（不发布）
├── deploy_maven_center.sh  # 一键发布
└── install-settings.sh
```

## 相关项目

| 项目 | 关系 |
|---|---|
| `z-boot` | BOM 与 starter 聚合（`z-boot-msg-starter:1.0.16` 是 repo1 上的最新件，实测 pin `z-msg-web:1.2.0`、且 `1.0.17` 404；`1.0.15` 那版 pin 的是 `1.1.0`。**聚合里只有 `z-msg-web` 这一条**，`ws`/`im`/`channels` 要直接引，见开头那条警告） |
| `z-ctc` | 宿主认证来源：`MsgPrincipalResolver` 生产实现接它的 JWT |
| `z-mq` | 同系列消息队列；z-msg 刻意不依赖它。`RealtimeTransport` 本仓库只有一个实现（`WsSessionRegistry`，单机内存），多节点部署要宿主自己接一层桥 |
| `z-opc` | 下游消费者（站内信、模板管理页、演示宿主的前端）。**实测仍跑在 1.0.0**：09-26 21:15 数过，7 处 z-msg pin 全是 `1.0.0`（`pom.xml` 的 depMgmt 3 处、`bootstraps/z-opc-main-starter/pom.xml` 直接依赖 1 处、`z-qa` 与 `z-qa-core` 3 处），另有 6 处 `z-boot-msg-starter:1.0.11`（那份发布件 pin 的也是 `z-msg-web:1.0.0`）。也就是说 §14 的收件箱归属校验还没落到线上字节上 |

## 许可

[MIT License](LICENSE)

---

_Maintained by z-opc-foundation organization._
