package com.xzll.common.pojo.base;

import lombok.*;

import java.io.Serializable;
import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2022/2/17 16:02:52
 * @Description: IM消息类响应基类，：所有《服务端》向《客户端》 发的消息 都是此数据结构
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ImBaseResponse<T> implements Serializable {
    private static final long serialVersionUID = -1L;

    //消息类型
    private String url;
    //消息业务属性和内容
    private T body;
    //附加属性 用于扩展
    private Map<String, String> extraMap;




    public static <T> ImBaseResponse buildPushToClientData(ImBaseRequest baseRequest, T data, Map<String, String> extraMap) {
        return new ImBaseResponse(baseRequest.getUrl(), data, extraMap);
    }

    public static <T> ImBaseResponse buildPushToClientData(String url, T data) {
        return new ImBaseResponse(url, data, null);
    }
}
