import be, { useState as _, useCallback as _e, useEffect as G } from "react";
import { useNavigate as je, Routes as ve, Route as O, Navigate as ke } from "react-router-dom";
import { Breadcrumb as Te, Button as V, Card as le, Table as oe, Alert as J, Tag as k, Row as Ee, Col as $, Statistic as L, Progress as we } from "antd";
import P from "@yuku123/z-frontend-common";
import { InboxOutlined as Se, ReloadOutlined as se, ExclamationCircleOutlined as Re } from "@ant-design/icons";
const z = {
  inbox: (t, n) => P.get("/msg/list", { params: { userId: t, limit: n } }),
  templatePage: (t, n) => P.post("/msg/template/list", { page: t, size: n }),
  deliveryPage: (t, n) => P.post("/msg/delivery/list", { page: t, size: n }),
  deliveryStats: () => P.get("/msg/delivery/stats"),
  batchPage: (t, n) => P.post("/msg/batch/list", { page: t, size: n })
};
function Ie() {
  const t = (i) => {
    try {
      return JSON.parse(localStorage.getItem(i) || "null");
    } catch {
      return null;
    }
  }, n = t("userInfo"), o = n && (n.id ?? n.userId);
  if (o != null && o !== "") return String(o);
  const d = t("USER_INFO"), s = d && (d.staffId ?? d.jobNumber);
  return s == null || s === "" ? null : String(s);
}
const Ae = {
  0: { text: "待发送", color: "default" },
  1: { text: "成功", color: "success" },
  2: { text: "失败", color: "error" },
  3: { text: "重试中", color: "processing" }
}, Oe = {
  0: { text: "待执行", color: "default" },
  1: { text: "执行中", color: "processing" },
  2: { text: "已完成", color: "success" },
  3: { text: "部分失败", color: "warning" },
  4: { text: "失败", color: "error" }
}, Pe = {
  0: { text: "草稿", color: "default" },
  1: { text: "启用", color: "success" },
  2: { text: "停用", color: "warning" }
};
var W = { exports: {} }, N = {};
/**
 * @license React
 * react-jsx-runtime.production.js
 *
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
var re;
function Ne() {
  if (re) return N;
  re = 1;
  var t = Symbol.for("react.transitional.element"), n = Symbol.for("react.fragment");
  function o(d, s, i) {
    var p = null;
    if (i !== void 0 && (p = "" + i), s.key !== void 0 && (p = "" + s.key), "key" in s) {
      i = {};
      for (var a in s)
        a !== "key" && (i[a] = s[a]);
    } else i = s;
    return s = i.ref, {
      $$typeof: t,
      type: d,
      key: p,
      ref: s !== void 0 ? s : null,
      props: i
    };
  }
  return N.Fragment = n, N.jsx = o, N.jsxs = o, N;
}
var C = {};
/**
 * @license React
 * react-jsx-runtime.development.js
 *
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
var ne;
function Ce() {
  return ne || (ne = 1, process.env.NODE_ENV !== "production" && (function() {
    function t(e) {
      if (e == null) return null;
      if (typeof e == "function")
        return e.$$typeof === he ? null : e.displayName || e.name || null;
      if (typeof e == "string") return e;
      switch (e) {
        case S:
          return "Fragment";
        case A:
          return "Profiler";
        case h:
          return "StrictMode";
        case ue:
          return "Suspense";
        case fe:
          return "SuspenseList";
        case me:
          return "Activity";
        case pe:
          return "ViewTransition";
      }
      if (typeof e == "object")
        switch (typeof e.tag == "number" && console.error(
          "Received an unexpected object in getComponentNameFromType(). This is likely a bug in React. Please file an issue."
        ), e.$$typeof) {
          case w:
            return "Portal";
          case M:
            return e.displayName || "Context";
          case y:
            return (e._context.displayName || "Context") + ".Consumer";
          case de:
            var l = e.render;
            return e = e.displayName, e || (e = l.displayName || l.name || "", e = e !== "" ? "ForwardRef(" + e + ")" : "ForwardRef"), e;
          case xe:
            return l = e.displayName || null, l !== null ? l : t(e.type) || "Memo";
          case B:
            l = e._payload, e = e._init;
            try {
              return t(e(l));
            } catch {
            }
        }
      return null;
    }
    function n(e) {
      return "" + e;
    }
    function o(e) {
      try {
        n(e);
        var l = !1;
      } catch {
        l = !0;
      }
      if (l) {
        l = console;
        var f = l.error, x = typeof Symbol == "function" && Symbol.toStringTag && e[Symbol.toStringTag] || e.constructor.name || "Object";
        return f.call(
          l,
          "The provided key is an unsupported type %s. This value must be coerced to a string before using it here.",
          x
        ), n(e);
      }
    }
    function d(e) {
      if (e === S) return "<>";
      if (typeof e == "object" && e !== null && e.$$typeof === B)
        return "<...>";
      try {
        var l = t(e);
        return l ? "<" + l + ">" : "<...>";
      } catch {
        return "<...>";
      }
    }
    function s() {
      var e = D.A;
      return e === null ? null : e.getOwner();
    }
    function i() {
      return Error("react-stack-top-frame");
    }
    function p(e) {
      if (X.call(e, "key")) {
        var l = Object.getOwnPropertyDescriptor(e, "key").get;
        if (l && l.isReactWarning) return !1;
      }
      return e.key !== void 0;
    }
    function a(e, l) {
      function f() {
        Z || (Z = !0, console.error(
          "%s: `key` is not a prop. Trying to access it will result in `undefined` being returned. If you need to access the same value within the child component, you should pass it as a different prop. (https://react.dev/link/special-props)",
          l
        ));
      }
      f.isReactWarning = !0, Object.defineProperty(e, "key", {
        get: f,
        configurable: !0
      });
    }
    function c() {
      var e = t(this.type);
      return K[e] || (K[e] = !0, console.error(
        "Accessing element.ref was removed in React 19. ref is now a regular prop. It will be removed from the JSX Element type in a future release."
      )), e = this.props.ref, e !== void 0 ? e : null;
    }
    function u(e, l, f, x, v, b) {
      var m = f.ref;
      return e = {
        $$typeof: I,
        type: e,
        key: l,
        props: f,
        _owner: x
      }, (m !== void 0 ? m : null) !== null ? Object.defineProperty(e, "ref", {
        enumerable: !1,
        get: c
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
        value: v
      }), Object.defineProperty(e, "_debugTask", {
        configurable: !1,
        enumerable: !1,
        writable: !0,
        value: b
      }), Object.freeze && (Object.freeze(e.props), Object.freeze(e)), e;
    }
    function g(e, l, f, x, v, b) {
      var m = l.children;
      if (m !== void 0)
        if (x)
          if (ye(m)) {
            for (x = 0; x < m.length; x++)
              j(m[x]);
            Object.freeze && Object.freeze(m);
          } else
            console.error(
              "React.jsx: Static children should always be an array. You are likely explicitly calling React.jsxs or React.jsxDEV. Use the Babel transform instead."
            );
        else j(m);
      if (X.call(l, "key")) {
        m = t(e);
        var R = Object.keys(l).filter(function(ge) {
          return ge !== "key";
        });
        x = 0 < R.length ? "{key: someKey, " + R.join(": ..., ") + ": ...}" : "{key: someKey}", te[m + x] || (R = 0 < R.length ? "{" + R.join(": ..., ") + ": ...}" : "{}", console.error(
          `A props object containing a "key" prop is being spread into JSX:
  let props = %s;
  <%s {...props} />
React keys must be passed directly to JSX without using spread:
  let props = %s;
  <%s key={someKey} {...props} />`,
          x,
          m,
          R,
          m
        ), te[m + x] = !0);
      }
      if (m = null, f !== void 0 && (o(f), m = "" + f), p(l) && (o(l.key), m = "" + l.key), "key" in l) {
        f = {};
        for (var U in l)
          U !== "key" && (f[U] = l[U]);
      } else f = l;
      return m && a(
        f,
        typeof e == "function" ? e.displayName || e.name || "Unknown" : e
      ), u(
        e,
        m,
        f,
        s(),
        v,
        b
      );
    }
    function j(e) {
      T(e) ? e._store && (e._store.validated = 1) : typeof e == "object" && e !== null && e.$$typeof === B && (e._payload.status === "fulfilled" ? T(e._payload.value) && e._payload.value._store && (e._payload.value._store.validated = 1) : e._store && (e._store.validated = 1));
    }
    function T(e) {
      return typeof e == "object" && e !== null && e.$$typeof === I;
    }
    var E = be, I = Symbol.for("react.transitional.element"), w = Symbol.for("react.portal"), S = Symbol.for("react.fragment"), h = Symbol.for("react.strict_mode"), A = Symbol.for("react.profiler"), y = Symbol.for("react.consumer"), M = Symbol.for("react.context"), de = Symbol.for("react.forward_ref"), ue = Symbol.for("react.suspense"), fe = Symbol.for("react.suspense_list"), xe = Symbol.for("react.memo"), B = Symbol.for("react.lazy"), me = Symbol.for("react.activity"), pe = Symbol.for("react.view_transition"), he = Symbol.for("react.client.reference"), D = E.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE, X = Object.prototype.hasOwnProperty, ye = Array.isArray, F = console.createTask ? console.createTask : function() {
      return null;
    };
    E = {
      react_stack_bottom_frame: function(e) {
        return e();
      }
    };
    var Z, K = {}, Q = E.react_stack_bottom_frame.bind(
      E,
      i
    )(), ee = F(d(i)), te = {};
    C.Fragment = S, C.jsx = function(e, l, f) {
      var x = 1e4 > D.recentlyCreatedOwnerStacks++;
      if (x) {
        var v = Error.stackTraceLimit;
        Error.stackTraceLimit = 10;
        var b = Error("react-stack-top-frame");
        Error.stackTraceLimit = v;
      } else b = Q;
      return g(
        e,
        l,
        f,
        !1,
        b,
        x ? F(d(e)) : ee
      );
    }, C.jsxs = function(e, l, f) {
      var x = 1e4 > D.recentlyCreatedOwnerStacks++;
      if (x) {
        var v = Error.stackTraceLimit;
        Error.stackTraceLimit = 10;
        var b = Error("react-stack-top-frame");
        Error.stackTraceLimit = v;
      } else b = Q;
      return g(
        e,
        l,
        f,
        !0,
        b,
        x ? F(d(e)) : ee
      );
    };
  })()), C;
}
var ae;
function ze() {
  return ae || (ae = 1, process.env.NODE_ENV === "production" ? W.exports = Ne() : W.exports = Ce()), W.exports;
}
var r = ze();
const Y = {
  // 卡片表面
  surfaceAlt: "#fafbfc",
  // 次级表面 (表格 hover 等)
  border: "#e5e7eb",
  text: "#111827",
  // 主文本
  textMuted: "#6b7280"
}, ie = {
  md: 12
};
function ce({
  icon: t,
  title: n = "暂无数据",
  description: o,
  actionText: d,
  onAction: s,
  size: i = "md",
  style: p
}) {
  const a = { sm: 24, md: 40, lg: 64 }, c = { sm: 14, md: 16, lg: 20 };
  return /* @__PURE__ */ r.jsxs(
    "div",
    {
      role: "status",
      "aria-live": "polite",
      style: {
        padding: a[i] || a.md,
        textAlign: "center",
        background: "#ffffff",
        borderRadius: ie.md,
        border: "1px dashed #e5e7eb",
        ...p
      },
      children: [
        /* @__PURE__ */ r.jsx("div", { style: {
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "center",
          width: 56,
          height: 56,
          borderRadius: "50%",
          background: Y.surfaceAlt,
          marginBottom: 16,
          fontSize: 28,
          color: "#9ca3af"
        }, children: t || /* @__PURE__ */ r.jsx(Se, {}) }),
        /* @__PURE__ */ r.jsx("div", { style: {
          fontSize: c[i],
          fontWeight: 600,
          color: Y.text,
          marginBottom: o ? 6 : 16
        }, children: n }),
        o && /* @__PURE__ */ r.jsx("div", { style: {
          fontSize: 13,
          color: Y.textMuted,
          lineHeight: 1.6,
          maxWidth: 360,
          margin: "0 auto 16px"
        }, children: o }),
        d && s && /* @__PURE__ */ r.jsx(
          V,
          {
            type: "primary",
            onClick: s,
            style: {
              background: "linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%)",
              border: "none",
              minHeight: 36
            },
            children: d
          }
        )
      ]
    }
  );
}
function $e({
  error: t,
  title: n = "加载失败",
  description: o,
  onRetry: d,
  retryText: s = "重新加载",
  variant: i = "inline",
  style: p
}) {
  var a;
  const c = i === "full", u = (a = t == null ? void 0 : t.response) == null ? void 0 : a.data, g = u && typeof u == "object" && (typeof u.message == "string" && u.message || typeof u.msg == "string" && u.msg) || null, j = g || (typeof t == "string" ? t : (t == null ? void 0 : t.message) || (t == null ? void 0 : t.msg) || (t ? String(t) : null)), T = o || (g ? `原因: ${g}` : j ? `原因: ${j}。请检查网络或稍后重试。` : "请检查网络或稍后重试。");
  return /* @__PURE__ */ r.jsxs(
    "div",
    {
      role: "alert",
      "aria-live": "assertive",
      style: {
        padding: c ? "48px 24px" : "20px 24px",
        background: c ? "#ffffff" : "#fef2f2",
        border: c ? `1px dashed ${Y.border}` : "1px solid #fecaca",
        borderRadius: ie.md,
        textAlign: c ? "center" : "left",
        display: "flex",
        flexDirection: c ? "column" : "row",
        alignItems: c ? "center" : "flex-start",
        gap: c ? 12 : 16,
        ...p
      },
      children: [
        /* @__PURE__ */ r.jsx("div", { style: {
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "center",
          width: c ? 56 : 36,
          height: c ? 56 : 36,
          borderRadius: "50%",
          background: c ? "#fef2f2" : "#ffffff",
          color: "#dc2626",
          fontSize: c ? 28 : 18,
          flexShrink: 0
        }, children: /* @__PURE__ */ r.jsx(Re, {}) }),
        /* @__PURE__ */ r.jsxs("div", { style: { flex: 1, minWidth: 0 }, children: [
          /* @__PURE__ */ r.jsx("div", { style: {
            fontSize: c ? 16 : 14,
            fontWeight: 600,
            color: "#991b1b",
            marginBottom: 4
          }, children: n }),
          /* @__PURE__ */ r.jsx("div", { style: {
            fontSize: 13,
            color: "#7f1d1d",
            lineHeight: 1.6,
            wordBreak: "break-word"
          }, children: T }),
          d && /* @__PURE__ */ r.jsx(
            V,
            {
              type: "primary",
              danger: !0,
              icon: /* @__PURE__ */ r.jsx(se, {}),
              onClick: d,
              size: c ? "middle" : "small",
              style: { marginTop: 12 },
              children: s
            }
          )
        ] })
      ]
    }
  );
}
function q({
  title: t,
  subtitle: n,
  breadcrumb: o,
  extra: d,
  back: s,
  onBack: i
}) {
  const p = je();
  return /* @__PURE__ */ r.jsx("div", { style: {
    marginBottom: 12,
    paddingBottom: 10,
    borderBottom: "1px solid #f1f5f9"
  }, children: /* @__PURE__ */ r.jsxs("div", { style: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 12,
    flexWrap: "wrap"
  }, children: [
    /* @__PURE__ */ r.jsxs("div", { style: {
      display: "flex",
      alignItems: "center",
      gap: 10,
      minWidth: 0,
      flexWrap: "wrap"
    }, children: [
      s && /* @__PURE__ */ r.jsx(
        "button",
        {
          onClick: () => {
            i ? i() : p(-1);
          },
          "aria-label": "返回上一页",
          style: {
            width: 28,
            height: 28,
            borderRadius: 6,
            border: "1px solid #e5e7eb",
            background: "#ffffff",
            color: "#6b7280",
            cursor: "pointer",
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: 13,
            transition: "all 150ms ease",
            flexShrink: 0
          },
          onMouseEnter: (a) => {
            a.currentTarget.style.background = "#f8fafc", a.currentTarget.style.color = "#111827", a.currentTarget.style.borderColor = "#7c3aed";
          },
          onMouseLeave: (a) => {
            a.currentTarget.style.background = "#ffffff", a.currentTarget.style.color = "#6b7280", a.currentTarget.style.borderColor = "#e5e7eb";
          },
          children: "←"
        }
      ),
      o && o.length > 0 ? /* @__PURE__ */ r.jsx(
        Te,
        {
          items: o.map((a, c) => {
            const u = c === o.length - 1;
            return {
              title: a.href && !u ? /* @__PURE__ */ r.jsx("a", { onClick: (g) => {
                g.preventDefault(), p(a.href);
              }, children: a.label }) : /* @__PURE__ */ r.jsx("span", { style: u ? {
                fontWeight: 600,
                color: "#111827"
              } : void 0, children: a.label })
            };
          })
        }
      ) : t && /* @__PURE__ */ r.jsx("span", { role: "heading", "aria-level": 1, style: {
        fontSize: 15,
        fontWeight: 600,
        color: "#111827",
        lineHeight: 1.4
      }, children: t }),
      n && /* @__PURE__ */ r.jsx("span", { title: n, style: {
        fontSize: 12,
        color: "#94a3b8",
        lineHeight: 1.4,
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
        maxWidth: 360
      }, children: n })
    ] }),
    d && /* @__PURE__ */ r.jsx("div", { style: {
      display: "flex",
      gap: 8,
      flexShrink: 0
    }, children: d })
  ] }) });
}
function H({ title: t, description: n, load: o, columns: d, rowKey: s = "id", extra: i, expandable: p }) {
  const [a, c] = _([]), [u, g] = _({ current: 1, size: 10, total: null }), [j, T] = _(!1), [E, I] = _(null), w = _e(async (h = u.current, A = u.size) => {
    T(!0), I(null);
    try {
      const y = await o(h, A);
      if (Array.isArray(y))
        c(y || []), g({ current: h, size: A, total: y.length });
      else {
        const M = (y == null ? void 0 : y.records) || [];
        c(M), g({ current: h, size: A, total: (y == null ? void 0 : y.total) == null ? null : Number(y.total) });
      }
    } catch (y) {
      c([]), I(y);
    } finally {
      T(!1);
    }
  }, [o, u.current, u.size]);
  G(() => {
    w(1);
  }, []);
  const S = u.total != null && u.total > 0;
  return /* @__PURE__ */ r.jsxs("div", { children: [
    /* @__PURE__ */ r.jsx(q, { title: t, subtitle: n }),
    /* @__PURE__ */ r.jsxs(
      le,
      {
        extra: /* @__PURE__ */ r.jsx(
          V,
          {
            icon: /* @__PURE__ */ r.jsx(se, {}),
            loading: j,
            onClick: () => w(u.current, u.size),
            children: "刷新"
          }
        ),
        children: [
          i,
          E ? /* @__PURE__ */ r.jsx($e, { error: E, onRetry: () => w(u.current, u.size), type: "inline" }) : /* @__PURE__ */ r.jsx(
            oe,
            {
              size: "small",
              columns: d,
              dataSource: a,
              loading: j,
              rowKey: (h) => (h == null ? void 0 : h[s]) ?? (h == null ? void 0 : h.key) ?? JSON.stringify(h).slice(0, 32),
              pagination: {
                current: u.current,
                pageSize: u.size,
                total: S ? u.total : a.length,
                showSizeChanger: !0,
                showTotal: () => S ? `共 ${u.total} 条` : `本页 ${a.length} 条（接口 total 未返回有效值）`
              },
              onChange: (h) => w(h.current, h.pageSize),
              expandable: p,
              locale: { emptyText: /* @__PURE__ */ r.jsx(ce, { title: "暂无数据", description: "接口返回空列表" }) }
            }
          )
        ]
      }
    )
  ] });
}
const Le = { 0: { text: "未读", color: "blue" }, 1: { text: "已读", color: "default" } };
function We({ userId: t }) {
  const [n, o] = _([]), [d, s] = _(!1), [i, p] = _(null);
  return G(() => {
    if (!t) return;
    let a = !1;
    return s(!0), z.inbox(t, 50).then((c) => {
      a || (o(c || []), p(null));
    }).catch((c) => {
      a || (o([]), p(c));
    }).finally(() => {
      a || s(!1);
    }), () => {
      a = !0;
    };
  }, [t]), t ? /* @__PURE__ */ r.jsxs("div", { children: [
    /* @__PURE__ */ r.jsx(
      q,
      {
        title: "站内信",
        description: `当前用户 ${t} 的收件箱（数据源 GET /api/msg/list?userId=${t}）`
      }
    ),
    /* @__PURE__ */ r.jsx(le, { children: i ? /* @__PURE__ */ r.jsx(
      J,
      {
        type: "error",
        showIcon: !0,
        message: `站内信读取失败：${i.message || i}`
      }
    ) : /* @__PURE__ */ r.jsx(
      oe,
      {
        size: "small",
        rowKey: "id",
        loading: d,
        dataSource: n,
        pagination: { pageSize: 20, showTotal: (a) => `共 ${a} 条` },
        columns: [
          { title: "标题", dataIndex: "title", key: "title" },
          { title: "内容", dataIndex: "content", key: "content", ellipsis: !0 },
          {
            title: "事件类型",
            dataIndex: "event_type",
            key: "event_type",
            width: 150,
            render: (a) => /* @__PURE__ */ r.jsx(k, { children: a })
          },
          {
            title: "已读",
            dataIndex: "is_read",
            key: "is_read",
            width: 90,
            render: (a) => {
              const c = Le[a] || { text: `未知(${a})`, color: "default" };
              return /* @__PURE__ */ r.jsx(k, { color: c.color, children: c.text });
            }
          },
          { title: "时间", dataIndex: "created_time", key: "created_time", width: 180 }
        ],
        locale: { emptyText: /* @__PURE__ */ r.jsx(ce, { title: "收件箱为空", description: "接口返回空列表" }) }
      }
    ) })
  ] }) : /* @__PURE__ */ r.jsxs("div", { children: [
    /* @__PURE__ */ r.jsx(q, { title: "站内信" }),
    /* @__PURE__ */ r.jsx(
      J,
      {
        type: "warning",
        showIcon: !0,
        message: "未取到当前登录用户 id",
        description: "GET /api/msg/list 需要 userId。本页依次读 localStorage 的 userInfo 与 USER_INFO（staffId/jobNumber），两个都没有才会走到这里 —— 请重新登录。"
      }
    )
  ] });
}
function Ye() {
  return /* @__PURE__ */ r.jsx(
    H,
    {
      title: "消息模板",
      description: "按渠道/业务类型维护的消息模板（数据源 POST /api/msg/template/list）",
      load: (t, n) => z.templatePage(t, n),
      columns: [
        { title: "业务类型", dataIndex: "biz_type", key: "biz_type", width: 160 },
        {
          title: "渠道",
          dataIndex: "channel",
          key: "channel",
          width: 120,
          render: (t) => /* @__PURE__ */ r.jsx(k, { color: "geekblue", children: t })
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
            const n = Pe[t] || { text: `未知(${t})`, color: "default" };
            return /* @__PURE__ */ r.jsx(k, { color: n.color, children: n.text });
          }
        },
        { title: "更新时间", dataIndex: "updated_time", key: "updated_time", width: 180 }
      ]
    }
  );
}
function Me() {
  const [t, n] = _(null), [o, d] = _(null);
  return G(() => {
    let s = !1;
    return z.deliveryStats().then((i) => {
      s || n(i);
    }).catch((i) => {
      s || d(i);
    }), () => {
      s = !0;
    };
  }, []), o ? /* @__PURE__ */ r.jsx(
    J,
    {
      type: "warning",
      showIcon: !0,
      style: { marginBottom: 12 },
      message: `投递统计读取失败：${o.message || o}`
    }
  ) : t ? /* @__PURE__ */ r.jsxs(Ee, { gutter: 16, style: { marginBottom: 12 }, children: [
    /* @__PURE__ */ r.jsx($, { span: 6, children: /* @__PURE__ */ r.jsx(L, { title: "投递总数", value: t.total ?? 0 }) }),
    /* @__PURE__ */ r.jsx($, { span: 6, children: /* @__PURE__ */ r.jsx(L, { title: "成功", value: t.success ?? 0, valueStyle: { color: "#3f8600" } }) }),
    /* @__PURE__ */ r.jsx($, { span: 6, children: /* @__PURE__ */ r.jsx(L, { title: "失败", value: t.failed ?? 0, valueStyle: { color: "#cf1322" } }) }),
    /* @__PURE__ */ r.jsx($, { span: 6, children: /* @__PURE__ */ r.jsx(
      L,
      {
        title: "成功率",
        value: Number(t.total) > 0 ? (Number(t.success) * 100 / Number(t.total)).toFixed(1) : "0.0",
        suffix: "%"
      }
    ) })
  ] }) : null;
}
function Be() {
  return /* @__PURE__ */ r.jsx(
    H,
    {
      title: "投递日志",
      description: "每条消息按渠道投递的结果与失败原因（数据源 POST /api/msg/delivery/list + GET /api/msg/delivery/stats）",
      load: (t, n) => z.deliveryPage(t, n),
      extra: /* @__PURE__ */ r.jsx(Me, {}),
      columns: [
        { title: "业务类型", dataIndex: "biz_type", key: "biz_type", width: 140 },
        { title: "渠道", dataIndex: "channel", key: "channel", width: 110, render: (t) => /* @__PURE__ */ r.jsx(k, { children: t }) },
        { title: "接收方", dataIndex: "receiver", key: "receiver", width: 140 },
        { title: "主题", dataIndex: "rendered_subject", key: "rendered_subject", width: 180, ellipsis: !0 },
        {
          title: "状态",
          dataIndex: "status",
          key: "status",
          width: 100,
          render: (t) => {
            const n = Ae[t] || { text: `未知(${t})`, color: "default" };
            return /* @__PURE__ */ r.jsx(k, { color: n.color, children: n.text });
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
function De() {
  return /* @__PURE__ */ r.jsx(
    H,
    {
      title: "批量发送任务",
      description: "批量任务的进度与失败数（数据源 POST /api/msg/batch/list）",
      load: (t, n) => z.batchPage(t, n),
      columns: [
        { title: "业务类型", dataIndex: "biz_type", key: "biz_type", width: 150 },
        { title: "渠道", dataIndex: "channel", key: "channel", width: 110, render: (t) => /* @__PURE__ */ r.jsx(k, { children: t }) },
        {
          title: "进度",
          key: "progress",
          width: 160,
          render: (t, n) => {
            const o = Number(n.total_count) || 0, d = (Number(n.success_count) || 0) + (Number(n.failed_count) || 0);
            return /* @__PURE__ */ r.jsx(we, { percent: o ? Math.round(d * 100 / o) : 0, size: "small" });
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
            const n = Oe[t] || { text: `未知(${t})`, color: "default" };
            return /* @__PURE__ */ r.jsx(k, { color: n.color, children: n.text });
          }
        },
        { title: "失败原因", dataIndex: "error_message", key: "error_message", ellipsis: !0, render: (t) => t || "-" },
        { title: "创建时间", dataIndex: "created_time", key: "created_time", width: 180 },
        { title: "结束时间", dataIndex: "finished_time", key: "finished_time", width: 180, render: (t) => t || "-" }
      ]
    }
  );
}
function Ve() {
  const t = Ie();
  return /* @__PURE__ */ r.jsxs(ve, { children: [
    /* @__PURE__ */ r.jsx(O, { index: !0, element: /* @__PURE__ */ r.jsx(ke, { to: "inbox", replace: !0 }) }),
    /* @__PURE__ */ r.jsx(O, { path: "inbox", element: /* @__PURE__ */ r.jsx(We, { userId: t }) }),
    /* @__PURE__ */ r.jsx(O, { path: "template", element: /* @__PURE__ */ r.jsx(Ye, {}) }),
    /* @__PURE__ */ r.jsx(O, { path: "delivery", element: /* @__PURE__ */ r.jsx(Be, {}) }),
    /* @__PURE__ */ r.jsx(O, { path: "batch", element: /* @__PURE__ */ r.jsx(De, {}) })
  ] });
}
export {
  Oe as B,
  Ae as D,
  We as I,
  Ve as M,
  Pe as T,
  Ye as a,
  Be as b,
  Ie as c,
  De as d,
  r as j,
  z as m
};
