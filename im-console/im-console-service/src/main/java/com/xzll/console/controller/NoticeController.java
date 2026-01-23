package com.xzll.console.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.console.entity.SystemNoticeDO;
import com.xzll.console.service.NoticeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 系统公告Controller
 *
 * @author xzll
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/notice")
@CrossOrigin(origins = "*")
public class NoticeController {

    @Autowired
    private NoticeService noticeService;

    /**
     * 分页查询公告列表
     */
    @GetMapping("/page")
    public WebBaseResponse<IPage<SystemNoticeDO>> page(@RequestParam(defaultValue = "1") Integer current,
                                              @RequestParam(defaultValue = "10") Integer size,
                                              @RequestParam(required = false) Integer status) {
        try {
            Page<SystemNoticeDO> page = new Page<>(current, size);
            IPage<SystemNoticeDO> result = noticeService.getPage(page, status);
            return WebBaseResponse.returnResultSuccess(result);
        } catch (Exception e) {
            log.error("查询公告列表失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }

    /**
     * 添加公告
     */
    @PostMapping("/add")
    public WebBaseResponse<Void> add(@RequestBody SystemNoticeDO notice,
                            @RequestHeader("X-Admin-Id") String adminId) {
        try {
            notice.setCreateBy(adminId);
            noticeService.save(notice);
            return WebBaseResponse.returnResultSuccess();
        } catch (Exception e) {
            log.error("添加公告失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }

    /**
     * 发布公告
     */
    @PostMapping("/{id}/publish")
    public WebBaseResponse<Void> publish(@PathVariable Long id,
                                @RequestHeader("X-Admin-Id") String adminId) {
        try {
            noticeService.publish(id, adminId);
            return WebBaseResponse.returnResultSuccess();
        } catch (Exception e) {
            log.error("发布公告失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }

    /**
     * 撤回公告
     */
    @PostMapping("/{id}/revoke")
    public WebBaseResponse<Void> revoke(@PathVariable Long id,
                               @RequestHeader("X-Admin-Id") String adminId) {
        try {
            noticeService.revoke(id);
            return WebBaseResponse.returnResultSuccess();
        } catch (Exception e) {
            log.error("撤回公告失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }
}
