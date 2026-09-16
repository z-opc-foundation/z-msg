# z-msg

> **多通道消息下发抽象层** — 短信 / 邮件 / In-app / Push / Webhook / IM 统一接口
> Java 8 + Spring Boot 2.7, 一行切换 Provider, 业务代码零修改

[![Maven Central](https://img.shields.io/badge/Maven%20Central-1.0.0-blue?logo=apache-maven)](https://central.sonatype.com/search?q=g:io.github.yuku123+a:z-msg*)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
[![Java](https://img.shields.io/badge/Java-8%2B-orange)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-6DB33F)](https://spring.io)

---

## 🚀 5 分钟接入

### 方式一：仅用 SPI 接口（最小依赖，零 Spring）

```xml
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-msg-api</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
import com.zifang.z.msg.api.MessageGateway;
import com.zifang.z.msg.api.EmailMessage;
import com.zifang.z.msg.api.MessageSendResult;

// 业务代码只依赖 MessageGateway 抽象，不绑死任何 Provider
public class NotificationService {
    private final MessageGateway gateway;

    public NotificationService(MessageGateway gateway) {
        this.gateway = gateway;       // 由 z-msg-core 提供默认实现
    }

    public MessageSendResult sendWelcome(String to) {
        EmailMessage msg = EmailMessage.builder()
                .to(to)
                .subject("欢迎加入")
                .body("感谢注册...")
                .build();
        return gateway.send(msg);
    }
}
```

### 方式二：完整接入（API + Core + Web）

```xml
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-msg-web</artifactId>
    <version>1.0.0</version>
</dependency>
```

```yaml
# application.yml
z:
  msg:
    enabled: true                       # 由 z-boot-msg-starter 控制（推荐）
    channels:
      - email
      - sms
      - in-app
      - push
      - webhook
    email:
      smtp-host: smtp.example.com
      smtp-port: 587
      username: ${SMTP_USER}
      password: ${SMTP_PASS}
    sms:
      provider: mock                    # aliyun / tencent / mock
```

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
    // z-msg-web 通过 spring.factories 自动注册 MsgAutoConfiguration
    // 自动暴露: /api/msg/send, /api/msg/template, /api/msg/batch ...
}
```

### 方式三：通过 z-boot 聚合 starter（推荐）

```xml
<!-- 只需 import 一个 z-boot-msg-starter，自动拿到 z-msg-api + z-msg-core + z-msg-web -->
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-boot-msg-starter</artifactId>
    <!-- version 由 z-boot-dependencies BOM 锁 -->
</dependency>
```

---

## 📦 模块说明

| 模块 | 说明 | 何时该引入 |
|---|---|---|
| `z-msg-api` | 纯 SPI（MessageGateway / EmailSender / SmsSender / MessageBus / Channels） | 想自己实现 Provider 的项目 |
| `z-msg-core` | 默认实现（Mock/Smtp + ChannelRouter + RateLimiter + 7 DO 实体） | 任何业务模块 |
| `z-msg-web` | Spring Boot Controller + AutoConfiguration（spring.factories） | 想开箱即用 HTTP API 的项目 |

> 三个模块均已发布到 Maven Central，groupId: `io.github.yuku123`，version: **1.0.0**

---

## ✨ 设计原则

### 通道无关

业务代码只依赖 `MessageGateway` 抽象接口，**不绑死** Aliyun SMS / SMTP / 微信推送。切换后端只改 `application.yml` 一行。

### 多通道架构

```
                ┌──────────────────────────────┐
                │  MessageGateway (统一入口)   │
                └──────────────────────────────┘
                          ▲   ▲   ▲   ▲   ▲
                          │   │   │   │   │
            ┌─────────────┘   │   │   └────────────┐
            │           ┌─────┘   └─────┐          │
            │           │               │          │
   ┌────────┴─────┐ ┌───┴────┐  ┌──────┴───┐ ┌────┴──────────┐
   │ EmailChannel │ │SmsChannel│  │PushChannel│ │WebhookChannel │
   │ (SMTP / SES) │ │(Aliyun)  │  │(APNs/FCM) │ │(HTTP callback)│
   └──────────────┘ └──────────┘  └───────────┘ └───────────────┘
```

### 默认 Provider

| 通道 | 默认实现 | 生产可替换为 |
|------|----------|-------------|
| Email | MockEmailSender / SmtpEmailSender | SES / SendGrid / Aliyun DirectMail |
| SMS | MockSmsSender | Aliyun SMS / Tencent Cloud SMS |
| In-app | InAppChannel | 自建消息中心 |
| Push | PushSender（HTTP） | APNs / FCM / 极光 / 个推 |
| Webhook | WebhookSender | Slack / 钉钉 / 飞书 |
| IM | ImSender | 微信 / 钉钉 / 飞书机器人 |

---

## ⚙️ 实用 Case

### Case 1: 单条邮件发送

```java
@Autowired private MessageGateway gateway;

EmailMessage msg = EmailMessage.builder()
        .to("user@example.com")
        .subject("订单确认")
        .body("您的订单 #12345 已提交成功")
        .build();

MessageSendResult result = gateway.send(msg);
// result.isSuccess() / result.getMessageId() / result.getChannel()
```

### Case 2: 模板渲染 + 批量发送

```java
@Autowired private MessageGateway gateway;
@Autowired private MessageTemplateEngine templateEngine;

// 1. 模板渲染（参数化）
String rendered = templateEngine.render("welcome-template", Map.of(
        "userName", "张三",
        "registerTime", "2026-09-15"
));

// 2. 批量下发（自动限流 + 重试）
List<String> recipients = List.of("a@x.com", "b@y.com", "c@z.com");
BatchSendResult batch = gateway.sendBatch(Channel.EMAIL, recipients, rendered, "欢迎注册");

System.out.println("成功 " + batch.getSuccessCount() + " / " + recipients.size());
```

### Case 3: 多通道并行发送（验证码场景）

```java
MultiChannelMessage multi = MultiChannelMessage.builder()
        .add(Channel.EMAIL,    EmailMessage.to("user@example.com"))
        .add(Channel.SMS,      SmsMessage.to("+8613800000000"))
        .add(Channel.PUSH,     PushMessage.toDevice("device-token-123"))
        .build();

MessageSendResult result = gateway.sendMulti(multi);
// 三个通道并行发送，任一成功即视为发送成功
```

### Case 4: 自定义 Provider 扩展（SPI 钩子）

```java
// 业务侧实现自定义 SMS Provider（不需要修改 z-msg 源码）
@Component
public class AliyunSmsProvider implements SmsSender {
    @Override
    public MessageSendResult send(SmsMessage message) {
        // 调用 Aliyun SDK
        return aliyunClient.sendSms(message.getTo(), message.getContent());
    }

    @Override
    public Channel channel() {
        return Channel.SMS;
    }
}

// Spring Boot 启动后，ChannelRouter 自动发现并注册该 Provider
```

### Case 5: HTTP API 直接调用（无需写 Java 代码）

```bash
# 1. 发送邮件
curl -X POST http://localhost:8080/api/msg/send \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "EMAIL",
    "to": "user@example.com",
    "subject": "测试",
    "body": "Hello z-msg"
  }'

# 2. 创建模板
curl -X POST http://localhost:8080/api/msg/template \
  -H "Content-Type: application/json" \
  -d '{
    "code": "welcome-template",
    "channel": "EMAIL",
    "subject": "欢迎 {{userName}}",
    "body": "Hi {{userName}}, 您的注册时间是 {{registerTime}}"
  }'

# 3. 查询投递日志
curl http://localhost:8080/api/msg/delivery-log?messageId=xxx
```

---

## 🏗️ 项目结构

```
z-msg/
├── pom.xml                          # 自给自足 parent (${revision} + flatten)
├── z-msg-api/                       # 纯 SPI ✅ 已发布 1.0.0
├── z-msg-core/                      # 默认实现 + Router + RateLimiter + 7 DO ✅ 已发布 1.0.0
├── z-msg-web/                       # Spring Boot Controller + AutoConfiguration ✅ 已发布 1.0.0
├── deploy_maven_center.sh           # 一键发布到 Maven Central
├── install-settings.sh              # 配置 ~/.m2/settings.xml
└── README.md
```

---

## 🔧 技术栈

- Java 8 (Spring Boot 2.7.12 + MyBatis-Plus 3.5.7)
- Spring Boot Mail（SmtpEmailSender 用 JavaMailSender）
- Log4j2 + SLF4J
- 通过 `z-boot-dependencies` BOM 锁定所有第三方版本

---

## 🚀 快速开始

### 前置条件

- JDK 8+
- Maven 3.6+
- 已配置 `~/.m2/settings.xml` 含 `<server id="central">`（跑 `install-settings.sh`）

### 编译

```bash
cd /path/to/z-msg
mvn clean verify -DskipTests          # 仅编译
mvn clean verify                      # 编译 + 单元测试
```

### 接入示例

最小集成只需 2 行 XML：

```xml
<!-- 1. z-boot-dependencies 锁版本 -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.yuku123</groupId>
            <artifactId>z-boot-dependencies</artifactId>
            <version>1.0.8</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- 2. 一行 import z-msg -->
<dependencies>
    <dependency>
        <groupId>io.github.yuku123</groupId>
        <artifactId>z-msg-web</artifactId>
    </dependency>
</dependencies>
```

启动你的 Spring Boot 应用，`/api/msg/*` 端点即自动可用。

---

## 🧪 测试覆盖

```
单元测试:    PASS（z-msg-core 7 DO + ChannelRouter + RateLimiter）
集成测试:    PASS（Mock 通道端到端）
```

---

## 📚 详细文档

- [Channel 设计](docs/CHANNEL_DESIGN.md)
- [RateLimiter 配置](docs/RATE_LIMITER.md)
- [模板引擎](docs/TEMPLATE_ENGINE.md)
- [迁移指南（直连 Aliyun SDK → z-msg）](docs/MIGRATION.md)

---

## 🤝 贡献

```bash
cd z-msg-core
mvn clean verify
```

---

## 📄 许可证

[MIT License](LICENSE)

---

## 🔗 相关项目

| 项目 | 关系 |
|------|------|
| [z-boot](https://github.com/z-opc-foundation/z-boot) | 提供 BOM + starter 聚合 (`z-boot-msg-starter`) |
| [z-mq](https://github.com/z-opc-foundation/z-mq) | 同系列 — 分布式消息队列（异步通道可结合） |
| [z-cache](https://github.com/z-opc-foundation/z-cache) | 同系列 — 分布式缓存（限流计数器可用） |
| [z-opcs](https://github.com/z-opc-foundation/z-opcs) | 下游消费者 — 任务撮合消息推送（B 端通知） |

---

## 📮 联系

- GitHub Issues: 提交 bug / feature request
- Email: yuku123@users.noreply.github.com

---

_S-MSG: 让任何 Java 业务都能 5 分钟接入多通道消息下发._
_Maintained by z-opc-foundation organization._