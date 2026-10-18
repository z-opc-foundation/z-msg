package com.zifang.z.msg.example;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA history fallback（只给 z-msg-example 这台演示进程用）。
 *
 * <p>前端是 BrowserRouter，刷新 {@code /z-msg/inbox} 这类路径时 Spring 找不到静态资源就返回 JSON 404，
 * 浏览器拿不到 index.html 直接白屏。vite dev / preview 自带 fallback，本地永远看不见，只在 jar 里现形。
 *
 * <p>为什么放这里而不是 {@code z-msg-web} 的 MsgAutoConfiguration：前端产物只打进 example 的
 * {@code resources/static}，而 web 是被别的进程消费的库。在库里装一条 catch-all 回落，会连宿主的
 * 404 一起吞掉（宿主 classpath 上也有 index.html，回落回去的是别人的页面）。
 *
 * <p>不能写成 {@code /**}：那会把 {@code /api/**} 的 404 也 forward 过去，把"接口不存在"伪造成
 * "返回了一坨 HTML"，客户端 JSON.parse 直接炸。逐条列前端路由前缀。
 */
@Configuration
public class MsgExampleWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/z-msg/login").setViewName("forward:/index.html");
        registry.addViewController("/z-msg/**").setViewName("forward:/index.html");
    }
}
