package com.xc.gateway.filter;

import com.alibaba.fastjson2.JSON;
import com.xc.boot.enums.ErrorEnum;
import com.xc.boot.model.result.Result;
import com.xc.boot.utils.AuthUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 权限控制过滤器
 * 
 * @author XieChen
 * @date 2023/08/11
 */
@Slf4j
@Component
public class AuthorizationFilter implements GlobalFilter, Ordered {

    @Override
	public int getOrder() {
		return 1;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		String path = exchange.getRequest().getURI().getPath();
		log.info("[AuthorizationFilter] path:{}", path);
		// 跳过白名单
		if (match(path)) {
			return chain.filter(exchange);
		}

		// 从请求头中取出token
		String token = exchange.getRequest().getHeaders().getFirst(AuthUtil.HEADER_KEY);
		
		log.debug("token:{}", token);
		if (StringUtils.isBlank(token)) {
			log.warn("[AuthorizationFilter] token is empty, url:{}", path);
			ServerHttpResponse originalResponse = exchange.getResponse();
			originalResponse.setStatusCode(HttpStatus.OK);
			originalResponse.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
			byte[] response = JSON.toJSONBytes(Result.error(ErrorEnum.NO_PERMISSION));
			DataBuffer buffer = originalResponse.bufferFactory().wrap(response);
			return originalResponse.writeWith(Flux.just(buffer));
		}
		// TODO:检查token是否在黑名单

		// 取出token身份信息
		Long userId;
		try {
			userId = AuthUtil.getUserIdFromToken(token);
			log.info("[AuthorizationFilter] userId:{}", userId);
		} catch (Exception e) {
			log.warn("[AuthorizationFilter] jwt verify fail, ip:{}", exchange.getRequest().getRemoteAddress());
			ServerHttpResponse originalResponse = exchange.getResponse();
			originalResponse.setStatusCode(HttpStatus.OK);
			originalResponse.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
			byte[] response = JSON.toJSONBytes(Result.error(ErrorEnum.USER_UNLOGIN));
			DataBuffer buffer = originalResponse.bufferFactory().wrap(response);
			return originalResponse.writeWith(Flux.just(buffer));
		}
		// 添加当前身份到request

		return chain.filter(exchange);
	}

	boolean match(String path) {
		PathMatcher pathMatcher = new AntPathMatcher();
		return pathMatcher.match("/api/open/**", path) ||
				pathMatcher.match("/api/xc-auth/user/wechat-login", path);
	}


}
