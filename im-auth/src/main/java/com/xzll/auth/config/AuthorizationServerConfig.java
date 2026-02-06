package com.xzll.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * @Author: hzz
 * @Date: 2024/6/10 11:06:10
 * @Description: JWT 认证配置（Spring Boot 3.x + Spring Security 6.x）
 * 替代原来的 Oauth2ServerConfig，使用 Nimbus JOSE JWT 进行 JWT 生成和验证
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AuthorizationServerConfig {

    private final Oauth2Config oauth2Config;

    /**
     * 从JKS文件加载RSA密钥对
     */
    @Bean
    public KeyPair keyPair() {
        try {
            String jksFile = oauth2Config.getJwtFile();
            String password = oauth2Config.getJwtPassword();
            String alias = oauth2Config.getJwtAlias();

            ClassPathResource resource = new ClassPathResource(jksFile);
            KeyStore keyStore = KeyStore.getInstance("JKS");
            try (InputStream inputStream = resource.getInputStream()) {
                keyStore.load(inputStream, password.toCharArray());
            }

            RSAPrivateKey privateKey = (RSAPrivateKey) keyStore.getKey(alias, password.toCharArray());
            RSAPublicKey publicKey = (RSAPublicKey) keyStore.getCertificate(alias).getPublicKey();

            log.info("成功加载RSA密钥对，别名: {}", alias);
            return new KeyPair(publicKey, privateKey);
        } catch (Exception e) {
            log.error("加载RSA密钥对失败", e);
            throw new RuntimeException("加载RSA密钥对失败", e);
        }
    }

    /**
     * JWK Source 用于JWT签名
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(KeyPair keyPair) {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("im-auth-key")
                .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)  // 显式指定签名算法为 RS256
                .build();

        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    /**
     * JWT编码器 - 用于生成JWT Token
     */
    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * JWT解码器 - 用于验证和解析JWT Token
     */
    @Bean
    public JwtDecoder jwtDecoder(KeyPair keyPair) {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }
}
