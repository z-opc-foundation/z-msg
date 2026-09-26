# z-msg-channels — 真实外部渠道 provider 集合

把 `z-msg-api` 的 `ChannelSender` SPI 落到真实厂商：**钉钉 / 企业微信 / 飞书 / Slack 群机器人、
微信公众号模板消息、阿里云短信 / 腾讯云短信、极光推送**，外加一个**录制 mock**。全部基于 JDK `HttpURLConnection`
+ core 的 `MsgJson`，不引厂商 SDK、不引 OkHttp；每家都有针对本地 stub server
（JDK `com.sun.net.httpserver`）的离线可复现测试，签名另有离线（python3）预制的
固定输入 → 固定期望值向量。

接入方式：宿主引本 jar（`spring.factories` 自动装配），写一段 yml，**不改 router / gateway 一行源码**。

## 语义前提（与 core 对齐）

- **channel = 投递介质**（`Channels` 常量），**provider = 厂商实现**（`ChannelSender.provider()`）。
  钉钉/企微/飞书是三个 channel；"阿里云短信"与"腾讯云短信"同属 `SMS` 通道下的两个 provider
  （`aliyun` / `tencent`），两个 bean 常驻、各按各的凭据回答 `ready()`。
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
| `provider: aliyun` | 是 | core `MessageProperties.providerOf` | 不配则默认 `mock`（走录制 mock） |
| `access-key-id` / `access-key-secret` | 是 | `AliyunSmsSender.configured/doSend` | secret 只参与 HMAC，绝不上日志 |
| `sign-name` | 是* | `AliyunSmsSender.doSend` | `param("signName")` 逐条覆盖 |
| `template-code` | 是* | `AliyunSmsSender.doSend` | `param("templateCode")` 逐条覆盖 |
| `region` | 否 | `AliyunSmsSender.doSend` | 默认 cn-hangzhou |
| `signature-version` | 否 | `AliyunSmsSender.doSend` | RPC 签名固定 "1.0"，随公共参数上送 |

签名：RPC/POP 风格 `Signature = Base64(HmacSHA1(secret+"&", "POST&%2F&encode(sortedCanonical)"))`，
期望向量钉在 `SignaturesTest`（离线 python3 计算）。

### SMS（腾讯云短信 SendSms，provider=`tencent`，`TencentSmsSender`）

同一个 `SMS` 通道，换一个 provider 名而已：`z-msg.channel.sms.provider: tencent`。

| 配置项 | 必填 | 在哪被读 | 说明 |
|---|---|---|---|
| `provider: tencent` | 是 | core `MessageProperties.providerOf` | 选腾讯云这条，与 `aliyun` 互斥（同 channel 各 pick 自己的 bean） |
| `access-key-id` / `access-key-secret` | 是 | `TencentSmsSender.configured/doSend` | 腾讯云侧叫 SecretId / SecretKey；SecretKey 只进派生签名，两个都不上日志 |
| `sdk-app-id` | 是 | `TencentSmsSender.configured/doSend` | `SmsSdkAppId`（短信应用的 `1400xxxxxxx`，与极光 `app-key` 不是一回事） |
| `sign-name` / `template-code` | 是* | `TencentSmsSender.doSend` | → `SignName` / `TemplateId`，同样可被 `param` 逐条覆盖 |
| `region` | 否 | `TencentSmsSender.doSend` | `X-TC-Region`，默认 `ap-guangzhou` |

签名：TC3-HMAC-SHA256（`Signatures.tc3Sign`），四段（canonical request / string to sign / 派生密钥链 /
十六进制签名）逐段钉在 `SignaturesTest` 的 python3 向量上；`TencentSmsSenderTest` 再钉一件事——
**签的就是发的那几个字节**：Authorization 用 stub 捕获的 `content-type`、`Host`、请求体原文重算
（测试侧独立实现 `TestSigns.tc3Authorization`）比对，所以"canonical 里的 host 与 JDK 实际发的
Host 头不一致"这类只在真机才会被拒的偏差在离线就红。
`receiver` 要 E.164：裸号码自动补 `+86`，已带 `+` 的原样送，补不出形状本地 `INVALID_RECEIVER`、
不发那次必然被拒的请求。`TemplateParamSet` 按 params 的 **key 字典序**取值（`Message.params` 是
HashMap，不按位置；要严格顺序用 `param("templateParamSet")` 逗号分隔显式给）。

### PUSH_JPUSH（极光推送 v3，provider=`jpush`，`JPushSender`）

