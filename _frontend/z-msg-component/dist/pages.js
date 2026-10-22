import { j as e, I as f, a as p, b as d, d as x, C as u, M as y } from "./ComponentDoc-cr_vHXJJ.js";
import { UserOutlined as j, LockOutlined as b, HomeOutlined as z, MailOutlined as k, SendOutlined as v, ReadOutlined as C } from "@ant-design/icons";
import { Typography as S, Card as l, Space as c, Tag as I, Row as w, Col as T, Form as i, Input as m, Button as O, message as g } from "antd";
import { useNavigate as M } from "react-router-dom";
import { useState as h, useEffect as B } from "react";
const { Title: L, Paragraph: P } = S;
function R() {
  const a = M(), [t, n] = h(null);
  B(() => {
    const s = localStorage.getItem("userInfo");
    if (s)
      try {
        n(JSON.parse(s));
      } catch {
        n({ name: s });
      }
  }, []);
  const o = A.filter((s) => s.key !== "/z-msg/home");
  return /* @__PURE__ */ e.jsxs("div", { children: [
    /* @__PURE__ */ e.jsx(l, { style: { marginBottom: 16, background: "linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%)", border: "none" }, children: /* @__PURE__ */ e.jsxs(c, { direction: "vertical", size: 4, style: { color: "#fff" }, children: [
      /* @__PURE__ */ e.jsxs(L, { level: 3, style: { color: "#fff", margin: 0 }, children: [
        "欢迎",
        t != null && t.name ? `，${t.name}` : ""
      ] }),
      /* @__PURE__ */ e.jsx(P, { style: { color: "rgba(255,255,255,0.85)", margin: 0 }, children: "z-msg 消息中心 管理台" }),
      (t == null ? void 0 : t.role) && /* @__PURE__ */ e.jsx(I, { style: { marginTop: 8, background: "rgba(255,255,255,0.2)", color: "#fff", border: "none" }, children: t.role })
    ] }) }),
    /* @__PURE__ */ e.jsx(w, { gutter: [16, 16], children: o.map((s) => /* @__PURE__ */ e.jsx(T, { xs: 24, sm: 12, md: 12, lg: 8, children: /* @__PURE__ */ e.jsx(l, { hoverable: !0, onClick: () => a(s.key), style: { borderTop: "3px solid #7c3aed" }, children: /* @__PURE__ */ e.jsxs(c, { align: "start", size: 12, children: [
      /* @__PURE__ */ e.jsx("div", { style: {
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
      }, children: s.icon }),
      /* @__PURE__ */ e.jsxs("div", { style: { minWidth: 0 }, children: [
        /* @__PURE__ */ e.jsx("div", { style: { fontSize: 15, fontWeight: 600, color: "#0f172a" }, children: s.label }),
        /* @__PURE__ */ e.jsx("div", { style: { fontSize: 12, color: "#94a3b8", marginTop: 2 }, children: s.key })
      ] })
    ] }) }) }, s.key)) })
  ] });
}
const r = { username: "admin", password: "123456", user: { name: "admin", role: "管理员" } };
function D() {
  const [a, t] = h(!1), n = async (o) => {
    t(!0);
    try {
      o.username === r.username && o.password === r.password ? (localStorage.setItem("token", "mock-" + r.username), localStorage.setItem("userInfo", JSON.stringify(r.user)), g.success("登录成功（mock 模式，待 CTC/SSO 接入）"), window.location.href = "/z-msg/home") : g.error("用户名或密码错误（默认 admin/123456）");
    } finally {
      t(!1);
    }
  };
  return /* @__PURE__ */ e.jsx("div", { style: {
    height: "100vh",
    display: "flex",
    justifyContent: "center",
    alignItems: "center",
    background: "linear-gradient(135deg, #eef2ff 0%, #f5f3ff 50%, #eff6ff 100%)"
  }, children: /* @__PURE__ */ e.jsxs(l, { style: { width: 400, boxShadow: "0 10px 40px rgba(124,58,237,0.12)" }, children: [
    /* @__PURE__ */ e.jsxs("div", { style: { textAlign: "center", marginBottom: 20 }, children: [
      /* @__PURE__ */ e.jsx("div", { style: {
        width: 48,
        height: 48,
        borderRadius: 12,
        margin: "0 auto 12px",
        background: "linear-gradient(135deg, #7c3aed, #6d28d9)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        color: "#fff",
        fontSize: 24,
        fontWeight: 700
      }, children: "z" }),
      /* @__PURE__ */ e.jsx("div", { style: { fontSize: 18, fontWeight: 600, color: "#0f172a" }, children: "z-msg 管理台" })
    ] }),
    /* @__PURE__ */ e.jsxs(i, { onFinish: n, size: "large", children: [
      /* @__PURE__ */ e.jsx(i.Item, { name: "username", rules: [{ required: !0, message: "请输入用户名" }], children: /* @__PURE__ */ e.jsx(m, { prefix: /* @__PURE__ */ e.jsx(j, {}), placeholder: "用户名" }) }),
      /* @__PURE__ */ e.jsx(i.Item, { name: "password", rules: [{ required: !0, message: "请输入密码" }], children: /* @__PURE__ */ e.jsx(m.Password, { prefix: /* @__PURE__ */ e.jsx(b, {}), placeholder: "密码" }) }),
      /* @__PURE__ */ e.jsx(i.Item, { style: { marginBottom: 8 }, children: /* @__PURE__ */ e.jsx(
        O,
        {
          type: "primary",
          htmlType: "submit",
          loading: a,
          block: !0,
          style: { background: "linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%)", border: "none" },
          children: "登 录"
        }
      ) }),
      /* @__PURE__ */ e.jsxs("div", { style: { textAlign: "center", color: "#94a3b8", fontSize: 12 }, children: [
        "Mock 模式：用户名 ",
        /* @__PURE__ */ e.jsx("b", { children: r.username }),
        " / 密码 ",
        /* @__PURE__ */ e.jsx("b", { children: r.password })
      ] })
    ] })
  ] }) });
}
const E = { title: "z-msg 消息中心", short: "z-msg" }, A = [
  { key: "/z-msg/home", label: "首页", icon: /* @__PURE__ */ e.jsx(z, {}) },
  { key: "/z-msg/templates", label: "消息模板", icon: /* @__PURE__ */ e.jsx(k, {}) },
  { key: "/z-msg/delivery", label: "投递记录", icon: /* @__PURE__ */ e.jsx(v, {}) },
  { key: "/z-msg/docs", label: "组件文档", icon: /* @__PURE__ */ e.jsx(C, {}) }
], F = [
  { path: "/z-msg/home", Component: R },
  { path: "/z-msg/inbox", title: "收件箱", order: 1, Component: f },
  { path: "/z-msg/templates", title: "消息模板", order: 2, Component: p },
  { path: "/z-msg/delivery", title: "投递记录", order: 3, Component: d },
  { path: "/z-msg/batch", title: "批量任务", order: 4, Component: x },
  { path: "/z-msg/docs", Component: u },
  { path: "/z-msg/:rest*", Component: y },
  { path: "/z-msg/delivery", Component: d }
];
export {
  u as ComponentDocPage,
  d as DeliveryLogs,
  R as HomePage,
  D as LoginPage,
  p as TemplateList,
  E as appMeta,
  A as menuItems,
  F as routes
};
