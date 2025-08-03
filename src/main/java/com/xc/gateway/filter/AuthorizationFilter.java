package com.xc.gateway.filter;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
public class AuthorizationFilter implements GlobalFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info(String.format("[AuthorizationFilter]收到请求:%s", exchange));
        return chain.filter(exchange).doFinally(
                signalType -> log.info(String.format("[AuthorizationFilter]请求结束%s:", JSON.toJSONString(exchange)))
        );
    }
}
