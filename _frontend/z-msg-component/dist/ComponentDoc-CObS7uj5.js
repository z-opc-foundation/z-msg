import { Routes as de, Route as T, Navigate as me } from "react-router-dom";
import pe, { useState as E, useEffect as V } from "react";
import { Card as fe, Alert as H, Tag as g, Row as ge, Col as v, Statistic as S, Progress as xe } from "antd";
import { request as _ } from "@yuku123/z-frontend-common";
import { PageHeader as ye, ListTable as Te, PagedTable as N, ComponentDoc as _e } from "@yuku123/z-frontend-common/ui";
const j = {
  /**
   * 收件箱列表。
   *
   * ⚠ **旧契约 `/msg/list?userId=&limit=` 已于 z-msg 1.1.0 下线** ⇒ 继续打必然 404
   *   （2026-10-23 生产实测：收件箱页 404 3/3）。
   * ⚠ **新契约 `/msg/inbox/list` 不接受 userId**：身份由宿主注册的 `MsgPrincipalResolver`
   *   从 SSO 凭证里解出来，服务端在 SQL 的 WHERE 里做归属过滤。
   *   ⇒ **不许把 userId 塞回 query/header**：那是 z-msg 1.0.0「改一下 query 就能读别人收件箱」
   *   的越权成因，1.1.0 起服务端已不再采信客户端自报的身份。
   * ⚠ `size` 缺省为 0，服务端会 clamp 到 `z-msg.inbox.default-page-size`；
   *   这里显式给 50，避免依赖服务端默认。
   */
  inbox: (t = 1, r = 50) => _.get("/msg/inbox/list", { params: { page: t, size: r } }),
  templatePage: (t, r) => _.post("/msg/template/list", { page: t, size: r }),
  deliveryPage: (t, r) => _.post("/msg/delivery/list", { page: t, size: r }),
  deliveryStats: () => _.get("/msg/delivery/stats"),
  batchPage: (t, r) => _.post("/msg/batch/list", { page: t, size: r })
};
function Ye() {
  const t = (i) => {
    try {
      return JSON.parse(localStorage.getItem(i) || "null");
    } catch {
      return null;
    }
  }, r = t("userInfo"), u = r && (r.id ?? r.userId);
  if (u != null && u !== "") return String(u);
  const m = t("USER_INFO"), a = m && (m.staffId ?? m.jobNumber);
  return a == null || a === "" ? null : String(a);
}
const be = {
  0: { text: "待发送", color: "default" },
  1: { text: "成功", color: "success" },
  2: { text: "失败", color: "error" },
  3: { text: "重试中", color: "processing" }
}, he = {
  0: { text: "待执行", color: "default" },
  1: { text: "执行中", color: "processing" },
  2: { text: "已完成", color: "success" },
  3: { text: "部分失败", color: "warning" },
  4: { text: "失败", color: "error" }
}, Ee = {
  0: { text: "草稿", color: "default" },
  1: { text: "启用", color: "success" },
  2: { text: "停用", color: "warning" }
};
var R = { exports: {} }, b = {};
/**
 * @license React
 * react-jsx-runtime.production.js
 *
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
var B;
function je() {
  if (B) return b;
  B = 1;
  var t = Symbol.for("react.transitional.element"), r = Symbol.for("react.fragment");
  function u(m, a, i) {
    var d = null;
    if (i !== void 0 && (d = "" + i), a.key !== void 0 && (d = "" + a.key), "key" in a) {
      i = {};
      for (var p in a)
        p !== "key" && (i[p] = a[p]);
    } else i = a;
    return a = i.ref, {
      $$typeof: t,
      type: m,
      key: d,
      ref: a !== void 0 ? a : null,
      props: i
    };
  }
  return b.Fragment = r, b.jsx = u, b.jsxs = u, b;
}
var h = {};
/**
 * @license React
 * react-jsx-runtime.development.js
 *
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
var W;
function ke() {
  return W || (W = 1, process.env.NODE_ENV !== "production" && (function() {
    function t(e) {
      if (e == null) return null;
      if (typeof e == "function")
        return e.$$typeof === le ? null : e.displayName || e.name || null;
      if (typeof e == "string") return e;
      switch (e) {
        case A:
          return "Fragment";
        case K:
          return "Profiler";
        case Z:
          return "StrictMode";
        case ne:
          return "Suspense";
        case se:
          return "SuspenseList";
        case oe:
          return "Activity";
        case ie:
          return "ViewTransition";
      }
      if (typeof e == "object")
        switch (typeof e.tag == "number" && console.error(
          "Received an unexpected object in getComponentNameFromType(). This is likely a bug in React. Please file an issue."
        ), e.$$typeof) {
          case Q:
            return "Portal";
          case te:
            return e.displayName || "Context";
          case ee:
            return (e._context.displayName || "Context") + ".Consumer";
          case re:
            var s = e.render;
            return e = e.displayName, e || (e = s.displayName || s.name || "", e = e !== "" ? "ForwardRef(" + e + ")" : "ForwardRef"), e;
          case ae:
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
    function r(e) {
      return "" + e;
    }
    function u(e) {
      try {
        r(e);
        var s = !1;
      } catch {
        s = !0;
      }
      if (s) {
        s = console;
        var o = s.error, l = typeof Symbol == "function" && Symbol.toStringTag && e[Symbol.toStringTag] || e.constructor.name || "Object";
        return o.call(
          s,
          "The provided key is an unsupported type %s. This value must be coerced to a string before using it here.",
          l
        ), r(e);
      }
    }
    function m(e) {
      if (e === A) return "<>";
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
      var e = w.A;
      return e === null ? null : e.getOwner();
    }
    function i() {
      return Error("react-stack-top-frame");
    }
    function d(e) {
      if (U.call(e, "key")) {
        var s = Object.getOwnPropertyDescriptor(e, "key").get;
        if (s && s.isReactWarning) return !1;
      }
      return e.key !== void 0;
    }
    function p(e, s) {
      function o() {
        Y || (Y = !0, console.error(
          "%s: `key` is not a prop. Trying to access it will result in `undefined` being returned. If you need to access the same value within the child component, you should pass it as a different prop. (https://react.dev/link/special-props)",
          s
        ));
      }
      o.isReactWarning = !0, Object.defineProperty(e, "key", {
        get: o,
        configurable: !0
      });
    }
    function J() {
      var e = t(this.type);
      return M[e] || (M[e] = !0, console.error(
        "Accessing element.ref was removed in React 19. ref is now a regular prop. It will be removed from the JSX Element type in a future release."
      )), e = this.props.ref, e !== void 0 ? e : null;
    }
    function X(e, s, o, l, x, f) {
      var c = o.ref;
      return e = {
        $$typeof: D,
        type: e,
        key: s,
        props: o,
        _owner: l
      }, (c !== void 0 ? c : null) !== null ? Object.defineProperty(e, "ref", {
        enumerable: !1,
        get: J
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
        value: x
      }), Object.defineProperty(e, "_debugTask", {
        configurable: !1,
        enumerable: !1,
        writable: !0,
        value: f
      }), Object.freeze && (Object.freeze(e.props), Object.freeze(e)), e;
    }
    function L(e, s, o, l, x, f) {
      var c = s.children;
      if (c !== void 0)
        if (l)
          if (ce(c)) {
            for (l = 0; l < c.length; l++)
              C(c[l]);
            Object.freeze && Object.freeze(c);
          } else
            console.error(
              "React.jsx: Static children should always be an array. You are likely explicitly calling React.jsxs or React.jsxDEV. Use the Babel transform instead."
            );
        else C(c);
      if (U.call(s, "key")) {
        c = t(e);
        var y = Object.keys(s).filter(function(ue) {
          return ue !== "key";
        });
        l = 0 < y.length ? "{key: someKey, " + y.join(": ..., ") + ": ...}" : "{key: someKey}", F[c + l] || (y = 0 < y.length ? "{" + y.join(": ..., ") + ": ...}" : "{}", console.error(
          `A props object containing a "key" prop is being spread into JSX:
  let props = %s;
  <%s {...props} />
React keys must be passed directly to JSX without using spread:
  let props = %s;
  <%s key={someKey} {...props} />`,
          l,
          c,
          y,
          c
        ), F[c + l] = !0);
      }
      if (c = null, o !== void 0 && (u(o), c = "" + o), d(s) && (u(s.key), c = "" + s.key), "key" in s) {
        o = {};
        for (var O in s)
          O !== "key" && (o[O] = s[O]);
      } else o = s;
      return c && p(
        o,
        typeof e == "function" ? e.displayName || e.name || "Unknown" : e
      ), X(
        e,
        c,
        o,
        a(),
        x,
        f
      );
    }
    function C(e) {
      z(e) ? e._store && (e._store.validated = 1) : typeof e == "object" && e !== null && e.$$typeof === P && (e._payload.status === "fulfilled" ? z(e._payload.value) && e._payload.value._store && (e._payload.value._store.validated = 1) : e._store && (e._store.validated = 1));
    }
    function z(e) {
      return typeof e == "object" && e !== null && e.$$typeof === D;
    }
    var k = pe, D = Symbol.for("react.transitional.element"), Q = Symbol.for("react.portal"), A = Symbol.for("react.fragment"), Z = Symbol.for("react.strict_mode"), K = Symbol.for("react.profiler"), ee = Symbol.for("react.consumer"), te = Symbol.for("react.context"), re = Symbol.for("react.forward_ref"), ne = Symbol.for("react.suspense"), se = Symbol.for("react.suspense_list"), ae = Symbol.for("react.memo"), P = Symbol.for("react.lazy"), oe = Symbol.for("react.activity"), ie = Symbol.for("react.view_transition"), le = Symbol.for("react.client.reference"), w = k.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE, U = Object.prototype.hasOwnProperty, ce = Array.isArray, I = console.createTask ? console.createTask : function() {
      return null;
    };
    k = {
      react_stack_bottom_frame: function(e) {
        return e();
      }
    };
    var Y, M = {}, $ = k.react_stack_bottom_frame.bind(
      k,
      i
    )(), q = I(m(i)), F = {};
    h.Fragment = A, h.jsx = function(e, s, o) {
      var l = 1e4 > w.recentlyCreatedOwnerStacks++;
      if (l) {
        var x = Error.stackTraceLimit;
        Error.stackTraceLimit = 10;
        var f = Error("react-stack-top-frame");
        Error.stackTraceLimit = x;
      } else f = $;
      return L(
        e,
        s,
        o,
        !1,
        f,
        l ? I(m(e)) : q
      );
    }, h.jsxs = function(e, s, o) {
      var l = 1e4 > w.recentlyCreatedOwnerStacks++;
      if (l) {
        var x = Error.stackTraceLimit;
        Error.stackTraceLimit = 10;
        var f = Error("react-stack-top-frame");
        Error.stackTraceLimit = x;
      } else f = $;
      return L(
        e,
        s,
        o,
        !0,
        f,
        l ? I(m(e)) : q
      );
    };
  })()), h;
}
var G;
function ve() {
  return G || (G = 1, process.env.NODE_ENV === "production" ? R.exports = je() : R.exports = ke()), R.exports;
}
var n = ve();
const Se = { 0: { text: "未读", color: "blue" }, 1: { text: "已读", color: "default" } };
function Re(t) {
  return Array.isArray(t) ? t : t && Array.isArray(t.records) ? t.records : [];
}
function Ae() {
  const [t, r] = E([]), [u, m] = E(!1), [a, i] = E(null);
  return V(() => {
    let d = !1;
    return m(!0), j.inbox(1, 50).then((p) => {
      d || (r(Re(p)), i(null));
    }).catch((p) => {
      d || (r([]), i(p));
    }).finally(() => {
      d || m(!1);
    }), () => {
      d = !0;
    };
  }, []), /* @__PURE__ */ n.jsxs("div", { children: [
    /* @__PURE__ */ n.jsx(
      ye,
      {
        title: "站内信",
        description: "当前登录用户的收件箱（数据源 GET /api/msg/inbox/list，身份由服务端从 SSO 凭证解析）"
      }
    ),
    a ? /* @__PURE__ */ n.jsx(fe, { children: /* @__PURE__ */ n.jsx(
      H,
      {
        type: "error",
        showIcon: !0,
        message: `站内信读取失败：${a.message || a}`
      }
    ) }) : (
      /* 收件箱一次取回最近 50 条、翻页在前端做 ⇒ 走基座 ListTable：
         分页/过滤/空态三件套由基座统一承接。
         ⚠ 列里**没有 content**：新契约的服务端投影刻意不返回正文
           （省掉一列 BIGTEXT，换来的是收件箱页不再渲染空列）。 */
      /* @__PURE__ */ n.jsx(
        Te,
        {
          rows: t,
          rowKey: "id",
          loading: u,
          searchKeys: "title,event_type,msg_type",
          searchPlaceholder: "按标题 / 事件类型 / 消息大类过滤",
          emptyTitle: "收件箱为空",
          emptyDescription: "接口返回空列表",
          columns: [
            { title: "标题", dataIndex: "title", key: "title" },
            {
              title: "消息大类",
              dataIndex: "msg_type",
              key: "msg_type",
              width: 120,
              render: (d) => d ? /* @__PURE__ */ n.jsx(g, { color: "geekblue", children: d }) : "-"
            },
            {
              title: "事件类型",
              dataIndex: "event_type",
              key: "event_type",
              width: 150,
              render: (d) => /* @__PURE__ */ n.jsx(g, { children: d })
            },
            {
              title: "已读",
              dataIndex: "is_read",
              key: "is_read",
              width: 90,
              render: (d) => {
                const p = Se[d] || { text: `未知(${d})`, color: "default" };
                return /* @__PURE__ */ n.jsx(g, { color: p.color, children: p.text });
              }
            },
            { title: "时间", dataIndex: "created_time", key: "created_time", width: 180 }
          ]
        }
      )
    )
  ] });
}
function Pe() {
  return /* @__PURE__ */ n.jsx(
    N,
    {
      title: "消息模板",
      description: "按渠道/业务类型维护的消息模板（数据源 POST /api/msg/template/list）",
      load: (t, r) => j.templatePage(t, r),
      columns: [
        { title: "业务类型", dataIndex: "biz_type", key: "biz_type", width: 160 },
        {
          title: "渠道",
          dataIndex: "channel",
          key: "channel",
          width: 120,
          render: (t) => /* @__PURE__ */ n.jsx(g, { color: "geekblue", children: t })
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
            const r = Ee[t] || { text: `未知(${t})`, color: "default" };
            return /* @__PURE__ */ n.jsx(g, { color: r.color, children: r.text });
          }
        },
        { title: "更新时间", dataIndex: "updated_time", key: "updated_time", width: 180 }
      ]
    }
  );
}
function we() {
  const [t, r] = E(null), [u, m] = E(null);
  return V(() => {
    let a = !1;
    return j.deliveryStats().then((i) => {
      a || r(i);
    }).catch((i) => {
      a || m(i);
    }), () => {
      a = !0;
    };
  }, []), u ? /* @__PURE__ */ n.jsx(
    H,
    {
      type: "warning",
      showIcon: !0,
      style: { marginBottom: 12 },
      message: `投递统计读取失败：${u.message || u}`
    }
  ) : t ? /* @__PURE__ */ n.jsxs(ge, { gutter: 16, style: { marginBottom: 12 }, children: [
    /* @__PURE__ */ n.jsx(v, { span: 6, children: /* @__PURE__ */ n.jsx(S, { title: "投递总数", value: t.total ?? 0 }) }),
    /* @__PURE__ */ n.jsx(v, { span: 6, children: /* @__PURE__ */ n.jsx(S, { title: "成功", value: t.success ?? 0, valueStyle: { color: "#3f8600" } }) }),
    /* @__PURE__ */ n.jsx(v, { span: 6, children: /* @__PURE__ */ n.jsx(S, { title: "失败", value: t.failed ?? 0, valueStyle: { color: "#cf1322" } }) }),
    /* @__PURE__ */ n.jsx(v, { span: 6, children: /* @__PURE__ */ n.jsx(
      S,
      {
        title: "成功率",
        value: Number(t.total) > 0 ? (Number(t.success) * 100 / Number(t.total)).toFixed(1) : "0.0",
        suffix: "%"
      }
    ) })
  ] }) : null;
}
function Ie() {
  return /* @__PURE__ */ n.jsx(
    N,
    {
      title: "投递日志",
      description: "每条消息按渠道投递的结果与失败原因（数据源 POST /api/msg/delivery/list + GET /api/msg/delivery/stats）",
      load: (t, r) => j.deliveryPage(t, r),
      extra: /* @__PURE__ */ n.jsx(we, {}),
      columns: [
        { title: "业务类型", dataIndex: "biz_type", key: "biz_type", width: 140 },
        { title: "渠道", dataIndex: "channel", key: "channel", width: 110, render: (t) => /* @__PURE__ */ n.jsx(g, { children: t }) },
        { title: "接收方", dataIndex: "receiver", key: "receiver", width: 140 },
        { title: "主题", dataIndex: "rendered_subject", key: "rendered_subject", width: 180, ellipsis: !0 },
        {
          title: "状态",
          dataIndex: "status",
          key: "status",
          width: 100,
          render: (t) => {
            const r = be[t] || { text: `未知(${t})`, color: "default" };
            return /* @__PURE__ */ n.jsx(g, { color: r.color, children: r.text });
          }
        },
        {
          title: "失败原因",
          key: "reason",
          ellipsis: !0,
          render: (t, r) => r.error_code ? /* @__PURE__ */ n.jsxs("span", { style: { color: "#cf1322" }, children: [
            r.error_code,
            r.error_message ? ` · ${r.error_message}` : ""
          ] }) : "-"
        },
        { title: "重试", dataIndex: "retry_count", key: "retry_count", width: 70 },
        { title: "时间", dataIndex: "created_time", key: "created_time", width: 180 }
      ]
    }
  );
}
function Oe() {
  return /* @__PURE__ */ n.jsx(
    N,
    {
      title: "批量发送任务",
      description: "批量任务的进度与失败数（数据源 POST /api/msg/batch/list）",
      load: (t, r) => j.batchPage(t, r),
      columns: [
        { title: "业务类型", dataIndex: "biz_type", key: "biz_type", width: 150 },
        { title: "渠道", dataIndex: "channel", key: "channel", width: 110, render: (t) => /* @__PURE__ */ n.jsx(g, { children: t }) },
        {
          title: "进度",
          key: "progress",
          width: 160,
          render: (t, r) => {
            const u = Number(r.total_count) || 0, m = (Number(r.success_count) || 0) + (Number(r.failed_count) || 0);
            return /* @__PURE__ */ n.jsx(xe, { percent: u ? Math.round(m * 100 / u) : 0, size: "small" });
          }
        },
        { title: "总数", dataIndex: "total_count", key: "total_count", width: 70 },
        {
          title: "成功",
          dataIndex: "success_count",
          key: "success_count",
          width: 70,
          render: (t) => /* @__PURE__ */ n.jsx("span", { style: { color: "#3f8600" }, children: t })
        },
        {
          title: "失败",
          dataIndex: "failed_count",
          key: "failed_count",
          width: 70,
          render: (t) => Number(t) > 0 ? /* @__PURE__ */ n.jsx("span", { style: { color: "#cf1322" }, children: t }) : t
        },
        {
          title: "状态",
          dataIndex: "status",
          key: "status",
          width: 110,
          render: (t) => {
            const r = he[t] || { text: `未知(${t})`, color: "default" };
            return /* @__PURE__ */ n.jsx(g, { color: r.color, children: r.text });
          }
        },
        { title: "失败原因", dataIndex: "error_message", key: "error_message", ellipsis: !0, render: (t) => t || "-" },
        { title: "创建时间", dataIndex: "created_time", key: "created_time", width: 180 },
        { title: "结束时间", dataIndex: "finished_time", key: "finished_time", width: 180, render: (t) => t || "-" }
      ]
    }
  );
}
function Me() {
  return /* @__PURE__ */ n.jsxs(de, { children: [
    /* @__PURE__ */ n.jsx(T, { index: !0, element: /* @__PURE__ */ n.jsx(me, { to: "inbox", replace: !0 }) }),
    /* @__PURE__ */ n.jsx(T, { path: "inbox", element: /* @__PURE__ */ n.jsx(Ae, {}) }),
    /* @__PURE__ */ n.jsx(T, { path: "template", element: /* @__PURE__ */ n.jsx(Pe, {}) }),
    /* @__PURE__ */ n.jsx(T, { path: "delivery", element: /* @__PURE__ */ n.jsx(Ie, {}) }),
    /* @__PURE__ */ n.jsx(T, { path: "batch", element: /* @__PURE__ */ n.jsx(Oe, {}) })
  ] });
}
const Ne = "# z-msg-component\n\nz-msg（消息中心）跨仓唯一前端面：把「站内信收件箱 / 消息模板 / 投递记录 / 批量发送任务」\n这一整套消息页面收敛成一个可复用的 React 组件包。\n\n- **npm 包名**：`@yuku123/z-msg-component`\n- **宿主接入**：`@yuku123/z-msg-component/pages`（manifest + 页面），主壳可只取 `ComponentDocPage`\n- **后端**：z-opc 主进程 8888 的 `/api/msg/**`（路径已于 2026-09-24 经 `/actuator/mappings` 核对）\n- **技术栈**：React 19 + antd 6 + react-router-dom 7 + vite（library mode，双入口 `.` / `./pages`）\n\n---\n\n## 一、能力概览\n\n路由前缀 `/z-msg`，菜单与路由在 `src/pages-manifest.jsx` 里声明。\n\n| 页面 | 路由（相对 `/z-msg/*`） | 源文件 | 能力 |\n|------|--------------------------|--------|------|\n| 登录 | `login` | `pages/LoginPage.jsx` | 登录页（suit 侧单独挂这条路由） |\n| 首页 | `home` | `pages/HomePage.jsx` | 入口卡片页 |\n| 收件箱 | `inbox` | `pages/Inbox.jsx` | 当前登录用户的站内信列表（取 50 条） |\n| 消息模板 | `templates` | `pages/TemplateList.jsx` | 按渠道 / 业务类型维护消息模板（基座 `PagedTable`） |\n| 投递记录 | `delivery` | `pages/DeliveryLogs.jsx` | 投递统计（总数 / 成功 / 失败 / 成功率）+ 投递日志列表 |\n| 批量任务 | `batch` | `pages/BatchTasks.jsx` | 批量发送任务的进度与失败数（基座 `PagedTable`） |\n| 组件文档 | `docs` | `pages/ComponentDoc.jsx` | 本文件渲染页（左侧 240px TOC + 右侧正文） |\n\n`src/pages/MsgApp.jsx` 是子路由挂载点（`inbox` / `template` / `delivery` / `batch`），\n被 `routes` 里的 `/z-msg/:rest*` 承接，同时从包根具名导出（OpsWorkbench / SystemShell 以 `/msg/*` 挂它）。\n注意它内部的子路径用的是**单数**（`template`），manifest 顶层用的是**复数**（`templates`）—— 两条路都通。\n\n## 二、导出面\n\n```js\n// 主入口（. 桶）\nimport {msgApi, currentUserId, DELIVERY_STATUS, BATCH_STATUS, TEMPLATE_STATUS} from '@yuku123/z-msg-component'\nimport {MsgApp, ComponentDocPage} from '@yuku123/z-msg-component'\n\n// 宿主组装面（./pages 桶）\nimport {menuItems, routes, HomePage, LoginPage, TemplateList, DeliveryLogs} from '@yuku123/z-msg-component/pages'\n```\n\n⚠ `./pages` 桶是**显式具名转发**（不是 `export *`）。新增页面时必须回来补一行，否则 suit 打包会报\n`\"Xxx\" is not exported by dist/pages.js` —— 之前漏过 `HomePage` / `LoginPage`，直接让 suit 红了。\n\n## 三、API 端点\n\n| 方法 | 路径 | 说明 |\n|---|---|---|\n| GET | `/msg/inbox/list` | 站内信列表（query `page` + `size`，**不带 userId**） |\n| POST | `/msg/template/list` | 消息模板分页（body `page` / `size`） |\n| POST | `/msg/delivery/list` | 投递记录分页（body `page` / `size`） |\n| GET | `/msg/delivery/stats` | 投递统计（总数 / 成功 / 失败） |\n| POST | `/msg/batch/list` | 批量任务分页（body `page` / `size`） |\n\n两条容易踩的现实：\n\n- 列表类接口是 **POST + body**，分页参数名是 `page` / `size`（**不是** z-meta 那套 `pageNum` / `pageSize`）。\n- 站内信接口**不带 `userId`**：身份由服务端从 SSO 凭证解析（宿主注册的 `MsgPrincipalResolver`），\n  服务端在 SQL 的 WHERE 里做归属过滤。⚠ **不要**把 `userId` 塞回 query —— 那是 z-msg 1.0.0\n  「改一下 query 就能读别人收件箱」的越权成因；旧路径 `/msg/list` 也已于 1.1.0 下线（生产实测 404）。\n\n## 四、页面说明\n\n### 4.1 收件箱（`pages/Inbox.jsx`）\n\n拉当前登录用户的站内信（`page=1` / `size=50`），空态文案「收件箱为空」。\n\n⚠ **本页不传 `userId`**（2026-10-23 起）：请求打 `GET /api/msg/inbox/list`，\n身份由服务端从 SSO 凭证解析；客户端自报身份不再被采信。\n响应是 `Result<IPage<InAppMessage>>`，页面取 `data.records` 渲染。\n⚠ 列表**没有正文列**：新契约的服务端投影刻意不返回 `content`（省一列 BIGTEXT）。\n\n### 4.2 消息模板（`pages/TemplateList.jsx`）\n\n基座 `PagedTable` 的薄用法：`title` / `description` / `load` / `columns`。\n状态用 `TEMPLATE_STATUS` 映射（0 草稿 / 1 启用 / 2 停用）。\n\n### 4.3 投递记录（`pages/DeliveryLogs.jsx`）\n\n顶部四张统计卡（投递总数 / 成功 / 失败 / 成功率），下面接投递日志列表。\n状态用 `DELIVERY_STATUS` 映射（0 待发送 / 1 成功 / 2 失败 / 3 重试中）。\n\n### 4.4 批量任务（`pages/BatchTasks.jsx`）\n\n基座 `PagedTable` 的薄用法，展示批量任务的进度与失败数。状态用 `BATCH_STATUS` 映射\n（0 待执行 / 1 执行中 / 2 已完成 / 3 部分失败 / 4 失败）。\n\n### 4.5 组件文档\n\n本文件。内容以 `?raw` 在编译期内联，零额外请求；左侧 240px TOC 随滚动联动。\n\n## 五、配置\n\n| 配置项 | 默认值 | 说明 |\n|---|---|---|\n| axios 实例 | 基座 `request`（`createRequest()`） | `baseURL='/api'`，token 键 `token`，`Authorization: Bearer` 自动注入 |\n| 当前用户 id | `currentUserId()`（**已废弃**） | 依次读 `userInfo` → `USER_INFO`，都取不到返回 `null`；收件箱不再使用它 |\n| 状态映射 | `DELIVERY_STATUS` / `BATCH_STATUS` / `TEMPLATE_STATUS` | 数字码 → 文案 + antd 色 |\n\n## 六、已知限制\n\n- ⚠ **`services/api.js` 必须用具名导入拿 axios 实例**：`import { request } from '@yuku123/z-frontend-common'`。\n  该包的默认导出是「桶对象」`{AppLayout, StatusTag, LoginPage, ErrorBoundary, request, authRequest, createRequest}`，\n  不是 axios 实例 —— 默认导入拿到的桶上没有 `.get` / `.post`，一调就抛\n  `TypeError: Xt.get is not a function`（线上 `/msg/inbox` 白屏就是这个，2026-10-20 实测）。\n- `routes` 数组里 `/z-msg/delivery` **出现了两次**（一条带 `title` / `order`，一条不带）。\n  React Router 按 `path` 建 key，重复项会产生重复 key 警告；建议后续合并成一条。\n- `routes` 里的 `title` / `order` 字段不是 react-router 的属性，是本仓自带的元信息，\n  宿主可用来给侧边栏排序。\n- `MsgApp` 的子路径是 `template`（单数），manifest 顶层是 `templates`（复数）—— 两条路径都能到模板页。\n- 收件箱固定取 50 条，没有分页。\n- `./pages` 桶是显式具名转发，新增页面**必须**补一行，否则消费方构建报错。\n- 组件包必须 external 基座；`vite.config.js` 的 `external` 是精确字符串匹配，\n  `@yuku123/z-frontend-common/ui` 与 `/ui/echarts` 两个子路径必须逐个列出。\n\n## 七、变更记录\n\n| 版本 | 变更 |\n|---|---|\n| 0.1.1 | 新增组件文档页 `ComponentDocPage`（lead 008 §7）：`src/DOC.md` + 基座 `ComponentDoc` 薄包装 |\n| 0.1.0 | 首版：消息中心页面集 + manifest + `msgApi` |\n";
function $e() {
  return /* @__PURE__ */ n.jsx(_e, { markdown: Ne, title: "组件文档 — z-msg" });
}
export {
  he as B,
  $e as C,
  be as D,
  Ae as I,
  Me as M,
  Ee as T,
  Pe as a,
  Ie as b,
  Ye as c,
  Oe as d,
  n as j,
  j as m
};
