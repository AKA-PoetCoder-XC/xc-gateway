package com.xc.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@SpringBootTest
public class AppTest {

    @Autowired
    private DiscoveryClient discoveryClient;

    @Test
    public void test1() {
        List<ServiceInstance> instances = discoveryClient.getInstances("xc-ai-service");
        System.out.println(String.format("instance count:%s", instances.size()));
    }

    @Test
    public void test2() {
        try {
            // 创建HttpClient实例
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30)) // 增加超时时间
                    .build();

            // 构建请求体
            String requestBody = "{\"jsonrpc\":\"2.0\",\"id\":17,\"method\":\"ping\"}";

            // 创建HttpRequest
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:80/api/xc-ai/mcp/message?sessionId=07722504-a32d-4fae-b66f-bd81c86ea98b"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // 使用异步方式发送请求并处理流式响应
            client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        System.out.println("Status Code: " + response.statusCode());
                        response.body().forEach(line -> {
                            System.out.println("Received: " + line);
                            // 如果收到特定结束标记，可以中断处理
                            if (line.contains("\"result\"") || line.contains("\"error\"")) {
                                System.out.println("Stream completed");
                            }
                        });
                    });

            // 等待一段时间以接收响应
            Thread.sleep(10000);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}