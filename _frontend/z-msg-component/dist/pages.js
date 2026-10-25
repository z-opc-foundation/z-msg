import { j as e, I as h, a as f, b as p, d as x, C as u, M as y } from "./ComponentDoc-Cw0037IF.js";
import { UserOutlined as j, LockOutlined as b, HomeOutlined as z, InboxOutlined as k, MailOutlined as S, SendOutlined as v, UnorderedListOutlined as C, ReadOutlined as I } from "@ant-design/icons";
import { Typography as w, Card as l, Space as d, Tag as O, Row as T, Col as L, Form as i, Input as c, Button as M, message as m } from "antd";
import { useNavigate as B } from "react-router-dom";
import { useState as g, useEffect as P } from "react";
const { Title: R, Paragraph: A } = w;
function U() {
  const a = B(), [t, r] = g(null);
  P(() => {
    const s = localStorage.getItem("userInfo");
    if (s)
      try {
        r(JSON.parse(s));
      } catch {
        r({ name: s });
      }
  }, []);
  const o = W.filter((s) => s.key !== "/z-msg/home");
  return /* @__PURE__ */ e.jsxs("div", { children: [
    /* @__PURE__ */ e.jsx(l, { style: { marginBottom: 16, background: "linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%)", border: "none" }, children: /* @__PURE__ */ e.jsxs(d, { direction: "vertical", size: 4, style: { color: "#fff" }, children: [
      /* @__PURE__ */ e.jsxs(R, { level: 3, style: { color: "#fff", margin: 0 }, children: [
        "欢迎",
        t != null && t.name ? `，${t.name}` : ""
      ] }),
      /* @__PURE__ */ e.jsx(A, { style: { color: "rgba(255,255,255,0.85)", margin: 0 }, children: "z-msg 消息中心 管理台" }),
      (t == null ? void 0 : t.role) && /* @__PURE__ */ e.jsx(O, { style: { marginTop: 8, background: "rgba(255,255,255,0.2)", color: "#fff", border: "none" }, children: t.role })
    ] }) }),
    /* @__PURE__ */ e.jsx(T, { gutter: [16, 16], children: o.map((s) => /* @__PURE__ */ e.jsx(L, { xs: 24, sm: 12, md: 12, lg: 8, children: /* @__PURE__ */ e.jsx(l, { hoverable: !0, onClick: () => a(s.key), style: { borderTop: "3px solid #7c3aed" }, children: /* @__PURE__ */ e.jsxs(d, { align: "start", size: 12, children: [
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
const n = { username: "admin", password: "123456", user: { name: "admin", role: "管理员" } };
function F() {
  const [a, t] = g(!1), r = async (o) => {
    t(!0);
    try {
      o.username === n.username && o.password === n.password ? (localStorage.setItem("token", "mock-" + n.username), localStorage.setItem("userInfo", JSON.stringify(n.user)), m.success("登录成功（mock 模式，待 CTC/SSO 接入）"), window.location.href = "/z-msg/home") : m.error("用户名或密码错误（默认 admin/123456）");
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
    /* @__PURE__ */ e.jsxs(i, { onFinish: r, size: "large", children: [
      /* @__PURE__ */ e.jsx(i.Item, { name: "username", rules: [{ required: !0, message: "请输入用户名" }], children: /* @__PURE__ */ e.jsx(c, { prefix: /* @__PURE__ */ e.jsx(j, {}), placeholder: "用户名" }) }),
      /* @__PURE__ */ e.jsx(i.Item, { name: "password", rules: [{ required: !0, message: "请输入密码" }], children: /* @__PURE__ */ e.jsx(c.Password, { prefix: /* @__PURE__ */ e.jsx(b, {}), placeholder: "密码" }) }),
      /* @__PURE__ */ e.jsx(i.Item, { style: { marginBottom: 8 }, children: /* @__PURE__ */ e.jsx(
        M,
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
        /* @__PURE__ */ e.jsx("b", { children: n.username }),
        " / 密码 ",
        /* @__PURE__ */ e.jsx("b", { children: n.password })
      ] })
    ] })
  ] }) });
}
const J = { title: "z-msg 消息中心", short: "z-msg" }, W = [
  { key: "/z-msg/home", label: "首页", icon: /* @__PURE__ */ e.jsx(z, {}) },
  { key: "/z-msg/inbox", label: "收件箱", icon: /* @__PURE__ */ e.jsx(k, {}) },
  { key: "/z-msg/templates", label: "消息模板", icon: /* @__PURE__ */ e.jsx(S, {}) },
  { key: "/z-msg/delivery", label: "投递记录", icon: /* @__PURE__ */ e.jsx(v, {}) },
  { key: "/z-msg/batch", label: "批量任务", icon: /* @__PURE__ */ e.jsx(C, {}) },
  { key: "/z-msg/docs", label: "组件文档", icon: /* @__PURE__ */ e.jsx(I, {}) }
], K = [
  { path: "/z-msg/home", Component: U },
  { path: "/z-msg/inbox", title: "收件箱", order: 1, Component: h },
  { path: "/z-msg/templates", title: "消息模板", order: 2, Component: f },
  { path: "/z-msg/delivery", title: "投递记录", order: 3, Component: p },
  { path: "/z-msg/batch", title: "批量任务", order: 4, Component: x },
  { path: "/z-msg/docs", Component: u },
  { path: "/z-msg/:rest*", Component: y }
];
export {
  u as ComponentDocPage,
  p as DeliveryLogs,
  U as HomePage,
  F as LoginPage,
  f as TemplateList,
  J as appMeta,
  W as menuItems,
  K as routes
};
