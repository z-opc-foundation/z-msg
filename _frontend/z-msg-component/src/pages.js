/**
 * ./pages 入口：manifest + 页面导出（§8.2/§8.3/§9.3）。
 *
 * 为什么整份转发而不是手挑名字：
 *   suit 的 src/App.jsx 从 `@yuku123/z-msg-component/pages` 导入
 *   {menuItems, routes, HomePage, LoginPage}，而本文件原来只转出了
 *   {menuItems, routes, TemplateList, DeliveryLogs} —— 漏了 HomePage / LoginPage，
 *   suit 打包时 rollup 直接报
 *   `"LoginPage" is not exported by ../z-msg-component/dist/pages.js` 而红。
 *   手挑名字就是「每次新增页面都要记得回来补一次」，漏一处就红一个 suit；
 *   所以这里按 manifest 的导出整份转出。
 */
export {
    HomePage,
    LoginPage,
    TemplateList,
    DeliveryLogs,
    appMeta,
    menuItems,
    routes,
} from './pages-manifest.jsx'
