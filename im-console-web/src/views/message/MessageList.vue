<template>
  <div class="message-list">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="消息内容">
          <el-input
            v-model="queryParams.content"
            placeholder="请输入消息内容关键字"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="发送方ID">
          <el-input
            v-model="queryParams.fromUserId"
            placeholder="请输入发送方ID"
            clearable
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="接收方ID">
          <el-input
            v-model="queryParams.toUserId"
            placeholder="请输入接收方ID"
            clearable
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="会话ID">
          <el-input
            v-model="queryParams.chatId"
            placeholder="请输入会话ID"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleSearch">搜索</el-button>
          <el-button icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="success" icon="Download" @click="loadLatestMessages">最新消息</el-button>
          <el-button
            :icon="showAdvancedSearch ? 'ArrowUp' : 'ArrowDown'"
            @click="showAdvancedSearch = !showAdvancedSearch"
          >
            {{ showAdvancedSearch ? '收起' : '高级搜索' }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 高级搜索折叠面板 -->
      <el-collapse-transition>
        <div v-show="showAdvancedSearch" class="advanced-search">
          <el-divider style="margin: 12px 0" />
          <el-form :model="queryParams" inline>
            <el-form-item label="消息状态">
              <el-select v-model="queryParams.msgStatus" placeholder="全部" clearable style="width: 140px">
                <el-option label="待发送" :value="0" />
                <el-option label="已发送" :value="1" />
                <el-option label="已送达" :value="2" />
                <el-option label="已读" :value="3" />
              </el-select>
            </el-form-item>

            <el-form-item label="消息格式">
              <el-select v-model="queryParams.msgFormat" placeholder="全部" clearable style="width: 140px">
                <el-option label="文本" :value="1" />
                <el-option label="图片" :value="2" />
                <el-option label="语音" :value="3" />
                <el-option label="视频" :value="4" />
                <el-option label="文件" :value="5" />
                <el-option label="位置" :value="6" />
              </el-select>
            </el-form-item>

            <el-form-item label="撤回状态">
              <el-select v-model="queryParams.withdrawFlag" placeholder="全部" clearable style="width: 120px">
                <el-option label="正常" :value="0" />
                <el-option label="已撤回" :value="1" />
              </el-select>
            </el-form-item>

            <el-form-item label="时间范围">
              <el-date-picker
                v-model="dateRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 360px"
              />
            </el-form-item>
          </el-form>
        </div>
      </el-collapse-transition>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>聊天消息记录</span>
          <el-tag type="info">共 {{ tableData.length }} 条</el-tag>
        </div>
      </template>
      
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        border
        style="width: 100%"
        max-height="600"
      >
        <el-table-column prop="msgId" label="消息ID" width="200" show-overflow-tooltip />
        <el-table-column prop="chatId" label="会话ID" width="180" show-overflow-tooltip />
        <el-table-column prop="fromUserId" label="发送方" width="120" />
        <el-table-column prop="toUserId" label="接收方" width="120" />
        <el-table-column prop="msgContent" label="消息内容" min-width="250" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="msg-content">
              <el-tag v-if="row.msgFormat === 1" type="info" size="small">文本</el-tag>
              <el-tag v-else-if="row.msgFormat === 2" type="warning" size="small">图片</el-tag>
              <el-tag v-else-if="row.msgFormat === 3" type="success" size="small">语音</el-tag>
              <el-tag v-else type="info" size="small">其他</el-tag>
              <span class="content-text" v-html="highlightContent(row.msgContent)"></span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="消息状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getMsgStatusType(row.msgStatus)" size="small">
              {{ getMsgStatusText(row.msgStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="撤回状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.withdrawFlag === 1 ? 'danger' : 'success'" size="small">
              {{ row.withdrawFlag === 1 ? '已撤回' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发送时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.msgCreateTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleViewDetail(row)">
              详情
            </el-button>
            <el-button type="warning" link size="small" @click="handleViewChat(row)">
              查看会话
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 加载更多 -->
      <div class="load-more" v-if="hasMore">
        <el-button type="primary" text :loading="loading" @click="loadMore">
          加载更多
        </el-button>
      </div>
    </el-card>

    <!-- 消息详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="消息详情" width="600px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="消息ID" :span="2">{{ currentMessage?.msgId }}</el-descriptions-item>
        <el-descriptions-item label="会话ID" :span="2">{{ currentMessage?.chatId }}</el-descriptions-item>
        <el-descriptions-item label="发送方">{{ currentMessage?.fromUserId }}</el-descriptions-item>
        <el-descriptions-item label="接收方">{{ currentMessage?.toUserId }}</el-descriptions-item>
        <el-descriptions-item label="消息格式">
          <el-tag>{{ getMsgFormatText(currentMessage?.msgFormat) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="消息状态">
          <el-tag :type="getMsgStatusType(currentMessage?.msgStatus)">
            {{ getMsgStatusText(currentMessage?.msgStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发送时间" :span="2">
          {{ formatTime(currentMessage?.msgCreateTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="消息内容" :span="2">
          <div class="detail-content" v-html="highlightContent(currentMessage?.msgContent)"></div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onActivated } from 'vue'
import { ElMessage } from 'element-plus'
import { getLatestMessages, searchMessages, getMessagesByChatId } from '../../api'

interface Message {
  msgId: string
  chatId: string
  fromUserId: string
  toUserId: string
  msgContent: string
  msgFormat: number
  msgStatus: number
  msgCreateTime: number
  createTime: number
  updateTime: number
}

const loading = ref(false)
const tableData = ref<Message[]>([])
const hasMore = ref(false)
const lastRowKey = ref('')

// 显示高级搜索
const showAdvancedSearch = ref(false)

// 搜索表单
const queryParams = reactive({
  content: '',
  fromUserId: '',
  toUserId: '',
  chatId: '',
  msgStatus: undefined as number | undefined,
  msgFormat: undefined as number | undefined,
  withdrawFlag: undefined as number | undefined,
  startTime: undefined as number | undefined,
  endTime: undefined as number | undefined
})

// 日期范围
const dateRange = ref<[string, string]>([])

const detailDialogVisible = ref(false)
const currentMessage = ref<Message | null>(null)

// 格式化时间
const formatTime = (timestamp: number | undefined) => {
  if (!timestamp) return '-'
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 获取消息格式文本
const getMsgFormatText = (format: number | undefined) => {
  const formatMap: Record<number, string> = {
    1: '文本消息',
    2: '图片消息',
    3: '语音消息',
    4: '视频消息',
    5: '文件消息'
  }
  return formatMap[format || 0] || '未知'
}

// 获取消息状态文本
const getMsgStatusText = (status: number | undefined) => {
  const statusMap: Record<number, string> = {
    1: '发送中',
    2: '已发送',
    3: '未读',
    4: '已读',
    5: '已撤回'
  }
  return statusMap[status || 0] || '未知'
}

// 获取消息状态标签类型
const getMsgStatusType = (status: number | undefined) => {
  const typeMap: Record<number, string> = {
    1: 'warning',
    2: 'info',
    3: 'primary',
    4: 'success',
    5: 'danger'
  }
  return typeMap[status || 0] || 'info'
}

// 高亮消息内容中的搜索关键字
const highlightContent = (content: string | undefined) => {
  if (!content) return ''
  if (!queryParams.content) return content

  // 转义特殊字符
  const escapedKeyword = queryParams.content.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

  // 使用正则替换高亮显示
  const regex = new RegExp(`(${escapedKeyword})`, 'gi')
  return content.replace(regex, '<mark style="background-color: #ffeb3b; padding: 0 2px; border-radius: 2px;">$1</mark>')
}

// 加载最新消息
const loadLatestMessages = async () => {
  loading.value = true
  try {
    // 返回格式: { code: 1, msg: "...", data: { data: [...], count: ..., dataSource: "ES" } }
    const res = await getLatestMessages(50)
    // 拦截器已检查 code=1，直接获取内层数据
    const resultVO = res.data
    if (resultVO && resultVO.data && resultVO.data.length > 0) {
      tableData.value = resultVO.data
      hasMore.value = resultVO.hasMore || false
      lastRowKey.value = resultVO.nextRowKey || ''
      ElMessage.success(`加载了 ${tableData.value.length} 条最新消息`)
    } else {
      tableData.value = []
      ElMessage.warning('暂无消息数据')
    }
  } catch (error) {
    console.error('加载消息失败:', error)
    tableData.value = []
    ElMessage.error('加载消息失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = async () => {
  // 至少需要一个搜索条件
  if (!queryParams.content &&
      !queryParams.fromUserId &&
      !queryParams.toUserId &&
      !queryParams.chatId) {
    ElMessage.warning('请至少输入一个搜索条件（消息内容/会话ID/发送方ID/接收方ID）')
    return
  }

  // 处理日期范围
  if (dateRange.value && dateRange.value.length === 2) {
    queryParams.startTime = new Date(dateRange.value[0]).getTime()
    queryParams.endTime = new Date(dateRange.value[1]).getTime()
  } else {
    queryParams.startTime = undefined
    queryParams.endTime = undefined
  }

  loading.value = true
  try {
    // 返回格式: { code: 1, msg: "...", data: { data: [...], total: ..., ... } }
    const res = await searchMessages({
      content: queryParams.content || undefined,
      fromUserId: queryParams.fromUserId || undefined,
      toUserId: queryParams.toUserId || undefined,
      chatId: queryParams.chatId || undefined,
      msgStatus: queryParams.msgStatus,
      msgFormat: queryParams.msgFormat,
      withdrawFlag: queryParams.withdrawFlag,
      startTime: queryParams.startTime,
      endTime: queryParams.endTime
    })
    const resultVO = res.data
    if (resultVO && resultVO.data) {
      tableData.value = resultVO.data
      ElMessage.success(`搜索到 ${tableData.value.length} 条消息`)
    } else {
      tableData.value = []
      ElMessage.warning('暂无数据')
    }
  } catch (error) {
    console.error('搜索失败:', error)
    ElMessage.error('搜索失败')
  } finally {
    loading.value = false
  }
}

// 重置
const handleReset = () => {
  queryParams.content = ''
  queryParams.fromUserId = ''
  queryParams.toUserId = ''
  queryParams.chatId = ''
  queryParams.msgStatus = undefined
  queryParams.msgFormat = undefined
  queryParams.withdrawFlag = undefined
  queryParams.startTime = undefined
  queryParams.endTime = undefined
  dateRange.value = []
  loadLatestMessages()
}

// 加载更多
const loadMore = async () => {
  if (!lastRowKey.value) return
  
  loading.value = true
  try {
    const res = await getLatestMessages(50)
    const resultVO = res.data
    if (resultVO && resultVO.data) {
      tableData.value = [...tableData.value, ...resultVO.data]
      hasMore.value = resultVO.hasMore || false
      lastRowKey.value = resultVO.nextRowKey || ''
    }
  } catch (error) {
    console.error('加载更多失败:', error)
  } finally {
    loading.value = false
  }
}

// 查看详情
const handleViewDetail = (row: Message) => {
  currentMessage.value = row
  detailDialogVisible.value = true
}

// 查看会话
const handleViewChat = async (row: Message) => {
  queryParams.chatId = row.chatId
  queryParams.fromUserId = ''
  queryParams.toUserId = ''
  
  loading.value = true
  try {
    const res = await getMessagesByChatId(row.chatId, 100)
    const resultVO = res.data
    if (resultVO && resultVO.data) {
      tableData.value = resultVO.data
      ElMessage.success(`会话 ${row.chatId} 共 ${tableData.value.length} 条消息`)
    } else {
      tableData.value = []
    }
  } catch (error) {
    console.error('查询会话消息失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadLatestMessages()
})

// keep-alive 组件激活时重新加载数据
onActivated(() => {
  loadLatestMessages()
})
</script>

<style scoped>
.message-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-card {
  padding: 12px;
}

.search-card :deep(.el-form-item) {
  margin-bottom: 0;
}

.table-card {
  flex: 1;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.msg-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.content-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.load-more {
  margin-top: 16px;
  text-align: center;
}

.detail-content {
  max-height: 200px;
  overflow-y: auto;
  word-break: break-all;
  white-space: pre-wrap;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
}
</style>
