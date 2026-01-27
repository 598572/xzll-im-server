package com.xzll.console.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.console.entity.ImChatDO;
import com.xzll.console.entity.ImUserDO;
import com.xzll.console.mapper.ImChatMapper;
import com.xzll.console.mapper.ImUserMapper;
import com.xzll.console.vo.SessionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author: hzz
 * @Date: 2026/01/23
 * @Description: 会话管理Controller - 管理后台专用
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/session")
@CrossOrigin(origins = "*")
public class SessionManageController {

    @Resource
    private ImChatMapper imChatMapper;
    
    @Resource
    private ImUserMapper imUserMapper;

    /**
     * 分页查询会话列表
     * 数据来源: MySQL im_chat 表
     */
    @GetMapping("/page")
    public WebBaseResponse<IPage<SessionVO>> pageSession(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) Integer chatType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        try {
            log.info("分页查询会话列表: current={}, size={}, userId={}, chatType={}, startTime={}, endTime={}",
                    current, size, userId, chatType, startTime, endTime);

            // 构建查询条件
            LambdaQueryWrapper<ImChatDO> queryWrapper = new LambdaQueryWrapper<>();

            // 按用户ID过滤（会话的任一方包含该用户）
            if (StrUtil.isNotBlank(userId)) {
                queryWrapper.and(wrapper -> wrapper
                        .eq(ImChatDO::getFromUserId, userId)
                        .or()
                        .eq(ImChatDO::getToUserId, userId)
                );
            }

            // 按会话类型过滤
            if (chatType != null) {
                queryWrapper.eq(ImChatDO::getChatType, chatType);
            }

            // 按创建时间范围过滤
            if (StrUtil.isNotBlank(startTime)) {
                queryWrapper.ge(ImChatDO::getCreateTime, startTime);
            }
            if (StrUtil.isNotBlank(endTime)) {
                // 结束日期加一天，确保包含当天数据
                String endDate = endTime + " 23:59:59";
                queryWrapper.le(ImChatDO::getCreateTime, endDate);
            }

            // 按创建时间倒序
            queryWrapper.orderByDesc(ImChatDO::getCreateTime);
            
            // 分页查询
            Page<ImChatDO> page = new Page<>(current, size);
            IPage<ImChatDO> chatPage = imChatMapper.selectPage(page, queryWrapper);
            
            // 获取所有用户ID用于查询用户信息
            List<String> allUserIds = chatPage.getRecords().stream()
                    .flatMap(chat -> List.of(chat.getFromUserId(), chat.getToUserId()).stream())
                    .distinct()
                    .collect(Collectors.toList());
            
            // 批量查询用户信息
            Map<String, ImUserDO> userMap = new HashMap<>();
            if (!allUserIds.isEmpty()) {
                LambdaQueryWrapper<ImUserDO> userQuery = new LambdaQueryWrapper<>();
                userQuery.in(ImUserDO::getUserId, allUserIds);
                List<ImUserDO> users = imUserMapper.selectList(userQuery);
                userMap = users.stream()
                        .collect(Collectors.toMap(ImUserDO::getUserId, u -> u, (a, b) -> a));
            }
            
            // 转换为VO
            Map<String, ImUserDO> finalUserMap = userMap;
            List<SessionVO> voList = chatPage.getRecords().stream()
                    .map(chat -> convertToVO(chat, finalUserMap))
                    .collect(Collectors.toList());
            
            // 构建返回结果
            Page<SessionVO> resultPage = new Page<>(current, size);
            resultPage.setRecords(voList);
            resultPage.setTotal(chatPage.getTotal());
            resultPage.setPages(chatPage.getPages());
            
            log.info("查询会话列表成功: total={}, records={}", chatPage.getTotal(), voList.size());
            return WebBaseResponse.returnResultSuccess(resultPage);
            
        } catch (Exception e) {
            log.error("查询会话列表失败", e);
            return WebBaseResponse.returnResultError("查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取会话统计信息
     */
    @GetMapping("/stats")
    public WebBaseResponse<Map<String, Object>> getSessionStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("total", imChatMapper.countTotal());
            stats.put("singleChat", imChatMapper.countSingleChat());
            stats.put("groupChat", imChatMapper.countGroupChat());
            return WebBaseResponse.returnResultSuccess(stats);
        } catch (Exception e) {
            log.error("获取会话统计失败", e);
            return WebBaseResponse.returnResultError("获取统计失败: " + e.getMessage());
        }
    }

    /**
     * 获取会话详情
     */
    @GetMapping("/{chatId}")
    public WebBaseResponse<SessionVO> getSessionDetail(@PathVariable String chatId) {
        try {
            LambdaQueryWrapper<ImChatDO> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ImChatDO::getChatId, chatId);
            ImChatDO chat = imChatMapper.selectOne(queryWrapper);
            
            if (chat == null) {
                return WebBaseResponse.returnResultError("会话不存在");
            }
            
            // 查询用户信息
            Map<String, ImUserDO> userMap = new HashMap<>();
            LambdaQueryWrapper<ImUserDO> userQuery = new LambdaQueryWrapper<>();
            userQuery.in(ImUserDO::getUserId, List.of(chat.getFromUserId(), chat.getToUserId()));
            List<ImUserDO> users = imUserMapper.selectList(userQuery);
            userMap = users.stream()
                    .collect(Collectors.toMap(ImUserDO::getUserId, u -> u, (a, b) -> a));
            
            SessionVO vo = convertToVO(chat, userMap);
            return WebBaseResponse.returnResultSuccess(vo);
            
        } catch (Exception e) {
            log.error("获取会话详情失败: chatId={}", chatId, e);
            return WebBaseResponse.returnResultError("获取详情失败: " + e.getMessage());
        }
    }

    /**
     * 将实体转换为VO
     */
    private SessionVO convertToVO(ImChatDO chat, Map<String, ImUserDO> userMap) {
        SessionVO vo = new SessionVO();
        vo.setId(chat.getId());
        vo.setChatId(chat.getChatId());
        vo.setFromUserId(chat.getFromUserId());
        vo.setToUserId(chat.getToUserId());
        vo.setChatType(chat.getChatType());
        vo.setCreateTime(chat.getCreateTime());
        vo.setUpdateTime(chat.getUpdateTime());
        
        // 填充用户信息
        ImUserDO fromUser = userMap.get(chat.getFromUserId());
        if (fromUser != null) {
            vo.setFromUserName(fromUser.getUserName());
            vo.setFromUserAvatar(fromUser.getHeadImage());
        }
        
        ImUserDO toUser = userMap.get(chat.getToUserId());
        if (toUser != null) {
            vo.setToUserName(toUser.getUserName());
            vo.setToUserAvatar(toUser.getHeadImage());
        }
        
        return vo;
    }
}
