package auth

import (
	"crypto/md5"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	"im-connect-go/internal/config"

	"github.com/golang-jwt/jwt/v4"
	"go.uber.org/zap"
)

// Handler 认证处理器（对标 Java AuthHandler）
// 功能：
// 1. WebSocket 连接认证
// 2. JWT Token 验证
// 3. 压测后门支持
// 4. IP 白名单控制
// 5. 防暴力破解保护
type Handler struct {
	config *config.Config
	logger *zap.Logger

	// JWT 相关
	jwtSecret []byte

	// 认证失败记录（简单版本，生产环境应使用 Redis）
	failedAttempts map[string]int // IP -> 失败次数

	// 压测模式配置
	stressTestEnabled bool
	stressTestToken   string
}

// Claims JWT 声明结构体
type Claims struct {
	UserID   string `json:"user_id"`
	Username string `json:"username"`
	jwt.RegisteredClaims
}

// NewHandler 创建认证处理器
func NewHandler(cfg *config.Config, logger *zap.Logger) *Handler {
	// 从配置读取 JWT 密钥
	jwtSecret := cfg.Auth.JWTSecret
	if jwtSecret == "" {
		jwtSecret = "your-secret-key" // 默认密钥（仅用于开发环境）
		logger.Warn("⚠️  未配置 JWT 密钥，使用默认值（不安全）")
	}

	handler := &Handler{
		config:            cfg,
		logger:            logger,
		jwtSecret:         []byte(jwtSecret),
		failedAttempts:    make(map[string]int),
		stressTestEnabled: cfg.Auth.StressTestEnabled,
		stressTestToken:   cfg.Auth.StressTestToken,
	}

	logger.Info("✅ 认证处理器初始化完成",
		zap.Bool("auth_enabled", cfg.Auth.Enabled),
		zap.Bool("stress_test_enabled", handler.stressTestEnabled),
		zap.String("stress_test_token", maskToken(handler.stressTestToken)),
	)

	return handler
}

// Authenticate WebSocket 连接认证（对标 Java AuthHandler.channelRead）
// 认证流程：
// 0. 检查是否启用认证
// 1. 检查压测后门（如果启用）
// 2. 提取 Token 和用户ID
// 3. 验证 JWT Token
// 4. 检查用户权限
// 5. 返回用户ID
func (h *Handler) Authenticate(r *http.Request) (string, error) {
	// 0. 检查是否启用认证（对标 Java auth.enabled）
	if !h.config.Auth.Enabled {
		// 认证未启用，直接从请求中提取用户ID（用于测试/开发环境）
		userID := r.Header.Get("uid")
		if userID == "" {
			userID = r.URL.Query().Get("userId")
		}
		if userID == "" {
			// 如果没有提供用户ID，生成一个测试用户ID
			userID = "test_user_" + fmt.Sprintf("%d", time.Now().Unix())
		}
		h.logger.Debug("⚠️  认证已禁用，跳过认证",
			zap.String("user_id", userID),
			zap.String("ip", h.getClientIP(r)),
		)
		return userID, nil
	}

	clientIP := h.getClientIP(r)

	// 1. 检查IP黑名单（防暴力破解）
	if h.isIPBlocked(clientIP) {
		h.logger.Warn("IP被阻止（认证失败过多）",
			zap.String("ip", clientIP),
		)
		return "", fmt.Errorf("IP被阻止")
	}

	// 2. 压测后门检查（对标 Java im.netty.auth.stress-test-enabled）
	if h.stressTestEnabled {
		if token := r.Header.Get("token"); token == h.stressTestToken {
			if userID := r.Header.Get("uid"); userID != "" {
				h.logger.Debug("✅ 压测后门认证通过",
					zap.String("user_id", userID),
					zap.String("ip", clientIP),
				)
				return userID, nil
			}
		}
	}

	// 3. 正常认证流程

	// 3.1 提取认证信息
	token := r.Header.Get("Authorization")
	if token == "" {
		token = r.URL.Query().Get("token") // 支持查询参数传递
	}
	if token == "" {
		h.recordFailedAttempt(clientIP)
		return "", fmt.Errorf("缺少认证token")
	}

	// 移除 "Bearer " 前缀
	if strings.HasPrefix(token, "Bearer ") {
		token = strings.TrimPrefix(token, "Bearer ")
	}

	userID := r.Header.Get("uid")
	if userID == "" {
		userID = r.URL.Query().Get("userId")
	}
	if userID == "" {
		h.recordFailedAttempt(clientIP)
		return "", fmt.Errorf("缺少用户ID")
	}

	// 3.2 验证 JWT Token
	claims, err := h.validateJWTToken(token)
	if err != nil {
		h.recordFailedAttempt(clientIP)
		h.logger.Warn("JWT Token 验证失败",
			zap.String("user_id", userID),
			zap.String("ip", clientIP),
			zap.Error(err),
		)
		return "", fmt.Errorf("token验证失败: %w", err)
	}

	// 3.3 验证用户ID匹配
	if claims.UserID != userID {
		h.recordFailedAttempt(clientIP)
		h.logger.Warn("用户ID不匹配",
			zap.String("token_user_id", claims.UserID),
			zap.String("header_user_id", userID),
			zap.String("ip", clientIP),
		)
		return "", fmt.Errorf("用户ID不匹配")
	}

	// 3.4 检查Token是否过期
	if claims.ExpiresAt != nil && claims.ExpiresAt.Time.Before(time.Now()) {
		h.recordFailedAttempt(clientIP)
		h.logger.Warn("Token已过期",
			zap.String("user_id", userID),
			zap.Time("expired_at", claims.ExpiresAt.Time),
			zap.String("ip", clientIP),
		)
		return "", fmt.Errorf("token已过期")
	}

	// 4. 认证成功
	h.clearFailedAttempts(clientIP)

	h.logger.Debug("✅ 认证成功",
		zap.String("user_id", userID),
		zap.String("username", claims.Username),
		zap.String("ip", clientIP),
	)

	return userID, nil
}

