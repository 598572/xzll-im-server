package service

import (
	"context"
	"fmt"
	"time"

	"im-connect-go/pkg/redis"

	"go.uber.org/zap"
)

// UserStatusService 用户状态管理服务（对标 Java UserStatusManagerService）
// 功能：
// 1. 用户上线后设置 Redis 状态和路由信息
// 2. 用户下线后清除 Redis 状态和路由信息
type UserStatusService struct {
	redisClient *redis.RedisClient
	logger      *zap.Logger
	serverAddr  string // 当前服务器地址（格式：ip:port）
}

// NewUserStatusService 创建用户状态管理服务
func NewUserStatusService(redisClient *redis.RedisClient, logger *zap.Logger, serverAddr string) *UserStatusService {
	return &UserStatusService{
		redisClient: redisClient,
		logger:      logger,
		serverAddr:  serverAddr,
	}
}

// UserConnectSuccessAfter 用户连接成功后设置状态（对标 Java userConnectSuccessAfter）
// 设置两个 Redis Hash：
// 1. userLogin:server: → uid: ip:port（路由信息）
// 2. userLogin:status: → uid: 5（在线状态，ON_LINE=5）
func (s *UserStatusService) UserConnectSuccessAfter(userID string) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	// Redis Key 常量（对标 Java ImConstant.RedisKeyConstant）
	routeKey := "userLogin:server:"  // ROUTE_PREFIX
	statusKey := "userLogin:status:" // LOGIN_STATUS_PREFIX

	// 1. 设置路由信息（用户所在服务器）
	if err := s.redisClient.HSet(ctx, routeKey, userID, s.serverAddr); err != nil {
		s.logger.Error("设置用户路由信息失败",
			zap.String("user_id", userID),
			zap.String("server_addr", s.serverAddr),
			zap.Error(err),
		)
		return fmt.Errorf("设置用户路由信息失败: %w", err)
	}

	// 2. 设置在线状态（ON_LINE = 5）
	if err := s.redisClient.HSet(ctx, statusKey, userID, "5"); err != nil {
		s.logger.Error("设置用户在线状态失败",
			zap.String("user_id", userID),
			zap.Error(err),
		)
		return fmt.Errorf("设置用户在线状态失败: %w", err)
	}

	s.logger.Info("✅ 用户上线状态已设置到 Redis",
		zap.String("user_id", userID),
		zap.String("status", "5"),
		zap.String("route", s.serverAddr),
	)

	return nil
}

// UserDisconnectAfter 用户断开连接后清除状态（对标 Java userDisconnectAfter）
// 清除两个 Redis Hash：
// 1. userLogin:server: → uid（路由信息）
// 2. userLogin:status: → uid（在线状态）
func (s *UserStatusService) UserDisconnectAfter(userID string) error {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	// Redis Key 常量（对标 Java ImConstant.RedisKeyConstant）
	routeKey := "userLogin:server:"  // ROUTE_PREFIX
	statusKey := "userLogin:status:" // LOGIN_STATUS_PREFIX

	// 1. 删除路由信息
	if err := s.redisClient.HDel(ctx, routeKey, userID); err != nil {
		s.logger.Warn("删除用户路由信息失败",
			zap.String("user_id", userID),
			zap.Error(err),
		)
		// 继续删除状态，不返回错误
	}

	// 2. 删除在线状态
	if err := s.redisClient.HDel(ctx, statusKey, userID); err != nil {
		s.logger.Warn("删除用户在线状态失败",
			zap.String("user_id", userID),
			zap.Error(err),
		)
		return fmt.Errorf("删除用户在线状态失败: %w", err)
	}

	s.logger.Info("✅ 用户下线状态已从 Redis 清除",
		zap.String("user_id", userID),
	)

	return nil
}

// GetUserStatus 获取用户在线状态（扩展功能）
func (s *UserStatusService) GetUserStatus(userID string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	statusKey := "userLogin:status:"
	status, err := s.redisClient.HGet(ctx, statusKey, userID)
	if err != nil {
		return "", err
	}

	return status, nil
}

// GetUserRoute 获取用户路由信息（扩展功能）
func (s *UserStatusService) GetUserRoute(userID string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	routeKey := "userLogin:server:"
	route, err := s.redisClient.HGet(ctx, routeKey, userID)
	if err != nil {
		return "", err
	}

	return route, nil
}
