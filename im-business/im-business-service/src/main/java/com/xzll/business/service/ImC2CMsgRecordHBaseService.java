package com.xzll.business.service;

import com.xzll.common.pojo.request.C2COffLineMsgAO;
import com.xzll.common.pojo.request.C2CReceivedMsgAckAO;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.pojo.request.C2CWithdrawMsgAO;

public interface ImC2CMsgRecordHBaseService {


    public boolean saveC2CMsg(C2CSendMsgAO dto);

    public boolean updateC2CMsgOffLineStatus(C2COffLineMsgAO dto);

    public boolean updateC2CMsgReceivedStatus(C2CReceivedMsgAckAO dto);

    public boolean updateC2CMsgWithdrawStatus(C2CWithdrawMsgAO dto);

}