// validateJWTToken 验证 JWT Token
func (h *Handler) validateJWTToken(tokenString string) (*Claims, error) {
	// 解析 Token
	token, err := jwt.ParseWithClaims(tokenString, &Claims{}, func(token *jwt.Token) (interface{}, error) {
		// 验证签名方法
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
		}
		return h.jwtSecret, nil
	})

	if err != nil {
		return nil, fmt.Errorf("解析token失败: %w", err)
	}

	// 验证 Token 有效性
	if !token.Valid {
		return nil, fmt.Errorf("token无效")
	}

	// 提取 Claims
	claims, ok := token.Claims.(*Claims)
	if !ok {
		return nil, fmt.Errorf("无法解析claims")
	}

	return claims, nil
}

// GenerateJWTToken 生成 JWT Token（用于测试）
func (h *Handler) GenerateJWTToken(userID, username string, expiry time.Duration) (string, error) {
	now := time.Now()
	claims := &Claims{
		UserID:   userID,
		Username: username,
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(now.Add(expiry)),
			IssuedAt:  jwt.NewNumericDate(now),
			NotBefore: jwt.NewNumericDate(now),
			Issuer:    "im-connect-go",
			Subject:   userID,
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	tokenString, err := token.SignedString(h.jwtSecret)
	if err != nil {
		return "", fmt.Errorf("生成token失败: %w", err)
	}

	return tokenString, nil
}

// getClientIP 获取客户端真实IP（对标 Java 获取IP逻辑）
func (h *Handler) getClientIP(r *http.Request) string {
	// 1. 检查 X-Forwarded-For 头（代理环境）
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		ips := strings.Split(xff, ",")
		if len(ips) > 0 {
			return strings.TrimSpace(ips[0])
		}
	}

	// 2. 检查 X-Real-IP 头（Nginx 代理）
	if xri := r.Header.Get("X-Real-IP"); xri != "" {
		return strings.TrimSpace(xri)
	}

	// 3. 使用 RemoteAddr（直连）
	ip := r.RemoteAddr
	if colon := strings.LastIndex(ip, ":"); colon != -1 {
		ip = ip[:colon]
	}

	return ip
}

// isIPBlocked 检查IP是否被阻止
func (h *Handler) isIPBlocked(ip string) bool {
	// 简单的失败次数检查（生产环境应该使用 Redis + 滑动窗口）
	if attempts, exists := h.failedAttempts[ip]; exists {
		return attempts >= 10 // 10次失败后阻止
	}
	return false
}

// recordFailedAttempt 记录认证失败
func (h *Handler) recordFailedAttempt(ip string) {
	h.failedAttempts[ip]++

	attempts := h.failedAttempts[ip]
	h.logger.Warn("认证失败",
		zap.String("ip", ip),
		zap.Int("attempts", attempts),
	)

	// 清理过期记录（简单实现）
	if len(h.failedAttempts) > 1000 {
		// 清理一半的记录
		for k := range h.failedAttempts {
			delete(h.failedAttempts, k)
			if len(h.failedAttempts) <= 500 {
				break
			}
		}
	}
}

// clearFailedAttempts 清除认证失败记录
func (h *Handler) clearFailedAttempts(ip string) {
	delete(h.failedAttempts, ip)
}

