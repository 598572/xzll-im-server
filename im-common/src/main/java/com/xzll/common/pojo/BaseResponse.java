package com.xzll.common.pojo;






import java.io.Serializable;

public class BaseResponse<T> implements Serializable{
	private int code;
	
	private String msg;

	private T data;

	public BaseResponse() {}

	public BaseResponse(int code, String msg, T data) {
		this.code = code;
		this.msg = msg;
		this.data = data;
	}

	/**
	 * 返回成功，沒有data值
	 */
	public static <T> BaseResponse<T> returnResultSuccess() {
		return setResult(AnswerCode.SUCCESS.getCode(), AnswerCode.SUCCESS.getMessage(), null);
	}

	/**
	 * 返回成功
	 *
	 * @param data
	 * @return
	 */
	public static <T> BaseResponse<T> returnResultSuccess(T data) {
		return setResult(AnswerCode.SUCCESS.getCode(), AnswerCode.SUCCESS.getMessage(), data);
	}

	/**
	 * 返回成功，沒有data值
	 *
	 * @param msg
	 * @return
	 */
	public static <T> BaseResponse<T> returnResultSuccess(String msg) {
		return returnResultSuccess(msg, null);
	}

	/**
	 * 返回成功
	 */
	public static <T> BaseResponse<T> returnResultSuccess(String msg, T data) {
		return setResult(AnswerCode.SUCCESS.getCode(), msg, data);
	}

	/**
	 * 返回失败, 可以指定code和msg
	 *
	 * @param code
	 * @param msg
	 * @return
	 */
	public static <T> BaseResponse<T> returnResultError(int code, String msg) {
		return setResult(code, msg, null);
	}

	/**
	 * 返回失败，可以传msg
	 *
	 * @param msg
	 * @return
	 */
	public static <T> BaseResponse<T> returnResultError(String msg) {
		return returnResultError(-200, msg);
	}

	/**
	 * 返回失败
	 * @param anwserCode
	 * @param <T>
	 * @return
	 */
	public static <T> BaseResponse<T> returnResultError(ImAnswerCode anwserCode) {
		if(anwserCode == null) {
			return returnResultError("操作失败");
		}

		return returnResultError(anwserCode.getCode(), anwserCode.getMessage());
	}

	/**
	 * 通用封装
	 *
	 * @param code 返回code
	 * @param msg  返回提示消息
	 * @param data 返回数据
	 * @return
	 */
	public static <T> BaseResponse<T> setResult(int code, String msg, T data) {
		return new BaseResponse(code, msg, data);
	}

	public static <T> BaseResponse<T> setResult(AnswerCode answerCode, T data) {
		return new BaseResponse(answerCode.getCode(), answerCode.getMessage(), data);
	}
	public static <T> BaseResponse<T> setResult(AnswerCode answerCode) {
		return new BaseResponse(answerCode.getCode(), answerCode.getMessage(), null);
	}

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

	public T getData() {
		return data;
	}

	public void setData(T data) {
		this.data = data;
	}


}
