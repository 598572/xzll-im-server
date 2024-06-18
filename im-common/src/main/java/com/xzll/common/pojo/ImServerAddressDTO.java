package com.xzll.common.pojo;

import lombok.Data;

/**
 * @Author: hzz
 * @Date: 2024/6/10 20:31:23
 * @Description: ip端口 dto 用于承载转化长连接服务的ip+端口
 */
@Data
public class ImServerAddressDTO {
    private String ip;
    private Integer port;

}
