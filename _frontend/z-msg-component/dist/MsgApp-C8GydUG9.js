import fe, { useState as j, useEffect as H } from "react";
import { Routes as me, Route as b, Navigate as xe } from "react-router-dom";
import { Alert as C, Card as pe, Tag as h, Row as _e, Col as R, Statistic as w, Progress as he } from "antd";
import { request as E } from "@yuku123/z-frontend-common";
import { PageHeader as J, ListTable as ye, PagedTable as $ } from "@yuku123/z-frontend-common/ui";
const g = {
  inbox: (t, n) => E.get("/msg/list", { params: { userId: t, limit: n } }),
  templatePage: (t, n) => E.post("/msg/template/list", { page: t, size: n }),
  deliveryPage: (t, n) => E.post("/msg/delivery/list", { page: t, size: n }),
  deliveryStats: () => E.get("/msg/delivery/stats"),
  batchPage: (t, n) => E.post("/msg/batch/list", { page: t, size: n })
};
function be() {
  const t = (l) => {
    try {
      return JSON.parse(localStorage.getItem(l) || "null");
    } catch {
      return null;
    }
  }, n = t("userInfo"), i = n && (n.id ?? n.userId);
  if (i != null && i !== "") return String(i);
  const d = t("USER_INFO"), s = d && (d.staffId ?? d.jobNumber);
  return s == null || s === "" ? null : String(s);
}
const Ee = {
  0: { text: "待发送", color: "default" },
  1: { text: "成功", color: "success" },
  2: { text: "失败", color: "error" },
  3: { text: "重试中", color: "processing" }
}, Te = {
  0: { text: "待执行", color: "default" },
  1: { text: "执行中", color: "processing" },
  2: { text: "已完成", color: "success" },
  3: { text: "部分失败", color: "warning" },
  4: { text: "失败", color: "error" }
}, ve = {
  0: { text: "草稿", color: "default" },
  1: { text: "启用", color: "success" },
  2: { text: "停用", color: "warning" }
};
var S = { exports: {} }, T = {};
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
function je() {
  if (V) return T;
  V = 1;
  var t = Symbol.for("react.transitional.element"), n = Symbol.for("react.fragment");
  function i(d, s, l) {
    var x = null;
    if (l !== void 0 && (x = "" + l), s.key !== void 0 && (x = "" + s.key), "key" in s) {
      l = {};
      for (var f in s)
        f !== "key" && (l[f] = s[f]);
    } else l = s;
    return s = l.ref, {
      $$typeof: t,
      type: d,
      key: x,
      ref: s !== void 0 ? s : null,
      props: l
    };
  }
  return T.Fragment = n, T.jsx = i, T.jsxs = i, T;
}
var v = {};
/**
 * @license React
 * react-jsx-runtime.development.js
 *
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
var B;
function ge() {
  return B || (B = 1, process.env.NODE_ENV !== "production" && (function() {
    function t(e) {
      if (e == null) return null;
      if (typeof e == "function")
        return e.$$typeof === ce ? null : e.displayName || e.name || null;
      if (typeof e == "string") return e;
      switch (e) {
        case I:
          return "Fragment";
        case ee:
          return "Profiler";
        case K:
          return "StrictMode";
        case ae:
          return "Suspense";
        case se:
          return "SuspenseList";
        case le:
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
          case re:
            return e.displayName || "Context";
          case te:
            return (e._context.displayName || "Context") + ".Consumer";
          case ne:
            var a = e.render;
            return e = e.displayName, e || (e = a.displayName || a.name || "", e = e !== "" ? "ForwardRef(" + e + ")" : "ForwardRef"), e;
          case oe:
            return a = e.displayName || null, a !== null ? a : t(e.type) || "Memo";
          case P:
            a = e._payload, e = e._init;
            try {
              return t(e(a));
            } catch {
            }
        }
      return null;
    }
    function n(e) {
      return "" + e;
    }
    function i(e) {
      try {
        n(e);
        var a = !1;
      } catch {
        a = !0;
      }
      if (a) {
        a = console;
        var o = a.error, c = typeof Symbol == "function" && Symbol.toStringTag && e[Symbol.toStringTag] || e.constructor.name || "Object";
        return o.call(
          a,
          "The provided key is an unsupported type %s. This value must be coerced to a string before using it here.",
          c
        ), n(e);
      }
    }
    function d(e) {
      if (e === I) return "<>";
      if (typeof e == "object" && e !== null && e.$$typeof === P)
        return "<...>";
      try {
        var a = t(e);
        return a ? "<" + a + ">" : "<...>";
      } catch {
        return "<...>";
      }
    }
    function s() {
      var e = A.A;
      return e === null ? null : e.getOwner();
    }
    function l() {
      return Error("react-stack-top-frame");
    }
    function x(e) {
      if (D.call(e, "key")) {
        var a = Object.getOwnPropertyDescriptor(e, "key").get;
        if (a && a.isReactWarning) return !1;
      }
      return e.key !== void 0;
    }
    function f(e, a) {
      function o() {
        M || (M = !0, console.error(
          "%s: `key` is not a prop. Trying to access it will result in `undefined` being returned. If you need to access the same value within the child component, you should pass it as a different prop. (https://react.dev/link/special-props)",
          a
        ));
      }
      o.isReactWarning = !0, Object.defineProperty(e, "key", {
        get: o,
        configurable: !0
      });
    }
    function p() {
      var e = t(this.type);
      return z[e] || (z[e] = !0, console.error(
        "Accessing element.ref was removed in React 19. ref is now a regular prop. It will be removed from the JSX Element type in a future release."
      )), e = this.props.ref, e !== void 0 ? e : null;
    }
    function Z(e, a, o, c, _, m) {
      var u = o.ref;
      return e = {
        $$typeof: U,
        type: e,
        key: a,
        props: o,
        _owner: c
      }, (u !== void 0 ? u : null) !== null ? Object.defineProperty(e, "ref", {
        enumerable: !1,
        get: p
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
        value: _
      }), Object.defineProperty(e, "_debugTask", {
        configurable: !1,
        enumerable: !1,
        writable: !0,
        value: m
      }), Object.freeze && (Object.freeze(e.props), Object.freeze(e)), e;
    }
    function L(e, a, o, c, _, m) {
      var u = a.children;
      if (u !== void 0)
        if (c)
          if (ue(u)) {
            for (c = 0; c < u.length; c++)
              Y(u[c]);
            Object.freeze && Object.freeze(u);
          } else
            console.error(
              "React.jsx: Static children should always be an array. You are likely explicitly calling React.jsxs or React.jsxDEV. Use the Babel transform instead."
            );
        else Y(u);
      if (D.call(a, "key")) {
        u = t(e);
        var y = Object.keys(a).filter(function(de) {
          return de !== "key";
        });
        c = 0 < y.length ? "{key: someKey, " + y.join(": ..., ") + ": ...}" : "{key: someKey}", G[u + c] || (y = 0 < y.length ? "{" + y.join(": ..., ") + ": ...}" : "{}", console.error(
          `A props object containing a "key" prop is being spread into JSX:
  let props = %s;
  <%s {...props} />
React keys must be passed directly to JSX without using spread:
  let props = %s;
  <%s key={someKey} {...props} />`,
          c,
          u,
          y,
          u
        ), G[u + c] = !0);
      }
      if (u = null, o !== void 0 && (i(o), u = "" + o), x(a) && (i(a.key), u = "" + a.key), "key" in a) {
        o = {};
        for (var N in a)
          N !== "key" && (o[N] = a[N]);
      } else o = a;
      return u && f(
        o,
        typeof e == "function" ? e.displayName || e.name || "Unknown" : e
      ), Z(
        e,
        u,
        o,
        s(),
        _,
        m
      );
    }
    function Y(e) {
      F(e) ? e._store && (e._store.validated = 1) : typeof e == "object" && e !== null && e.$$typeof === P && (e._payload.status === "fulfilled" ? F(e._payload.value) && e._payload.value._store && (e._payload.value._store.validated = 1) : e._store && (e._store.validated = 1));
    }
    function F(e) {
      return typeof e == "object" && e !== null && e.$$typeof === U;
    }
    var k = fe, U = Symbol.for("react.transitional.element"), Q = Symbol.for("react.portal"), I = Symbol.for("react.fragment"), K = Symbol.for("react.strict_mode"), ee = Symbol.for("react.profiler"), te = Symbol.for("react.consumer"), re = Symbol.for("react.context"), ne = Symbol.for("react.forward_ref"), ae = Symbol.for("react.suspense"), se = Symbol.for("react.suspense_list"), oe = Symbol.for("react.memo"), P = Symbol.for("react.lazy"), le = Symbol.for("react.activity"), ie = Symbol.for("react.view_transition"), ce = Symbol.for("react.client.reference"), A = k.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE, D = Object.prototype.hasOwnProperty, ue = Array.isArray, O = console.createTask ? console.createTask : function() {
      return null;
    };
    k = {
      react_stack_bottom_frame: function(e) {
        return e();
      }
    };
    var M, z = {}, W = k.react_stack_bottom_frame.bind(
      k,
      l
    )(), q = O(d(l)), G = {};
    v.Fragment = I, v.jsx = function(e, a, o) {
      var c = 1e4 > A.recentlyCreatedOwnerStacks++;
      if (c) {
        var _ = Error.stackTraceLimit;
        Error.stackTraceLimit = 10;
        var m = Error("react-stack-top-frame");
        Error.stackTraceLimit = _;
      } else m = W;
      return L(
        e,
        a,
        o,
        !1,
        m,
        c ? O(d(e)) : q
      );
    }, v.jsxs = function(e, a, o) {
      var c = 1e4 > A.recentlyCreatedOwnerStacks++;
      if (c) {
        var _ = Error.stackTraceLimit;
        Error.stackTraceLimit = 10;
        var m = Error("react-stack-top-frame");
        Error.stackTraceLimit = _;
      } else m = W;
      return L(
        e,
        a,
        o,
        !0,
        m,
        c ? O(d(e)) : q
      );
    };
  })()), v;
}
var X;
function ke() {
  return X || (X = 1, process.env.NODE_ENV === "production" ? S.exports = je() : S.exports = ge()), S.exports;
}
var r = ke();
const Re = { 0: { text: "未读", color: "blue" }, 1: { text: "已读", color: "default" } };
function we({ userId: t }) {
  const [n, i] = j([]), [d, s] = j(!1), [l, x] = j(null);
  return H(() => {
    if (!t) return;
    let f = !1;
    return s(!0), g.inbox(t, 50).then((p) => {
      f || (i(p || []), x(null));
    }).catch((p) => {
      f || (i([]), x(p));
    }).finally(() => {
      f || s(!1);
    }), () => {
      f = !0;
    };
  }, [t]), t ? /* @__PURE__ */ r.jsxs("div", { children: [
    /* @__PURE__ */ r.jsx(
      J,
      {
        title: "站内信",
        description: `当前用户 ${t} 的收件箱（数据源 GET /api/msg/list?userId=${t}）`
      }
    ),
    l ? /* @__PURE__ */ r.jsx(pe, { children: /* @__PURE__ */ r.jsx(
      C,
      {
        type: "error",
        showIcon: !0,
        message: `站内信读取失败：${l.message || l}`
      }
    ) }) : (
      /* 收件箱一次取回最近 50 条、翻页在前端做 ⇒ 走基座 ListTable：
         分页/过滤/空态三件套由基座统一承接。 */
      /* @__PURE__ */ r.jsx(
        ye,
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
              render: (f) => /* @__PURE__ */ r.jsx(h, { children: f })
            },
            {
              title: "已读",
              dataIndex: "is_read",
              key: "is_read",
              width: 90,
              render: (f) => {
                const p = Re[f] || { text: `未知(${f})`, color: "default" };
                return /* @__PURE__ */ r.jsx(h, { color: p.color, children: p.text });
              }
            },
            { title: "时间", dataIndex: "created_time", key: "created_time", width: 180 }
          ]
        }
      )
    )
  ] }) : /* @__PURE__ */ r.jsxs("div", { children: [
    /* @__PURE__ */ r.jsx(J, { title: "站内信" }),
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
function Se() {
  return /* @__PURE__ */ r.jsx(
    $,
    {
      title: "消息模板",
      description: "按渠道/业务类型维护的消息模板（数据源 POST /api/msg/template/list）",
      load: (t, n) => g.templatePage(t, n),
      columns: [
        { title: "业务类型", dataIndex: "biz_type", key: "biz_type", width: 160 },
        {
          title: "渠道",
          dataIndex: "channel",
          key: "channel",
          width: 120,
          render: (t) => /* @__PURE__ */ r.jsx(h, { color: "geekblue", children: t })
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
            const n = ve[t] || { text: `未知(${t})`, color: "default" };
            return /* @__PURE__ */ r.jsx(h, { color: n.color, children: n.text });
          }
        },
        { title: "更新时间", dataIndex: "updated_time", key: "updated_time", width: 180 }
      ]
    }
  );
}
function Ie() {
  const [t, n] = j(null), [i, d] = j(null);
  return H(() => {
    let s = !1;
    return g.deliveryStats().then((l) => {
      s || n(l);
    }).catch((l) => {
      s || d(l);
    }), () => {
      s = !0;
    };
  }, []), i ? /* @__PURE__ */ r.jsx(
    C,
    {
      type: "warning",
      showIcon: !0,
      style: { marginBottom: 12 },
      message: `投递统计读取失败：${i.message || i}`
    }
  ) : t ? /* @__PURE__ */ r.jsxs(_e, { gutter: 16, style: { marginBottom: 12 }, children: [
    /* @__PURE__ */ r.jsx(R, { span: 6, children: /* @__PURE__ */ r.jsx(w, { title: "投递总数", value: t.total ?? 0 }) }),
    /* @__PURE__ */ r.jsx(R, { span: 6, children: /* @__PURE__ */ r.jsx(w, { title: "成功", value: t.success ?? 0, valueStyle: { color: "#3f8600" } }) }),
    /* @__PURE__ */ r.jsx(R, { span: 6, children: /* @__PURE__ */ r.jsx(w, { title: "失败", value: t.failed ?? 0, valueStyle: { color: "#cf1322" } }) }),
    /* @__PURE__ */ r.jsx(R, { span: 6, children: /* @__PURE__ */ r.jsx(
      w,
      {
        title: "成功率",
        value: Number(t.total) > 0 ? (Number(t.success) * 100 / Number(t.total)).toFixed(1) : "0.0",
        suffix: "%"
      }
    ) })
  ] }) : null;
}
function Pe() {
  return /* @__PURE__ */ r.jsx(
    $,
    {
      title: "投递日志",
      description: "每条消息按渠道投递的结果与失败原因（数据源 POST /api/msg/delivery/list + GET /api/msg/delivery/stats）",
      load: (t, n) => g.deliveryPage(t, n),
      extra: /* @__PURE__ */ r.jsx(Ie, {}),
      columns: [
        { title: "业务类型", dataIndex: "biz_type", key: "biz_type", width: 140 },
        { title: "渠道", dataIndex: "channel", key: "channel", width: 110, render: (t) => /* @__PURE__ */ r.jsx(h, { children: t }) },
        { title: "接收方", dataIndex: "receiver", key: "receiver", width: 140 },
        { title: "主题", dataIndex: "rendered_subject", key: "rendered_subject", width: 180, ellipsis: !0 },
        {
          title: "状态",
          dataIndex: "status",
          key: "status",
          width: 100,
          render: (t) => {
            const n = Ee[t] || { text: `未知(${t})`, color: "default" };
            return /* @__PURE__ */ r.jsx(h, { color: n.color, children: n.text });
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
function Ae() {
  return /* @__PURE__ */ r.jsx(
    $,
    {
      title: "批量发送任务",
      description: "批量任务的进度与失败数（数据源 POST /api/msg/batch/list）",
      load: (t, n) => g.batchPage(t, n),
      columns: [
        { title: "业务类型", dataIndex: "biz_type", key: "biz_type", width: 150 },
        { title: "渠道", dataIndex: "channel", key: "channel", width: 110, render: (t) => /* @__PURE__ */ r.jsx(h, { children: t }) },
        {
          title: "进度",
          key: "progress",
          width: 160,
          render: (t, n) => {
            const i = Number(n.total_count) || 0, d = (Number(n.success_count) || 0) + (Number(n.failed_count) || 0);
            return /* @__PURE__ */ r.jsx(he, { percent: i ? Math.round(d * 100 / i) : 0, size: "small" });
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
            const n = Te[t] || { text: `未知(${t})`, color: "default" };
            return /* @__PURE__ */ r.jsx(h, { color: n.color, children: n.text });
          }
        },
        { title: "失败原因", dataIndex: "error_message", key: "error_message", ellipsis: !0, render: (t) => t || "-" },
        { title: "创建时间", dataIndex: "created_time", key: "created_time", width: 180 },
        { title: "结束时间", dataIndex: "finished_time", key: "finished_time", width: 180, render: (t) => t || "-" }
      ]
    }
  );
}
function Ye() {
  const t = be();
  return /* @__PURE__ */ r.jsxs(me, { children: [
    /* @__PURE__ */ r.jsx(b, { index: !0, element: /* @__PURE__ */ r.jsx(xe, { to: "inbox", replace: !0 }) }),
    /* @__PURE__ */ r.jsx(b, { path: "inbox", element: /* @__PURE__ */ r.jsx(we, { userId: t }) }),
    /* @__PURE__ */ r.jsx(b, { path: "template", element: /* @__PURE__ */ r.jsx(Se, {}) }),
    /* @__PURE__ */ r.jsx(b, { path: "delivery", element: /* @__PURE__ */ r.jsx(Pe, {}) }),
    /* @__PURE__ */ r.jsx(b, { path: "batch", element: /* @__PURE__ */ r.jsx(Ae, {}) })
  ] });
}
export {
  Te as B,
  Ee as D,
  we as I,
  Ye as M,
  ve as T,
  Se as a,
  Pe as b,
  be as c,
  Ae as d,
  r as j,
  g as m
};
