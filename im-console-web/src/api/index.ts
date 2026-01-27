import request from '../utils/request'

// ==================== 登录认证 ====================
export const login = (data: { username: string; password: string }) => {
  return request.post('/admin/auth/login', data)
}

export const logout = () => {
  return request.post('/admin/auth/logout')
}

export const getAdminInfo = () => {
  return request.get('/admin/auth/info')
}

// ==================== 仪表盘 ====================
export const getDashboardStats = () => {
  return request.get('/admin/dashboard/stats')
}

export const getOnlineUserCount = () => {
  return request.get('/admin/dashboard/online-count')
}

export const getTodayMessageCount = () => {
  return request.get('/admin/dashboard/today-messages')
}

export const getMessageTps = () => {
  return request.get('/admin/dashboard/message-tps')
}

// ==================== 用户管理 ====================
export const pageUsers = (data: any) => {
  return request.post('/admin/user/page', data)
}

export const getUserDetail = (userId: string) => {
  return request.get(`/admin/user/detail/${userId}`)
}

export const searchUsers = (keyword: string) => {
  return request.get('/admin/user/search', { params: { keyword } })
}

export const kickUser = (userId: string) => {
  return request.post(`/admin/user/kick/${userId}`)
}

export const disableUser = (userId: string, reason: string) => {
  return request.post(`/admin/user/disable/${userId}`, null, { params: { reason } })
}

export const enableUser = (userId: string) => {
  return request.post(`/admin/user/enable/${userId}`)
}

// ==================== 好友管理 ====================
export const pageFriendRelations = (data: any) => {
  return request.post('/admin/friend/page', data)
}

export const getUserFriends = (userId: string) => {
  return request.get(`/admin/friend/list/${userId}`)
}

export const checkFriendship = (userId: string, friendId: string) => {
  return request.get('/admin/friend/check', { params: { userId, friendId } })
}

// ==================== 敏感词管理 ====================
export const pageSensitiveWords = (params: any) => {
  return request.get('/admin/sensitive-word/page', { params })
}

export const addSensitiveWord = (data: any) => {
  return request.post('/admin/sensitive-word/add', data)
}

export const updateSensitiveWord = (data: any) => {
  return request.put('/admin/sensitive-word/update', data)
}

export const deleteSensitiveWord = (id: number) => {
  return request.delete(`/admin/sensitive-word/delete/${id}`)
}

export const toggleSensitiveWord = (id: number, status: number) => {
  return request.post(`/admin/sensitive-word/toggle/${id}`, null, { params: { status } })
}

export const batchAddSensitiveWords = (words: string[], wordType?: number) => {
  return request.post('/admin/sensitive-word/batch-add', { words, wordType })
}

export const checkSensitiveContent = (content: string) => {
  return request.post('/admin/sensitive-word/check', null, { params: { content } })
}

export const filterSensitiveContent = (content: string) => {
  return request.post('/admin/sensitive-word/filter', null, { params: { content } })
}

// ==================== 消息管理 ====================
// 分页查询聊天历史消息
export const pageMessageHistory = (params: { limit?: number; lastRowKey?: string }) => {
  return request.get('/c2c/message/history/page', { params })
}

// ==================== 会话管理 ====================
// 分页查询会话列表（来源：MySQL im_chat表）
export const pageSessionList = (params: { current?: number; size?: number; userId?: string; chatType?: number }) => {
  return request.get('/admin/session/page', { params })
}

// 获取会话统计信息
export const getSessionStats = () => {
  return request.get('/admin/session/stats')
}

// 获取会话详情
export const getSessionDetail = (chatId: string) => {
  return request.get(`/admin/session/${chatId}`)
}

// 获取最新消息
export const getLatestMessages = (limit: number = 20) => {
  return request.get('/c2c/message/history/latest', { params: { limit } })
}

// 根据会话ID查询消息
export const getMessagesByChatId = (chatId: string, limit: number = 50) => {
  return request.get(`/c2c/message/history/chat/${chatId}`, { params: { limit } })
}

