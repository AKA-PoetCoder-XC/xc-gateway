package com.xc.gateway.predicate;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.GatewayPredicate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * 自定义管理员断言工厂
 *
 * @author XieChen
 * @date 2025/08/03
 */
@Component
public class AdminRoutePredicateFactory extends AbstractRoutePredicateFactory<AdminRoutePredicateFactory.Config> {

    public AdminRoutePredicateFactory() {
        super(Config.class);
    }

    /**
     * 指定短断言写法时传参的顺序
     */
    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("param", "value");
    }

    /**
     * 自定义断言逻辑
     */
    @Override
    public Predicate<ServerWebExchange> apply(Config config) {
        return new GatewayPredicate() {
            @Override
            public boolean test(ServerWebExchange serverWebExchange) {
                // 1、拿到请求对象
                ServerHttpRequest request = serverWebExchange.getRequest();
                // 2、拿到配置的请求参数Key
                String param = request.getQueryParams().getFirst(config.getParam());
                // 3、判断请求中的是否有配置的参数，且和配置的值一样
                return StringUtils.hasText(param) && param.equals(config.getValue());
            }
        };
    }


    /**
     * 自定义断言配置中可以配置的参数
     */
    @Validated
    public static class Config {

        @NotEmpty
        private String param;

        @NotEmpty
        private String value;

        public String getParam() {
            return param;
        }

        public AdminRoutePredicateFactory.Config setParam(String param) {
            this.param = param;
            return this;
        }

        public String getValue() {
            return value;
        }

        public AdminRoutePredicateFactory.Config setValue(String regexp) {
            this.value = regexp;
            return this;
        }

    }
}
