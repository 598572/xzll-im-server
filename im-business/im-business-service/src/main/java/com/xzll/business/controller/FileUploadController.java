package com.xzll.business.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;

import javax.annotation.Resource;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 文件上传控制器
 * 处理头像等文件上传到MinIO
 */
@RestController
@RequestMapping("/api/file")
@Slf4j
public class FileUploadController {

    @Value("${minio.endpoint}")
    private String minioEndpoint;
    
    @Value("${minio.bucketName}")
    private String bucketName;
    
    @Resource
    private MinioClient minioClient;
    
    @Autowired
    private UserProfileController userProfileController;
    
    /**
     * 上传头像
     */
    @PostMapping("/uploadAvatar")
    public AvatarUploadResult uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId) {
        
        log.info("用户{}上传头像，文件大小：{}", userId, file.getSize());
        
        try {
            // 1. 参数校验
            if (file.isEmpty()) {
                return AvatarUploadResult.error("文件不能为空");
            }
            
            // 2. 文件类型校验
            String contentType = file.getContentType();
            if (!isImageFile(contentType)) {
                return AvatarUploadResult.error("只支持图片格式文件");
            }
            
            // 3. 文件大小校验（限制5MB）
            if (file.getSize() > 5 * 1024 * 1024) {
                return AvatarUploadResult.error("文件大小不能超过5MB");
            }
            
            // 4. 生成文件名
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            String fileName = "avatars/" + userId + "/" + UUID.randomUUID() + fileExtension;
            
            // 5. 上传到MinIO
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(contentType)
                    .build()
            );
            
            // 6. 构建访问URL
            String avatarUrl = minioEndpoint + "/" + bucketName + "/" + fileName;
            
            // 7. 更新用户头像URL到数据库（安全版本，通过上下文验证用户）
            boolean updateSuccess = userProfileController.updateUserAvatar(avatarUrl);
            if (!updateSuccess) {
                log.warn("头像上传成功但更新数据库失败，avatarUrl：{}", avatarUrl);
                // 注意：这里不回滚MinIO中的文件，因为后续可以重试更新数据库
            }
            
            log.info("用户{}头像上传成功：{}", userId, avatarUrl);
            return AvatarUploadResult.success(avatarUrl);
            
        } catch (Exception e) {
            log.error("头像上传失败", e);
            return AvatarUploadResult.error("上传失败：" + e.getMessage());
        }
    }
    
    /**
     * 检查是否为图片文件
     */
    private boolean isImageFile(String contentType) {
        if (contentType == null) return false;
        return contentType.startsWith("image/") && 
               (contentType.equals("image/jpeg") || 
                contentType.equals("image/png") || 
                contentType.equals("image/gif") || 
                contentType.equals("image/webp"));
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null) return ".jpg";
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex) : ".jpg";
    }
    
    /**
     * 头像上传结果
     */
    public static class AvatarUploadResult {
        private boolean success;
        private String message;
        private String avatarUrl;
        
        public static AvatarUploadResult success(String avatarUrl) {
            AvatarUploadResult result = new AvatarUploadResult();
            result.success = true;
            result.message = "上传成功";
            result.avatarUrl = avatarUrl;
            return result;
        }
        
        public static AvatarUploadResult error(String message) {
            AvatarUploadResult result = new AvatarUploadResult();
            result.success = false;
            result.message = message;
            return result;
        }
        
        // getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    }
}
