import he, { useState as m, useEffect as W } from "react";
import { ReloadOutlined as Q, HomeOutlined as ge, MailOutlined as ye, SendOutlined as xe } from "@ant-design/icons";
import { Typography as U, Space as z, Button as ee, Alert as re, Card as k, Table as te, Tag as I, Row as ne, Col as N, Statistic as F } from "antd";
import { m as M } from "./api-CCr3uJUB.js";
import { c as Ue } from "./api-CCr3uJUB.js";
import { useNavigate as ve } from "react-router-dom";
var C = { exports: {} }, P = {};
/**
 * @license React
 * react-jsx-runtime.production.js
 *
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
var X;
function Ee() {
  if (X) return P;
  X = 1;
  var i = Symbol.for("react.transitional.element"), l = Symbol.for("react.fragment");
  function d(p, n, f) {
    var h = null;
    if (f !== void 0 && (h = "" + f), n.key !== void 0 && (h = "" + n.key), "key" in n) {
      f = {};
      for (var y in n)
        y !== "key" && (f[y] = n[y]);
    } else f = n;
    return n = f.ref, {
      $$typeof: i,
      type: p,
      key: h,
      ref: n !== void 0 ? n : null,
      props: f
    };
  }
  return P.Fragment = l, P.jsx = d, P.jsxs = d, P;
}
var O = {};
/**
 * @license React
 * react-jsx-runtime.development.js
 *
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
var Z;
function Te() {
  return Z || (Z = 1, process.env.NODE_ENV !== "production" && (function() {
    function i(e) {
      if (e == null) return null;
      if (typeof e == "function")
        return e.$$typeof === fe ? null : e.displayName || e.name || null;
      if (typeof e == "string") return e;
      switch (e) {
        case o:
          return "Fragment";
        case A:
          return "Profiler";
        case _:
          return "StrictMode";
        case le:
          return "Suspense";
        case ie:
          return "SuspenseList";
        case ue:
          return "Activity";
        case de:
          return "ViewTransition";
      }
      if (typeof e == "object")
        switch (typeof e.tag == "number" && console.error(
          "Received an unexpected object in getComponentNameFromType(). This is likely a bug in React. Please file an issue."
        ), e.$$typeof) {
          case g:
            return "Portal";
          case oe:
            return e.displayName || "Context";
          case ae:
            return (e._context.displayName || "Context") + ".Consumer";
          case se:
            var t = e.render;
            return e = e.displayName, e || (e = t.displayName || t.name || "", e = e !== "" ? "ForwardRef(" + e + ")" : "ForwardRef"), e;
          case ce:
            return t = e.displayName || null, t !== null ? t : i(e.type) || "Memo";
          case L:
            t = e._payload, e = e._init;
            try {
              return i(e(t));
            } catch {
            }
        }
      return null;
    }
    function l(e) {
      return "" + e;
    }
    function d(e) {
      try {
        l(e);
        var t = !1;
      } catch {
        t = !0;
      }
      if (t) {
        t = console;
        var s = t.error, c = typeof Symbol == "function" && Symbol.toStringTag && e[Symbol.toStringTag] || e.constructor.name || "Object";
        return s.call(
          t,
          "The provided key is an unsupported type %s. This value must be coerced to a string before using it here.",
          c
        ), l(e);
      }
    }
    function p(e) {
      if (e === o) return "<>";
      if (typeof e == "object" && e !== null && e.$$typeof === L)
        return "<...>";
      try {
        var t = i(e);
        return t ? "<" + t + ">" : "<...>";
      } catch {
        return "<...>";
      }
    }
    function n() {
      var e = $.A;
      return e === null ? null : e.getOwner();
    }
    function f() {
      return Error("react-stack-top-frame");
    }
    function h(e) {
      if (B.call(e, "key")) {
        var t = Object.getOwnPropertyDescriptor(e, "key").get;
        if (t && t.isReactWarning) return !1;
      }
      return e.key !== void 0;
    }
    function y(e, t) {
      function s() {
        J || (J = !0, console.error(
          "%s: `key` is not a prop. Trying to access it will result in `undefined` being returned. If you need to access the same value within the child component, you should pass it as a different prop. (https://react.dev/link/special-props)",
          t
        ));
      }
      s.isReactWarning = !0, Object.defineProperty(e, "key", {
        get: s,
        configurable: !0
      });
    }
    function R() {
      var e = i(this.type);
      return q[e] || (q[e] = !0, console.error(
        "Accessing element.ref was removed in React 19. ref is now a regular prop. It will be removed from the JSX Element type in a future release."
      )), e = this.props.ref, e !== void 0 ? e : null;
    }
    function S(e, t, s, c, b, v) {
      var u = s.ref;
      return e = {
        $$typeof: a,
        type: e,
        key: t,
        props: s,
        _owner: c
      }, (u !== void 0 ? u : null) !== null ? Object.defineProperty(e, "ref", {
        enumerable: !1,
        get: R
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
        value: b
      }), Object.defineProperty(e, "_debugTask", {
        configurable: !1,
        enumerable: !1,
        writable: !0,
        value: v
      }), Object.freeze && (Object.freeze(e.props), Object.freeze(e)), e;
    }
    function E(e, t, s, c, b, v) {
      var u = t.children;
      if (u !== void 0)
        if (c)
          if (me(u)) {
            for (c = 0; c < u.length; c++)
              T(u[c]);
            Object.freeze && Object.freeze(u);
          } else
            console.error(
              "React.jsx: Static children should always be an array. You are likely explicitly calling React.jsxs or React.jsxDEV. Use the Babel transform instead."
            );
        else T(u);
      if (B.call(t, "key")) {
        u = i(e);
        var w = Object.keys(t).filter(function(pe) {
          return pe !== "key";
        });
        c = 0 < w.length ? "{key: someKey, " + w.join(": ..., ") + ": ...}" : "{key: someKey}", H[u + c] || (w = 0 < w.length ? "{" + w.join(": ..., ") + ": ...}" : "{}", console.error(
          `A props object containing a "key" prop is being spread into JSX:
  let props = %s;
  <%s {...props} />
React keys must be passed directly to JSX without using spread:
  let props = %s;
  <%s key={someKey} {...props} />`,
          c,
          u,
          w,
          u
        ), H[u + c] = !0);
      }
      if (u = null, s !== void 0 && (d(s), u = "" + s), h(t) && (d(t.key), u = "" + t.key), "key" in t) {
        s = {};
        for (var D in t)
          D !== "key" && (s[D] = t[D]);
      } else s = t;
      return u && y(
        s,
        typeof e == "function" ? e.displayName || e.name || "Unknown" : e
      ), S(
        e,
        u,
        s,
        n(),
        b,
        v
      );
    }
    function T(e) {
      j(e) ? e._store && (e._store.validated = 1) : typeof e == "object" && e !== null && e.$$typeof === L && (e._payload.status === "fulfilled" ? j(e._payload.value) && e._payload.value._store && (e._payload.value._store.validated = 1) : e._store && (e._store.validated = 1));
    }
    function j(e) {
      return typeof e == "object" && e !== null && e.$$typeof === a;
    }
    var x = he, a = Symbol.for("react.transitional.element"), g = Symbol.for("react.portal"), o = Symbol.for("react.fragment"), _ = Symbol.for("react.strict_mode"), A = Symbol.for("react.profiler"), ae = Symbol.for("react.consumer"), oe = Symbol.for("react.context"), se = Symbol.for("react.forward_ref"), le = Symbol.for("react.suspense"), ie = Symbol.for("react.suspense_list"), ce = Symbol.for("react.memo"), L = Symbol.for("react.lazy"), ue = Symbol.for("react.activity"), de = Symbol.for("react.view_transition"), fe = Symbol.for("react.client.reference"), $ = x.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE, B = Object.prototype.hasOwnProperty, me = Array.isArray, Y = console.createTask ? console.createTask : function() {
      return null;
    };
    x = {
      react_stack_bottom_frame: function(e) {
        return e();
      }
    };
    var J, q = {}, V = x.react_stack_bottom_frame.bind(
      x,
      f
    )(), G = Y(p(f)), H = {};
    O.Fragment = o, O.jsx = function(e, t, s) {
      var c = 1e4 > $.recentlyCreatedOwnerStacks++;
      if (c) {
        var b = Error.stackTraceLimit;
        Error.stackTraceLimit = 10;
        var v = Error("react-stack-top-frame");
        Error.stackTraceLimit = b;
      } else v = V;
      return E(
        e,
        t,
        s,
        !1,
        v,
        c ? Y(p(e)) : G
      );
    }, O.jsxs = function(e, t, s) {
      var c = 1e4 > $.recentlyCreatedOwnerStacks++;
      if (c) {
        var b = Error.stackTraceLimit;
        Error.stackTraceLimit = 10;
        var v = Error("react-stack-top-frame");
        Error.stackTraceLimit = b;
      } else v = V;
      return E(
        e,
        t,
        s,
        !0,
        v,
        c ? Y(p(e)) : G
      );
    };
  })()), O;
}
var K;
function je() {
  return K || (K = 1, process.env.NODE_ENV === "production" ? C.exports = Ee() : C.exports = Te()), C.exports;
}
var r = je();
const { Title: be, Paragraph: _e } = U;
function ke(i) {
  const l = { IN_APP: "blue", EMAIL: "purple", SMS: "green", WEBHOOK: "orange" };
  return /* @__PURE__ */ r.jsx(I, { color: l[i] || "default", children: i || "—" });
}
function Re() {
  const [i, l] = m([]), [d, p] = m(1), [n, f] = m(20), [h, y] = m(0), [R, S] = m(!1), [E, T] = m(null), j = async () => {
    S(!0);
    try {
      const a = await M.templatePage(d, n), g = (a == null ? void 0 : a.records) || (a == null ? void 0 : a.list) || (a == null ? void 0 : a.data) || [];
      l(Array.isArray(g) ? g : []), y((a == null ? void 0 : a.total) || g.length), T(null);
    } catch (a) {
      T((a == null ? void 0 : a.message) || String(a));
    } finally {
      S(!1);
    }
  };
  W(() => {
    j();
  }, [d, n]);
  const x = [
    { title: "code", dataIndex: "code", key: "code", width: 160 },
    { title: "名称", dataIndex: "name", key: "name", width: 200, ellipsis: !0 },
    { title: "渠道", dataIndex: "channel", key: "channel", width: 100, render: ke },
    { title: "标题模板", dataIndex: "titleTemplate", key: "title", ellipsis: !0 },
    { title: "内容模板", dataIndex: "contentTemplate", key: "content", ellipsis: !0 },
    {
      title: "启用",
      dataIndex: "enabled",
      key: "enabled",
      width: 80,
      render: (a) => a ? /* @__PURE__ */ r.jsx(I, { color: "green", children: "启用" }) : /* @__PURE__ */ r.jsx(I, { color: "default", children: "停用" })
    }
  ];
  return /* @__PURE__ */ r.jsxs("div", { children: [
    /* @__PURE__ */ r.jsxs(z, { style: { marginBottom: 16 }, children: [
      /* @__PURE__ */ r.jsx(be, { level: 4, style: { margin: 0 }, children: "消息模板" }),
      /* @__PURE__ */ r.jsx(ee, { icon: /* @__PURE__ */ r.jsx(Q, {}), onClick: j, loading: R, children: "刷新" })
    ] }),
    /* @__PURE__ */ r.jsx(_e, { type: "secondary", children: "站内信 / 邮件 / 短信模板清单（/msg/template/list）。" }),
    E && /* @__PURE__ */ r.jsx(re, { type: "error", showIcon: !0, style: { marginBottom: 16 }, message: "后端未连接", description: E }),
    /* @__PURE__ */ r.jsx(k, { children: /* @__PURE__ */ r.jsx(
      te,
      {
        rowKey: (a, g) => a.id || a.code || g,
        dataSource: i,
        columns: x,
        loading: R,
        size: "small",
        pagination: {
          current: d,
          pageSize: n,
          total: h,
          showSizeChanger: !0,
          onChange: (a, g) => {
            p(a), f(g);
          }
        }
      }
    ) })
  ] });
}
const { Title: Se, Paragraph: we } = U;
function Ae(i) {
  const l = { SENT: "green", PENDING: "gold", FAILED: "red", CANCELED: "default" };
  return /* @__PURE__ */ r.jsx(I, { color: l[i] || "default", children: i || "—" });
}
function Pe() {
  const [i, l] = m([]), [d, p] = m(null), [n, f] = m(1), [h, y] = m(20), [R, S] = m(0), [E, T] = m(!1), [j, x] = m(null), a = async () => {
    T(!0);
    try {
      const [o, _] = await Promise.all([M.deliveryPage(n, h), M.deliveryStats().catch(() => null)]), A = (o == null ? void 0 : o.records) || (o == null ? void 0 : o.list) || (o == null ? void 0 : o.data) || [];
      l(Array.isArray(A) ? A : []), S((o == null ? void 0 : o.total) || A.length), p(_), x(null);
    } catch (o) {
      x((o == null ? void 0 : o.message) || String(o));
    } finally {
      T(!1);
    }
  };
  W(() => {
    a();
  }, [n, h]);
  const g = [
    { title: "ID", dataIndex: "id", key: "id", width: 80 },
    { title: "收件人", dataIndex: "recipient", key: "recipient", width: 160, ellipsis: !0 },
    { title: "渠道", dataIndex: "channel", key: "channel", width: 100 },
    { title: "模板", dataIndex: "templateCode", key: "tpl", width: 140, ellipsis: !0 },
    { title: "状态", dataIndex: "status", key: "status", width: 100, render: Ae },
    { title: "重试次数", dataIndex: "retryCount", key: "retry", width: 100 },
    { title: "最后错误", dataIndex: "lastError", key: "err", ellipsis: !0 },
    {
      title: "时间",
      dataIndex: "createdAt",
      key: "when",
      width: 160,
      render: (o) => o ? new Date(o).toLocaleString("zh-CN") : "—"
    }
  ];
  return /* @__PURE__ */ r.jsxs("div", { children: [
    /* @__PURE__ */ r.jsxs(z, { style: { marginBottom: 16 }, children: [
      /* @__PURE__ */ r.jsx(Se, { level: 4, style: { margin: 0 }, children: "投递清单" }),
      /* @__PURE__ */ r.jsx(ee, { icon: /* @__PURE__ */ r.jsx(Q, {}), onClick: a, loading: E, children: "刷新" })
    ] }),
    /* @__PURE__ */ r.jsx(we, { type: "secondary", children: "消息投递记录（/msg/delivery/list）+ 全局统计（/msg/delivery/stats）。" }),
    j && /* @__PURE__ */ r.jsx(re, { type: "error", showIcon: !0, style: { marginBottom: 16 }, message: "后端未连接", description: j }),
    d && /* @__PURE__ */ r.jsxs(ne, { gutter: 16, style: { marginBottom: 16 }, children: [
      /* @__PURE__ */ r.jsx(N, { span: 8, children: /* @__PURE__ */ r.jsx(k, { children: /* @__PURE__ */ r.jsx(F, { title: "已发送", value: d.sent || 0, valueStyle: { color: "#3f8600" } }) }) }),
      /* @__PURE__ */ r.jsx(N, { span: 8, children: /* @__PURE__ */ r.jsx(k, { children: /* @__PURE__ */ r.jsx(F, { title: "待投递", value: d.pending || 0, valueStyle: { color: "#faad14" } }) }) }),
      /* @__PURE__ */ r.jsx(N, { span: 8, children: /* @__PURE__ */ r.jsx(k, { children: /* @__PURE__ */ r.jsx(F, { title: "失败", value: d.failed || 0, valueStyle: { color: "#cf1322" } }) }) })
    ] }),
    /* @__PURE__ */ r.jsx(k, { children: /* @__PURE__ */ r.jsx(
      te,
      {
        rowKey: (o, _) => o.id || _,
        dataSource: i,
        columns: g,
        loading: E,
        size: "small",
        scroll: { x: 1e3 },
        pagination: {
          current: n,
          pageSize: h,
          total: R,
          showSizeChanger: !0,
          onChange: (o, _) => {
            f(o), y(_);
          }
        }
      }
    ) })
  ] });
}
const { Title: Oe, Paragraph: Ie } = U;
function Ce() {
  const i = ve(), [l, d] = m(null);
  W(() => {
    const n = localStorage.getItem("userInfo");
    if (n)
      try {
        d(JSON.parse(n));
      } catch {
        d({ name: n });
      }
  }, []);
  const p = Ne.filter((n) => n.key !== "/z-msg/home");
  return /* @__PURE__ */ r.jsxs("div", { children: [
    /* @__PURE__ */ r.jsx(k, { style: { marginBottom: 16, background: "linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%)", border: "none" }, children: /* @__PURE__ */ r.jsxs(z, { direction: "vertical", size: 4, style: { color: "#fff" }, children: [
      /* @__PURE__ */ r.jsxs(Oe, { level: 3, style: { color: "#fff", margin: 0 }, children: [
        "欢迎",
        l != null && l.name ? `，${l.name}` : ""
      ] }),
      /* @__PURE__ */ r.jsx(Ie, { style: { color: "rgba(255,255,255,0.85)", margin: 0 }, children: "z-msg 消息中心 管理台" }),
      (l == null ? void 0 : l.role) && /* @__PURE__ */ r.jsx(I, { style: { marginTop: 8, background: "rgba(255,255,255,0.2)", color: "#fff", border: "none" }, children: l.role })
    ] }) }),
    /* @__PURE__ */ r.jsx(ne, { gutter: [16, 16], children: p.map((n) => /* @__PURE__ */ r.jsx(N, { xs: 24, sm: 12, md: 12, lg: 8, children: /* @__PURE__ */ r.jsx(k, { hoverable: !0, onClick: () => i(n.key), style: { borderTop: "3px solid #7c3aed" }, children: /* @__PURE__ */ r.jsxs(z, { align: "start", size: 12, children: [
      /* @__PURE__ */ r.jsx("div", { style: {
        width: 44,
        height: 44,
        borderRadius: 8,
        flexShrink: 0,
        background: "rgba(124,58,237,0.08)",
        color: "#7c3aed",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        fontSize: 20
      }, children: n.icon }),
      /* @__PURE__ */ r.jsxs("div", { style: { minWidth: 0 }, children: [
        /* @__PURE__ */ r.jsx("div", { style: { fontSize: 15, fontWeight: 600, color: "#0f172a" }, children: n.label }),
        /* @__PURE__ */ r.jsx("div", { style: { fontSize: 12, color: "#94a3b8", marginTop: 2 }, children: n.key })
      ] })
    ] }) }) }, n.key)) })
  ] });
}
const Ne = [
  { key: "/z-msg/home", label: "首页", icon: /* @__PURE__ */ r.jsx(ge, {}) },
  { key: "/z-msg/templates", label: "消息模板", icon: /* @__PURE__ */ r.jsx(ye, {}) },
  { key: "/z-msg/delivery", label: "投递记录", icon: /* @__PURE__ */ r.jsx(xe, {}) }
], Fe = [
  { path: "/z-msg/home", Component: Ce },
  { path: "/z-msg/templates", Component: Re },
  { path: "/z-msg/delivery", Component: Pe }
];
export {
  Pe as DeliveryLogs,
  Re as TemplateList,
  Ue as configureMsg,
  Ne as menuItems,
  Fe as routes
};
