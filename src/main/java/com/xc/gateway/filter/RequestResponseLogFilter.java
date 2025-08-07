package com.xc.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
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
        // 1、记录请求信息
        ServerHttpRequest request = exchange.getRequest(); // 请求对象
        String queryParams = request.getQueryParams().toString(); // 请求参数
        String path = request.getPath().value(); // 请求路径
        String method = request.getMethod().name(); // 请求方法
        Map<String, String> requestHeaders = new HashMap<>(); // 请求头
        request.getHeaders().forEach((key, value) -> requestHeaders.put(key, String.join(",", value)));
        Flux<DataBuffer> fluxRequestBody = request.getBody(); // 请求体


        // 对于GET请求，直接记录日志，不尝试读取请求体
        if ("GET".equals(method)) {
            log.info("\n[RequestResponseLogFilter]收到请求 \nURI: {}, \nMethod: {}, \nQueryParams: {}, \nHeaders: {}, \nRequestBody:{}",
                    path, method, queryParams, requestHeaders, "");

            // 直接处理响应日志
            ServerHttpResponse response = exchange.getResponse();
            DataBufferFactory bufferFactory = response.bufferFactory();
            ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(response) {
                @Override
                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                    if (body instanceof Flux<? extends DataBuffer> fluxBody) {
                        return super.writeWith(fluxBody.map(dataBuffer -> {
                            byte[] content = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(content);
                            DataBufferUtils.release(dataBuffer);
                            String responseBody = new String(content, StandardCharsets.UTF_8);
                            log.info("[RequestResponseLogFilter-writeWith]收到响应: \nURI: {}, \nMethod: {}, \nResponse: {}",
                                    path, method, responseBody);
                            return bufferFactory.wrap(content);
                        }));
                    }
                    return super.writeWith(body);
                }

                @Override
                public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                    return super.writeAndFlushWith(Flux.from(body).map(dataBufferFlux ->
                            Flux.from(dataBufferFlux).map(dataBuffer -> {
                                byte[] content = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(content);
                                DataBufferUtils.release(dataBuffer);
                                String responseBody = new String(content, StandardCharsets.UTF_8);
                                log.info("\n[RequestResponseLogFilter-writeAndFlushWith]收到响应: \nURI: {}, \nMethod: {}, \nResponse: {}",
                                        path, method, responseBody);
                                return bufferFactory.wrap(content);
                            })
                    ));
                }
            };
            // 将修改过的response放入exchange
            return chain.filter(exchange.mutate().response(decoratedResponse).build());
        } else if ("POST".equals(method) || "PUT".equals(method)) {
            return DataBufferUtils.join(fluxRequestBody)
                    .flatMap(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        String requestBody = new String(bytes, StandardCharsets.UTF_8);
                        log.info("\n[RequestResponseLogFilter]收到请求 \nURI: {}, \nMethod: {}, \nQueryParams: {}, \nHeaders: {}, \nRequestBody:{}",
                                path, method, queryParams, requestHeaders, requestBody);

                        // 使用ServerHttpRequestDecorator重新构造请求，将body放回
                        ServerHttpRequestDecorator decoratedRequest = new ServerHttpRequestDecorator(request) {
                            @Override
                            public Flux<DataBuffer> getBody() {
                                if (bytes.length > 0) {
                                    DataBufferFactory bufferFactory = new DefaultDataBufferFactory();
                                    DataBuffer dataBuffer = bufferFactory.wrap(bytes);
                                    return Flux.just(dataBuffer);
                                } else {
                                    return Flux.empty();
                                }
                            }
                        };

                        // 2、记录响应信息
                        ServerHttpResponse response = exchange.getResponse();
                        DataBufferFactory bufferFactory = response.bufferFactory();
                        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(response) {
                            @Override
                            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                                if (body instanceof Flux<? extends DataBuffer> fluxBody) {
                                    return super.writeWith(fluxBody.map(dataBuffer -> {
                                        byte[] content = new byte[dataBuffer.readableByteCount()];
                                        dataBuffer.read(content);
                                        DataBufferUtils.release(dataBuffer);
                                        String responseBody = new String(content, StandardCharsets.UTF_8);
                                        log.info("\n[RequestResponseLogFilter-writeWith]收到响应: \nURI: {}, \nMethod: {}, \nResponse: {}",
                                                path, method, responseBody);
                                        return bufferFactory.wrap(content);
                                    }));
                                }
                                return super.writeWith(body);
                            }

                            @Override
                            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                                return super.writeAndFlushWith(Flux.from(body).map(dataBufferFlux ->
                                        Flux.from(dataBufferFlux).map(dataBuffer -> {
                                            byte[] content = new byte[dataBuffer.readableByteCount()];
                                            dataBuffer.read(content);
                                            DataBufferUtils.release(dataBuffer);
                                            String responseBody = new String(content, StandardCharsets.UTF_8);
                                            log.info("\n[RequestResponseLogFilter-writeAndFlushWith]收到响应: \nURI: {}, \nMethod: {}, \nResponse: {}",
                                                    path, method, responseBody);
                                            return bufferFactory.wrap(content);
                                        })
                                ));
                            }
                        };
                        // 将修改过的request和response放入exchange
                        return chain.filter(exchange.mutate().request(decoratedRequest).response(decoratedResponse).build());
                    });
        } else {
            return chain.filter(exchange);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
