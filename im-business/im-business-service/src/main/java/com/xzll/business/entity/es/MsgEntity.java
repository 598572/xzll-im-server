package com.xzll.business.entity.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

/**
 * @Author: hzz
 * @Date: 2024/6/16 14:20:35
 * @Description:
 */
@Data
@Document(indexName = "test_es")
public class MsgEntity {

    @Id
    private String id;

    private String fromUserId;
    private String toUserId;
    private Integer msgFormat;
    private String msgContent;

    private String firstUserName;

    private Integer firstUserType;

    private String secondUserName;

    private Integer secondUserType;

}
