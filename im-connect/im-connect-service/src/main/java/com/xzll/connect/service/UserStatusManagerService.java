package com.xzll.connect.service;


/**
 * @Author: hzz
 * @Date: 2024/5/30 15:56:57
 * @Description:
 */
public interface UserStatusManagerService {


    public void userConnectSuccessAfter(Integer status, String uidStr);

    public void userDisconnectAfter(String uid);
}
