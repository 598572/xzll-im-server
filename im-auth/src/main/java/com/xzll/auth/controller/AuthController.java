package com.xzll.auth.controller;

import com.xzll.auth.config.nacos.Oauth2Config;
import com.xzll.auth.domain.Oauth2TokenDto;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.answercode.AnswerCode;
import com.xzll.common.pojo.base.WebBaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.endpoint.TokenEndpoint;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.util.Assert;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.security.Principal;
import java.util.Map;
import java.util.Objects;

/**
 * @Author: hzz
 * @Date: 2024/6/10 11:05:14
 * @Description: 登录 获取token
 */
@RestController
@Slf4j
@RequestMapping("/oauth")
public class AuthController {

    @Resource
    private TokenEndpoint tokenEndpoint;

    @Resource
    private TokenStore tokenStore;
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Resource
    private Oauth2Config oauth2Config;

    /**
     * Oauth2登录认证
     */
    @RequestMapping(value = "/token", method = RequestMethod.POST)
    public WebBaseResponse<Oauth2TokenDto> postAccessToken(Principal principal, @RequestParam Map<String, String> parameters) throws HttpRequestMethodNotSupportedException {
        OAuth2AccessToken oAuth2AccessToken = tokenEndpoint.postAccessToken(principal, parameters).getBody();
        Oauth2TokenDto oauth2TokenDto = Oauth2TokenDto.builder()
                .token(oAuth2AccessToken.getValue())
                .refreshToken(oAuth2AccessToken.getRefreshToken().getValue())
                .expiresIn(oauth2Config.getTokenTimeOut())
                .tokenHead("Bearer ").build();

        //设置用户登录或的token和uid到redis
        if (Objects.nonNull(oauth2TokenDto) && StringUtils.isNotBlank(oauth2TokenDto.getToken())) {
            String uid = oAuth2AccessToken.getAdditionalInformation().getOrDefault("id", StringUtils.EMPTY).toString();
            Assert.isTrue(StringUtils.isNotBlank(uid), "缺少用户id信息！");
            redisTemplate.opsForValue().set(ImConstant.RedisKeyConstant.USER_TOKEN_KEY + oauth2TokenDto.getToken(), uid, oauth2Config.getTokenTimeOut());
        }
        return WebBaseResponse.returnResultSuccess(oauth2TokenDto);
    }

    //token 刷新接口 todo

    /**
     * 验证 jwt Token ，供 不走网关的服务验证token，走网关的话不走这个接口验证token
     */
    @GetMapping("/validate")
    public WebBaseResponse<String> validateToken(@RequestParam String token) {
        try {
            OAuth2AccessToken accessToken = tokenStore.readAccessToken(token);
            //空或者过期 返回token无效
            if (accessToken == null || accessToken.isExpired()) {
                return WebBaseResponse.returnResultError(AnswerCode.TOKEN_INVALID.getMessage());
            }
            return WebBaseResponse.returnResultSuccess(AnswerCode.SUCCESS.getMessage());
        } catch (Exception e) {
            log.error("验证token接口异常e:", e);
            return WebBaseResponse.returnResultError("验证token接口异常: " + e.getMessage());
        }
    }


    /**
     * 填充im服务地址 此逻辑移到网关层 在响应时做处理
     *
     * @param oauth2TokenDto
     */
//    private void fillImServerAddress(Oauth2TokenDto oauth2TokenDto) {
//        Map<Object, Object> entries = redisTemplate.opsForHash().entries(NETTY_IP_PORT);
//        Assert.isTrue(!CollectionUtils.isEmpty(entries), "无长连接服务可用");
//
//        List<ImServerAddressDTO> addressDTOS = entries.entrySet().stream().map(x -> {
//            ImServerAddressDTO imServerAddressDTO = new ImServerAddressDTO();
//            imServerAddressDTO.setIp(x.getKey().toString());
//            imServerAddressDTO.setPort(Integer.valueOf(x.getValue().toString()));
//            return imServerAddressDTO;
//        }).collect(Collectors.toList());
//
//        //todo 策略模式 支持多种负载算法 ：随机 hash 轮询
//
//        //【随机】边界： 左闭 右开
//        int randomIndex = ThreadLocalRandom.current().nextInt(0, addressDTOS.size());
//        log.info("随机策略_randomIndex值:{}", randomIndex);
//        ImServerAddressDTO randomResult = addressDTOS.get(randomIndex);
//
//        //【轮询】
//        int seatCount = addressDTOS.size();
//        long current = redisTemplate.opsForValue().increment(IMSERVER_ROUND_COUNTER_KEY) - 1;
//        long index = current % seatCount;
//
//        ImServerAddressDTO roundResult = addressDTOS.get((int) index);
//        log.info("轮询策略_当前轮询值:{},index结果:{},胜出的ip端口:{}", current, index, JSONUtil.toJsonStr(roundResult));
//        BeanUtil.copyProperties(randomResult, oauth2TokenDto);
//    }
}
