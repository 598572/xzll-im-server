package com.xzll.auth.controller;

import com.xzll.auth.domain.Oauth2TokenDto;
import com.xzll.common.pojo.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.endpoint.TokenEndpoint;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;
import java.security.Principal;
import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2024/6/11 11:05:14
 * @Description: 登录 获取token
 */
@RestController
@Slf4j
@RequestMapping("/oauth")
public class AuthController {
    @Resource
    private TokenEndpoint tokenEndpoint;

    /**
     * Oauth2登录认证
     */
    @RequestMapping(value = "/token", method = RequestMethod.POST)
    public BaseResponse<Oauth2TokenDto> postAccessToken(Principal principal, @RequestParam Map<String, String> parameters) throws HttpRequestMethodNotSupportedException {
        OAuth2AccessToken oAuth2AccessToken = tokenEndpoint.postAccessToken(principal, parameters).getBody();
        Oauth2TokenDto oauth2TokenDto = Oauth2TokenDto.builder()
                .token(oAuth2AccessToken.getValue())
                .refreshToken(oAuth2AccessToken.getRefreshToken().getValue())
                .expiresIn(oAuth2AccessToken.getExpiresIn())
                .tokenHead("Bearer ").build();
        return BaseResponse.returnResultSuccess(oauth2TokenDto);
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
