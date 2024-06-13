package com.xzll.common.pojo;


/**
 * @author hzz
 */

public class ImAnswerCode extends AnswerCode {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public ImAnswerCode(int code, String message) {
        super(code, message);
    }

    public static final AnswerCode SELECT_NULL = new AnswerCode(-21, "查询为null");
    public static AnswerCode UPDATE_ERROR = new AnswerCode(-18, "修改数据失败");
    public static AnswerCode SAVE_ERROR = new AnswerCode(-17, "添加数据失败");
    public static AnswerCode NOT_DATA = new AnswerCode(-127, "数据不存在");
    public static AnswerCode SAVE_FAIL = new AnswerCode(-6, "保存失败");

    public static AnswerCode PARAMETER_IS_NULL = new AnswerCode(-26, "参数为空");
    public static AnswerCode ARGS_ERRORS = new AnswerCode(-29, "参数错误");

    public static AnswerCode ID_NOTNULL = new AnswerCode(-301, "id不能为空");
    public static AnswerCode ID_NOTEXIT = new AnswerCode(-302, "id不存在");
    public static AnswerCode DATA_NOTEXIT = new AnswerCode(-303, "查无数据");
}
