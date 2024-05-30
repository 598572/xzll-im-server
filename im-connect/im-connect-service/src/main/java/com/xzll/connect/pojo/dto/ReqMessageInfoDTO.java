package com.xzll.connect.pojo.dto;

import lombok.Data;

@Data
public class ReqMessageInfoDTO {

    private int currentPage = 1;
    private int pageSize = 15;

    private Long userId;

    private String sessionId;


}
