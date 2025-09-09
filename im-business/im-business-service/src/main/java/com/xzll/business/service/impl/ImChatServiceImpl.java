package com.xzll.business.service.impl;

import org.springframework.util.CollectionUtils;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.collect.Lists;
import com.xzll.business.entity.mysql.ImChat;
import com.xzll.business.entity.mysql.ImC2CMsgRecord;
import com.xzll.business.entity.mysql.ImPersonalChatOpt;
import com.xzll.business.mapper.ImChatMapper;
import com.xzll.business.service.ImC2CMsgRecordHBaseService;
import com.xzll.business.service.ImChatService;
import com.xzll.business.service.ImPersonalChatOptService;
import com.xzll.business.service.UnreadCountService;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.pojo.request.LastChatListAO;
import com.xzll.common.pojo.response.LastChatListVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author: hzz
 * @Date: 2024/6/3 09:10:48
 * @Description:
 */
@Service
@Slf4j
public class ImChatServiceImpl implements ImChatService {

    @Resource
    private ImChatMapper imChatMapper;

    @Resource
    private ImPersonalChatOptService imPersonalChatOptService;

    @Resource
    private ImC2CMsgRecordHBaseService imC2CMsgRecordHBaseService;

    @Resource
    private ConversionService conversionService;

    @Resource
    private UnreadCountService unreadCountService;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public boolean saveOrUpdateC2CChat(C2CSendMsgAO dto) {
        log.info("保存/更新C2C会话信息_入参:{}", JSONUtil.toJsonStr(dto));

        try {
            // 查询会话是否存在
            LambdaQueryWrapper<ImChat> queryWrapper = Wrappers.lambdaQuery(ImChat.class)
                    .eq(ImChat::getChatId, dto.getChatId());
            ImChat existingChat = imChatMapper.selectOne(queryWrapper);

            if (existingChat != null) {
                // 会话已存在，不需要更新最后消息信息（已移除）
                log.info("会话已存在，无需更新: chatId={}", dto.getChatId());
                return true;
            } else {
                // 创建新会话
                ImChat newChat = new ImChat();
                newChat.setChatId(dto.getChatId());
                newChat.setFromUserId(dto.getFromUserId());
                newChat.setToUserId(dto.getToUserId());
                newChat.setChatType(1); // 单聊类型
                
                int insertResult = imChatMapper.insert(newChat);
                log.info("创建新会话: chatId={}, result={}", dto.getChatId(), insertResult);
                return insertResult > 0;
            }
        } catch (Exception e) {
            log.error("保存/更新C2C会话信息失败", e);
            return false;
        }
    }


