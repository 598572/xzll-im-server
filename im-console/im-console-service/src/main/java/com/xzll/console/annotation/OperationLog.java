package com.xzll.console.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解
 * 用于标记需要记录操作日志的方法
 *
 * @author xzll
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /**
     * 操作类型
     * 例如: USER_DISABLE, REPORT_HANDLE, NOTICE_PUBLISH等
     */
    String operationType() default "";

    /**
     * 目标类型
     * 例如: USER, REPORT, NOTICE等
     */
    String targetType() default "";

    /**
     * 操作描述
     */
    String description() default "";

    /**
     * 是否记录请求参数
     */
    boolean recordParams() default true;
}