// 条件搜索消息
export const searchMessages = (params: {
  fromUserId?: string
  toUserId?: string
  chatId?: string
  content?: string
  msgStatus?: number
  msgFormat?: number
  withdrawFlag?: number
  startTime?: number
  endTime?: number
}) => {
  return request.get('/c2c/message/history/search', { params })
}

// ==================== 封禁管理 ====================
// 分页查询封禁列表
export const pageBanList = (params: { current?: number; size?: number; userId?: string }) => {
  return request.get('/admin/ban/page', { params })
}

// 封禁用户
export const banUser = (userId: string, banReason: string, banDays?: number) => {
  return request.post(`/admin/ban/user/${userId}`, null, { 
    params: { banReason, banDays },
    headers: { 'X-Admin-Id': 'admin' }
  })
}

// 解封用户
export const unbanUser = (id: number, unbanReason?: string) => {
  return request.post(`/admin/ban/unban/${id}`, null, { 
    params: { unbanReason },
    headers: { 'X-Admin-Id': 'admin' }
  })
}

// ==================== 举报处理 ====================
// 分页查询举报列表
export const pageReportList = (params: { current?: number; size?: number; status?: number }) => {
  return request.get('/admin/report/page', { params })
}

// 处理举报
export const handleReport = (id: number, handleResult: number, result?: string) => {
  return request.post(`/admin/report/${id}/handle`, null, { 
    params: { handleResult, result },
    headers: { 'X-Admin-Id': 'admin' }
  })
}

// ==================== 系统公告 ====================
// 分页查询公告列表
export const pageNoticeList = (params: { current?: number; size?: number; status?: number }) => {
  return request.get('/admin/notice/page', { params })
}

// 获取公告详情
export const getNoticeDetail = (id: number) => {
  return request.get(`/admin/notice/${id}`)
}

// 创建公告
export const createNotice = (data: { title: string; content: string; type?: number }) => {
  return request.post('/admin/notice/create', data, {
    headers: { 'X-Admin-Id': 'admin' }
  })
}

// 更新公告
export const updateNotice = (id: number, data: { title?: string; content?: string; status?: number }) => {
  return request.put(`/admin/notice/${id}`, data, {
    headers: { 'X-Admin-Id': 'admin' }
  })
}

// 删除公告
export const deleteNotice = (id: number) => {
  return request.delete(`/admin/notice/${id}`, {
    headers: { 'X-Admin-Id': 'admin' }
  })
}

// 发布公告
export const publishNotice = (id: number) => {
  return request.post(`/admin/notice/${id}/publish`, null, {
    headers: { 'X-Admin-Id': 'admin' }
  })
}

// ==================== 操作日志 ====================
// 分页查询操作日志
export const pageOperationLogs = (params: { current?: number; size?: number; adminId?: string; operationType?: string }) => {
  return request.get('/admin/operation-logs/page', { params })
}

// ==================== AI管理 ====================
// AI对话
export const aiChat = (userId: string, message: string) => {
  return request.post('/admin/ai/chat', null, { params: { userId, message } })
}

// 分页查询知识库
export const pageAiKnowledge = (params: { current?: number; size?: number; category?: string }) => {
  return request.get('/admin/ai/knowledge/page', { params })
}

// 添加知识条目
export const addAiKnowledge = (data: { question: string; answer: string; category?: string }) => {
  return request.post('/admin/ai/knowledge/add', data)
}

// 更新知识条目
export const updateAiKnowledge = (id: number, data: { question?: string; answer?: string; category?: string }) => {
  return request.put(`/admin/ai/knowledge/${id}`, data)
}

// 删除知识条目
export const deleteAiKnowledge = (id: number) => {
  return request.delete(`/admin/ai/knowledge/${id}`)
}

// 获取AI配置列表
export const getAiConfigList = () => {
  return request.get('/admin/ai/config/list')
}

// 更新AI配置
export const updateAiConfig = (id: number, data: { configValue: string }) => {
  return request.put(`/admin/ai/config/${id}`, data)
}
