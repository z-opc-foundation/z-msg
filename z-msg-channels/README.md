# z-msg-channels — 真实外部渠道 provider 集合

把 `z-msg-api` 的 `ChannelSender` SPI 落到真实厂商：**钉钉 / 企业微信 / 飞书 / Slack 群机器人、
微信公众号模板消息、阿里云短信**，外加一个**录制 mock**。全部基于 JDK `HttpURLConnection`
+ core 的 `MsgJson`，不引厂商 SDK、不引 OkHttp；每家都有针对本地 stub server
（JDK `com.sun.net.httpserver`）的离线可复现测试，签名另有离线（python3）预制的
固定输入 → 固定期望值向量。

接入方式：宿主引本 jar（`spring.factories` 自动装配），写一段 yml，**不改 router / gateway 一行源码**。

## 语义前提（与 core 对齐）

- **channel = 投递介质**（`Channels` 常量），**provider = 厂商实现**（`ChannelSender.provider()`）。
  钉钉/企微/飞书是三个 channel；"阿里云短信"是 `SMS` 通道下的 `aliyun` provider。
- 选哪个 provider 由 core 的 `MessageProperties.providerOf(channel)` 决定
  （`z-msg.channel.<ch>.provider`，缺省时 IM 通道为 `mock`）。
- provider bean **常驻装配**（除 `z-msg.enabled=false`）；配置不齐时 `ready()=false`，
  `ChannelRouter` 实测行为 = 跳过该通道并记 `PROVIDER_NOT_CONFIGURED`（status=4），
  不抛异常、不发 HTTP。"没配就不装配"反而会掉进 `SenderRegistry` 的 mock 兜底
  （假成功 status=3），排查方向是反的——所以选了现在这条路。
- 供应商业务拒绝一律 `MessageSendResult.fail(code, msg)` 上抛是禁止的（`ChannelSender` 契约），
  base 类兜底捕获一切异常翻成 `PROVIDER_EXCEPTION`。

## 配置表（前缀 `z-msg.channel.`，key 支持 `im-dingtalk` / `im_dingtalk` / `IM_DINGTALK`）

「在哪被读」列出真正消费该字段的类；没有消费者的字段不存在于本表。

### IM_DINGTALK（钉钉群机器人，provider=`robot`，`DingTalkRobotSender`）

| 配置项 | 必填 | 在哪被读 | 说明 |
|---|---|---|---|
| `provider: robot` | 是 | core `MessageProperties.providerOf` | 不配则默认 `mock`（走录制 mock） |
| `token` | 是 | `DingTalkRobotSender.configured/send` | 机器人 access_token，进 query |
| `secret` | 开加签时 | `DingTalkRobotSender.send` → `Signatures.dingTalkSign` | `urlencode(Base64(HmacSHA256(secret, ts+"\n"+secret)))` |
| `url` / `base-url` | 否 | `AbstractHttpChannelSender.buildUrl` | 测试注入 stub / 私有化网关 |
| `connect-timeout-ms` / `read-timeout-ms` | 否 | `AbstractHttpChannelSender.execute` → `SimpleHttpClient` | 上限 10s / 60s，见下 |
| `retries` | 否 | `AbstractHttpChannelSender.execute` | 仅传输错误/5xx 重试，封顶 3 |
| `mock` | 否 | `AbstractHttpChannelSender.ready/send/isMock` | true=不外发、录制进内存且 `isMock()` 翻转 |

### IM_FEISHU（飞书群机器人，provider=`robot`，`FeishuRobotSender`）

同钉钉表；差异：`token` 是 hook 地址尾段（或直接给整段 `url`）；`secret` 加签时
`sign = Base64(HmacSHA256(key=timestamp+"\n"+secret, data=""))`、timestamp 为**秒**，
且 timestamp/sign 放 **body** 不放 query。

### IM_WECOM（企业微信群机器人，provider=`robot`，`WecomRobotSender`）

同钉钉表；`token` = webhook 的 `key` 参数；无签名机制；`vendorOptions.mentionedMobiles`
（List 或逗号串）→ `text.mentioned_mobile_list`。

### IM_SLACK（`chat.postMessage`，provider=`bot`，`SlackBotSender`）

| 配置项 | 必填 | 在哪被读 | 说明 |
|---|---|---|---|
| `token` | 是 | `SlackBotSender.configured/send` | bot user token（xoxb-…），只进 `Authorization: Bearer` header |
| `slack-channel` | 否 | `SlackBotSender.send` | 消息 `receiver` 为空时的默认会话 |

### IM_WEIXIN_MP（微信公众号模板消息，provider=`mp`，`WeixinMpSender`）

| 配置项 | 必填 | 在哪被读 | 说明 |
|---|---|---|---|
| `app-id` / `app-secret` | 是 | `WeixinMpSender.configured/obtainToken` | 取 access_token；**按 appId 缓存到过期前 5 分钟**，40001/42001/40014 作废重取一次 |
| `template-code` | 否 | `WeixinMpSender.send` | 默认模板 id；`message.param("templateCode")` 逐条覆盖 |

`receiver` = 用户 openid；`linkUrl` → 模板消息 `url`；`params`（去掉保留字）逐个包成
`data.{key}.value`。

### SMS（阿里云短信 dysmsapi，provider=`aliyun`，`AliyunSmsSender`）

| 配置项 | 必填 | 在哪被读 | 说明 |
|---|---|---|---|
| `access-key-id` / `access-key-secret` | 是 | `AliyunSmsSender.configured/doSend` | secret 只参与 HMAC，绝不上日志 |
| `sign-name` | 是* | `AliyunSmsSender.doSend` | `param("signName")` 逐条覆盖 |
| `template-code` | 是* | `AliyunSmsSender.doSend` | `param("templateCode")` 逐条覆盖 |
| `region` | 否 | `AliyunSmsSender.doSend` | 默认 cn-hangzhou |
| `signature-version` | 否 | `AliyunSmsSender.doSend` | RPC 签名固定 "1.0"，随公共参数上送 |

