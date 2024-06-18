package com.xzll.business.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xzll.business.entity.mysql.ImC2CMsgRecord;
import com.xzll.business.mapper.ImC2CMsgRecordMapper;
import com.xzll.business.mapstruct.C2CMsgMapping;
import com.xzll.business.entity.es.MsgEntity;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.pojo.ClientReceivedMsgAckDTO;
import com.xzll.common.pojo.OffLineMsgDTO;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;

import com.xzll.business.service.ImC2CMsgRecordService;
import com.xzll.common.pojo.C2CMsgRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @Author: hzz
 * @Date: 2024/6/3 09:10:48
 * @Description:
 */
@Service
@Slf4j
public class ImC2CMsgRecordServiceImpl implements ImC2CMsgRecordService {

    @Resource
    private ImC2CMsgRecordMapper imC2CMsgRecordMapper;
    @Resource
    private C2CMsgMapping c2CMsgMapping;
    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    /**
     * 保存单聊消息记录
     *
     * @param dto
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public boolean saveC2CMsg(C2CMsgRequestDTO dto) {
        log.info("保存单聊消息入参:{}", JSONUtil.toJsonStr(dto));
        ImC2CMsgRecord imC2CMsgRecord = c2CMsgMapping.convertC2CMsgRecord(dto);
        imC2CMsgRecord.setMsgStatus(MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getCode());
        int row = 0;
        if (Objects.isNull(dto.getRetryMsgFlag())) {
            row = imC2CMsgRecordMapper.insert(imC2CMsgRecord);
            log.info("保存单聊消息结果:{}", row);
        } else {
            //如果是重试消息 需要特殊处理
            LambdaQueryWrapper<ImC2CMsgRecord> msgRecord = Wrappers.lambdaQuery(ImC2CMsgRecord.class).eq(ImC2CMsgRecord::getMsgId, dto.getMsgId());
            ImC2CMsgRecord c2CMsgRecord = imC2CMsgRecordMapper.selectOne(msgRecord);
            if (Objects.nonNull(c2CMsgRecord)) {
                c2CMsgRecord.setRetryCount(c2CMsgRecord.getRetryCount() + 1);
                row = imC2CMsgRecordMapper.updateById(c2CMsgRecord);
                log.info("更新重试次数row:{}", row);
            }
        }
        return row > 0;
    }

    /**
     * 更新消息状态为：离线
     *
     * @param dto
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public boolean updateC2CMsgOffLineStatus(OffLineMsgDTO dto) {
        log.info("更新消息为离线入参:{}", JSONUtil.toJsonStr(dto));
        //如果是重试消息 需要特殊处理
        LambdaQueryWrapper<ImC2CMsgRecord> msgRecord = Wrappers.lambdaQuery(ImC2CMsgRecord.class).eq(ImC2CMsgRecord::getMsgId, dto.getMsgId());
        ImC2CMsgRecord dbResult = imC2CMsgRecordMapper.selectOne(msgRecord);
        Assert.isTrue(Objects.nonNull(dbResult), "数据为空抛出异常待mq重试消费,一般顺序消费情况下，不会出现此异常");
        ImC2CMsgRecord updateValue = new ImC2CMsgRecord();
        updateValue.setMsgStatus(MsgStatusEnum.MsgStatus.OFF_LINE.getCode());
        LambdaUpdateWrapper<ImC2CMsgRecord> updateParam = Wrappers.lambdaUpdate(ImC2CMsgRecord.class).eq(ImC2CMsgRecord::getMsgId, dto.getMsgId()).eq(ImC2CMsgRecord::getMsgStatus, MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getCode());
        int update = imC2CMsgRecordMapper.update(updateValue, updateParam);
        log.info("更新离线消息结果:{}", update);
        return update > 0;
    }

    /**
     * 更新消息为：未读/已读
     *
     * @param dto
     * @return
     */
    @Override
    public boolean updateC2CMsgReceivedStatus(ClientReceivedMsgAckDTO dto) {
        String currentName = MsgStatusEnum.MsgStatus.getNameByCode(dto.getMsgStatus());
        log.info("更新消息状态为:{}，入参:{}", currentName, JSONUtil.toJsonStr(dto));
        //如果是重试消息 需要特殊处理
        LambdaQueryWrapper<ImC2CMsgRecord> msgRecord = Wrappers.lambdaQuery(ImC2CMsgRecord.class).eq(ImC2CMsgRecord::getMsgId, dto.getMsgId());
        ImC2CMsgRecord dbResult = imC2CMsgRecordMapper.selectOne(msgRecord);
        Assert.isTrue(Objects.nonNull(dbResult), "数据为空抛出异常待mq重试消费,一般顺序消费情况下，不会出现此异常");
        //如果db中已经是已读且当前要更新为未读，则将此未读忽略掉
        if (Objects.equals(dbResult.getMsgStatus(), MsgStatusEnum.MsgStatus.READED.getCode()) && dto.getMsgStatus().equals(MsgStatusEnum.MsgStatus.UN_READ.getCode())) {
            log.info("db中已经是已读状态不再更新为未读");
            return true;
        }
        if (!(dbResult.getMsgStatus().equals(MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getCode()) || dbResult.getMsgStatus().equals(MsgStatusEnum.MsgStatus.OFF_LINE.getCode()))) {
            log.info("当前消息状态不支持更新为:{}，当前状态:{}", currentName, MsgStatusEnum.MsgStatus.getNameByCode(dbResult.getMsgStatus()));
            return true;
        }
        ImC2CMsgRecord updateValue = new ImC2CMsgRecord();
        updateValue.setMsgStatus(dto.getMsgStatus());
        LambdaUpdateWrapper<ImC2CMsgRecord> updateParam = Wrappers.lambdaUpdate(ImC2CMsgRecord.class).eq(ImC2CMsgRecord::getMsgId, dto.getMsgId());
        int update = imC2CMsgRecordMapper.update(updateValue, updateParam);
        log.info("更新消息状态为:{}，结果:{}", currentName, update);
        return update > 0;
    }


    @Override
    public void testEs(C2CMsgRequestDTO dto) {
        MsgEntity msgEntity = new MsgEntity();
        BeanUtil.copyProperties(dto, msgEntity);
        //测试es
        save(msgEntity);
        findAll();
    }


    public MsgEntity save(MsgEntity entity) {
        log.info("【测试es】保存数据到es，入参:{}", JSONUtil.toJsonStr(entity));
        MsgEntity save = elasticsearchRestTemplate.save(entity);
        log.info("【测试es】保存数据返回:{}", JSONUtil.toJsonStr(save));
        return save;
    }

    public List<MsgEntity> findAll() {
        MatchAllQueryBuilder matchAllQueryBuilder = org.elasticsearch.index.query.QueryBuilders.matchAllQuery();
        NativeSearchQuery nativeSearchQuery = new NativeSearchQuery(matchAllQueryBuilder);
        //查询,获取查询结果
        SearchHits<MsgEntity> search = elasticsearchRestTemplate.search(nativeSearchQuery, MsgEntity.class);
        log.info("查询到的数据:{}", JSONUtil.toJsonStr(search));
        return search.getSearchHits().stream()
                .map(hit -> hit.getContent())
                .collect(Collectors.toList());
    }
}
