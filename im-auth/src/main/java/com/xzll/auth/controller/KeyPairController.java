package com.xzll.auth.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2024/6/10 11:05:14
 * @Description: 获取rsa公钥，供网关检验token使用（只在第一次会获取，获取到后网关会进行缓存）
 */
@Slf4j
@RestController
public class KeyPairController {

    @Resource
    private KeyPair keyPair;

    @GetMapping("/rsa/publicKey")
    public Map<String, Object> getKey() {
        log.info("====获取公钥====");
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        // 构建完整的 RSAKey，包含 kid 和 algorithm（必须与 AuthorizationServerConfig 中的配置一致）
        RSAKey key = new RSAKey.Builder(publicKey)
                .keyID("im-auth-key")  // 必须与 AuthorizationServerConfig 中的 keyID 一致
                .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)  // 必须与 AuthorizationServerConfig 中的算法一致
                .build();

        return new JWKSet(key).toJSONObject();
    }

}
