//package com.xzll.business.service.impl;
//
//import cn.hutool.core.bean.BeanUtil;
//import cn.hutool.core.lang.Assert;
//import cn.hutool.json.JSONUtil;
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
//import com.baomidou.mybatisplus.core.toolkit.Wrappers;
//import com.xzll.business.entity.mysql.ImC2CMsgRecord;
//import com.xzll.business.mapper.ImC2CMsgRecordMapper;
//import com.xzll.business.entity.es.MsgEntity;
//import com.xzll.common.constant.ImConstant;
//import com.xzll.common.constant.MsgStatusEnum;
//import com.xzll.common.pojo.request.C2CReceivedMsgAckAO;
//import com.xzll.common.pojo.request.C2COffLineMsgAO;
//import com.xzll.common.pojo.request.C2CWithdrawMsgAO;
//import org.elasticsearch.index.query.MatchAllQueryBuilder;
//import org.springframework.core.convert.ConversionService;
//import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
//import org.springframework.data.elasticsearch.core.SearchHits;
//import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
//
//import com.xzll.business.service.ImC2CMsgRecordService;
//import com.xzll.common.pojo.request.C2CSendMsgAO;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Propagation;
//import org.springframework.transaction.annotation.Transactional;
//
//import javax.annotation.Resource;
//import java.util.List;
//import java.util.Objects;
//import java.util.stream.Collectors;
//
///**
// * @Author: hzz
// * @Date: 2024/6/3 09:10:48
// * @Description:
// */
//@Service
//@Slf4j
//public class ImC2CMsgRecordServiceImpl implements ImC2CMsgRecordService {
//
//    @Resource
//    private ImC2CMsgRecordMapper imC2CMsgRecordMapper;
//    @Resource
//    private ConversionService conversionService;
//    @Resource
//    private ElasticsearchRestTemplate elasticsearchRestTemplate;
//
//
//    /**
//     * 根据分片键 获取数据（chat_id是（水平）分库的分片键 msg_id是（水平）分表的分片键），从而避免广播
//     *
//     * @param chatId
//     * @param msgId
//     * @return
//     */
//    private ImC2CMsgRecord getImC2CMsgRecordByShardingKey(String chatId, String msgId) {
//        LambdaQueryWrapper<ImC2CMsgRecord> msgRecord = Wrappers.lambdaQuery(ImC2CMsgRecord.class).eq(ImC2CMsgRecord::getChatId, chatId).eq(ImC2CMsgRecord::getMsgId, msgId);
//        return imC2CMsgRecordMapper.selectOne(msgRecord);
//    }
//
//    /**
//     * 保存单聊消息记录
//     *
//     * @param dto
//     */
//    @Override
//    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
//    public boolean saveC2CMsg(C2CSendMsgAO dto) {
//        log.info("保存单聊消息入参:{}", JSONUtil.toJsonStr(dto));
//        ImC2CMsgRecord imC2CMsgRecord = conversionService.convert(dto, ImC2CMsgRecord.class);
//        imC2CMsgRecord.setMsgStatus(MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getCode());
//        int row = 0;
//        if (Objects.isNull(dto.getRetryMsgFlag())) {
//            row = imC2CMsgRecordMapper.insert(imC2CMsgRecord);
//            log.info("保存单聊消息结果:{}", row);
//        } else {
//            //如果是重试消息 需要特殊处理
//            ImC2CMsgRecord c2CMsgRecord = getImC2CMsgRecordByShardingKey(dto.getChatId(), dto.getMsgId());
//            if (Objects.nonNull(c2CMsgRecord)) {
//                //分库分表后，对此表的读写 能用分片键尽量用分片键 避免广播
//                LambdaUpdateWrapper<ImC2CMsgRecord> updateParam = Wrappers.lambdaUpdate(ImC2CMsgRecord.class).eq(ImC2CMsgRecord::getChatId, dto.getChatId()).eq(ImC2CMsgRecord::getMsgId, dto.getMsgId());
//                c2CMsgRecord.setRetryCount(c2CMsgRecord.getRetryCount() + 1);
//                row = imC2CMsgRecordMapper.update(c2CMsgRecord, updateParam);
//                log.info("更新重试次数row:{}", row);
//            }
//        }
//        return row > 0;
//    }
//
//
//    /**
//     * 更新消息状态为：离线
//     *
//     * @param dto
//     * @return
//     */
//    @Override
//    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
//    public boolean updateC2CMsgOffLineStatus(C2COffLineMsgAO dto) {
//        log.info("更新消息为离线入参:{}", JSONUtil.toJsonStr(dto));
//        //如果是重试消息 需要特殊处理
//        ImC2CMsgRecord dbResult = getImC2CMsgRecordByShardingKey(dto.getChatId(), dto.getMsgId());
//        Assert.isTrue(Objects.nonNull(dbResult), "数据为空抛出异常待mq重试消费,一般顺序消费情况下，不会出现此异常");
//        ImC2CMsgRecord updateValue = new ImC2CMsgRecord();
//        updateValue.setMsgStatus(MsgStatusEnum.MsgStatus.OFF_LINE.getCode());
//        LambdaUpdateWrapper<ImC2CMsgRecord> updateParam = Wrappers.lambdaUpdate(ImC2CMsgRecord.class).eq(ImC2CMsgRecord::getChatId, dto.getChatId()).eq(ImC2CMsgRecord::getMsgId, dto.getMsgId()).eq(ImC2CMsgRecord::getMsgStatus, MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getCode());
//        int update = imC2CMsgRecordMapper.update(updateValue, updateParam);
//        log.info("更新离线消息结果:{}", update);
//        return update > 0;
//    }
//
//    /**
//     * 更新消息为：未读/已读
//     *
//     * @param dto
//     * @return
//     */
//    @Override
//    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
//    public boolean updateC2CMsgReceivedStatus(C2CReceivedMsgAckAO dto) {
//        String currentName = MsgStatusEnum.MsgStatus.getNameByCode(dto.getMsgStatus());
//        log.info("更新消息状态为:{}，入参:{}", currentName, JSONUtil.toJsonStr(dto));
//        //如果是重试消息 需要特殊处理
//        ImC2CMsgRecord dbResult = getImC2CMsgRecordByShardingKey(dto.getChatId(), dto.getMsgId());
//        if (Objects.isNull(dbResult)) {
//            log.error("数据为空,可能发送消息时数据未落库成功，或者顺序消费出现了乱序，需排查具体原因");
//            //如果出现此情况 ， 则大概率是客户端发送消息时  rocketMQ的生产者/消费者出现了 生产消息失败，消费消息失败，或者入db时等等异常，
//            //从而导致发消息时，数据没入库，此情况下 ，需要取舍。目前取舍策略如下：
//            // 【取】：保证 端到端的消息送达率，和 高吞吐
//            // 【舍】：舍弃 服务端消息存储
//            //所以此处仍旧返回true 让消息送达ack 到达发送方，这样做的好处是 客户端之间传递的 消息不会丢失，坏处是换设备登录后，从服务器拉取的消息 可能存在丢失的情况
//            return true;
//
//            //备注：
//            //如果想避免上述问题，则需要牺牲吞吐性能，也就是发消息流程变成： 发送方发消息后，服务端必须确保存到db后，再给接收方发消息。此情况下 因为发消息流程变成了同步，吞吐将收到很大影响。目前舍弃此方案。
//            //而是采用：（服务端接收到消息后，异步发送mq消息入库，之后马上发消息给接收方） 此方案，吞吐将会很高，如果想尽量减少异步化带来的服务端丢失消息的问题。则目前尽可能保证以下几点：
//            //1. 发送：同步发送，保证消息送达broker
//            //2. 消费：由于是顺序消费（加锁了），所以不能并发消费，只能从消费速度上下手，也就是下边第三点：
//            //3. 入口：分库分表，提高db的写性能，保证实时成功写入
//
//            //另外：后期可能采用两个或多个方案，然后根据配置来走对应的方案，如目前我想到的两个方案：
//            //1. 就是现在的 发送时：异步入口，之后马上发送消息给接收者。此方案保证了：高吞吐，低延迟，抗高并发
//            //2. 服务端接收到消息后，必须保证入口，再发送消息给接收者。 此方案保证了：不丢消息。加重了消息的延迟性并降低了吞吐
//            //但是多方案设计，无疑加重系统的复杂度。看以后的压测表现和真实的业务对异常情况的容忍度
//
//            //but: 无论方案1 还是方案2 ，都需要最大努力：做到mq 投递/消费以及broker 的稳定性 和 较高的db读写性能 中间件稳了 异常情况也就少了 ，消息也就更稳定了。
//            // todo 后期项目做的差不多（至少单群聊相关功能都完善）后，将这些设计文档补全到 架构设计细节中。 2024/06/22 记
//
//        }
//        //如果db中已经是已读且当前要更新为未读，则将此未读忽略掉
//        if (Objects.equals(dbResult.getMsgStatus(), MsgStatusEnum.MsgStatus.READED.getCode()) && Objects.equals(dto.getMsgStatus(), MsgStatusEnum.MsgStatus.UN_READ.getCode())) {
//            log.info("db中已经是已读状态不再更新为未读");
//            return true;
//        }
//        //如果是未读 则更新前状态一定是（离线/到达服务器）
//        if (Objects.equals(dto.getMsgStatus(), MsgStatusEnum.MsgStatus.UN_READ.getCode())) {
//            if (!ImConstant.MsgStatusUpdateCondition.CAN_UPDATE_UN_READ.contains(dbResult.getMsgStatus())) {
//                log.info("当前消息状态不支持更新为:{}，当前状态:{}", currentName, MsgStatusEnum.MsgStatus.getNameByCode(dbResult.getMsgStatus()));
//                return true;
//            }
//        }
//        //如果是已读 则更新前状态一定是（离线/到达服务器/未读）
//        if (Objects.equals(dto.getMsgStatus(), MsgStatusEnum.MsgStatus.READED.getCode())) {
//            if (!ImConstant.MsgStatusUpdateCondition.CAN_UPDATE_READED.contains(dbResult.getMsgStatus())) {
//                log.info("当前消息状态不支持更新为:{}，当前状态:{}", currentName, MsgStatusEnum.MsgStatus.getNameByCode(dbResult.getMsgStatus()));
//                return true;
//            }
//        }
//        ImC2CMsgRecord updateValue = new ImC2CMsgRecord();
//        updateValue.setMsgStatus(dto.getMsgStatus());
//        LambdaUpdateWrapper<ImC2CMsgRecord> updateParam = Wrappers.lambdaUpdate(ImC2CMsgRecord.class).eq(ImC2CMsgRecord::getChatId, dto.getChatId()).eq(ImC2CMsgRecord::getMsgId, dto.getMsgId());
//        int update = imC2CMsgRecordMapper.update(updateValue, updateParam);
//        log.info("更新消息状态为:{}，结果:{}", currentName, update);
//        return update > 0;
//    }
//
//    /**
//     * 更新消息为 已撤回（目前先查再更 其实也可直接update）
//     *
//     * @param dto
//     * @return
//     */
//    @Override
//    public boolean updateC2CMsgWithdrawStatus(C2CWithdrawMsgAO dto) {
//        log.info("更新消息为撤回状态_入参:{}", JSONUtil.toJsonStr(dto));
//        ImC2CMsgRecord dbResult = getImC2CMsgRecordByShardingKey(dto.getChatId(), dto.getMsgId());
//        if (Objects.isNull(dbResult)) {
//            log.error("数据为空,可能发送消息时数据未落库成功，或者顺序消费出现了乱序，需排查具体原因");
//            //如果出现此情况 ， 则大概率是客户端发送消息时  rocketMQ的生产者/消费者出现了 生产消息失败，消费消息失败，或者入db时等等异常，与客户端的ack消息的思路一样 不在赘述
//            return true;
//        }
//        //如果是已读 则更新前状态一定是（离线/到达服务器/未读）
//        if (Objects.equals(dbResult.getWithdrawFlag(), MsgStatusEnum.MsgWithdrawStatus.YES.getCode())) {
//            log.info("db中消息已撤回");
//            return true;
//        }
//        ImC2CMsgRecord updateValue = new ImC2CMsgRecord();
//        updateValue.setMsgStatus(MsgStatusEnum.MsgWithdrawStatus.YES.getCode());
//        LambdaUpdateWrapper<ImC2CMsgRecord> updateParam = Wrappers.lambdaUpdate(ImC2CMsgRecord.class).eq(ImC2CMsgRecord::getChatId, dto.getChatId()).eq(ImC2CMsgRecord::getMsgId, dto.getMsgId());
//        int update = imC2CMsgRecordMapper.update(updateValue, updateParam);
//        log.info("更新消息状态为撤回_结果:{}", update);
//        return update > 0;
//    }
//
//
//    @Override
//    public void testEs(C2CSendMsgAO dto) {
//        MsgEntity msgEntity = new MsgEntity();
//        BeanUtil.copyProperties(dto, msgEntity);
//        //测试es
//        save(msgEntity);
//        findAll();
//    }
//
//
//    public MsgEntity save(MsgEntity entity) {
//        log.info("【测试es】保存数据到es，入参:{}", JSONUtil.toJsonStr(entity));
//        MsgEntity save = elasticsearchRestTemplate.save(entity);
//        log.info("【测试es】保存数据返回:{}", JSONUtil.toJsonStr(save));
//        return save;
//    }
//
//    public List<MsgEntity> findAll() {
//        MatchAllQueryBuilder matchAllQueryBuilder = org.elasticsearch.index.query.QueryBuilders.matchAllQuery();
//        NativeSearchQuery nativeSearchQuery = new NativeSearchQuery(matchAllQueryBuilder);
//        //查询,获取查询结果
//        SearchHits<MsgEntity> search = elasticsearchRestTemplate.search(nativeSearchQuery, MsgEntity.class);
//        log.info("查询到的数据:{}", JSONUtil.toJsonStr(search));
//        return search.getSearchHits().stream()
//                .map(hit -> hit.getContent())
//                .collect(Collectors.toList());
//    }
//}
