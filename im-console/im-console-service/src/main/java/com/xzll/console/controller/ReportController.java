package com.xzll.console.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.console.entity.ReportDO;
import com.xzll.console.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 举报处理Controller
 *
 * @author xzll
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/report")
@CrossOrigin(origins = "*")
public class ReportController {

    @Autowired
    private ReportService reportService;

    /**
     * 分页查询举报列表
     */
    @GetMapping("/page")
    public WebBaseResponse<IPage<ReportDO>> page(@RequestParam(defaultValue = "1") Integer current,
                                        @RequestParam(defaultValue = "10") Integer size,
                                        @RequestParam(required = false) Integer status) {
        try {
            Page<ReportDO> page = new Page<>(current, size);
            IPage<ReportDO> result = reportService.getPage(page, status);
            return WebBaseResponse.returnResultSuccess(result);
        } catch (Exception e) {
            log.error("查询举报列表失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }

    /**
     * 处理举报
     */
    @PostMapping("/{id}/handle")
    public WebBaseResponse<Void> handleReport(@PathVariable Long id,
                                      @RequestParam Integer handleResult,
                                      @RequestParam(required = false) String result,
                                      @RequestHeader("X-Admin-Id") String adminId) {
        try {
            reportService.handleReport(id, handleResult, result, adminId);
            return WebBaseResponse.returnResultSuccess();
        } catch (Exception e) {
            log.error("处理举报失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }
}
