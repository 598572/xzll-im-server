package com.xzll.business.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xzll.business.entity.mysql.ImC2CMsgRecord;
import com.xzll.business.mapper.ImC2CMsgRecordMapper;
import com.xzll.business.mapstruct.C2CMsgMapping;
import com.xzll.business.entity.es.MsgEntity;
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
