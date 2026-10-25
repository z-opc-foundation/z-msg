import { j as e, I as h, a as f, b as p, d as x, C as u, M as y } from "./ComponentDoc-Cw0037IF.js";
import { UserOutlined as j, LockOutlined as b, HomeOutlined as z, InboxOutlined as k, MailOutlined as S, SendOutlined as C, UnorderedListOutlined as I, ReadOutlined as v } from "@ant-design/icons";
import { Typography as O, Card as d, Space as c, Tag as T, Row as w, Col as M, Form as i, Input as m, Button as R, message as a } from "antd";
import { useNavigate as _ } from "react-router-dom";
import { useState as g, useEffect as E } from "react";
const { Title: P, Paragraph: A } = O;
function L() {
  const l = _(), [t, n] = g(null);
  E(() => {
    const r = localStorage.getItem("userInfo");
    if (r)
      try {
        n(JSON.parse(r));
      } catch {
        n({ name: r });
      }
  }, []);
  const o = U.filter((r) => r.key !== "/z-msg/home");
  return /* @__PURE__ */ e.jsxs("div", { children: [
    /* @__PURE__ */ e.jsx(d, { style: { marginBottom: 16, background: "linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%)", border: "none" }, children: /* @__PURE__ */ e.jsxs(c, { direction: "vertical", size: 4, style: { color: "#fff" }, children: [
      /* @__PURE__ */ e.jsxs(P, { level: 3, style: { color: "#fff", margin: 0 }, children: [
        "欢迎",
        t != null && t.name ? `，${t.name}` : ""
      ] }),
      /* @__PURE__ */ e.jsx(A, { style: { color: "rgba(255,255,255,0.85)", margin: 0 }, children: "z-msg 消息中心 管理台" }),
      (t == null ? void 0 : t.role) && /* @__PURE__ */ e.jsx(T, { style: { marginTop: 8, background: "rgba(255,255,255,0.2)", color: "#fff", border: "none" }, children: t.role })
    ] }) }),
    /* @__PURE__ */ e.jsx(w, { gutter: [16, 16], children: o.map((r) => /* @__PURE__ */ e.jsx(M, { xs: 24, sm: 12, md: 12, lg: 8, children: /* @__PURE__ */ e.jsx(d, { hoverable: !0, onClick: () => l(r.key), style: { borderTop: "3px solid #7c3aed" }, children: /* @__PURE__ */ e.jsxs(c, { align: "start", size: 12, children: [
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
const s = {
  username: "",
  password: "",
  user: { name: "", role: "管理员" }
};
function H() {
  const [l, t] = g(!1), n = async (o) => {
    t(!0);
    try {
      if (!s.username || !s.password) {
        a.error("未配置 mock 账号（VITE_MOCK_USER / VITE_MOCK_PASSWORD）");
        return;
      }
      o.username === s.username && o.password === s.password ? (localStorage.setItem("token", "mock-" + s.username), localStorage.setItem("userInfo", JSON.stringify(s.user)), a.success("登录成功（mock 模式，待 CTC/SSO 接入）"), window.location.href = "/z-msg/home") : a.error("用户名或密码错误");
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
  }, children: /* @__PURE__ */ e.jsxs(d, { style: { width: 400, boxShadow: "0 10px 40px rgba(124,58,237,0.12)" }, children: [
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
        R,
        {
          type: "primary",
          htmlType: "submit",
          loading: l,
          block: !0,
          style: { background: "linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%)", border: "none" },
          children: "登 录"
        }
      ) }),
      /* @__PURE__ */ e.jsx("div", { style: { textAlign: "center", color: "#94a3b8", fontSize: 12 }, children: "Mock 模式：账号由宿主注入（VITE_MOCK_USER / VITE_MOCK_PASSWORD）" })
    ] })
  ] }) });
}
const N = { title: "z-msg 消息中心", short: "z-msg" }, U = [
  { key: "/z-msg/home", label: "首页", icon: /* @__PURE__ */ e.jsx(z, {}) },
  { key: "/z-msg/inbox", label: "收件箱", icon: /* @__PURE__ */ e.jsx(k, {}) },
  { key: "/z-msg/templates", label: "消息模板", icon: /* @__PURE__ */ e.jsx(S, {}) },
  { key: "/z-msg/delivery", label: "投递记录", icon: /* @__PURE__ */ e.jsx(C, {}) },
  { key: "/z-msg/batch", label: "批量任务", icon: /* @__PURE__ */ e.jsx(I, {}) },
  { key: "/z-msg/docs", label: "组件文档", icon: /* @__PURE__ */ e.jsx(v, {}) }
], q = [
  { path: "/z-msg/home", Component: L },
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
  L as HomePage,
  H as LoginPage,
  f as TemplateList,
  N as appMeta,
  U as menuItems,
  q as routes
};
