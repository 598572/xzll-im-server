package com.xzll.common.util;


import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ServerWebExchange;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetUtils {

	private static final Logger logger = LoggerFactory.getLogger(NetUtils.class);
	private static final Pattern IPV4_PATTERN = Pattern.compile("^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");
	private static final Pattern IPV6_STD_PATTERN = Pattern.compile("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");
	private static final Pattern IPV6_HEX_COMPRESSED_PATTERN = Pattern.compile("^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$");
	private static Properties outAddrPro;

	public NetUtils() {
	}

	public static String getMachineHostName() {
		String hostname = null;

		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException var2) {
		}

		return hostname;
	}

	public static String getMachineIpAddr() {
		String hostname = null;

		try {
			hostname = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException var2) {
		}

		return hostname;
	}


	public static String getIpAddress(ServerWebExchange exchange) {
		String ip = getHeaderValue(exchange, "x-forwarded-for");
		if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
			ip = getHeaderValue(exchange, "Proxy-Client-IP");
		}
		if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
			ip = getHeaderValue(exchange, "WL-Proxy-Client-IP");
		}
		if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
			ip = getHeaderValue(exchange, "HTTP_CLIENT_IP");
		}
		if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
			ip = getHeaderValue(exchange, "HTTP_X_FORWARDED_FOR");
		}
		if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
			InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
			if (remoteAddress != null) {
				ip = remoteAddress.getAddress().getHostAddress();
			}
		}
		return StringUtils.isEmpty(ip) ? null : ip.split(",")[0];
	}

	private static String getHeaderValue(ServerWebExchange exchange, String headerName) {
		return exchange.getRequest().getHeaders().getFirst(headerName);
	}

	public static String getIpAddress(HttpServletRequest request) {
		String ip = request.getHeader("x-forwarded-for");
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("Proxy-Client-IP");
		}

		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("WL-Proxy-Client-IP");
		}

		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("HTTP_CLIENT_IP");
		}

		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("HTTP_X_FORWARDED_FOR");
		}

		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getRemoteAddr();
		}

		return StringUtils.isBlank(ip) ? null : ip.split(",")[0];
	}

	public static boolean checkIfInnerIp(String ip) {
		String pattern = "((127\\.0\\.0\\.1)|(192\\.168|172\\.([1][6-9]|[2]\\d|3[01]))(\\.([2][0-4]\\d|[2][5][0-5]|[01]?\\d?\\d)){2}|^(\\D)*10(\\.([2][0-4]\\d|[2][5][0-5]|[01]?\\d?\\d)){3})";
		Pattern reg = Pattern.compile(pattern);
		Matcher match = reg.matcher(ip);
		return match.find();
	}

	public static boolean isIPv4Address(String input) {
		return IPV4_PATTERN.matcher(input).matches();
	}

	private static boolean isIPv6StdAddress(String input) {
		return IPV6_STD_PATTERN.matcher(input).matches();
	}

	private static boolean isIPv6HexCompressedAddress(String input) {
		return IPV6_HEX_COMPRESSED_PATTERN.matcher(input).matches();
	}

	public static boolean isIPv6Address(String input) {
		return isIPv6StdAddress(input) || isIPv6HexCompressedAddress(input);
	}

	public static String getOneInnerIp() {
		try {
			Enumeration netInterfaces = NetworkInterface.getNetworkInterfaces();

			while(netInterfaces.hasMoreElements()) {
				NetworkInterface ni = (NetworkInterface)netInterfaces.nextElement();
				Enumeration addresses = ni.getInetAddresses();

				while(addresses.hasMoreElements()) {
					InetAddress inetAddress = (InetAddress)addresses.nextElement();
					if (!inetAddress.isLoopbackAddress()) {
						String ip = inetAddress.getHostAddress();
						if (isIPv4Address(ip) && checkIfInnerIp(ip)) {
							return ip;
						}
					}
				}
			}
		} catch (Exception var5) {
			logger.error("获取主机内网ip异常,", var5);
		}

		return null;
	}

	public static List<String> getAllInnerIp() {
		ArrayList ips = new ArrayList();

		try {
			Enumeration netInterfaces = NetworkInterface.getNetworkInterfaces();

			while(netInterfaces.hasMoreElements()) {
				NetworkInterface ni = (NetworkInterface)netInterfaces.nextElement();
				Enumeration addresses = ni.getInetAddresses();

				while(addresses.hasMoreElements()) {
					InetAddress inetAddress = (InetAddress)addresses.nextElement();
					if (!inetAddress.isLoopbackAddress()) {
						String ip = inetAddress.getHostAddress();
						if (isIPv4Address(ip) && checkIfInnerIp(ip)) {
							ips.add(ip);
						}
					}
				}
			}
		} catch (Exception var6) {
			logger.error("获取主机内网ip异常,", var6);
		}

		return ips;
	}

	public static String getOutIpAddr() {
		String outAddr = null;
		return (String)outAddr;
	}
}
