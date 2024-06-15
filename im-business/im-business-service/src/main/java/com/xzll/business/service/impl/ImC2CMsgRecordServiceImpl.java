package com.xzll.business.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
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

import javax.annotation.Resource;
import java.util.List;
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


    @Override
    public void saveC2CMsg(C2CMsgRequestDTO dto) {
        log.info("保存单聊消息入参:{}", JSONUtil.toJsonStr(dto));
        ImC2CMsgRecord imC2CMsgRecord = c2CMsgMapping.convertC2CMsgRecord(dto);
        int row = imC2CMsgRecordMapper.insert(imC2CMsgRecord);
        log.info("保存单聊消息结果:{}", row);
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
        log.info("查询到的数据:{}",JSONUtil.toJsonStr(search));
        return search.getSearchHits().stream()
                .map(hit -> hit.getContent())
                .collect(Collectors.toList());
    }
}
