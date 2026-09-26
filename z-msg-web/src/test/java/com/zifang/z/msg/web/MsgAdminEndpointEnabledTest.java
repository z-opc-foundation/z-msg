package com.zifang.z.msg.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同一套请求，把 {@code z-msg.web.admin-endpoints-enabled} 打开就要答。
 * <p>
 * 这个上下文存在的唯一理由：{@link MsgAdminEndpointGateTest} 里那堆 403 只证明了"被拒"，
 * 没证明"拒的是闸门"。这里四个端点全 200 且返回真结构，说明路由、分页插件、表都在，
 * 那句 403 只能来自开关 —— 顺带钉住属性键真的能绑上（键名写错的话这里就会红）。
 */
@SpringBootTest(classes = MsgWebTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "z-msg.enabled=true",
        "z-msg.web.admin-endpoints-enabled=true",
        "z.msg.test.db=msg_admin_on_it"
})
public class MsgAdminEndpointEnabledTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TestRestTemplate rest;

    @Test
    public void templateBatchAndDeliveryAllAnswerOnceEnabled() {
        JsonNode templates = pageOf(HttpMethod.POST, "/api/msg/template/list");
        assertTrue(templates.has("records"), "模板列表要返回分页结构: " + templates);

        JsonNode batches = pageOf(HttpMethod.POST, "/api/msg/batch/list");
        assertTrue(batches.has("records"), "批量任务列表要返回分页结构: " + batches);

        JsonNode logs = pageOf(HttpMethod.POST, "/api/msg/delivery/list");
        assertTrue(logs.has("records"), "投递日志列表要返回分页结构: " + logs);

        ResponseEntity<String> stats = exchange(HttpMethod.GET, "/api/msg/delivery/stats");
        assertEquals(HttpStatus.OK, stats.getStatusCode(), stats.getBody());
        JsonNode data = body(stats).path("data");
        assertTrue(data.has("success") && data.has("failed") && data.has("total"),
                "GET /delivery/stats 挂在 MessageController 上，也要一起放开: " + data);
    }

    private JsonNode pageOf(HttpMethod method, String path) {
        ResponseEntity<String> resp = exchange(method, path);
        assertEquals(HttpStatus.OK, resp.getStatusCode(), method + " " + path + " body=" + resp.getBody());
        JsonNode data = body(resp).path("data");
        assertTrue(data.isObject(), path + " 的 data 应是分页对象: " + data);
        return data;
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        return rest.exchange(path, method,
                new HttpEntity<String>(HttpMethod.POST.equals(method) ? "{}" : null, headers),
                String.class);
    }

    private static JsonNode body(ResponseEntity<String> resp) {
        try {
            return JSON.readTree(resp.getBody());
        } catch (Exception e) {
            throw new AssertionError("响应不是 JSON: " + resp.getBody(), e);
        }
    }
}
