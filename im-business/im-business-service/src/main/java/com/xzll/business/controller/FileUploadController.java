package com.xzll.business.controller;

import com.xzll.business.service.UserProfileService;
import com.xzll.common.constant.enums.FileBusinessType;
import com.xzll.common.controller.BaseController;
import com.xzll.common.pojo.request.UpdateUserProfileAO;
import com.xzll.common.pojo.response.UserProfileVO;
import com.xzll.common.pojo.base.WebBaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import com.xzll.business.config.MinioConfig;

import javax.annotation.Resource;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import java.io.InputStream;
import org.springframework.util.StreamUtils;
import io.minio.GetObjectArgs;

/**
 * 文件上传控制器
 * 处理头像等文件上传到MinIO
 */
@RestController
@RequestMapping("/api/file")
@Slf4j
public class FileUploadController extends BaseController {

    @Resource
    private MinioClient minioClient;

    @Resource
    private MinioConfig minioConfig;

    @Resource
    private UserProfileService userProfileService;

    // 文件上传配置
    @Value("${minio.file.upload.base-url:http://localhost:8080/im-business/api/file/}")
    private String fileBaseUrl;

    @Value("${minio.file.upload.avatar.max-size:5242880}")  // 5MB = 5 * 1024 * 1024
    private long avatarMaxSize;

    @Value("${minio.file.upload.chat.max-size:20971520}")   // 20MB = 20 * 1024 * 1024
    private long chatFileMaxSize;

    /**
     * 上传头像（安全版本，自动从Token获取当前用户ID）
     */
    @PostMapping("/uploadAvatar")
    public WebBaseResponse<FileUploadResult> uploadAvatar(
            @RequestParam("file") MultipartFile file) {

        try {
            // 1. 从上下文获取当前登录用户ID（安全校验）
            String userId = getCurrentUserIdWithValidation();
            if (userId == null) {
                return WebBaseResponse.returnResultError("用户未登录或token无效");
            }
            
            log.info("用户{}上传头像，文件大小：{}", userId, file.getSize());

            // 2. 参数校验
            if (file.isEmpty()) {
                return WebBaseResponse.returnResultError("文件不能为空");
            }

            // 2. 文件类型校验
            String contentType = file.getContentType();
            log.info("文件上传调试 - 原始文件名: {}, ContentType: {}, 文件大小: {}", 
                file.getOriginalFilename(), contentType, file.getSize());
            
            if (!isImageFile(contentType)) {
                log.error("图片格式验证失败 - ContentType: {}, OriginalFilename: {}", 
                    contentType, file.getOriginalFilename());
                return WebBaseResponse.returnResultError("只支持图片格式文件");
            }

            // 3. 文件大小校验
            if (file.getSize() > avatarMaxSize) {
                return WebBaseResponse.returnResultError("文件大小不能超过" + (avatarMaxSize / 1024 / 1024) + "MB");
            }

            // 4. 生成文件名（按用户ID和业务类型划分）
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            String cleanUserId = userId.replaceAll("[^a-zA-Z0-9]", "_"); // 替换特殊字符为下划线
            String fileName = generateFilePath(cleanUserId, FileBusinessType.AVATAR, fileExtension);

            // 5. 上传到MinIO
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(fileName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(contentType)
                    .build()
            );

            // 6. 存储文件路径到数据库（不是完整URL，避免预签名URL过期问题）
            String storagePath = fileName;

            // 7. 更新用户头像路径到数据库（使用统一更新接口）
            UpdateUserProfileAO updateAO = new UpdateUserProfileAO();
            updateAO.setHeadImage(storagePath);
            UserProfileVO result = userProfileService.updateUserProfile(userId, updateAO);
            boolean updateSuccess = (result != null);

            // 8. 生成永久访问URL（短链接）
            String shortCode = generateShortCode(fileName);
            String avatarUrl = fileBaseUrl + "s/" + shortCode;
            log.info("URL生成调试 - fileBaseUrl: {}, shortCode: {}, 完整URL: {}", fileBaseUrl, shortCode, avatarUrl);
            if (!updateSuccess) {
                log.warn("头像上传成功但更新数据库失败，avatarUrl：{}", avatarUrl);
                // 注意：这里不回滚MinIO中的文件，因为后续可以重试更新数据库
            }

            log.info("用户{}头像上传成功：{}", userId, avatarUrl);
            return WebBaseResponse.returnResultSuccess("头像上传成功", new FileUploadResult(avatarUrl));

        } catch (Exception e) {
            log.error("头像上传失败", e);
            return WebBaseResponse.returnResultError("上传失败：" + e.getMessage());
        }
    }

