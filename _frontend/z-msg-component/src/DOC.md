# z-msg-component

z-msg（消息中心）跨仓唯一前端面：把「站内信收件箱 / 消息模板 / 投递记录 / 批量发送任务」
这一整套消息页面收敛成一个可复用的 React 组件包。

- **npm 包名**：`@yuku123/z-msg-component`
- **宿主接入**：`@yuku123/z-msg-component/pages`（manifest + 页面），主壳可只取 `ComponentDocPage`
- **后端**：z-opc 主进程 8888 的 `/api/msg/**`（路径已于 2026-09-24 经 `/actuator/mappings` 核对）
- **技术栈**：React 19 + antd 6 + react-router-dom 7 + vite（library mode，双入口 `.` / `./pages`）

---

## 一、能力概览

路由前缀 `/z-msg`，菜单与路由在 `src/pages-manifest.jsx` 里声明。

| 页面 | 路由（相对 `/z-msg/*`） | 源文件 | 能力 |
|------|--------------------------|--------|------|
| 登录 | `login` | `pages/LoginPage.jsx` | 登录页（suit 侧单独挂这条路由） |
| 首页 | `home` | `pages/HomePage.jsx` | 入口卡片页 |
| 收件箱 | `inbox` | `pages/Inbox.jsx` | 当前登录用户的站内信列表（取 50 条） |
| 消息模板 | `templates` | `pages/TemplateList.jsx` | 按渠道 / 业务类型维护消息模板（基座 `PagedTable`） |
| 投递记录 | `delivery` | `pages/DeliveryLogs.jsx` | 投递统计（总数 / 成功 / 失败 / 成功率）+ 投递日志列表 |
| 批量任务 | `batch` | `pages/BatchTasks.jsx` | 批量发送任务的进度与失败数（基座 `PagedTable`） |
| 组件文档 | `docs` | `pages/ComponentDoc.jsx` | 本文件渲染页（左侧 240px TOC + 右侧正文） |

`src/pages/MsgApp.jsx` 是子路由挂载点（`inbox` / `template` / `delivery` / `batch`），
被 `routes` 里的 `/z-msg/:rest*` 承接，同时从包根具名导出（OpsWorkbench / SystemShell 以 `/msg/*` 挂它）。
注意它内部的子路径用的是**单数**（`template`），manifest 顶层用的是**复数**（`templates`）—— 两条路都通。

## 二、导出面

```js
// 主入口（. 桶）
import {msgApi, currentUserId, DELIVERY_STATUS, BATCH_STATUS, TEMPLATE_STATUS} from '@yuku123/z-msg-component'
import {MsgApp, ComponentDocPage} from '@yuku123/z-msg-component'

// 宿主组装面（./pages 桶）
import {menuItems, routes, HomePage, LoginPage, TemplateList, DeliveryLogs} from '@yuku123/z-msg-component/pages'
```

⚠ `./pages` 桶是**显式具名转发**（不是 `export *`）。新增页面时必须回来补一行，否则 suit 打包会报
`"Xxx" is not exported by dist/pages.js` —— 之前漏过 `HomePage` / `LoginPage`，直接让 suit 红了。

## 三、API 端点

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/msg/list` | 站内信列表（query `userId` + `limit`） |
| POST | `/msg/template/list` | 消息模板分页（body `page` / `size`） |
| POST | `/msg/delivery/list` | 投递记录分页（body `page` / `size`） |
| GET | `/msg/delivery/stats` | 投递统计（总数 / 成功 / 失败） |
| POST | `/msg/batch/list` | 批量任务分页（body `page` / `size`） |

两条容易踩的现实：

- 列表类接口是 **POST + body**，分页参数名是 `page` / `size`（**不是** z-meta 那套 `pageNum` / `pageSize`）。
- 站内信接口要求 `userId`，取当前登录用户（见下）。

## 四、页面说明

### 4.1 收件箱（`pages/Inbox.jsx`）

拉当前登录用户的站内信（`limit=50`），空态文案「收件箱为空」。

`userId` 取自 `currentUserId()`：依次读 localStorage 的 `userInfo`（取 `id` / `userId`）与
`USER_INFO`（取 `staffId` / `jobNumber`）。**两个键都读不到时一个请求都不发**，页面提示
「GET /api/msg/list 需要 userId … 请重新登录」。

### 4.2 消息模板（`pages/TemplateList.jsx`）

基座 `PagedTable` 的薄用法：`title` / `description` / `load` / `columns`。
状态用 `TEMPLATE_STATUS` 映射（0 草稿 / 1 启用 / 2 停用）。

### 4.3 投递记录（`pages/DeliveryLogs.jsx`）

顶部四张统计卡（投递总数 / 成功 / 失败 / 成功率），下面接投递日志列表。
状态用 `DELIVERY_STATUS` 映射（0 待发送 / 1 成功 / 2 失败 / 3 重试中）。

### 4.4 批量任务（`pages/BatchTasks.jsx`）

基座 `PagedTable` 的薄用法，展示批量任务的进度与失败数。状态用 `BATCH_STATUS` 映射
（0 待执行 / 1 执行中 / 2 已完成 / 3 部分失败 / 4 失败）。

### 4.5 组件文档

本文件。内容以 `?raw` 在编译期内联，零额外请求；左侧 240px TOC 随滚动联动。

## 五、配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| axios 实例 | 基座 `request`（`createRequest()`） | `baseURL='/api'`，token 键 `token`，`Authorization: Bearer` 自动注入 |
| 当前用户 id | `currentUserId()` | 依次读 `userInfo` → `USER_INFO`，都取不到返回 `null` |
| 状态映射 | `DELIVERY_STATUS` / `BATCH_STATUS` / `TEMPLATE_STATUS` | 数字码 → 文案 + antd 色 |

## 六、已知限制

- ⚠ **`services/api.js` 必须用具名导入拿 axios 实例**：`import { request } from '@yuku123/z-frontend-common'`。
  该包的默认导出是「桶对象」`{AppLayout, StatusTag, LoginPage, ErrorBoundary, request, authRequest, createRequest}`，
  不是 axios 实例 —— 默认导入拿到的桶上没有 `.get` / `.post`，一调就抛
  `TypeError: Xt.get is not a function`（线上 `/msg/inbox` 白屏就是这个，2026-10-20 实测）。
- `routes` 数组里 `/z-msg/delivery` **出现了两次**（一条带 `title` / `order`，一条不带）。
  React Router 按 `path` 建 key，重复项会产生重复 key 警告；建议后续合并成一条。
- `routes` 里的 `title` / `order` 字段不是 react-router 的属性，是本仓自带的元信息，
  宿主可用来给侧边栏排序。
- `MsgApp` 的子路径是 `template`（单数），manifest 顶层是 `templates`（复数）—— 两条路径都能到模板页。
- 收件箱固定取 50 条，没有分页。
- `./pages` 桶是显式具名转发，新增页面**必须**补一行，否则消费方构建报错。
- 组件包必须 external 基座；`vite.config.js` 的 `external` 是精确字符串匹配，
  `@yuku123/z-frontend-common/ui` 与 `/ui/echarts` 两个子路径必须逐个列出。

## 七、变更记录

| 版本 | 变更 |
|---|---|
| 0.1.1 | 新增组件文档页 `ComponentDocPage`（lead 008 §7）：`src/DOC.md` + 基座 `ComponentDoc` 薄包装 |
| 0.1.0 | 首版：消息中心页面集 + manifest + `msgApi` |
