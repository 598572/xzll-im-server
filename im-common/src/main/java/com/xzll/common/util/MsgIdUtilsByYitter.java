package com.xzll.common.util;

import com.github.yitter.contract.IdGeneratorOptions;
import com.github.yitter.idgen.YitIdHelper;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @Author: hzz
 * @Date: 2024/6/16 09:26:59
 * @Description:
 */
public class MsgIdUtilsByYitter {

    // 本地递增序列，用于确保严格递增
    private final AtomicLong localSequence = new AtomicLong(0);

    public MsgIdUtilsByYitter(short workerId) {
        // 创建 IdGeneratorOptions 对象，可在构造函数中输入 WorkerId：
        IdGeneratorOptions options = new IdGeneratorOptions(workerId);
        // options.WorkerIdBitLength = 10; // 默认值6，限定 WorkerId 最大值为2^6-1，即默认最多支持64个节点。
        // options.SeqBitLength = 6; // 默认值6，限制每毫秒生成的ID个数。若生成速度超过5万个/秒，建议加大 SeqBitLength 到 10。
        // options.BaseTime = Your_Base_Time; // 如果要兼容老系统的雪花算法，此处应设置为老系统的BaseTime。
        // ...... 其它参数参考 IdGeneratorOptions 定义。
        // 保存参数（务必调用，否则参数设置不生效）：
        YitIdHelper.setIdGenerator(options);
    }

    public String generateMessageId(long userId, boolean isGroupChat) {
        long id = nextId();
        //获取本地递增序列 保证严格递增
        long localSeq = localSequence.incrementAndGet();
        Integer type = isGroupChat ? 2 : 1;
        return String.format("%d-%d-%d-%d", type, userId, id,localSeq);
    }


    public static long nextId() {
        return YitIdHelper.nextId();
    }

    public static void main(String[] args) {
        MsgIdUtilsByYitter msgIdUtilsByYitter = new MsgIdUtilsByYitter(Short.parseShort("1"));
        for (int i = 0; i < 10; i++) {
            System.out.println(msgIdUtilsByYitter.generateMessageId(123, false));
        }
    }

}
