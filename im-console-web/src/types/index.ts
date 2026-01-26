// 通用响应类型
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

// 分页参数
export interface PageParams {
  pageNum: number
  pageSize: number
}

// 分页结果
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

// 用户类型
export interface User {
  id: number
  odlId: string
  odlName: string
  odlIconUrl: string
  userId: string
  userName: string
  userFullName: string
  sex: number
  sexDesc: string
  phone: string
  registerTerminalType: number
  terminalTypeDesc: string
  status: number
  statusDesc: string
  createTime: string
  updateTime: string
  online: boolean
  friendCount: number
}

// 用户查询参数
export interface UserQueryParams extends PageParams {
  keyword?: string
  userId?: string
  sex?: number
  terminalType?: number
}

// 好友关系类型
export interface FriendRelation {
  id: number
  userId: string
  userName: string
  friendId: string
  friendName: string
  blackFlag: number
  blackFlagDesc: string
  delFlag: number
  createTime: string
  updateTime: string
}

// 好友查询参数
export interface FriendQueryParams extends PageParams {
  keyword?: string
  userId?: string
  friendId?: string
  blackFlag?: number
}

// 敏感词类型
export interface SensitiveWord {
  id: number
  word: string
  wordType: number
  category: string // 前端显示用，实际是wordType映射
  status: number
  createTime: string
  updateTime: string
}

// 敏感词查询参数
export interface SensitiveWordQueryParams extends PageParams {
  keyword?: string
  wordType?: number
  status?: number
}

// wordType映射表
export const WORD_TYPE_MAP: Record<number, string> = {
  1: '政治敏感',
  2: '色情低俗',
  3: '暴力血腥',
  4: '广告营销',
  5: '其他'
}

// 仪表盘数据
export interface DashboardStats {
  totalUsers: number
  todayNewUsers: number
  onlineUsers: number
  todayMessages: number
  totalMessages: number
  totalFriendRelations: number
  messageTps: number
  usersByTerminal: Record<string, number>
  messagesTrend: Record<string, number>
  usersTrend: Record<string, number>
}

// 菜单项
export interface MenuItem {
  path: string
  title: string
  icon?: string
  children?: MenuItem[]
  hidden?: boolean
}