    /**
     * 查询最近会话列表
     * 
     * 正确的查询流程：
     * 1. 从 im_chat 表查询用户参与的所有会话（作为发送者或接收者）
     * 2. 查询用户对这些会话的个人操作（置顶、隐藏、删除等）
     * 3. 从 HBase 批量查询每个会话的最后消息
     * 4. 从 Redis 批量获取未读消息数
     * 5. 在内存中组装数据并按最后消息时间排序
     * 6. 处理置顶逻辑
     * 7. 在内存中分页返回结果
     * 
     * @param ao 查询参数
     * @return 会话列表
     */
    @Override
    public List<LastChatListVO> findLastChatList(LastChatListAO ao) {
        log.info("查询最近会话列表_入参:{}", JSONUtil.toJsonStr(ao));
        
        // 1. 先查询用户参与的所有会话
        List<ImChat> userChats = getUserChats(ao.getUserId());
        
        if (CollectionUtils.isEmpty(userChats)) {
            log.info("用户{}没有任何会话", ao.getUserId());
            return Lists.newArrayList();
        }
        
        // 2. 获取所有会话ID
        List<String> chatIds = userChats.stream()
                .map(ImChat::getChatId)
                .collect(Collectors.toList());
        
        log.info("用户{}共有{}个会话，开始查询最后消息和个人操作", ao.getUserId(), chatIds.size());
        
        // 3. 查询用户对这些会话的个人操作（置顶、隐藏、删除等）
        ImPersonalChatOpt queryOpt = new ImPersonalChatOpt();
        queryOpt.setUserId(ao.getUserId());
        List<ImPersonalChatOpt> allPersonalChats = imPersonalChatOptService.findPersonalChatByUserId(queryOpt, null, null);
        
        // 4. 从HBase批量查询每个会话的最后一条消息
        Map<String, ImC2CMsgRecord> lastMsgMap = imC2CMsgRecordHBaseService.batchGetLastMessagesByChatIds(chatIds);
        
        // 5. 从Redis批量获取未读消息数
        Map<String, Integer> unreadCountMap = unreadCountService.getAllUnreadCounts(ao.getUserId());
        
        // 6. 创建个人操作映射，用于过滤隐藏/删除的会话
        Map<String, ImPersonalChatOpt> personalChatMap = allPersonalChats.stream()
                .collect(Collectors.toMap(ImPersonalChatOpt::getChatId, opt -> opt));
        
        // 7. 组装返回结果，过滤隐藏和删除的会话
        List<LastChatListVO> allChatListVOs = userChats.stream()
                .filter(chat -> {
                    String chatId = chat.getChatId();
                    ImPersonalChatOpt personalOpt = personalChatMap.get(chatId);
                    
                    // 如果用户对该会话有操作记录，检查是否隐藏或删除
                    if (personalOpt != null) {
                        // 过滤隐藏的会话
                        if (ImConstant.CommonConstant.YES.equals(personalOpt.getUnShow())) {
                            log.debug("会话{}被用户{}隐藏，跳过", chatId, ao.getUserId());
                            return false;
                        }
                        // 过滤删除的会话
                        if (ImConstant.CommonConstant.YES.equals(personalOpt.getDelChat())) {
                            log.debug("会话{}被用户{}删除，跳过", chatId, ao.getUserId());
                            return false;
                        }
                    }
                    return true;
                })
                .map(chat -> {
                    String chatId = chat.getChatId();
                    ImC2CMsgRecord lastMsg = lastMsgMap.get(chatId);
                    
                    LastChatListVO vo = new LastChatListVO();
                    vo.setChatId(chatId);
                    vo.setUserId(ao.getUserId());
                    
                    // 设置未读消息数（从Redis获取）
                    Integer unreadCount = unreadCountMap.get(chatId);
                    vo.setUnReadCount(unreadCount != null ? unreadCount : 0);
                    
                    // 设置最后消息信息
                    if (lastMsg != null) {
                        vo.setLastMsgFormat(lastMsg.getMsgFormat());
                        vo.setLastMessageContent(lastMsg.getMsgContent());
                        vo.setLastMsgId(lastMsg.getMsgId());
                        vo.setLastMsgTime(lastMsg.getMsgCreateTime());
                        vo.setMsgId(lastMsg.getMsgId());
                        vo.setMsgCreateTime(lastMsg.getMsgCreateTime());
                    } else {
                        // 如果HBase中没有找到最后一条消息，设置默认值
                        log.warn("会话{}未找到最后一条消息记录", chatId);
                        vo.setLastMessageContent("");
                        vo.setLastMsgFormat(0);
                        vo.setLastMsgId("");
                        vo.setLastMsgTime(chat.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                        vo.setMsgId("");
                        vo.setMsgCreateTime(chat.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                    }
                    
                    vo.setUrl("/api/chat/lastChatList");
                    return vo;
                })
                .collect(Collectors.toList());

        // 8. 按最后消息时间倒序排序
        allChatListVOs.sort((a, b) -> Long.compare(b.getLastMsgTime(), a.getLastMsgTime()));

        // 9. 处理置顶逻辑
        allChatListVOs = sortChatListWithTopPriority(allChatListVOs, allPersonalChats);

        // 10. 在内存中分页
        List<LastChatListVO> result = applyPagination(allChatListVOs, ao.getCurrentPage(), ao.getPageSize());

        log.info("查询最近会话列表成功，用户ID:{}, 总会话数:{}, 返回{}条记录", 
                ao.getUserId(), allChatListVOs.size(), result.size());
        
        return result;
    }

    /**
     * 查询用户参与的所有会话
     * @param userId 用户ID
     * @return 用户参与的会话列表
     */
    private List<ImChat> getUserChats(String userId) {
        log.info("查询用户{}参与的所有会话", userId);
        
        try {
            LambdaQueryWrapper<ImChat> queryWrapper = Wrappers.lambdaQuery(ImChat.class)
                    .and(wrapper -> wrapper
                            .eq(ImChat::getFromUserId, userId)
                            .or()
                            .eq(ImChat::getToUserId, userId)
                    )
                    .orderByDesc(ImChat::getCreateTime);
            
            List<ImChat> chats = imChatMapper.selectList(queryWrapper);
            
            log.info("查询到用户{}参与的会话{}个", userId, chats.size());
            return chats;
            
        } catch (Exception e) {
            log.error("查询用户{}参与的会话失败", userId, e);
            return Lists.newArrayList();
        }
    }


    /**
     * 在内存中应用分页
     * @param allChats 所有会话列表
     * @param currentPage 当前页码
     * @param pageSize 每页大小
     * @return 分页后的结果
     */
    private List<LastChatListVO> applyPagination(List<LastChatListVO> allChats, Integer currentPage, Integer pageSize) {
        if (CollectionUtils.isEmpty(allChats)) {
            return Lists.newArrayList();
        }

        // 设置默认值
        if (currentPage == null || currentPage <= 0) {
            currentPage = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 20;
        }

        // 限制每页最大数量
        if (pageSize > 100) {
            pageSize = 100;
        }

        int start = (currentPage - 1) * pageSize;
        int end = Math.min(start + pageSize, allChats.size());
        
        if (start >= allChats.size()) {
            return Lists.newArrayList();
        }
        
        return allChats.subList(start, end);
    }

    /**
     * 处理置顶逻辑，将置顶的会话排到头部
     * @param allChats 所有会话列表
     * @param personalChats 个人操作记录
     * @return 排序后的会话列表
     */
    private List<LastChatListVO> sortChatListWithTopPriority(List<LastChatListVO> allChats, List<ImPersonalChatOpt> personalChats) {
        if (CollectionUtils.isEmpty(allChats)) {
            return allChats;
        }

        // 创建个人操作映射
        Map<String, ImPersonalChatOpt> personalChatMap = personalChats.stream()
                .collect(Collectors.toMap(ImPersonalChatOpt::getChatId, opt -> opt));

        // 分离置顶和非置顶会话
        List<LastChatListVO> topChats = Lists.newArrayList();
        List<LastChatListVO> normalChats = Lists.newArrayList();

        for (LastChatListVO chat : allChats) {
            ImPersonalChatOpt personalChat = personalChatMap.get(chat.getChatId());
            if (personalChat != null && ImConstant.CommonConstant.YES.equals(personalChat.getToTop())) {
                topChats.add(chat);
            } else {
                normalChats.add(chat);
            }
        }

        // 分别对置顶和普通会话按最后消息时间倒序排序
        topChats.sort((a, b) -> Long.compare(b.getLastMsgTime(), a.getLastMsgTime()));
        normalChats.sort((a, b) -> Long.compare(b.getLastMsgTime(), a.getLastMsgTime()));

        // 置顶会话在前，普通会话在后
        List<LastChatListVO> result = Lists.newArrayList();
        result.addAll(topChats);
        result.addAll(normalChats);

        log.info("置顶处理完成，置顶会话数:{}, 普通会话数:{}", topChats.size(), normalChats.size());
        return result;
    }
}
