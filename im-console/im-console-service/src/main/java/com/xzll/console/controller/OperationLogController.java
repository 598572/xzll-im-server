package com.xzll.console.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.console.entity.OperationLogDO;
import com.xzll.console.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 操作日志Controller
 *
 * @author xzll
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/operation-logs")
@CrossOrigin(origins = "*")
public class OperationLogController {

    @Autowired
    private OperationLogService operationLogService;

    /**
     * 分页查询操作日志
     */
    @GetMapping("/page")
    public WebBaseResponse<IPage<OperationLogDO>> page(@RequestParam(defaultValue = "1") Integer current,
                                              @RequestParam(defaultValue = "10") Integer size,
                                              @RequestParam(required = false) String adminId,
                                              @RequestParam(required = false) String operationType) {
        try {
            Page<OperationLogDO> page = new Page<>(current, size);
            IPage<OperationLogDO> result = operationLogService.getPage(page, adminId, operationType);
            return WebBaseResponse.returnResultSuccess(result);
        } catch (Exception e) {
            log.error("查询操作日志失败: {}", e.getMessage());
            return WebBaseResponse.returnResultError(e.getMessage());
        }
    }
}