| 配置项 | 必填 | 在哪被读 | 说明 |
|---|---|---|---|
| `provider: jpush` | 是 | core `MessageProperties.providerOf` | `PUSH_JPUSH` 此前只能落 mock |
| `app-key` / `master-secret` | 是 | `JPushSender.configured/doSend` | HTTP Basic 的两段；Base64 后的整串凭据也不得进日志 |
| `url` / `base-url` | 否 | `AbstractHttpChannelSender.buildUrl` | 默认 `https://api.jpush.cn`，测试注入 stub |

`receiver` = registration_id，或 `"all"` 广播；`param("alias")` / `param("tag")`（逗号分隔）按别名/标签定组，
优先级高于 `receiver`；`param("platform")` 收窄到 `android` / `ios`。正文取 `content`，缺位时用 `subject`，
两者全空本地 `INVALID_CONTENT`。

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
public class DemoPushSender implements ChannelSender {
    @Override public String channel()  { return Channels.PUSH_FCM; }
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
    push-fcm:
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
- 腾讯云短信：`SendCode`（国际/港澳台）、`TemplateParamSetV5`、回调与状态查询、
  频控字段（`SmsSdkAppId` 维度的日限）；**形状按 TC3 规范实现并已用离线固定向量钉死算法，
  但没真机投过一条** —— 官方文档的示例串是 SPA 壳子（`curl` 回来 grep 不到 `TC3-HMAC-SHA256`），
  所以外部真值用的是 python3 独立实现那份，与仓库里其他三家同口径。
- 极光：透传 `message`、`sms`、`live_activity`、`push_sth`/`push_status` 这类异步与查询接口、
  人群/分段（`segment`/`filter`）定向。
- SendGrid、FCM / APNs / Web Push（`PUSH_WEB`）：**未实现**，形状参照
  `AliyunSmsSender` / `TencentSmsSender` + 测试模板补齐即可。

## 测试地图（全部离线，`mvn -B -o -pl z-msg-channels test`）

| 测试类 | 钉住什么 |
|---|---|
| `SignaturesTest` | 钉钉/飞书/阿里云 RPC/腾讯云 TC3/Basic 五套凭据的固定输入→固定期望值（python3 离线算），编码三替换，另有一支独立重写的 TC3 实现与生产实现互验 |
| `DingTalkRobotSenderTest` | query 的 access_token/timestamp/sign（独立重算）、body 形状、errcode→fail |
| `FeishuRobotSenderTest` | hook path、body 内 timestamp(秒)/sign 独立重算、code→fail |
| `WecomRobotSenderTest` | key 在 query、mentioned_mobile_list、errcode!=0 → fail 带 errmsg |
| `SlackBotSenderTest` | Bearer header、receiver/slack-channel 优先级、ok:false → fail |
| `WeixinMpSenderTest` | token 缓存 N:1、42001 作废重取重发各 1 次（数 stub 侧请求条数） |
| `AliyunSmsSenderTest` | 整条 canonical query + Signature 等于离线向量、Code!=OK → fail |
| `TencentSmsSenderTest` | Authorization 用**捕获到的** content-type/host/body 独立重算后逐字相等（含"换一个 secret 必须不等"的阳性对照）、body 逐字段顺序、E.164 补码与拒绝、403 不重试、200 里逐号码 `Ok` 判定 |
| `JPushSenderTest` | Basic header 等于离线向量、platform/audience/notification 双端 body、receiver=all 广播与 alias/tag 优先级、4xx error.code→`JPUSH_<code>` 且不重试 |
| `HttpResilienceTest` | 超时快速 fail（耗时上限断言）、上限夹值、5xx 重试/业务拒不重试、响应体截断、scheme 白名单 |
| `RouterWiringTest` | 缺配置 → router 走 `PROVIDER_NOT_CONFIGURED` 跳过且零 HTTP；配齐 → 同一 router 真投出去；新渠道 bean+yml 即被选中 |
| `CredentialsNotLoggedTest` | 六家（钉钉/Slack/微信/阿里云/腾讯云/极光）凭据先证"真的在请求原文里"，再断言日志零命中 + 捕获非空 |
| `ChannelsAutoConfigurationTest` | spring.factories 注册、kebab/大写 key 绑定、`@ConditionalOnMissingBean` 可被宿主顶掉、enabled=false 全撤 |
| `RecordingMockSenderTest` | isMock/录制/clear；robot provider 配 `mock: true` 的翻转 |
