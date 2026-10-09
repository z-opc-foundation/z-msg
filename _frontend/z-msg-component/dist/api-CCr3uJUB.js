import { createRequest as a } from "@yuku123/z-frontend-common";
const t = a({ baseURL: "", tokenKey: "zmsg_token" });
function m(e) {
  e && e.apiBase !== void 0 && (t.defaults.baseURL = e.apiBase);
}
const g = {
  inbox: (e, s) => t.get("/msg/list", { params: { userId: e, limit: s } }),
  templatePage: (e, s) => t.post("/msg/template/list", { page: e, size: s }),
  deliveryPage: (e, s) => t.post("/msg/delivery/list", { page: e, size: s }),
  deliveryStats: () => t.get("/msg/delivery/stats"),
  batchPage: (e, s) => t.post("/msg/batch/list", { page: e, size: s })
};
export {
  m as c,
  g as m
};
