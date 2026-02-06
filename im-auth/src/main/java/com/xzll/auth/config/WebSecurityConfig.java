package com.xzll.auth.config;

import com.xzll.auth.service.impl.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;


/**
 * @Author: hzz
 * @Date: 2024/6/10 11:06:10
 * @Description: Web安全配置（Spring Boot 3.x + Spring Security 6.x）
 * 替代旧版的 WebSecurityConfigurerAdapter
 * 
 * 注意：PasswordEncoder 已移至 PasswordEncoderConfig，避免循环依赖
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final UserServiceImpl userDetailsService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 认证提供者 - 使用自定义的UserDetailsService和PasswordEncoder
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    /**
     * 认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * 安全过滤器链配置
     * Spring Security 6.x 使用 SecurityFilterChain 替代 WebSecurityConfigurerAdapter
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用CSRF（因为使用JWT，无状态认证）
                .csrf(AbstractHttpConfigurer::disable)
                // 配置请求授权规则
                .authorizeHttpRequests(authorize -> authorize
                        // OAuth2相关接口放行
                        .requestMatchers("/oauth/**").permitAll()
                        // 用户注册接口放行
                        .requestMatchers("/user/register").permitAll()
                        // 健康检查接口放行
                        .requestMatchers("/actuator/**").permitAll()
                        // RSA公钥接口放行（Gateway需要获取公钥验证JWT）
                        .requestMatchers("/rsa/publicKey").permitAll()
                        // JWK接口放行（用于获取公钥）
                        .requestMatchers("/.well-known/jwks.json").permitAll()
                        // Swagger文档放行
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**").permitAll()
                        // 其他请求需要认证
                        .anyRequest().authenticated()
                )
                // 配置会话管理 - 无状态
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // 配置认证提供者
                .authenticationProvider(authenticationProvider())
                // 配置HTTP Basic认证（用于OAuth2客户端认证）
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