    /**
     * 短链接文件访问接口
     */
    @GetMapping("/s/{shortCode}")
    @CrossOrigin
    public ResponseEntity<byte[]> getFileByShortCode(@PathVariable String shortCode) {
        try {
            // 从短码解码文件路径
            String filePath = new String(java.util.Base64.getDecoder().decode(shortCode));
            log.info("短链接访问文件，短码：{}，文件路径：{}", shortCode, filePath);
            
            return getFileContent(filePath);
        } catch (Exception e) {
            log.error("短链接访问失败，shortCode：{}", shortCode, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 永久图片访问代理接口（兼容旧接口）
     */
    @GetMapping("/image/{encodedPath}")
    @CrossOrigin
    public ResponseEntity<byte[]> getImage(@PathVariable String encodedPath) {
        try {
            // 解码文件路径
            String filePath = new String(java.util.Base64.getDecoder().decode(encodedPath));
            log.info("访问图片，文件路径：{}", filePath);

            // 从MinIO获取文件流
            InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(filePath)
                    .build()
            );

            // 读取文件内容
            byte[] imageBytes = StreamUtils.copyToByteArray(inputStream);
            inputStream.close();

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();

            // 根据文件扩展名设置Content-Type
            String contentType = getContentTypeByExtension(filePath);
            headers.set("Content-Type", contentType);
            headers.set("Cache-Control", "public, max-age=86400"); // 缓存1天
            headers.set("ETag", "\"" + filePath.hashCode() + "\""); // 添加ETag便于缓存
            headers.set("Access-Control-Allow-Origin", "*"); // 允许跨域访问

            log.info("图片访问成功，文件路径：{}，大小：{} bytes", filePath, imageBytes.length);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(imageBytes);

        } catch (Exception e) {
            log.error("图片访问失败，encodedPath：{}", encodedPath, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 根据文件扩展名获取Content-Type
     */
    private String getContentTypeByExtension(String filePath) {
        String extension = getFileExtension(filePath).toLowerCase();
        switch (extension) {
            case ".jpg":
            case ".jpeg":
                return "image/jpeg";
            case ".png":
                return "image/png";
            case ".gif":
                return "image/gif";
            case ".webp":
                return "image/webp";
            case ".bmp":
                return "image/bmp";
            case ".svg":
                return "image/svg+xml";
            default:
                return "image/jpeg"; // 默认
        }
    }

    /**
     * 上传聊天文件（单聊/群聊通用，安全版本，自动从Token获取当前用户ID）
     */
    @PostMapping("/uploadChatFile")
    public WebBaseResponse<FileUploadResult> uploadChatFile(
            @RequestParam("file") MultipartFile file) {

        try {
            // 1. 从上下文获取当前登录用户ID（安全校验）
            String userId = getCurrentUserIdWithValidation();
            if (userId == null) {
                return WebBaseResponse.returnResultError("用户未登录或token无效");
            }
            
            log.info("用户{}上传聊天文件，文件大小：{}", userId, file.getSize());

            // 2. 参数校验
            if (file.isEmpty()) {
                return WebBaseResponse.returnResultError("文件不能为空");
            }

            // 3. 文件大小校验
            if (file.getSize() > chatFileMaxSize) {
                return WebBaseResponse.returnResultError("文件大小不能超过" + (chatFileMaxSize / 1024 / 1024) + "MB");
            }

            // 2. 生成文件名（统一存储到chat目录）
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            String cleanUserId = userId.replaceAll("[^a-zA-Z0-9]", "_");
            String fileName = generateChatFilePath(cleanUserId, fileExtension);

            // 5. 上传到MinIO
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(fileName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build()
            );

            // 6. 生成永久访问URL（短链接）
            String shortCode = generateShortCode(fileName);
            String fileUrl = fileBaseUrl + "s/" + shortCode;
            log.info("聊天文件URL生成调试 - fileBaseUrl: {}, shortCode: {}, 完整URL: {}", fileBaseUrl, shortCode, fileUrl);

            log.info("用户{}聊天文件上传成功：{}", userId, fileUrl);
            return WebBaseResponse.returnResultSuccess("文件上传成功", new FileUploadResult(fileUrl));

        } catch (Exception e) {
            log.error("聊天文件上传失败", e);
            return WebBaseResponse.returnResultError("上传失败：" + e.getMessage());
        }
    }

    /**
     * 生成文件存储路径
     */
    private String generateFilePath(String cleanUserId, FileBusinessType fileBusinessType, String fileExtension) {
        return cleanUserId + "/" + fileBusinessType.getPath() + "/" + UUID.randomUUID() + fileExtension;
    }

    /**
     * 生成聊天文件存储路径（统一存储到chat目录）
     */
    private String generateChatFilePath(String cleanUserId, String fileExtension) {
        return cleanUserId + "/chat/" + UUID.randomUUID() + fileExtension;
    }

    /**
     * 检查是否为图片文件
     */
    private boolean isImageFile(String contentType) {
        if (contentType == null) return false;

        // 支持的图片格式列表
        String[] supportedFormats = {
            "image/jpeg", "image/jpg", "image/png", "image/gif",
            "image/webp", "image/bmp", "image/tiff", "image/tif",
            "image/svg+xml", "image/ico", "image/heic", "image/heif",
            "image/x-icon", "image/vnd.microsoft.icon"
        };

        // 先检查是否以image/开头
        if (!contentType.toLowerCase().startsWith("image/")) {
            return false;
        }

        // 检查具体格式
        String lowerContentType = contentType.toLowerCase();
        for (String format : supportedFormats) {
            if (lowerContentType.equals(format)) {
                return true;
            }
        }

        // 如果是image/开头但不在支持列表中，也允许（较为宽松的策略）
        log.warn("未明确支持的图片格式，但允许上传：{}", contentType);
        return true;
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
     * 生成短码（实际就是Base64编码，但URL中看起来像短码）
     */
    private String generateShortCode(String filePath) {
        return java.util.Base64.getEncoder().encodeToString(filePath.getBytes());
    }
    
    /**
     * 获取文件内容的通用方法
     */
    private ResponseEntity<byte[]> getFileContent(String filePath) {
        try {
            // 从MinIO获取文件流
            InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(filePath)
                    .build()
            );
            
            // 读取文件内容
            byte[] fileBytes = StreamUtils.copyToByteArray(inputStream);
            inputStream.close();
            
            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            String contentType = getContentTypeByExtension(filePath);
            headers.set("Content-Type", contentType);
            headers.set("Cache-Control", "public, max-age=86400"); // 缓存1天
            headers.set("ETag", "\"" + filePath.hashCode() + "\"");
            headers.set("Access-Control-Allow-Origin", "*");
            
            log.info("文件访问成功，文件路径：{}，大小：{} bytes", filePath, fileBytes.length);
            return ResponseEntity.ok().headers(headers).body(fileBytes);
            
        } catch (Exception e) {
            log.error("文件访问失败，filePath：{}", filePath, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 文件上传结果
     */
    public static class FileUploadResult {
        private String url;

        public FileUploadResult() {}

        public FileUploadResult(String url) {
            this.url = url;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    /**
     * 头像上传结果（已废弃，保留兼容性）
     */
    @Deprecated
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
