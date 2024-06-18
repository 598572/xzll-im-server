package com.xzll.business.service;

import com.xzll.common.pojo.C2CMsgRequestDTO;
import com.xzll.common.pojo.ClientReceivedMsgAckDTO;
import com.xzll.common.pojo.OffLineMsgDTO;

public interface ImC2CMsgRecordService {

    public boolean saveC2CMsg(C2CMsgRequestDTO dto);

    public boolean updateC2CMsgOffLineStatus(OffLineMsgDTO dto);

    public boolean updateC2CMsgReceivedStatus(ClientReceivedMsgAckDTO dto);

    public void testEs(C2CMsgRequestDTO dto);
}
