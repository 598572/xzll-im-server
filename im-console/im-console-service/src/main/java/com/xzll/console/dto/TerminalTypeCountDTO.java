package com.xzll.console.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 终端类型统计DTO
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerminalTypeCountDTO {

    /**
     * 终端类型
     * 1-Android 2-iOS 3-小程序 4-Web
     */
    private Integer terminalType;

    /**
     * 用户数量
     */
    private Long count;
}
