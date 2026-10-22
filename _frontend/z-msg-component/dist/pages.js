import { j as e, I as d, a as c, b as n, d as m, M as g } from "./MsgApp-6s4LXk9h.js";
import { HomeOutlined as p, MailOutlined as h, SendOutlined as f } from "@ant-design/icons";
import { Typography as x, Card as s, Space as i, Tag as y, Row as b, Col as j } from "antd";
import { useNavigate as u } from "react-router-dom";
import { useState as z, useEffect as v } from "react";
const { Title: k, Paragraph: C } = x;
function S() {
  const l = u(), [o, r] = z(null);
  v(() => {
    const t = localStorage.getItem("userInfo");
    if (t)
      try {
        r(JSON.parse(t));
      } catch {
        r({ name: t });
      }
  }, []);
  const a = T.filter((t) => t.key !== "/z-msg/home");
  return /* @__PURE__ */ e.jsxs("div", { children: [
    /* @__PURE__ */ e.jsx(s, { style: { marginBottom: 16, background: "linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%)", border: "none" }, children: /* @__PURE__ */ e.jsxs(i, { direction: "vertical", size: 4, style: { color: "#fff" }, children: [
      /* @__PURE__ */ e.jsxs(k, { level: 3, style: { color: "#fff", margin: 0 }, children: [
        "欢迎",
        o != null && o.name ? `，${o.name}` : ""
      ] }),
      /* @__PURE__ */ e.jsx(C, { style: { color: "rgba(255,255,255,0.85)", margin: 0 }, children: "z-msg 消息中心 管理台" }),
      (o == null ? void 0 : o.role) && /* @__PURE__ */ e.jsx(y, { style: { marginTop: 8, background: "rgba(255,255,255,0.2)", color: "#fff", border: "none" }, children: o.role })
    ] }) }),
    /* @__PURE__ */ e.jsx(b, { gutter: [16, 16], children: a.map((t) => /* @__PURE__ */ e.jsx(j, { xs: 24, sm: 12, md: 12, lg: 8, children: /* @__PURE__ */ e.jsx(s, { hoverable: !0, onClick: () => l(t.key), style: { borderTop: "3px solid #7c3aed" }, children: /* @__PURE__ */ e.jsxs(i, { align: "start", size: 12, children: [
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
      }, children: t.icon }),
      /* @__PURE__ */ e.jsxs("div", { style: { minWidth: 0 }, children: [
        /* @__PURE__ */ e.jsx("div", { style: { fontSize: 15, fontWeight: 600, color: "#0f172a" }, children: t.label }),
        /* @__PURE__ */ e.jsx("div", { style: { fontSize: 12, color: "#94a3b8", marginTop: 2 }, children: t.key })
      ] })
    ] }) }) }, t.key)) })
  ] });
}
const T = [
  { key: "/z-msg/home", label: "首页", icon: /* @__PURE__ */ e.jsx(p, {}) },
  { key: "/z-msg/templates", label: "消息模板", icon: /* @__PURE__ */ e.jsx(h, {}) },
  { key: "/z-msg/delivery", label: "投递记录", icon: /* @__PURE__ */ e.jsx(f, {}) }
], B = [
  { path: "/z-msg/home", Component: S },
  { path: "/z-msg/inbox", title: "收件箱", order: 1, Component: d },
  { path: "/z-msg/templates", title: "消息模板", order: 2, Component: c },
  { path: "/z-msg/delivery", title: "投递记录", order: 3, Component: n },
  { path: "/z-msg/batch", title: "批量任务", order: 4, Component: m },
  { path: "/z-msg/:rest*", Component: g },
  { path: "/z-msg/delivery", Component: n }
];
export {
  n as DeliveryLogs,
  c as TemplateList,
  T as menuItems,
  B as routes
};