// ValidateStressTestCredentials 验证压测凭证（对标 Java 压测后门）
func (h *Handler) ValidateStressTestCredentials(token, userID string) bool {
	if !h.stressTestEnabled {
		return false
	}

	if token != h.stressTestToken {
		return false
	}

	if userID == "" {
		return false
	}

	// 压测用户ID应该符合特定格式（可选验证）
	if !h.isValidStressTestUserID(userID) {
		h.logger.Warn("无效的压测用户ID格式",
			zap.String("user_id", userID),
		)
		return false
	}

	return true
}

// isValidStressTestUserID 验证压测用户ID格式
func (h *Handler) isValidStressTestUserID(userID string) bool {
	// 压测用户ID应该是纯数字（对标 Gatling 生成的 Snowflake ID）
	if _, err := strconv.ParseInt(userID, 10, 64); err != nil {
		return false
	}

	return true
}

// GetAuthStats 获取认证统计信息
func (h *Handler) GetAuthStats() AuthStats {
	blockedIPs := 0
	for _, attempts := range h.failedAttempts {
		if attempts >= 10 {
			blockedIPs++
		}
	}

	return AuthStats{
		TotalFailedAttempts: len(h.failedAttempts),
		BlockedIPs:          blockedIPs,
		StressTestEnabled:   h.stressTestEnabled,
	}
}

// AuthStats 认证统计信息
type AuthStats struct {
	TotalFailedAttempts int  `json:"total_failed_attempts"`
	BlockedIPs          int  `json:"blocked_ips"`
	StressTestEnabled   bool `json:"stress_test_enabled"`
}

// maskToken 掩码 Token（用于日志）
func maskToken(token string) string {
	if len(token) <= 8 {
		return "***"
	}
	return token[:4] + "***" + token[len(token)-4:]
}

// CreateStressTestToken 创建压测专用Token（用于测试）
func (h *Handler) CreateStressTestToken(userID string) string {
	// 简单的压测Token生成（基于用户ID + 时间戳 + 密钥的MD5）
	data := fmt.Sprintf("%s_%d_%s", userID, time.Now().Unix(), h.stressTestToken)
	hash := md5.Sum([]byte(data))
	return fmt.Sprintf("%x", hash)
}

// ValidateUserPermissions 验证用户权限（扩展方法）
func (h *Handler) ValidateUserPermissions(userID string, resource string) bool {
	// 这里可以添加基于 Redis 的权限检查逻辑
	// 对标 Java 版本的权限验证

	// 暂时简单实现：允许所有经过认证的用户
	return true
}

// RefreshToken 刷新Token（扩展方法）
func (h *Handler) RefreshToken(oldToken string) (string, error) {
	// 验证旧Token
	claims, err := h.validateJWTToken(oldToken)
	if err != nil {
		return "", fmt.Errorf("旧token验证失败: %w", err)
	}

	// 检查是否可以刷新（例如，过期时间在1小时内）
	if claims.ExpiresAt != nil {
		timeDiff := time.Until(claims.ExpiresAt.Time)
		if timeDiff > time.Hour {
			return "", fmt.Errorf("token未到刷新时间")
		}
	}

	// 生成新Token
	return h.GenerateJWTToken(claims.UserID, claims.Username, h.config.Auth.TokenExpiry)
}

// IsTokenExpiringSoon 检查Token是否即将过期
func (h *Handler) IsTokenExpiringSoon(tokenString string, threshold time.Duration) bool {
	claims, err := h.validateJWTToken(tokenString)
	if err != nil {
		return true // 无效token视为已过期
	}

	if claims.ExpiresAt == nil {
		return false // 无过期时间
	}

	return time.Until(claims.ExpiresAt.Time) <= threshold
}

// 中间件相关方法（用于 HTTP 路由保护）

// HTTPAuthMiddleware HTTP 认证中间件
func (h *Handler) HTTPAuthMiddleware(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		// 从 Header 或 Query 提取 Token
		token := r.Header.Get("Authorization")
		if token == "" {
			token = r.URL.Query().Get("token")
		}

		if token == "" {
			http.Error(w, "缺少认证token", http.StatusUnauthorized)
			return
		}

		// 移除 Bearer 前缀
		if strings.HasPrefix(token, "Bearer ") {
			token = strings.TrimPrefix(token, "Bearer ")
		}

		// 验证 Token
		claims, err := h.validateJWTToken(token)
		if err != nil {
			http.Error(w, fmt.Sprintf("token验证失败: %v", err), http.StatusUnauthorized)
			return
		}

		// 将用户信息添加到请求上下文（可选）
		// context.WithValue 可以存储用户信息

		h.logger.Debug("HTTP认证成功",
			zap.String("user_id", claims.UserID),
			zap.String("path", r.URL.Path),
		)

		next.ServeHTTP(w, r)
	}
}