签名：RPC/POP 风格 `Signature = Base64(HmacSHA1(secret+"&", "POST&%2F&encode(sortedCanonical)"))`，
期望向量钉在 `SignaturesTest`（离线 python3 计算）。

### 录制 mock（provider=`mock`，`RecordingMockSender`）

自动装配默认给 `IM_DINGTALK / IM_FEISHU / IM_WECOM / IM_SLACK` 各注册一个
（bean 名 `recordingMockDingTalk` 等）。两种用法：

```yaml
z-msg:
  channel:
    im-dingtalk:
      provider: mock    # 不配 provider 时缺省也是 mock
```

`getBean(RecordingMockSender.class)` / 任何 robot provider 实例的 `recorded()` 可查看内容。
`isMock()=true` 恒成立，投递日志记 status=3。

## 超时与硬上限

| 项 | 缺省 | 上限（代码夹死，`ChannelsProperties`） |
|---|---|---|
| connect-timeout-ms | 3000 | 10000 |
| read-timeout-ms | 5000 | 60000 |
| 响应体读取 | — | 64 KiB（`MAX_RESPONSE_BYTES`，超出截断） |
| HTTP 层 retries | 0 | 3，且只重试传输错误/5xx |

配 0 或负数不表达"无限等"，回落到缺省值。

## 加一个新渠道 = 一个 `ChannelSender` bean + 一段 yml

不需要动 `ChannelRouter` / `SenderRegistry` / gateway——core 的
`MsgCoreConfiguration.senderRegistry(...)` 用 `ObjectProvider<ChannelSender>` 收集所有 bean。
下面这段就是 `RouterWiringTest.newChannelNeedsOnlyABeanAndOneYmlKey` 里真跑过的形状：

```java
@Component
public class JpushSender implements ChannelSender {
    @Override public String channel()  { return Channels.PUSH_JPUSH; }
    @Override public String provider() { return "demo"; }
    @Override public boolean ready()   { return true; }
    @Override public MessageSendResult send(Message m) {
        // ... 组请求、发 HTTP；业务拒绝返回 fail(...)，不要抛异常
        return MessageSendResult.ok("demo", "DEMO-" + m.getMsgId());
    }
}
```

```yaml
z-msg:
  channel:
    push-jpush:
      provider: demo    # ← pick() 按这个名字选中上面的 bean
```

## 明确不支持 / 待与官方文档核对

- 钉钉：markdown/link/actionCard/feedCard、`atUserIds`；限流额度（约 20 条/分钟，超限
  errcode 会被如实翻成 fail，但客户端不限速）。
- 飞书：post/interactive 卡片、@人（需通讯录接口）；**签名形状（key=ts+"\n"+secret、
  空数据、秒级 ts）按记忆实现，`FeishuRobotSender` 类注释标了核对点**。
- 企微：应用消息（corpid+corpsecret+agentid 那条链路）、markdown/image/news/file msgtype；
  群机器人**无限流文档背书的成功率语义**（45099 频控等 errcode 原样 fail 上抛）。
- Slack：Blocks、thread_ts、unfurls；`chat.postMessage` 官方速率（scope 分级）未做客户端限速。
- 微信：小程序订阅消息（IM_WEIXIN_MINI）、miniprogram 跳转字段、回调加解密。
- 阿里云短信：BatchSendSms、多号码逗号语义、TemplateParam 与模板 ${var} 的严格校验。
- 腾讯云 SMS（TC3-HMAC-SHA256）、SendGrid、极光、FCM/APNs：**未实现**，形状参照
  `AliyunSmsSender` + 测试模板补齐即可。

## 测试地图（全部离线，`mvn -B -o -pl z-msg-channels test`）

| 测试类 | 钉住什么 |
|---|---|
| `SignaturesTest` | 三家签名的固定输入→固定期望值（python3 离线算），编码三替换 |
| `DingTalkRobotSenderTest` | query 的 access_token/timestamp/sign（独立重算）、body 形状、errcode→fail |
| `FeishuRobotSenderTest` | hook path、body 内 timestamp(秒)/sign 独立重算、code→fail |
| `WecomRobotSenderTest` | key 在 query、mentioned_mobile_list、errcode!=0 → fail 带 errmsg |
| `SlackBotSenderTest` | Bearer header、receiver/slack-channel 优先级、ok:false → fail |
| `WeixinMpSenderTest` | token 缓存 N:1、42001 作废重取重发各 1 次（数 stub 侧请求条数） |
| `AliyunSmsSenderTest` | 整条 canonical query + Signature 等于离线向量、Code!=OK → fail |
| `HttpResilienceTest` | 超时快速 fail（耗时上限断言）、上限夹值、5xx 重试/业务拒不重试、响应体截断、scheme 白名单 |
| `RouterWiringTest` | 缺配置 → router 走 `PROVIDER_NOT_CONFIGURED` 跳过且零 HTTP；配齐 → 同一 router 真投出去；新渠道 bean+yml 即被选中 |
| `CredentialsNotLoggedTest` | 四家凭据先证"真的在请求原文里"，再断言日志零命中 + 捕获非空 |
| `ChannelsAutoConfigurationTest` | spring.factories 注册、kebab/大写 key 绑定、`@ConditionalOnMissingBean` 可被宿主顶掉、enabled=false 全撤 |
| `RecordingMockSenderTest` | isMock/录制/clear；robot provider 配 `mock: true` 的翻转 |
