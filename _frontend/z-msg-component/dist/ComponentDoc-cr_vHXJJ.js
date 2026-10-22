import { Routes as me, Route as y, Navigate as fe } from "react-router-dom";
import pe, { useState as j, useEffect as X } from "react";
import { Alert as C, Card as xe, Tag as T, Row as ge, Col as S, Statistic as R, Progress as Te } from "antd";
import { request as b } from "@yuku123/z-frontend-common";
import { PageHeader as G, ListTable as _e, PagedTable as L, ComponentDoc as ye } from "@yuku123/z-frontend-common/ui";
const v = {
  inbox: (t, n) => b.get("/msg/list", { params: { userId: t, limit: n } }),
  templatePage: (t, n) => b.post("/msg/template/list", { page: t, size: n }),
  deliveryPage: (t, n) => b.post("/msg/delivery/list", { page: t, size: n }),
  deliveryStats: () => b.get("/msg/delivery/stats"),
  batchPage: (t, n) => b.post("/msg/batch/list", { page: t, size: n })
};
function be() {
  const t = (i) => {
    try {
      return JSON.parse(localStorage.getItem(i) || "null");
    } catch {
      return null;
    }
  }, n = t("userInfo"), l = n && (n.id ?? n.userId);
  if (l != null && l !== "") return String(l);
  const d = t("USER_INFO"), a = d && (d.staffId ?? d.jobNumber);
  return a == null || a === "" ? null : String(a);
}
const he = {
  0: { text: "待发送", color: "default" },
  1: { text: "成功", color: "success" },
  2: { text: "失败", color: "error" },
  3: { text: "重试中", color: "processing" }
}, Ee = {
  0: { text: "待执行", color: "default" },
  1: { text: "执行中", color: "processing" },
  2: { text: "已完成", color: "success" },
  3: { text: "部分失败", color: "warning" },
  4: { text: "失败", color: "error" }
}, je = {
  0: { text: "草稿", color: "default" },
  1: { text: "启用", color: "success" },
  2: { text: "停用", color: "warning" }
};
var I = { exports: {} }, h = {};
/**
 * @license React
 * react-jsx-runtime.production.js
 *
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
var V;
function ve() {
  if (V) return h;
  V = 1;
  var t = Symbol.for("react.transitional.element"), n = Symbol.for("react.fragment");
  function l(d, a, i) {
    var p = null;
    if (i !== void 0 && (p = "" + i), a.key !== void 0 && (p = "" + a.key), "key" in a) {
      i = {};
      for (var m in a)
        m !== "key" && (i[m] = a[m]);
    } else i = a;
    return a = i.ref, {
      $$typeof: t,
      type: d,
      key: p,
      ref: a !== void 0 ? a : null,
      props: i
    };
  }
  return h.Fragment = n, h.jsx = l, h.jsxs = l, h;
}
var E = {};
/**
 * @license React
 * react-jsx-runtime.development.js
 *
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
var H;
function ke() {
  return H || (H = 1, process.env.NODE_ENV !== "production" && (function() {
    function t(e) {
      if (e == null) return null;
      if (typeof e == "function")
        return e.$$typeof === ce ? null : e.displayName || e.name || null;
      if (typeof e == "string") return e;
      switch (e) {
        case w:
          return "Fragment";
        case ee:
          return "Profiler";
        case K:
          return "StrictMode";
        case se:
          return "Suspense";
        case ae:
          return "SuspenseList";
        case ie:
          return "Activity";
        case le:
          return "ViewTransition";
      }
      if (typeof e == "object")
        switch (typeof e.tag == "number" && console.error(
          "Received an unexpected object in getComponentNameFromType(). This is likely a bug in React. Please file an issue."
        ), e.$$typeof) {
          case Q:
            return "Portal";
          case re:
            return e.displayName || "Context";
          case te:
            return (e._context.displayName || "Context") + ".Consumer";
          case ne:
            var s = e.render;
            return e = e.displayName, e || (e = s.displayName || s.name || "", e = e !== "" ? "ForwardRef(" + e + ")" : "ForwardRef"), e;
          case oe:
            return s = e.displayName || null, s !== null ? s : t(e.type) || "Memo";
          case P:
            s = e._payload, e = e._init;
            try {
              return t(e(s));
            } catch {
            }
        }
      return null;
    }
    function n(e) {
      return "" + e;
    }
    function l(e) {
      try {
        n(e);
        var s = !1;
      } catch {
        s = !0;
      }
      if (s) {
        s = console;
        var o = s.error, c = typeof Symbol == "function" && Symbol.toStringTag && e[Symbol.toStringTag] || e.constructor.name || "Object";
        return o.call(
          s,
          "The provided key is an unsupported type %s. This value must be coerced to a string before using it here.",
          c
        ), n(e);
      }
    }
    function d(e) {
      if (e === w) return "<>";
      if (typeof e == "object" && e !== null && e.$$typeof === P)
        return "<...>";
      try {
        var s = t(e);
        return s ? "<" + s + ">" : "<...>";
      } catch {
        return "<...>";
      }
    }
    function a() {
      var e = A.A;
      return e === null ? null : e.getOwner();
    }
    function i() {
      return Error("react-stack-top-frame");
    }
    function p(e) {
      if (Y.call(e, "key")) {
        var s = Object.getOwnPropertyDescriptor(e, "key").get;
        if (s && s.isReactWarning) return !1;
      }
      return e.key !== void 0;
    }
    function m(e, s) {
      function o() {
        M || (M = !0, console.error(
          "%s: `key` is not a prop. Trying to access it will result in `undefined` being returned. If you need to access the same value within the child component, you should pass it as a different prop. (https://react.dev/link/special-props)",
          s
        ));
      }
      o.isReactWarning = !0, Object.defineProperty(e, "key", {
        get: o,
        configurable: !0
      });
    }
    function x() {
      var e = t(this.type);
      return F[e] || (F[e] = !0, console.error(
        "Accessing element.ref was removed in React 19. ref is now a regular prop. It will be removed from the JSX Element type in a future release."
      )), e = this.props.ref, e !== void 0 ? e : null;
    }
    function Z(e, s, o, c, g, f) {
      var u = o.ref;
      return e = {
        $$typeof: $,
        type: e,
        key: s,
        props: o,
        _owner: c
      }, (u !== void 0 ? u : null) !== null ? Object.defineProperty(e, "ref", {
        enumerable: !1,
        get: x
      }) : Object.defineProperty(e, "ref", { enumerable: !1, value: null }), e._store = {}, Object.defineProperty(e._store, "validated", {
        configurable: !1,
        enumerable: !1,
        writable: !0,
        value: 0
      }), Object.defineProperty(e, "_debugInfo", {
        configurable: !1,
        enumerable: !1,
        writable: !0,
        value: null
      }), Object.defineProperty(e, "_debugStack", {
        configurable: !1,
        enumerable: !1,
        writable: !0,
        value: g
      }), Object.defineProperty(e, "_debugTask", {
        configurable: !1,
        enumerable: !1,
        writable: !0,
        value: f
      }), Object.freeze && (Object.freeze(e.props), Object.freeze(e)), e;
    }
    function z(e, s, o, c, g, f) {
      var u = s.children;
      if (u !== void 0)
        if (c)
          if (ue(u)) {
            for (c = 0; c < u.length; c++)
              U(u[c]);
            Object.freeze && Object.freeze(u);
          } else
            console.error(
              "React.jsx: Static children should always be an array. You are likely explicitly calling React.jsxs or React.jsxDEV. Use the Babel transform instead."
            );
        else U(u);
      if (Y.call(s, "key")) {
        u = t(e);
        var _ = Object.keys(s).filter(function(de) {
          return de !== "key";
        });
        c = 0 < _.length ? "{key: someKey, " + _.join(": ..., ") + ": ...}" : "{key: someKey}", W[u + c] || (_ = 0 < _.length ? "{" + _.join(": ..., ") + ": ...}" : "{}", console.error(
          `A props object containing a "key" prop is being spread into JSX:
  let props = %s;
  <%s {...props} />
React keys must be passed directly to JSX without using spread:
  let props = %s;
  <%s key={someKey} {...props} />`,
          c,
          u,
          _,
          u
        ), W[u + c] = !0);
      }
      if (u = null, o !== void 0 && (l(o), u = "" + o), p(s) && (l(s.key), u = "" + s.key), "key" in s) {
        o = {};
        for (var N in s)
          N !== "key" && (o[N] = s[N]);
      } else o = s;
      return u && m(
        o,
        typeof e == "function" ? e.displayName || e.name || "Unknown" : e
      ), Z(
        e,
        u,
        o,
        a(),
        g,
        f
      );
    }
    function U(e) {
      D(e) ? e._store && (e._store.validated = 1) : typeof e == "object" && e !== null && e.$$typeof === P && (e._payload.status === "fulfilled" ? D(e._payload.value) && e._payload.value._store && (e._payload.value._store.validated = 1) : e._store && (e._store.validated = 1));
    }
    function D(e) {
      return typeof e == "object" && e !== null && e.$$typeof === $;
    }
    var k = pe, $ = Symbol.for("react.transitional.element"), Q = Symbol.for("react.portal"), w = Symbol.for("react.fragment"), K = Symbol.for("react.strict_mode"), ee = Symbol.for("react.profiler"), te = Symbol.for("react.consumer"), re = Symbol.for("react.context"), ne = Symbol.for("react.forward_ref"), se = Symbol.for("react.suspense"), ae = Symbol.for("react.suspense_list"), oe = Symbol.for("react.memo"), P = Symbol.for("react.lazy"), ie = Symbol.for("react.activity"), le = Symbol.for("react.view_transition"), ce = Symbol.for("react.client.reference"), A = k.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE, Y = Object.prototype.hasOwnProperty, ue = Array.isArray, O = console.createTask ? console.createTask : function() {
      return null;
    };
    k = {
      react_stack_bottom_frame: function(e) {
        return e();
      }
    };
    var M, F = {}, q = k.react_stack_bottom_frame.bind(
      k,
      i
    )(), B = O(d(i)), W = {};
    E.Fragment = w, E.jsx = function(e, s, o) {
      var c = 1e4 > A.recentlyCreatedOwnerStacks++;
      if (c) {
        var g = Error.stackTraceLimit;
        Error.stackTraceLimit = 10;
        var f = Error("react-stack-top-frame");
        Error.stackTraceLimit = g;
      } else f = q;
      return z(
        e,
        s,
        o,
        !1,
        f,
        c ? O(d(e)) : B
      );
    }, E.jsxs = function(e, s, o) {
      var c = 1e4 > A.recentlyCreatedOwnerStacks++;
      if (c) {
        var g = Error.stackTraceLimit;
        Error.stackTraceLimit = 10;
        var f = Error("react-stack-top-frame");
        Error.stackTraceLimit = g;
      } else f = q;
      return z(
        e,
        s,
        o,
        !0,
        f,
        c ? O(d(e)) : B
      );
    };
  })()), E;
}
var J;
function Se() {
  return J || (J = 1, process.env.NODE_ENV === "production" ? I.exports = ve() : I.exports = ke()), I.exports;
}
var r = Se();
const Re = { 0: { text: "未读", color: "blue" }, 1: { text: "已读", color: "default" } };
function Ie({ userId: t }) {
  const [n, l] = j([]), [d, a] = j(!1), [i, p] = j(null);
  return X(() => {
    if (!t) return;
    let m = !1;
    return a(!0), v.inbox(t, 50).then((x) => {
      m || (l(x || []), p(null));
    }).catch((x) => {
      m || (l([]), p(x));
    }).finally(() => {
      m || a(!1);
    }), () => {
      m = !0;
    };
  }, [t]), t ? /* @__PURE__ */ r.jsxs("div", { children: [
    /* @__PURE__ */ r.jsx(
      G,
      {
        title: "站内信",
        description: `当前用户 ${t} 的收件箱（数据源 GET /api/msg/list?userId=${t}）`
      }
    ),
    i ? /* @__PURE__ */ r.jsx(xe, { children: /* @__PURE__ */ r.jsx(
      C,
      {
        type: "error",
        showIcon: !0,
        message: `站内信读取失败：${i.message || i}`
      }
    ) }) : (
      /* 收件箱一次取回最近 50 条、翻页在前端做 ⇒ 走基座 ListTable：
         分页/过滤/空态三件套由基座统一承接。 */
      /* @__PURE__ */ r.jsx(
        _e,
        {
          rows: n,
          rowKey: "id",
          loading: d,
          searchKeys: "title,content,event_type",
          searchPlaceholder: "按标题 / 内容 / 事件类型过滤",
          emptyTitle: "收件箱为空",
          emptyDescription: "接口返回空列表",
          columns: [
            { title: "标题", dataIndex: "title", key: "title" },
            { title: "内容", dataIndex: "content", key: "content", ellipsis: !0 },
            {
              title: "事件类型",
              dataIndex: "event_type",
              key: "event_type",
              width: 150,
              render: (m) => /* @__PURE__ */ r.jsx(T, { children: m })
            },
            {
              title: "已读",
              dataIndex: "is_read",
              key: "is_read",
              width: 90,
              render: (m) => {
                const x = Re[m] || { text: `未知(${m})`, color: "default" };
                return /* @__PURE__ */ r.jsx(T, { color: x.color, children: x.text });
              }
            },
            { title: "时间", dataIndex: "created_time", key: "created_time", width: 180 }
          ]
        }
      )
    )
  ] }) : /* @__PURE__ */ r.jsxs("div", { children: [
    /* @__PURE__ */ r.jsx(G, { title: "站内信" }),
    /* @__PURE__ */ r.jsx(
      C,
      {
        type: "warning",
        showIcon: !0,
        message: "未取到当前登录用户 id",
        description: "GET /api/msg/list 需要 userId。本页依次读 localStorage 的 userInfo 与 USER_INFO（staffId/jobNumber），两个都没有才会走到这里 —— 请重新登录。"
      }
    )
  ] });
}
function we() {
  return /* @__PURE__ */ r.jsx(
    L,
    {
      title: "消息模板",
      description: "按渠道/业务类型维护的消息模板（数据源 POST /api/msg/template/list）",
      load: (t, n) => v.templatePage(t, n),
      columns: [
        { title: "业务类型", dataIndex: "biz_type", key: "biz_type", width: 160 },
        {
          title: "渠道",
          dataIndex: "channel",
          key: "channel",
          width: 120,
          render: (t) => /* @__PURE__ */ r.jsx(T, { color: "geekblue", children: t })
        },
        { title: "主题", dataIndex: "subject", key: "subject", width: 220, ellipsis: !0 },
        { title: "正文模板", dataIndex: "content", key: "content", ellipsis: !0 },
        { title: "版本", dataIndex: "version", key: "version", width: 80 },
        {
          title: "状态",
          dataIndex: "status",
          key: "status",
          width: 90,
          render: (t) => {
            const n = je[t] || { text: `未知(${t})`, color: "default" };
            return /* @__PURE__ */ r.jsx(T, { color: n.color, children: n.text });
          }
        },
        { title: "更新时间", dataIndex: "updated_time", key: "updated_time", width: 180 }
      ]
    }
  );
}
function Pe() {
  const [t, n] = j(null), [l, d] = j(null);
  return X(() => {
    let a = !1;
    return v.deliveryStats().then((i) => {
      a || n(i);
    }).catch((i) => {
      a || d(i);
    }), () => {
      a = !0;
    };
  }, []), l ? /* @__PURE__ */ r.jsx(
    C,
    {
      type: "warning",
      showIcon: !0,
      style: { marginBottom: 12 },
      message: `投递统计读取失败：${l.message || l}`
    }
  ) : t ? /* @__PURE__ */ r.jsxs(ge, { gutter: 16, style: { marginBottom: 12 }, children: [
    /* @__PURE__ */ r.jsx(S, { span: 6, children: /* @__PURE__ */ r.jsx(R, { title: "投递总数", value: t.total ?? 0 }) }),
    /* @__PURE__ */ r.jsx(S, { span: 6, children: /* @__PURE__ */ r.jsx(R, { title: "成功", value: t.success ?? 0, valueStyle: { color: "#3f8600" } }) }),
    /* @__PURE__ */ r.jsx(S, { span: 6, children: /* @__PURE__ */ r.jsx(R, { title: "失败", value: t.failed ?? 0, valueStyle: { color: "#cf1322" } }) }),
    /* @__PURE__ */ r.jsx(S, { span: 6, children: /* @__PURE__ */ r.jsx(
      R,
      {
        title: "成功率",
        value: Number(t.total) > 0 ? (Number(t.success) * 100 / Number(t.total)).toFixed(1) : "0.0",
        suffix: "%"
      }
    ) })
  ] }) : null;
}
function Ae() {
  return /* @__PURE__ */ r.jsx(
    L,
    {
      title: "投递日志",
      description: "每条消息按渠道投递的结果与失败原因（数据源 POST /api/msg/delivery/list + GET /api/msg/delivery/stats）",
      load: (t, n) => v.deliveryPage(t, n),
      extra: /* @__PURE__ */ r.jsx(Pe, {}),
      columns: [
        { title: "业务类型", dataIndex: "biz_type", key: "biz_type", width: 140 },
        { title: "渠道", dataIndex: "channel", key: "channel", width: 110, render: (t) => /* @__PURE__ */ r.jsx(T, { children: t }) },
        { title: "接收方", dataIndex: "receiver", key: "receiver", width: 140 },
        { title: "主题", dataIndex: "rendered_subject", key: "rendered_subject", width: 180, ellipsis: !0 },
        {
          title: "状态",
          dataIndex: "status",
          key: "status",
          width: 100,
          render: (t) => {
            const n = he[t] || { text: `未知(${t})`, color: "default" };
            return /* @__PURE__ */ r.jsx(T, { color: n.color, children: n.text });
          }
        },
        {
          title: "失败原因",
          key: "reason",
          ellipsis: !0,
          render: (t, n) => n.error_code ? /* @__PURE__ */ r.jsxs("span", { style: { color: "#cf1322" }, children: [
            n.error_code,
            n.error_message ? ` · ${n.error_message}` : ""
          ] }) : "-"
        },
        { title: "重试", dataIndex: "retry_count", key: "retry_count", width: 70 },
        { title: "时间", dataIndex: "created_time", key: "created_time", width: 180 }
      ]
    }
  );
}
function Oe() {
  return /* @__PURE__ */ r.jsx(
    L,
    {
      title: "批量发送任务",
      description: "批量任务的进度与失败数（数据源 POST /api/msg/batch/list）",
      load: (t, n) => v.batchPage(t, n),
      columns: [
        { title: "业务类型", dataIndex: "biz_type", key: "biz_type", width: 150 },
        { title: "渠道", dataIndex: "channel", key: "channel", width: 110, render: (t) => /* @__PURE__ */ r.jsx(T, { children: t }) },
        {
          title: "进度",
          key: "progress",
          width: 160,
          render: (t, n) => {
            const l = Number(n.total_count) || 0, d = (Number(n.success_count) || 0) + (Number(n.failed_count) || 0);
            return /* @__PURE__ */ r.jsx(Te, { percent: l ? Math.round(d * 100 / l) : 0, size: "small" });
          }
        },
        { title: "总数", dataIndex: "total_count", key: "total_count", width: 70 },
        {
          title: "成功",
          dataIndex: "success_count",
          key: "success_count",
          width: 70,
          render: (t) => /* @__PURE__ */ r.jsx("span", { style: { color: "#3f8600" }, children: t })
        },
        {
          title: "失败",
          dataIndex: "failed_count",
          key: "failed_count",
          width: 70,
          render: (t) => Number(t) > 0 ? /* @__PURE__ */ r.jsx("span", { style: { color: "#cf1322" }, children: t }) : t
        },
        {
          title: "状态",
          dataIndex: "status",
          key: "status",
          width: 110,
          render: (t) => {
            const n = Ee[t] || { text: `未知(${t})`, color: "default" };
            return /* @__PURE__ */ r.jsx(T, { color: n.color, children: n.text });
          }
        },
        { title: "失败原因", dataIndex: "error_message", key: "error_message", ellipsis: !0, render: (t) => t || "-" },
        { title: "创建时间", dataIndex: "created_time", key: "created_time", width: 180 },
        { title: "结束时间", dataIndex: "finished_time", key: "finished_time", width: 180, render: (t) => t || "-" }
      ]
    }
  );
}
function $e() {
  const t = be();
  return /* @__PURE__ */ r.jsxs(me, { children: [
    /* @__PURE__ */ r.jsx(y, { index: !0, element: /* @__PURE__ */ r.jsx(fe, { to: "inbox", replace: !0 }) }),
    /* @__PURE__ */ r.jsx(y, { path: "inbox", element: /* @__PURE__ */ r.jsx(Ie, { userId: t }) }),
    /* @__PURE__ */ r.jsx(y, { path: "template", element: /* @__PURE__ */ r.jsx(we, {}) }),
    /* @__PURE__ */ r.jsx(y, { path: "delivery", element: /* @__PURE__ */ r.jsx(Ae, {}) }),
    /* @__PURE__ */ r.jsx(y, { path: "batch", element: /* @__PURE__ */ r.jsx(Oe, {}) })
  ] });
}
const Ne = "# z-msg-component\n\nz-msg（消息中心）跨仓唯一前端面：把「站内信收件箱 / 消息模板 / 投递记录 / 批量发送任务」\n这一整套消息页面收敛成一个可复用的 React 组件包。\n\n- **npm 包名**：`@yuku123/z-msg-component`\n- **宿主接入**：`@yuku123/z-msg-component/pages`（manifest + 页面），主壳可只取 `ComponentDocPage`\n- **后端**：z-opc 主进程 8888 的 `/api/msg/**`（路径已于 2026-09-24 经 `/actuator/mappings` 核对）\n- **技术栈**：React 19 + antd 6 + react-router-dom 7 + vite（library mode，双入口 `.` / `./pages`）\n\n---\n\n## 一、能力概览\n\n路由前缀 `/z-msg`，菜单与路由在 `src/pages-manifest.jsx` 里声明。\n\n| 页面 | 路由（相对 `/z-msg/*`） | 源文件 | 能力 |\n|------|--------------------------|--------|------|\n| 登录 | `login` | `pages/LoginPage.jsx` | 登录页（suit 侧单独挂这条路由） |\n| 首页 | `home` | `pages/HomePage.jsx` | 入口卡片页 |\n| 收件箱 | `inbox` | `pages/Inbox.jsx` | 当前登录用户的站内信列表（取 50 条） |\n| 消息模板 | `templates` | `pages/TemplateList.jsx` | 按渠道 / 业务类型维护消息模板（基座 `PagedTable`） |\n| 投递记录 | `delivery` | `pages/DeliveryLogs.jsx` | 投递统计（总数 / 成功 / 失败 / 成功率）+ 投递日志列表 |\n| 批量任务 | `batch` | `pages/BatchTasks.jsx` | 批量发送任务的进度与失败数（基座 `PagedTable`） |\n| 组件文档 | `docs` | `pages/ComponentDoc.jsx` | 本文件渲染页（左侧 240px TOC + 右侧正文） |\n\n`src/pages/MsgApp.jsx` 是子路由挂载点（`inbox` / `template` / `delivery` / `batch`），\n被 `routes` 里的 `/z-msg/:rest*` 承接，同时从包根具名导出（OpsWorkbench / SystemShell 以 `/msg/*` 挂它）。\n注意它内部的子路径用的是**单数**（`template`），manifest 顶层用的是**复数**（`templates`）—— 两条路都通。\n\n## 二、导出面\n\n```js\n// 主入口（. 桶）\nimport {msgApi, currentUserId, DELIVERY_STATUS, BATCH_STATUS, TEMPLATE_STATUS} from '@yuku123/z-msg-component'\nimport {MsgApp, ComponentDocPage} from '@yuku123/z-msg-component'\n\n// 宿主组装面（./pages 桶）\nimport {menuItems, routes, HomePage, LoginPage, TemplateList, DeliveryLogs} from '@yuku123/z-msg-component/pages'\n```\n\n⚠ `./pages` 桶是**显式具名转发**（不是 `export *`）。新增页面时必须回来补一行，否则 suit 打包会报\n`\"Xxx\" is not exported by dist/pages.js` —— 之前漏过 `HomePage` / `LoginPage`，直接让 suit 红了。\n\n## 三、API 端点\n\n| 方法 | 路径 | 说明 |\n|---|---|---|\n| GET | `/msg/list` | 站内信列表（query `userId` + `limit`） |\n| POST | `/msg/template/list` | 消息模板分页（body `page` / `size`） |\n| POST | `/msg/delivery/list` | 投递记录分页（body `page` / `size`） |\n| GET | `/msg/delivery/stats` | 投递统计（总数 / 成功 / 失败） |\n| POST | `/msg/batch/list` | 批量任务分页（body `page` / `size`） |\n\n两条容易踩的现实：\n\n- 列表类接口是 **POST + body**，分页参数名是 `page` / `size`（**不是** z-meta 那套 `pageNum` / `pageSize`）。\n- 站内信接口要求 `userId`，取当前登录用户（见下）。\n\n## 四、页面说明\n\n### 4.1 收件箱（`pages/Inbox.jsx`）\n\n拉当前登录用户的站内信（`limit=50`），空态文案「收件箱为空」。\n\n`userId` 取自 `currentUserId()`：依次读 localStorage 的 `userInfo`（取 `id` / `userId`）与\n`USER_INFO`（取 `staffId` / `jobNumber`）。**两个键都读不到时一个请求都不发**，页面提示\n「GET /api/msg/list 需要 userId … 请重新登录」。\n\n### 4.2 消息模板（`pages/TemplateList.jsx`）\n\n基座 `PagedTable` 的薄用法：`title` / `description` / `load` / `columns`。\n状态用 `TEMPLATE_STATUS` 映射（0 草稿 / 1 启用 / 2 停用）。\n\n### 4.3 投递记录（`pages/DeliveryLogs.jsx`）\n\n顶部四张统计卡（投递总数 / 成功 / 失败 / 成功率），下面接投递日志列表。\n状态用 `DELIVERY_STATUS` 映射（0 待发送 / 1 成功 / 2 失败 / 3 重试中）。\n\n### 4.4 批量任务（`pages/BatchTasks.jsx`）\n\n基座 `PagedTable` 的薄用法，展示批量任务的进度与失败数。状态用 `BATCH_STATUS` 映射\n（0 待执行 / 1 执行中 / 2 已完成 / 3 部分失败 / 4 失败）。\n\n### 4.5 组件文档\n\n本文件。内容以 `?raw` 在编译期内联，零额外请求；左侧 240px TOC 随滚动联动。\n\n## 五、配置\n\n| 配置项 | 默认值 | 说明 |\n|---|---|---|\n| axios 实例 | 基座 `request`（`createRequest()`） | `baseURL='/api'`，token 键 `token`，`Authorization: Bearer` 自动注入 |\n| 当前用户 id | `currentUserId()` | 依次读 `userInfo` → `USER_INFO`，都取不到返回 `null` |\n| 状态映射 | `DELIVERY_STATUS` / `BATCH_STATUS` / `TEMPLATE_STATUS` | 数字码 → 文案 + antd 色 |\n\n## 六、已知限制\n\n- ⚠ **`services/api.js` 必须用具名导入拿 axios 实例**：`import { request } from '@yuku123/z-frontend-common'`。\n  该包的默认导出是「桶对象」`{AppLayout, StatusTag, LoginPage, ErrorBoundary, request, authRequest, createRequest}`，\n  不是 axios 实例 —— 默认导入拿到的桶上没有 `.get` / `.post`，一调就抛\n  `TypeError: Xt.get is not a function`（线上 `/msg/inbox` 白屏就是这个，2026-10-20 实测）。\n- `routes` 数组里 `/z-msg/delivery` **出现了两次**（一条带 `title` / `order`，一条不带）。\n  React Router 按 `path` 建 key，重复项会产生重复 key 警告；建议后续合并成一条。\n- `routes` 里的 `title` / `order` 字段不是 react-router 的属性，是本仓自带的元信息，\n  宿主可用来给侧边栏排序。\n- `MsgApp` 的子路径是 `template`（单数），manifest 顶层是 `templates`（复数）—— 两条路径都能到模板页。\n- 收件箱固定取 50 条，没有分页。\n- `./pages` 桶是显式具名转发，新增页面**必须**补一行，否则消费方构建报错。\n- 组件包必须 external 基座；`vite.config.js` 的 `external` 是精确字符串匹配，\n  `@yuku123/z-frontend-common/ui` 与 `/ui/echarts` 两个子路径必须逐个列出。\n\n## 七、变更记录\n\n| 版本 | 变更 |\n|---|---|\n| 0.1.1 | 新增组件文档页 `ComponentDocPage`（lead 008 §7）：`src/DOC.md` + 基座 `ComponentDoc` 薄包装 |\n| 0.1.0 | 首版：消息中心页面集 + manifest + `msgApi` |\n";
function Ye() {
  return /* @__PURE__ */ r.jsx(ye, { markdown: Ne, title: "组件文档 — z-msg" });
}
export {
  Ee as B,
  Ye as C,
  he as D,
  Ie as I,
  $e as M,
  je as T,
  we as a,
  Ae as b,
  be as c,
  Oe as d,
  r as j,
  v as m
};
