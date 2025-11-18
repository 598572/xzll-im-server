package com.xzll.common.utils;

import lombok.extern.slf4j.Slf4j;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * 数据压缩工具类
 * 使用LZ4算法进行高速压缩/解压
 * 
 * LZ4特点：
 * - 压缩速度：~500 MB/s
 * - 解压速度：~2 GB/s
 * - 压缩率：可减少50-70%体积
 * 
 * @Author: hzz
 * @Date: 2025-11-18
 */
@Slf4j
public class CompressionUtil {
    
    private static final LZ4Factory factory = LZ4Factory.fastestInstance();
    private static final LZ4Compressor compressor = factory.fastCompressor();
    private static final LZ4FastDecompressor decompressor = factory.fastDecompressor();
    
    /**
     * LZ4压缩并Base64编码（适合存储到Redis）
     * 
     * @param data 原始字符串
     * @return Base64编码的压缩数据
     */
    public static String compressToBase64(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        
        try {
            byte[] compressed = compressLZ4(data);
            return Base64.getEncoder().encodeToString(compressed);
        } catch (Exception e) {
            log.error("LZ4压缩失败，返回原始数据", e);
            return data;
        }
    }
    
    /**
     * Base64解码并LZ4解压
     * 
     * @param base64Data Base64编码的压缩数据
     * @return 原始字符串
     */
    public static String decompressFromBase64(String base64Data) {
        if (base64Data == null || base64Data.isEmpty()) {
            return base64Data;
        }
        
        try {
            byte[] compressed = Base64.getDecoder().decode(base64Data);
            return decompressLZ4(compressed);
        } catch (Exception e) {
            log.error("LZ4解压失败，返回原始数据: {}", base64Data, e);
            // 如果解压失败，可能是未压缩的数据，直接返回
            return base64Data;
        }
    }
    
    /**
     * LZ4压缩（内部方法）
     * 
     * 数据格式：[4字节原始长度] + [压缩数据]
     * 
     * @param data 原始字符串
     * @return 压缩后的字节数组
     */
    private static byte[] compressLZ4(String data) {
        byte[] srcBytes = data.getBytes(StandardCharsets.UTF_8);
        int maxCompressedLength = compressor.maxCompressedLength(srcBytes.length);
        byte[] compressed = new byte[maxCompressedLength + 4]; // +4存储原始长度
        
        // 前4字节存储原始数据长度（大端序）
        compressed[0] = (byte) (srcBytes.length >> 24);
        compressed[1] = (byte) (srcBytes.length >> 16);
        compressed[2] = (byte) (srcBytes.length >> 8);
        compressed[3] = (byte) srcBytes.length;
        
        // 压缩数据
        int compressedLength = compressor.compress(srcBytes, 0, srcBytes.length, 
            compressed, 4, maxCompressedLength);
        
        // 返回实际使用的字节数组
        return Arrays.copyOf(compressed, compressedLength + 4);
    }
    
    /**
     * LZ4解压（内部方法）
     * 
     * @param compressed 压缩后的字节数组
     * @return 原始字符串
     */
    private static String decompressLZ4(byte[] compressed) {
        // 读取原始长度（大端序）
        int originalLength = ((compressed[0] & 0xFF) << 24) |
                            ((compressed[1] & 0xFF) << 16) |
                            ((compressed[2] & 0xFF) << 8) |
                            (compressed[3] & 0xFF);
        
        // 解压数据
        byte[] restored = new byte[originalLength];
        decompressor.decompress(compressed, 4, restored, 0, originalLength);
        
        return new String(restored, StandardCharsets.UTF_8);
    }
    
    /**
     * 计算压缩率
     * 
     * @param originalSize 原始大小
     * @param compressedSize 压缩后大小
     * @return 压缩率百分比（例如：60表示压缩后占原始大小的60%）
     */
    public static double compressionRatio(int originalSize, int compressedSize) {
        if (originalSize == 0) {
            return 0;
        }
        return (double) compressedSize / originalSize * 100;
    }
}
