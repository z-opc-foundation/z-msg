import pe, { useState as f, useEffect as H } from "react";
import { ReloadOutlined as Z, MailOutlined as he, SendOutlined as Ee } from "@ant-design/icons";
import { Typography as K, Space as Q, Button as ee, Alert as te, Card as O, Table as re, Tag as C, Row as ye, Col as z, Statistic as D } from "antd";
import { m as F } from "./api-CCr3uJUB.js";
import { c as $e } from "./api-CCr3uJUB.js";
var I = { exports: {} }, A = {};
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
function ge() {
  if (V) return A;
  V = 1;
  var c = Symbol.for("react.transitional.element"), m = Symbol.for("react.fragment");
  function u(E, s, d) {
    var p = null;
    if (d !== void 0 && (p = "" + d), s.key !== void 0 && (p = "" + s.key), "key" in s) {
      d = {};
      for (var y in s)
        y !== "key" && (d[y] = s[y]);
    } else d = s;
    return s = d.ref, {
      $$typeof: c,
      type: E,
      key: p,
      ref: s !== void 0 ? s : null,
      props: d
    };
  }
  return A.Fragment = m, A.jsx = u, A.jsxs = u, A;
}
var P = {};
/**
 * @license React
 * react-jsx-runtime.development.js
 *
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
var G;
function ve() {
  return G || (G = 1, process.env.NODE_ENV !== "production" && (function() {
    function c(e) {
      if (e == null) return null;
      if (typeof e == "function")
        return e.$$typeof === de ? null : e.displayName || e.name || null;
      if (typeof e == "string") return e;
      switch (e) {
        case n:
          return "Fragment";
        case S:
          return "Profiler";
        case b:
          return "StrictMode";
        case se:
          return "Suspense";
        case le:
          return "SuspenseList";
        case ce:
          return "Activity";
        case ue:
          return "ViewTransition";
      }
      if (typeof e == "object")
        switch (typeof e.tag == "number" && console.error(
          "Received an unexpected object in getComponentNameFromType(). This is likely a bug in React. Please file an issue."
        ), e.$$typeof) {
          case h:
            return "Portal";
          case ne:
            return e.displayName || "Context";
          case ae:
            return (e._context.displayName || "Context") + ".Consumer";
          case oe:
            var t = e.render;
            return e = e.displayName, e || (e = t.displayName || t.name || "", e = e !== "" ? "ForwardRef(" + e + ")" : "ForwardRef"), e;
          case ie:
            return t = e.displayName || null, t !== null ? t : c(e.type) || "Memo";
          case N:
            t = e._payload, e = e._init;
            try {
              return c(e(t));
            } catch {
            }
        }
      return null;
    }
    function m(e) {
      return "" + e;
    }
    function u(e) {
      try {
        m(e);
        var t = !1;
      } catch {
        t = !0;
      }
      if (t) {
        t = console;
        var o = t.error, l = typeof Symbol == "function" && Symbol.toStringTag && e[Symbol.toStringTag] || e.constructor.name || "Object";
        return o.call(
          t,
          "The provided key is an unsupported type %s. This value must be coerced to a string before using it here.",
          l
        ), m(e);
      }
    }
    function E(e) {
      if (e === n) return "<>";
      if (typeof e == "object" && e !== null && e.$$typeof === N)
        return "<...>";
      try {
        var t = c(e);
        return t ? "<" + t + ">" : "<...>";
      } catch {
        return "<...>";
      }
    }
    function s() {
      var e = L.A;
      return e === null ? null : e.getOwner();
    }
    function d() {
      return Error("react-stack-top-frame");
    }
    function p(e) {
      if (M.call(e, "key")) {
        var t = Object.getOwnPropertyDescriptor(e, "key").get;
        if (t && t.isReactWarning) return !1;
      }
      return e.key !== void 0;
    }
    function y(e, t) {
      function o() {
        W || (W = !0, console.error(
          "%s: `key` is not a prop. Trying to access it will result in `undefined` being returned. If you need to access the same value within the child component, you should pass it as a different prop. (https://react.dev/link/special-props)",
          t
        ));
      }
      o.isReactWarning = !0, Object.defineProperty(e, "key", {
        get: o,
        configurable: !0
      });
    }
    function j() {
      var e = c(this.type);
      return U[e] || (U[e] = !0, console.error(
        "Accessing element.ref was removed in React 19. ref is now a regular prop. It will be removed from the JSX Element type in a future release."
      )), e = this.props.ref, e !== void 0 ? e : null;
    }
    function k(e, t, o, l, R, v) {
      var i = o.ref;
      return e = {
        $$typeof: a,
        type: e,
        key: t,
        props: o,
        _owner: l
      }, (i !== void 0 ? i : null) !== null ? Object.defineProperty(e, "ref", {
        enumerable: !1,
        get: j
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
        value: R
      }), Object.defineProperty(e, "_debugTask", {
        configurable: !1,
        enumerable: !1,
        writable: !0,
        value: v
      }), Object.freeze && (Object.freeze(e.props), Object.freeze(e)), e;
    }
    function x(e, t, o, l, R, v) {
      var i = t.children;
      if (i !== void 0)
        if (l)
          if (fe(i)) {
            for (l = 0; l < i.length; l++)
              T(i[l]);
            Object.freeze && Object.freeze(i);
          } else
            console.error(
              "React.jsx: Static children should always be an array. You are likely explicitly calling React.jsxs or React.jsxDEV. Use the Babel transform instead."
            );
        else T(i);
      if (M.call(t, "key")) {
        i = c(e);
        var w = Object.keys(t).filter(function(me) {
          return me !== "key";
        });
        l = 0 < w.length ? "{key: someKey, " + w.join(": ..., ") + ": ...}" : "{key: someKey}", J[i + l] || (w = 0 < w.length ? "{" + w.join(": ..., ") + ": ...}" : "{}", console.error(
          `A props object containing a "key" prop is being spread into JSX:
  let props = %s;
  <%s {...props} />
React keys must be passed directly to JSX without using spread:
  let props = %s;
  <%s key={someKey} {...props} />`,
          l,
          i,
          w,
          i
        ), J[i + l] = !0);
      }
      if (i = null, o !== void 0 && (u(o), i = "" + o), p(t) && (u(t.key), i = "" + t.key), "key" in t) {
        o = {};
        for (var $ in t)
          $ !== "key" && (o[$] = t[$]);
      } else o = t;
      return i && y(
        o,
        typeof e == "function" ? e.displayName || e.name || "Unknown" : e
      ), k(
        e,
        i,
        o,
        s(),
        R,
        v
      );
    }
    function T(e) {
      _(e) ? e._store && (e._store.validated = 1) : typeof e == "object" && e !== null && e.$$typeof === N && (e._payload.status === "fulfilled" ? _(e._payload.value) && e._payload.value._store && (e._payload.value._store.validated = 1) : e._store && (e._store.validated = 1));
    }
    function _(e) {
      return typeof e == "object" && e !== null && e.$$typeof === a;
    }
    var g = pe, a = Symbol.for("react.transitional.element"), h = Symbol.for("react.portal"), n = Symbol.for("react.fragment"), b = Symbol.for("react.strict_mode"), S = Symbol.for("react.profiler"), ae = Symbol.for("react.consumer"), ne = Symbol.for("react.context"), oe = Symbol.for("react.forward_ref"), se = Symbol.for("react.suspense"), le = Symbol.for("react.suspense_list"), ie = Symbol.for("react.memo"), N = Symbol.for("react.lazy"), ce = Symbol.for("react.activity"), ue = Symbol.for("react.view_transition"), de = Symbol.for("react.client.reference"), L = g.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE, M = Object.prototype.hasOwnProperty, fe = Array.isArray, Y = console.createTask ? console.createTask : function() {
      return null;
    };
    g = {
      react_stack_bottom_frame: function(e) {
        return e();
      }
    };
    var W, U = {}, B = g.react_stack_bottom_frame.bind(
      g,
      d
    )(), q = Y(E(d)), J = {};
    P.Fragment = n, P.jsx = function(e, t, o) {
      var l = 1e4 > L.recentlyCreatedOwnerStacks++;
      if (l) {
        var R = Error.stackTraceLimit;
        Error.stackTraceLimit = 10;
        var v = Error("react-stack-top-frame");
        Error.stackTraceLimit = R;
      } else v = B;
      return x(
        e,
        t,
        o,
        !1,
        v,
        l ? Y(E(e)) : q
      );
    }, P.jsxs = function(e, t, o) {
      var l = 1e4 > L.recentlyCreatedOwnerStacks++;
      if (l) {
        var R = Error.stackTraceLimit;
        Error.stackTraceLimit = 10;
        var v = Error("react-stack-top-frame");
        Error.stackTraceLimit = R;
      } else v = B;
      return x(
        e,
        t,
        o,
        !0,
        v,
        l ? Y(E(e)) : q
      );
    };
  })()), P;
}
var X;
function xe() {
  return X || (X = 1, process.env.NODE_ENV === "production" ? I.exports = ge() : I.exports = ve()), I.exports;
}
var r = xe();
const { Title: Te, Paragraph: _e } = K;
function Re(c) {
  const m = { IN_APP: "blue", EMAIL: "purple", SMS: "green", WEBHOOK: "orange" };
  return /* @__PURE__ */ r.jsx(C, { color: m[c] || "default", children: c || "—" });
}
function be() {
  const [c, m] = f([]), [u, E] = f(1), [s, d] = f(20), [p, y] = f(0), [j, k] = f(!1), [x, T] = f(null), _ = async () => {
    k(!0);
    try {
      const a = await F.templatePage(u, s), h = (a == null ? void 0 : a.records) || (a == null ? void 0 : a.list) || (a == null ? void 0 : a.data) || [];
      m(Array.isArray(h) ? h : []), y((a == null ? void 0 : a.total) || h.length), T(null);
    } catch (a) {
      T((a == null ? void 0 : a.message) || String(a));
    } finally {
      k(!1);
    }
  };
  H(() => {
    _();
  }, [u, s]);
  const g = [
    { title: "code", dataIndex: "code", key: "code", width: 160 },
    { title: "名称", dataIndex: "name", key: "name", width: 200, ellipsis: !0 },
    { title: "渠道", dataIndex: "channel", key: "channel", width: 100, render: Re },
    { title: "标题模板", dataIndex: "titleTemplate", key: "title", ellipsis: !0 },
    { title: "内容模板", dataIndex: "contentTemplate", key: "content", ellipsis: !0 },
    {
      title: "启用",
      dataIndex: "enabled",
      key: "enabled",
      width: 80,
      render: (a) => a ? /* @__PURE__ */ r.jsx(C, { color: "green", children: "启用" }) : /* @__PURE__ */ r.jsx(C, { color: "default", children: "停用" })
    }
  ];
  return /* @__PURE__ */ r.jsxs("div", { children: [
    /* @__PURE__ */ r.jsxs(Q, { style: { marginBottom: 16 }, children: [
      /* @__PURE__ */ r.jsx(Te, { level: 4, style: { margin: 0 }, children: "消息模板" }),
      /* @__PURE__ */ r.jsx(ee, { icon: /* @__PURE__ */ r.jsx(Z, {}), onClick: _, loading: j, children: "刷新" })
    ] }),
    /* @__PURE__ */ r.jsx(_e, { type: "secondary", children: "站内信 / 邮件 / 短信模板清单（/msg/template/list）。" }),
    x && /* @__PURE__ */ r.jsx(te, { type: "error", showIcon: !0, style: { marginBottom: 16 }, message: "后端未连接", description: x }),
    /* @__PURE__ */ r.jsx(O, { children: /* @__PURE__ */ r.jsx(
      re,
      {
        rowKey: (a, h) => a.id || a.code || h,
        dataSource: c,
        columns: g,
        loading: j,
        size: "small",
        pagination: {
          current: u,
          pageSize: s,
          total: p,
          showSizeChanger: !0,
          onChange: (a, h) => {
            E(a), d(h);
          }
        }
      }
    ) })
  ] });
}
const { Title: je, Paragraph: ke } = K;
function we(c) {
  const m = { SENT: "green", PENDING: "gold", FAILED: "red", CANCELED: "default" };
  return /* @__PURE__ */ r.jsx(C, { color: m[c] || "default", children: c || "—" });
}
function Se() {
  const [c, m] = f([]), [u, E] = f(null), [s, d] = f(1), [p, y] = f(20), [j, k] = f(0), [x, T] = f(!1), [_, g] = f(null), a = async () => {
    T(!0);
    try {
      const [n, b] = await Promise.all([F.deliveryPage(s, p), F.deliveryStats().catch(() => null)]), S = (n == null ? void 0 : n.records) || (n == null ? void 0 : n.list) || (n == null ? void 0 : n.data) || [];
      m(Array.isArray(S) ? S : []), k((n == null ? void 0 : n.total) || S.length), E(b), g(null);
    } catch (n) {
      g((n == null ? void 0 : n.message) || String(n));
    } finally {
      T(!1);
    }
  };
  H(() => {
    a();
  }, [s, p]);
  const h = [
    { title: "ID", dataIndex: "id", key: "id", width: 80 },
    { title: "收件人", dataIndex: "recipient", key: "recipient", width: 160, ellipsis: !0 },
    { title: "渠道", dataIndex: "channel", key: "channel", width: 100 },
    { title: "模板", dataIndex: "templateCode", key: "tpl", width: 140, ellipsis: !0 },
    { title: "状态", dataIndex: "status", key: "status", width: 100, render: we },
    { title: "重试次数", dataIndex: "retryCount", key: "retry", width: 100 },
    { title: "最后错误", dataIndex: "lastError", key: "err", ellipsis: !0 },
    {
      title: "时间",
      dataIndex: "createdAt",
      key: "when",
      width: 160,
      render: (n) => n ? new Date(n).toLocaleString("zh-CN") : "—"
    }
  ];
  return /* @__PURE__ */ r.jsxs("div", { children: [
    /* @__PURE__ */ r.jsxs(Q, { style: { marginBottom: 16 }, children: [
      /* @__PURE__ */ r.jsx(je, { level: 4, style: { margin: 0 }, children: "投递清单" }),
      /* @__PURE__ */ r.jsx(ee, { icon: /* @__PURE__ */ r.jsx(Z, {}), onClick: a, loading: x, children: "刷新" })
    ] }),
    /* @__PURE__ */ r.jsx(ke, { type: "secondary", children: "消息投递记录（/msg/delivery/list）+ 全局统计（/msg/delivery/stats）。" }),
    _ && /* @__PURE__ */ r.jsx(te, { type: "error", showIcon: !0, style: { marginBottom: 16 }, message: "后端未连接", description: _ }),
    u && /* @__PURE__ */ r.jsxs(ye, { gutter: 16, style: { marginBottom: 16 }, children: [
      /* @__PURE__ */ r.jsx(z, { span: 8, children: /* @__PURE__ */ r.jsx(O, { children: /* @__PURE__ */ r.jsx(D, { title: "已发送", value: u.sent || 0, valueStyle: { color: "#3f8600" } }) }) }),
      /* @__PURE__ */ r.jsx(z, { span: 8, children: /* @__PURE__ */ r.jsx(O, { children: /* @__PURE__ */ r.jsx(D, { title: "待投递", value: u.pending || 0, valueStyle: { color: "#faad14" } }) }) }),
      /* @__PURE__ */ r.jsx(z, { span: 8, children: /* @__PURE__ */ r.jsx(O, { children: /* @__PURE__ */ r.jsx(D, { title: "失败", value: u.failed || 0, valueStyle: { color: "#cf1322" } }) }) })
    ] }),
    /* @__PURE__ */ r.jsx(O, { children: /* @__PURE__ */ r.jsx(
      re,
      {
        rowKey: (n, b) => n.id || b,
        dataSource: c,
        columns: h,
        loading: x,
        size: "small",
        scroll: { x: 1e3 },
        pagination: {
          current: s,
          pageSize: p,
          total: j,
          showSizeChanger: !0,
          onChange: (n, b) => {
            d(n), y(b);
          }
        }
      }
    ) })
  ] });
}
const Ce = [
  { key: "/templates", icon: /* @__PURE__ */ r.jsx(he, {}), label: "消息模板" },
  { key: "/delivery", icon: /* @__PURE__ */ r.jsx(Ee, {}), label: "投递记录" }
], Ne = [
  { path: "templates", Component: be },
  { path: "delivery", Component: Se }
];
export {
  Se as DeliveryLogs,
  be as TemplateList,
  $e as configureMsg,
  Ce as menuItems,
  Ne as routeTable
};
