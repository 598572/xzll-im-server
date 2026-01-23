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
  category: string
  level: number
  levelDesc: string
  status: number
  statusDesc: string
  createTime: string
  updateTime: string
}

// 敏感词查询参数
export interface SensitiveWordQueryParams extends PageParams {
  keyword?: string
  category?: string
  level?: number
  status?: number
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
