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
import java.util.HashMap;
import java.util.Map;

/**
 * 自定义全局过滤器，用于打印请求和响应信息
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

        // 记录请求头
        Map<String, String> requestHeaders = new HashMap<>();
        request.getHeaders().forEach((key, value) -> requestHeaders.put(key, String.join(",", value)));

        // 如果是POST/PUT请求，尝试读取请求体
        if (HttpMethod.POST.equals(request.getMethod()) || HttpMethod.PUT.equals(request.getMethod())) {
            return logRequestBody(exchange, chain);
        } else {
            log.info("[RequestResponseLogFilter]收到请求, URI: {}, Method: {}, QueryParams: {}, Headers: {}",
                    path, method, queryParams, requestHeaders);
            // 包装响应以捕获响应体
            return logResponseBody(exchange, chain);
        }
    }

    private Mono<Void> logRequestBody(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 记录请求头
        Map<String, String> requestHeaders = new HashMap<>();
        request.getHeaders().forEach((key, value) -> requestHeaders.put(key, String.join(",", value)));

        return DataBufferUtils.join(request.getBody())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    String requestBody = new String(bytes, StandardCharsets.UTF_8);
                    log.info("[RequestResponseLogFilter]收到请求 URI: {}, Method: {}, Body: {}, Headers: {}",
                            path, request.getMethod(), requestBody, requestHeaders);

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
                try {
                    // 检查是否为SSE流式响应
                    boolean isSSE = "text/event-stream".equals(getHeaders().getFirst("Content-Type"));

                    if (isSSE) {
                        // 处理SSE流式响应
                        return logSSEStreamResponse(body, path);
                    } else {
                        // 处理普通响应
                        return logRegularResponse(body, path);
                    }
                } catch (Exception e) {
                    log.error("[RequestResponseLogFilter]处理writeWith时发生异常: ", e);
                    return super.writeWith(body);
                }
            }

            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                try {
                    // 记录响应头
                    Map<String, String> responseHeaders = new HashMap<>();
                    getHeaders().forEach((key, value) -> responseHeaders.put(key, String.join(",", value)));

                    log.info("[RequestResponseLogFilter]收到SSE响应 URI: {}, Status: {}, Headers: {}",
                            path, getStatusCode(), responseHeaders);

                    // 处理SSE流式响应数据
                    Publisher<? extends Publisher<? extends DataBuffer>> loggedBody =
                            Flux.from(body).map(dataBufferFlux ->
                                    Flux.from(dataBufferFlux).map(dataBuffer -> {
                                        try {
                                            // 复制buffer以避免消费问题
                                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                            dataBuffer.read(bytes);
                                            DataBufferUtils.release(dataBuffer);
                                            String responseBody = new String(bytes, StandardCharsets.UTF_8);

                                            log.info("[RequestResponseLogFilter]收到SSE响应数据 URI: {}, Status: {}, Data: {}",
                                                    path, getStatusCode(), responseBody);

                                            // 返回新的DataBuffer
                                            return originalResponse.bufferFactory().wrap(bytes);
                                        } catch (Exception e) {
                                            log.error("[RequestResponseLogFilter]处理SSE数据时发生异常: ", e);
                                            return dataBuffer;
                                        }
                                    }).onErrorContinue((throwable, o) -> {
                                        log.error("[RequestResponseLogFilter]处理SSE流数据时发生异常: ", throwable);
                                    })
                            ).onErrorContinue((throwable, o) -> {
                                log.error("[RequestResponseLogFilter]处理SSE流时发生异常: ", throwable);
                            });

                    return super.writeAndFlushWith(loggedBody);
                } catch (Exception e) {
                    log.error("[RequestResponseLogFilter]处理writeAndFlushWith时发生异常: ", e);
                    return super.writeAndFlushWith(body);
                }
            }

            @Override
            public Mono<Void> setComplete() {
                try {
                    log.debug("[RequestResponseLogFilter]响应完成 URI: {}, Status: {}", path, getStatusCode());
                    return super.setComplete();
                } catch (Exception e) {
                    log.error("[RequestResponseLogFilter]处理setComplete时发生异常: ", e);
                    return super.setComplete();
                }
            }

            private Mono<Void> logSSEStreamResponse(Publisher<? extends DataBuffer> body, String path) {
                Flux<DataBuffer> fluxBody = Flux.from(body).map(dataBuffer -> {
                    try {
                        // 复制buffer以避免消费问题
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        String responseBody = new String(bytes, StandardCharsets.UTF_8);

                        log.info("[RequestResponseLogFilter]收到SSE响应数据 URI: {}, Status: {}, Data: {}",
                                path, getStatusCode(), responseBody);

                        // 返回新的DataBuffer
                        return originalResponse.bufferFactory().wrap(bytes);
                    } catch (Exception e) {
                        log.error("[RequestResponseLogFilter]处理SSE数据时发生异常: ", e);
                        return dataBuffer;
                    }
                }).onErrorContinue((throwable, o) -> {
                    log.error("[RequestResponseLogFilter]处理SSE流时发生异常: ", throwable);
                });

                return super.writeWith(fluxBody);
            }

            private Mono<Void> logRegularResponse(Publisher<? extends DataBuffer> body, String path) {
                return DataBufferUtils.join(body)
                        .doOnSubscribe(subscription -> {
                            log.debug("[RequestResponseLogFilter]开始处理响应体 URI: {}", path);
                        })
                        .flatMap(dataBuffer -> {
                            try {
                                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(bytes);
                                DataBufferUtils.release(dataBuffer);
                                String responseBody = new String(bytes, StandardCharsets.UTF_8);

                                // 记录响应头
                                Map<String, String> responseHeaders = new HashMap<>();
                                getHeaders().forEach((key, value) -> responseHeaders.put(key, String.join(",", value)));

                                log.info("[RequestResponseLogFilter]收到响应 URI: {}, Status: {}, Body: {}, Headers: {}",
                                        path, getStatusCode(), responseBody, responseHeaders);

                                return super.writeWith(
                                        Flux.just(originalResponse.bufferFactory().wrap(bytes))
                                );
                            } catch (Exception e) {
                                log.error("[RequestResponseLogFilter]处理响应体时发生异常: ", e);
                                return super.writeWith(Flux.just(dataBuffer));
                            }
                        })
                        .onErrorResume(throwable -> {
                            log.error("[RequestResponseLogFilter]处理响应体时发生异常: ", throwable);
                            return super.writeWith(body);
                        });
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build())
                .doOnSuccess(aVoid -> {
                    log.debug("[RequestResponseLogFilter]过滤器链执行完成 URI: {}", path);
                })
                .doOnError(throwable -> {
                    log.error("[RequestResponseLogFilter]过滤器链执行异常 URI: {}", path, throwable);
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
