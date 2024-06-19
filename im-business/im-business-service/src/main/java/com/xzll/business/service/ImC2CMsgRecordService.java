package com.xzll.business.service;

import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.pojo.request.ClientReceivedMsgAckAO;
import com.xzll.common.pojo.request.OffLineMsgAO;

public interface ImC2CMsgRecordService {

    public boolean saveC2CMsg(C2CSendMsgAO dto);

    public boolean updateC2CMsgOffLineStatus(OffLineMsgAO dto);

    public boolean updateC2CMsgReceivedStatus(ClientReceivedMsgAckAO dto);

    public void testEs(C2CSendMsgAO dto);
}
