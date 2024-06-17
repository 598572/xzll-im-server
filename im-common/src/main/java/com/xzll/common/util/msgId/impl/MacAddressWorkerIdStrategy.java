package com.xzll.common.util.msgId.impl;

import com.xzll.common.util.msgId.WorkerIdStrategy;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * @Author: hzz
 * @Date: 2024/6/16 11:15:44
 * @Description:
 */
@Component(value = "macAddressWorkerIdStrategy")
public class MacAddressWorkerIdStrategy implements WorkerIdStrategy {
    @Override
    public long getWorkerId() {
        try {
            byte[] mac = NetworkInterface.getByInetAddress(InetAddress.getLocalHost()).getHardwareAddress();
            long macAddress = ((0xFF & mac[mac.length - 1]) |
                    (0xFF00 & (mac[mac.length - 2] << 8))) >> 6; // 使用 MAC 地址的后几位
            return macAddress % 32; // 假设 workerId 的范围是0-31
        } catch (UnknownHostException | SocketException e) {
            throw new RuntimeException("Failed to get MAC address", e);
        }
    }
}
