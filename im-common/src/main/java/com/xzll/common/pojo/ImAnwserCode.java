package com.xzll.common.pojo;


/**
 * @author hzz
 */

public class ImAnwserCode extends AnwserCode {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public ImAnwserCode(int code, String message) {
        super(code, message);
    }

    public static final AnwserCode SELECT_NULL = new AnwserCode(-21, "查询为null");
    public static AnwserCode UPDATE_ERROR = new AnwserCode(-18, "修改数据失败");
    public static AnwserCode SAVE_ERROR = new AnwserCode(-17, "添加数据失败");
    public static AnwserCode NOT_DATA = new AnwserCode(-127, "数据不存在");
    public static AnwserCode SAVE_FAIL = new AnwserCode(-6, "保存失败");

    public static AnwserCode PARAMETER_IS_NULL = new AnwserCode(-26, "参数为空");
    public static AnwserCode ARGS_ERRORS = new AnwserCode(-29, "参数错误");

    public static AnwserCode ID_NOTNULL = new AnwserCode(-301, "id不能为空");
    public static AnwserCode ID_NOTEXIT = new AnwserCode(-302, "id不存在");
    public static AnwserCode DATA_NOTEXIT = new AnwserCode(-303, "查无数据");
}
