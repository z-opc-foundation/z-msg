import { j as e, I as f, a as p, b as d, d as x, M as u } from "./MsgApp-C8GydUG9.js";
import { UserOutlined as y, LockOutlined as j, HomeOutlined as b, MailOutlined as z, SendOutlined as k } from "@ant-design/icons";
import { Typography as v, Card as l, Space as c, Tag as S, Row as C, Col as I, Form as i, Input as m, Button as w, message as g } from "antd";
import { useNavigate as T } from "react-router-dom";
import { useState as h, useEffect as O } from "react";
const { Title: M, Paragraph: B } = v;
function L() {
  const a = T(), [t, n] = h(null);
  O(() => {
    const r = localStorage.getItem("userInfo");
    if (r)
      try {
        n(JSON.parse(r));
      } catch {
        n({ name: r });
      }
  }, []);
  const o = A.filter((r) => r.key !== "/z-msg/home");
  return /* @__PURE__ */ e.jsxs("div", { children: [
    /* @__PURE__ */ e.jsx(l, { style: { marginBottom: 16, background: "linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%)", border: "none" }, children: /* @__PURE__ */ e.jsxs(c, { direction: "vertical", size: 4, style: { color: "#fff" }, children: [
      /* @__PURE__ */ e.jsxs(M, { level: 3, style: { color: "#fff", margin: 0 }, children: [
        "欢迎",
        t != null && t.name ? `，${t.name}` : ""
      ] }),
      /* @__PURE__ */ e.jsx(B, { style: { color: "rgba(255,255,255,0.85)", margin: 0 }, children: "z-msg 消息中心 管理台" }),
      (t == null ? void 0 : t.role) && /* @__PURE__ */ e.jsx(S, { style: { marginTop: 8, background: "rgba(255,255,255,0.2)", color: "#fff", border: "none" }, children: t.role })
    ] }) }),
    /* @__PURE__ */ e.jsx(C, { gutter: [16, 16], children: o.map((r) => /* @__PURE__ */ e.jsx(I, { xs: 24, sm: 12, md: 12, lg: 8, children: /* @__PURE__ */ e.jsx(l, { hoverable: !0, onClick: () => a(r.key), style: { borderTop: "3px solid #7c3aed" }, children: /* @__PURE__ */ e.jsxs(c, { align: "start", size: 12, children: [
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
      }, children: r.icon }),
      /* @__PURE__ */ e.jsxs("div", { style: { minWidth: 0 }, children: [
        /* @__PURE__ */ e.jsx("div", { style: { fontSize: 15, fontWeight: 600, color: "#0f172a" }, children: r.label }),
        /* @__PURE__ */ e.jsx("div", { style: { fontSize: 12, color: "#94a3b8", marginTop: 2 }, children: r.key })
      ] })
    ] }) }) }, r.key)) })
  ] });
}
const s = { username: "admin", password: "123456", user: { name: "admin", role: "管理员" } };
function U() {
  const [a, t] = h(!1), n = async (o) => {
    t(!0);
    try {
      o.username === s.username && o.password === s.password ? (localStorage.setItem("token", "mock-" + s.username), localStorage.setItem("userInfo", JSON.stringify(s.user)), g.success("登录成功（mock 模式，待 CTC/SSO 接入）"), window.location.href = "/z-msg/home") : g.error("用户名或密码错误（默认 admin/123456）");
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
      /* @__PURE__ */ e.jsx(i.Item, { name: "username", rules: [{ required: !0, message: "请输入用户名" }], children: /* @__PURE__ */ e.jsx(m, { prefix: /* @__PURE__ */ e.jsx(y, {}), placeholder: "用户名" }) }),
      /* @__PURE__ */ e.jsx(i.Item, { name: "password", rules: [{ required: !0, message: "请输入密码" }], children: /* @__PURE__ */ e.jsx(m.Password, { prefix: /* @__PURE__ */ e.jsx(j, {}), placeholder: "密码" }) }),
      /* @__PURE__ */ e.jsx(i.Item, { style: { marginBottom: 8 }, children: /* @__PURE__ */ e.jsx(
        w,
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
        /* @__PURE__ */ e.jsx("b", { children: s.username }),
        " / 密码 ",
        /* @__PURE__ */ e.jsx("b", { children: s.password })
      ] })
    ] })
  ] }) });
}
const q = { title: "z-msg 消息中心", short: "z-msg" }, A = [
  { key: "/z-msg/home", label: "首页", icon: /* @__PURE__ */ e.jsx(b, {}) },
  { key: "/z-msg/templates", label: "消息模板", icon: /* @__PURE__ */ e.jsx(z, {}) },
  { key: "/z-msg/delivery", label: "投递记录", icon: /* @__PURE__ */ e.jsx(k, {}) }
], E = [
  { path: "/z-msg/home", Component: L },
  { path: "/z-msg/inbox", title: "收件箱", order: 1, Component: f },
  { path: "/z-msg/templates", title: "消息模板", order: 2, Component: p },
  { path: "/z-msg/delivery", title: "投递记录", order: 3, Component: d },
  { path: "/z-msg/batch", title: "批量任务", order: 4, Component: x },
  { path: "/z-msg/:rest*", Component: u },
  { path: "/z-msg/delivery", Component: d }
];
export {
  d as DeliveryLogs,
  L as HomePage,
  U as LoginPage,
  p as TemplateList,
  q as appMeta,
  A as menuItems,
  E as routes
};
