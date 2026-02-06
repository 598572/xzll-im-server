package com.xzll.gateway.config;

import cn.hutool.core.util.ArrayUtil;
import com.xzll.gateway.component.RestAuthenticationEntryPoint;
import com.xzll.gateway.component.RestfulAccessDeniedHandler;
import com.xzll.gateway.config.nacos.IgnoreUrlsConfig;
import com.xzll.gateway.constant.AuthConstant;
import com.xzll.gateway.filter.IgnoreUrlsRemoveJwtFilter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

/**
 * @Author: hzz
 * @Date: 2024/6/10 09:45:38
 * @Description: 资源服务配置（jwt 白名单 认证 或者权限相关的配置）
 */
@AllArgsConstructor
@Slf4j
@Configuration
@EnableWebFluxSecurity
public class ResourceServerConfig {

    private final ReactiveAuthorizationManager authorizationManager;
    private final IgnoreUrlsConfig ignoreUrlsConfig;
    private final RestfulAccessDeniedHandler restfulAccessDeniedHandler;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final IgnoreUrlsRemoveJwtFilter ignoreUrlsRemoveJwtFilter;

    /**
     * 配置了 Spring Security 的 WebFlux 安全过滤链，主要用于 JWT 认证和授权。以下是每一部分的详细解释
     *
     * @param http
     * @return
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                //配置 Spring Security 以使用 OAuth2 资源服务器
                .oauth2ResourceServer()
                //设置 JWT 作为令牌格式
                .jwt()
                //设置自定义的 JWT 认证转换器，用于将 JWT 转换为 AbstractAuthenticationToken
                .jwtAuthenticationConverter(jwtAuthenticationConverter());

        //自定义处理JWT请求头过期或签名错误的结果
        http.oauth2ResourceServer().authenticationEntryPoint(restAuthenticationEntryPoint);
        //对白名单路径，直接移除JWT请求头，后续到AuthGlobalFilter后 会判断 没有jwt请求头的直接放行
        http.addFilterBefore(ignoreUrlsRemoveJwtFilter, SecurityWebFiltersOrder.AUTHENTICATION);
        //开始配置授权规则。
        http.authorizeExchange()
                //将白名单路径配置为允许所有请求，不需要 JWT。
                .pathMatchers(ArrayUtil.toArray(ignoreUrlsConfig.getUrls(), String.class)).permitAll()
                //配置所有其他路径的访问控制，使用自定义的鉴权管理器 authorizationManager
                .anyExchange().access(authorizationManager)
                //配置异常处理。此处暂时没处理
                .and().exceptionHandling()
                //处理未授权的情况 使用  restfulAccessDeniedHandler处理器
                .accessDeniedHandler(restfulAccessDeniedHandler)
                //处理未认证 使用  restAuthenticationEntryPoint 处理器
                .authenticationEntryPoint(restAuthenticationEntryPoint)
                //禁用 CSRF（跨站请求伪造）保护，因为在 JWT 认证中通常不需要 CSRF 保护。
                .and().csrf().disable();
        return http.build();
    }

    /**
     * 通过 JwtGrantedAuthoritiesConverter 从 JWT 中提取权限信息，并通过 JwtAuthenticationConverter 将 JWT 转换为 AbstractAuthenticationToken。
     * 最终，这个过程被适配为反应式的，以便在使用 Spring WebFlux 的应用中使用
     *
     * @return
     */
    @Bean
    public Converter<Jwt, ? extends Mono<? extends AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        //从 JWT 提取权限信息的转换器
        JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        jwtGrantedAuthoritiesConverter.setAuthorityPrefix(AuthConstant.AUTHORITY_PREFIX);
        jwtGrantedAuthoritiesConverter.setAuthoritiesClaimName(AuthConstant.AUTHORITY_CLAIM_NAME);
        //将 JWT 转换为 AbstractAuthenticationToken 的转换器。
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
        //使用 ReactiveJwtAuthenticationConverterAdapter 将传统的 JWT 转换器适配为反应式(Reactive)版本，适用于 Spring WebFlux 应用
        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
    }
}
