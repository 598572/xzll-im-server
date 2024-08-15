package com.xzll.common.constant.answercode;

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
    public static final AnswerCode REQUEST_ILLEGAL = new AnswerCode(-5, "请求非法");
    public static final AnswerCode PARAMETER_ERROR = new AnswerCode(-8, "参数错误");
    public static final AnswerCode VERIFICATION_CODE_ERROR = new AnswerCode(-9, "验证码错误");
    public static final AnswerCode SERVER_DEGRADE_ERROR = new AnswerCode(-10, "系统繁忙，稍后重试");
    public static final AnswerCode SERVER_FLOW_ERROR = new AnswerCode(-11, "系统繁忙，稍后重试");
    public static final AnswerCode ACCESSSERVER_ERROR = new AnswerCode(-12, "接入服务器错误");
    public static final AnswerCode TOKEN_INVALID = new AnswerCode(-13, "无效token");
    public static final AnswerCode FUNCTION_UNUSE = new AnswerCode(-22, "系统功能不支持");
    public static final AnswerCode FORBIDDEN = new AnswerCode(-23, "没有相关权限");
    public static final AnswerCode SELECT_NULL = new AnswerCode(-21, "查询为null");
    public static final AnswerCode UPDATE_ERROR = new AnswerCode(-18, "修改数据失败");
    public static final AnswerCode SAVE_ERROR = new AnswerCode(-17, "添加数据失败");
    public static final AnswerCode NOT_DATA = new AnswerCode(-127, "数据不存在");
    public static final AnswerCode SAVE_FAIL = new AnswerCode(-6, "保存失败");
    public static final AnswerCode PARAMETER_IS_NULL = new AnswerCode(-26, "参数为空");
    public static final AnswerCode ARGS_ERRORS = new AnswerCode(-29, "参数错误");
    public static final AnswerCode ID_NOTNULL = new AnswerCode(-301, "id不能为空");
    public static final AnswerCode ID_NOTEXIT = new AnswerCode(-302, "id不存在");
    public static final AnswerCode DATA_NOTEXIT = new AnswerCode(-303, "查无数据");

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
        return code.equals(URL_ERROR)  || code.equals(REQUEST_ILLEGAL) || code.equals(PARAMETER_ERROR) || code.equals(VERIFICATION_CODE_ERROR) || code.equals(TOKEN_INVALID);
    }
}
