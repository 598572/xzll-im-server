package com.xzll.console.aspect;

import com.alibaba.fastjson2.JSON;
import com.xzll.console.annotation.OperationLog;
import com.xzll.console.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * 操作日志切面
 * 拦截带有@OperationLog注解的方法，记录操作日志
 *
 * @author xzll
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    @Autowired
    private OperationLogService operationLogService;

    @Around("@annotation(com.xzll.console.annotation.OperationLog)")
    public Object recordLog(ProceedingJoinPoint pjp) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        String adminId = null;
        String adminName = null;
        String clientIp = null;

        if (request != null) {
            adminId = request.getHeader("X-Admin-Id");
            adminName = request.getHeader("X-Admin-Name");
            clientIp = getClientIp(request);
        }

        // 获取方法信息
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        OperationLog annotation = method.getAnnotation(OperationLog.class);

        // 获取请求参数
        String params = null;
        if (annotation.recordParams()) {
            try {
                Object[] args = pjp.getArgs();
                if (args != null && args.length > 0) {
                    params = JSON.toJSONString(args);
                }
            } catch (Exception e) {
                log.warn("序列化请求参数失败", e);
            }
        }

        String result = "SUCCESS";
        try {
            // 执行方法
            Object returnValue = pjp.proceed();
            long elapsedTime = System.currentTimeMillis() - startTime;

            log.info("操作执行成功: operationType={}, adminId={}, elapsedTime={}ms",
                    annotation.operationType(), adminId, elapsedTime);

            return returnValue;
        } catch (Exception e) {
            result = "FAILED: " + e.getMessage();
            log.error("操作执行失败: operationType={}, adminId={}, error={}",
                    annotation.operationType(), adminId, e.getMessage(), e);
            throw e;
        } finally {
            // 记录操作日志
            try {
                String targetId = extractTargetId(pjp.getArgs());
                operationLogService.recordLog(
                        adminId,
                        adminName,
                        annotation.operationType(),
                        annotation.targetType(),
                        targetId,
                        annotation.description(),
                        clientIp,
                        params,
                        result
                );
            } catch (Exception e) {
                log.error("记录操作日志失败", e);
            }
        }
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 处理多个IP的情况，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    /**
     * 从方法参数中提取目标ID
     */
    private String extractTargetId(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        // 尝试从参数中提取ID
        for (Object arg : args) {
            if (arg instanceof Long || arg instanceof Integer || arg instanceof String) {
                return String.valueOf(arg);
            }
        }

        return null;
    }
}
