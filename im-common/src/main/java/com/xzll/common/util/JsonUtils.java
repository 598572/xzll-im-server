package com.xzll.common.util;

import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2024/6/16 09:59:44
 * @Description:
 */
public class JsonUtils {

    private static ObjectMapper objectMapper;

    private JsonUtils() {
    }

    /**
     * 对象转json串
     *
     * @param object 对象
     * @return str
     */
    @SneakyThrows
    public static String toJsonStr(Object object) {
        return getObjectMapper().writeValueAsString(object);
    }

    /**
     * 解析对象
     *
     * @param str   json字符串
     * @param clazz 类型
     * @return 对象
     */
    @SneakyThrows
    public static <T> T fromJsonStr(String str, Class<T> clazz) {
        return getObjectMapper().readValue(str, clazz);
    }


    /**
     * 解析对象
     *
     * @param str json字符串
     * @return 对象
     */
    @SneakyThrows
    public static <K, V> Map<K, V> fromJsonStrToMap(String str, Class<K> keyType, Class<V> valueType) {
        return getObjectMapper().readValue(str, getObjectMapper().getTypeFactory()
                .constructMapType(Map.class, keyType, valueType));
    }

    /**
     * 解析对象
     *
     * @param bytes json字符串
     * @param clazz 类型
     * @return 对象
     */
    @SneakyThrows
    public static <T> T fromJsonByte(byte[] bytes, Class<T> clazz) {
        return getObjectMapper().readValue(bytes, clazz);
    }


    /**
     * 解析对象
     *
     * @param bytes json字符串
     * @param clazz 类型
     * @return 对象
     */
    @SneakyThrows
    public static <T> T fromJsonByte(byte[] bytes, TypeReference<T> clazz) {
        return getObjectMapper().readValue(bytes, clazz);
    }

    /**
     * 解析对象
     *
     * @param str json字符串
     * @return 对象
     */
    @SneakyThrows
    public static <T> List<T> fromJsonList(String str, Class<T> tClass) {
        return getObjectMapper().readValue(str, getObjectMapper().getTypeFactory()
                .constructCollectionType(List.class, tClass));
    }

    /**
     * 解析对象
     *
     * @param str   json字符串
     * @param clazz TypeReference
     * @return 对象
     */
    @SneakyThrows
    public static <T> T fromJsonStr(String str, TypeReference<T> clazz) {
        return getObjectMapper().readValue(str, clazz);
    }

    /**
     * 解析对象
     *
     * @param str   json字符串
     * @param clazz TypeReference
     * @return 对象
     */
    @SneakyThrows
    public static <T> T fromJsonStr(String str, Type clazz) {
        return getObjectMapper().readValue(str, getObjectMapper().getTypeFactory().constructType(clazz));
    }

    private static synchronized ObjectMapper getObjectMapper() {
        if (objectMapper != null) {
            return objectMapper;
        }
        objectMapper = SpringUtil.getBean(ObjectMapper.class);
        return objectMapper;
    }
}
