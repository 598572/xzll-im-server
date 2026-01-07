package com.xzll.common.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Author: Gang
 * Date: 2025/11/02 19:30
 * Description: 用户上下文工具类
 */
@Slf4j
public class UserContextUtils {

	private static final String USER_ID_HEADER = "X-User-Id";

	private static final String USER_HEADER = "user";

	private static HttpServletRequest getCurrentRequest() {
		try {
			ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
			return attributes.getRequest();
		} catch (Exception e) {
			log.warn("无法获取当前请求对象", e);
			return null;
		}
	}

	public static String getCurrentUserId() {
		HttpServletRequest request = getCurrentRequest();
		return getCurrentUserId(request);
	}

	public static JSONObject getCurrentUserInfo() {
		HttpServletRequest request = getCurrentRequest();
		return getCurrentUserInfo(request);
	}

	public static String getCurrentUserId(HttpServletRequest request) {
		if (request == null) {
			log.warn("HttpServletRequest为空，无法获取用户ID");
			return null;
		}

		// 优先从X-User-Id头获取（网关已解析好的userId）
		String userId = request.getHeader(USER_ID_HEADER);
		if (StringUtils.isNotBlank(userId)) {
			return userId.trim();
		}

		// 如果X-User-Id头不存在，尝试从user头解析
		String userStr = request.getHeader(USER_HEADER);
		if (StringUtils.isNotBlank(userStr)) {
			try {
				JSONObject userJson = JSON.parseObject(userStr);
				if (userJson != null && userJson.containsKey("id")) {
					userId = userJson.getString("id");
					if (StringUtils.isNotBlank(userId)) {
						return userId.trim();
					}
				}
			} catch (Exception e) {
				log.warn("解析user头中的用户信息失败: {}", userStr, e);
			}
		}

		log.warn("无法从请求头中获取用户ID，请检查网关配置或认证状态");
		return null;
	}

	public static JSONObject getCurrentUserInfo(HttpServletRequest request) {
		if (request == null) {
			log.warn("HttpServletRequest为空，无法获取用户信息");
			return null;
		}

		String userStr = request.getHeader(USER_HEADER);
		if (StringUtils.isBlank(userStr)) {
			log.warn("请求头中不存在用户信息");
			return null;
		}

		try {
			return JSON.parseObject(userStr);
		} catch (Exception e) {
			log.warn("解析用户信息失败: {}", userStr, e);
			return null;
		}
	}
}