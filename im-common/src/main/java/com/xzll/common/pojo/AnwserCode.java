package com.xzll.common.pojo;

import java.io.Serializable;

/**
 * @Author: hzz
 * @Date: 2024/5/30 13:18:58
 * @Description:
 */
public class AnwserCode implements Serializable {

    private static final long serialVersionUID = 1L;
    private int code = -1;
    private String message;
    public static final AnwserCode SUCCESS = new AnwserCode(1, "响应成功");
    public static final AnwserCode ERROR = new AnwserCode(-1, "系统繁忙，稍后重试");
    public static final AnwserCode APPLICATION_EXCEPTIONS = new AnwserCode(-2, "程序异常");
    public static final AnwserCode URL_ERROR = new AnwserCode(-3, "请求url错误");
    public static final AnwserCode OP_ERROR = new AnwserCode(-4, "请求op错误");
    public static final AnwserCode REQUEST_ILLEGAL = new AnwserCode(-5, "请求非法");
    public static final AnwserCode PARAMETER_ERROR = new AnwserCode(-8, "参数错误");
    public static final AnwserCode VERIFICATION_CODE_ERROR = new AnwserCode(-9, "验证码错误");
    public static final AnwserCode SERVER_DEGRADE_ERROR = new AnwserCode(-10, "系统繁忙，稍后重试");
    public static final AnwserCode SERVER_FLOW_ERROR = new AnwserCode(-11, "系统繁忙，稍后重试");
    public static AnwserCode ACCESSSERVER_ERROR = new AnwserCode(-12, "接入服务器错误");
    public static AnwserCode TOKEN_INVALID = new AnwserCode(-13, "无效token");
    public static AnwserCode FUNCTION_UNUSE = new AnwserCode(-22, "系统功能不支持");

    public static AnwserCode create(String message) {
        return new AnwserCode(message);
    }

    public static AnwserCode create(int code, String message) {
        return new AnwserCode(code, message);
    }

    public static AnwserCode create(AnwserCode code, String message) {
        return new AnwserCode(code.getCode(), message);
    }

    public AnwserCode() {
    }

    public AnwserCode(String message) {
        this.message = message;
    }

    public AnwserCode(int code, String message) {
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
        AnwserCode temp = (AnwserCode)obj;
        return this.getCode() == temp.getCode();
    }

    public static boolean isServerError(AnwserCode code) {
        return code.equals(ERROR) || code.equals(APPLICATION_EXCEPTIONS) || code.equals(ACCESSSERVER_ERROR) || code.equals(FUNCTION_UNUSE);
    }

    public static boolean isClientError(AnwserCode code) {
        return code.equals(URL_ERROR) || code.equals(OP_ERROR) || code.equals(REQUEST_ILLEGAL) || code.equals(PARAMETER_ERROR) || code.equals(VERIFICATION_CODE_ERROR) || code.equals(TOKEN_INVALID);
    }
}
