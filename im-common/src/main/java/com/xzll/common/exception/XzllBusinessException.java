package com.xzll.common.exception;

import com.xzll.common.constant.answercode.AnswerCode;

/**
 * @Auther: Huangzhuangzhuang
 * @Date: 2021/8/7 18:57
 * @Description:
 */
public class XzllBusinessException extends RuntimeException{

	private String msg;
	private Object errorObj;
	protected int code;

	public XzllBusinessException(String msg){
		super(msg);
	}

	public XzllBusinessException(String msg, Object errorObj, int code) {
		this.msg = msg;
		this.errorObj = errorObj;
		this.code = code;
	}

	public XzllBusinessException(String message, String msg, Object errorObj, int code) {
		super(message);
		this.msg = msg;
		this.errorObj = errorObj;
		this.code = code;
	}

	public XzllBusinessException(String message, Throwable cause, String msg, Object errorObj, int code) {
		super(message, cause);
		this.msg = msg;
		this.errorObj = errorObj;
		this.code = code;
	}

	public XzllBusinessException(Throwable cause, String msg, Object errorObj, int code) {
		super(cause);
		this.msg = msg;
		this.errorObj = errorObj;
		this.code = code;
	}

	public XzllBusinessException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, String msg, Object errorObj, int code) {
		super(message, cause, enableSuppression, writableStackTrace);
		this.msg = msg;
		this.errorObj = errorObj;
		this.code = code;
	}



	public XzllBusinessException(AnswerCode answerCode) {
		this.msg = answerCode.getMessage();
		this.code = answerCode.getCode();
	}



	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

	public Object getErrorObj() {
		return errorObj;
	}

	public void setErrorObj(Object errorObj) {
		this.errorObj = errorObj;
	}

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}
}
