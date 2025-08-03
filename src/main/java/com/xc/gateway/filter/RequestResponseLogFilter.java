package com.xc.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 请求响应日志过滤器
 *
 * @author XieChen
 * @date 2025/08/03
 */
@Slf4j
@Component
public class RequestResponseLogFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 记录请求基本信息
        String queryParams = request.getQueryParams().toString();
        String path = request.getPath().value();
        String method = request.getMethod().name();

        // 如果是POST/PUT请求，尝试读取请求体
        if (HttpMethod.POST.equals(request.getMethod()) || HttpMethod.PUT.equals(request.getMethod())) {
            return logRequestBody(exchange, chain);
        } else {
            log.info("[RequestResponseLogFilter]收到请求, URI: {}, Method: {}, QueryParams: {}", path, method, queryParams);
            // 包装响应以捕获响应体
            return logResponseBody(exchange, chain);
        }
    }

    private Mono<Void> logRequestBody(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        return DataBufferUtils.join(request.getBody())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    String requestBody = new String(bytes, StandardCharsets.UTF_8);
                    log.info("[RequestResponseLogFilter]收到请求 URI: {}, Method: {}, Body: {}", path, request.getMethod(), requestBody);

                    // 重新构造请求，因为原始请求体已被消费
                    ServerHttpRequestDecorator decoratedRequest = new ServerHttpRequestDecorator(request) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return Flux.just(exchange.getResponse().bufferFactory().wrap(bytes));
                        }
                    };

                    // 包装响应以捕获响应体
                    return logResponseBody(exchange.mutate().request(decoratedRequest).build(), chain);
                })
                .switchIfEmpty(logResponseBody(exchange, chain));
    }

    private Mono<Void> logResponseBody(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        ServerHttpResponse originalResponse = exchange.getResponse();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                return DataBufferUtils.join(body)
                        .flatMap(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);
                            String responseBody = new String(bytes, StandardCharsets.UTF_8);
                            log.info("[RequestResponseLogFilter]收到响应 URI: {}, Status: {}, Body: {}", path, getStatusCode(), responseBody);

                            return originalResponse.writeWith(
                                    Flux.just(originalResponse.bufferFactory().wrap(bytes))
                            );
                        });
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
