package com.xzll.common.pojo;

import java.io.Serializable;

/**
 * @Author: hzz
 * @Date: 2024/5/30 13:18:58
 * @Description:
 */
public class AnswerCode implements Serializable {

    private static final long serialVersionUID = 1L;
    private int code = -1;
    private String message;
    public static final AnswerCode SUCCESS = new AnswerCode(1, "响应成功");
    public static final AnswerCode ERROR = new AnswerCode(-1, "系统繁忙，稍后重试");
    public static final AnswerCode APPLICATION_EXCEPTIONS = new AnswerCode(-2, "程序异常");
    public static final AnswerCode URL_ERROR = new AnswerCode(-3, "请求url错误");
    public static final AnswerCode OP_ERROR = new AnswerCode(-4, "请求op错误");
    public static final AnswerCode REQUEST_ILLEGAL = new AnswerCode(-5, "请求非法");
    public static final AnswerCode PARAMETER_ERROR = new AnswerCode(-8, "参数错误");
    public static final AnswerCode VERIFICATION_CODE_ERROR = new AnswerCode(-9, "验证码错误");
    public static final AnswerCode SERVER_DEGRADE_ERROR = new AnswerCode(-10, "系统繁忙，稍后重试");
    public static final AnswerCode SERVER_FLOW_ERROR = new AnswerCode(-11, "系统繁忙，稍后重试");
    public static AnswerCode ACCESSSERVER_ERROR = new AnswerCode(-12, "接入服务器错误");
    public static AnswerCode TOKEN_INVALID = new AnswerCode(-13, "无效token");
    public static AnswerCode FUNCTION_UNUSE = new AnswerCode(-22, "系统功能不支持");
    public static AnswerCode FORBIDDEN = new AnswerCode(-23, "没有相关权限");

    public static AnswerCode create(String message) {
        return new AnswerCode(message);
    }

    public static AnswerCode create(int code, String message) {
        return new AnswerCode(code, message);
    }

    public static AnswerCode create(AnswerCode code, String message) {
        return new AnswerCode(code.getCode(), message);
    }

    public AnswerCode() {
    }

    public AnswerCode(String message) {
        this.message = message;
    }

    public AnswerCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return this.code;
    }

    public String getMessage() {
        return this.message;
    }

    @Override
    public int hashCode() {
        return this.code;
    }

    @Override
    public String toString() {
        return "rtv=" + this.code + "   message=" + this.message;
    }

    @Override
    public boolean equals(Object obj) {
        AnswerCode temp = (AnswerCode)obj;
        return this.getCode() == temp.getCode();
    }

    public static boolean isServerError(AnswerCode code) {
        return code.equals(ERROR) || code.equals(APPLICATION_EXCEPTIONS) || code.equals(ACCESSSERVER_ERROR) || code.equals(FUNCTION_UNUSE);
    }

    public static boolean isClientError(AnswerCode code) {
        return code.equals(URL_ERROR) || code.equals(OP_ERROR) || code.equals(REQUEST_ILLEGAL) || code.equals(PARAMETER_ERROR) || code.equals(VERIFICATION_CODE_ERROR) || code.equals(TOKEN_INVALID);
    }
}
