package com.xzll.common.controller;

import com.alibaba.fastjson.JSONObject;
import com.xzll.common.util.UserContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Author: Gang
 * Date: 2025/11/02 19:30
 * Description: Controller层的一些通用操作
 */
@Slf4j
public abstract class BaseController {

	protected String getCurrentUserId() {
		return UserContextUtils.getCurrentUserId();
	}

	protected String getCurrentUserIdWithValidation() {
		String userId = getCurrentUserId();
		if (StringUtils.isBlank(userId)) {
			log.warn("获取当前用户ID失败，用户未登录或token无效");
			return null;
		}
		return userId;
	}

	protected JSONObject getCurrentUserInfo() {
		return UserContextUtils.getCurrentUserInfo();
	}
}