package com.xzll.common.util.msgId.impl;

import com.xzll.common.util.msgId.WorkerIdStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * @Author: hzz
 * @Date: 2024/6/16 11:15:44
 * @Description:
 */
@Slf4j
@Component(value = "macAddressWorkerIdStrategy")
public class MacAddressWorkerIdStrategy implements WorkerIdStrategy {

    @Override
    public long getWorkerId() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (networkInterface != null && !networkInterface.isLoopback() && networkInterface.getHardwareAddress() != null) {
                    byte[] mac = networkInterface.getHardwareAddress();
                    if (mac != null && mac.length >= 2) {
                        long macAddress = ((0xFF & mac[mac.length - 1]) | (0xFF00 & (mac[mac.length - 2] << 8))) >> 6;
                        long l = macAddress % 32;
                        log.info("当前机器的mac地址%32的结果:{}", l);
                        // 假设 workerId 的范围是 0-31
                        return l;
                    }
                }
            }
            throw new RuntimeException("No suitable MAC address found");
        } catch (SocketException e) {
            throw new RuntimeException("Failed to get MAC address", e);
        }
    }
}
