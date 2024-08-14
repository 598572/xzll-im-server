package com.xzll.connect.strategy.impl.group;

import com.xzll.common.pojo.base.ImBaseRequest;
import com.xzll.connect.strategy.MsgHandlerCommonAbstract;
import com.xzll.connect.strategy.MsgHandlerStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author: hzz
 * @Date: 2024/6/23 09:32:28
 * @Description: 群聊管理
 */
@Slf4j
@Service
public class GroupCreateStrategyImpl  extends MsgHandlerCommonAbstract implements MsgHandlerStrategy {

    @Override
    public boolean support(ImBaseRequest baseRequest) {
        return false;
    }
}
