package com.xzll.common.pojo.base;


import cn.hutool.core.lang.Assert;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * @Author: hzz
 * @Date: 2022/2/17 16:02:52
 * @Description: IM消息请求基类：所有【客户端】向《服务端》 发的消息 都是此数据结构
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ImBaseRequest<T> implements Serializable {
    private static final long serialVersionUID = -1L;

    //消息类型
    private String url;
    //消息业务属性和内容
    private T body;
    //附加属性 用于扩展
    private Map<String, String> extraMap;




    public static boolean checkSupport(String url, String requestUrl) {
        //Assert.isTrue(StringUtils.isNotBlank(url) && StringUtils.isNotBlank(requestUrl), "url参数有误");
        return StringUtils.equals(url,requestUrl);
    }

}
