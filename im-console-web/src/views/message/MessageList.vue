<template>
  <div class="message-list">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
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
        </el-form-item>
      </el-form>
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
              <span class="content-text">{{ row.msgContent }}</span>
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
          <div class="detail-content">{{ currentMessage?.msgContent }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
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

const queryParams = reactive({
  fromUserId: '',
  toUserId: '',
  chatId: ''
})

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

// 加载最新消息
const loadLatestMessages = async () => {
  loading.value = true
  try {
    const res = await getLatestMessages(50)
    if (res.success) {
      tableData.value = res.data || []
      hasMore.value = res.hasMore || false
      lastRowKey.value = res.nextRowKey || ''
      ElMessage.success(`加载了 ${tableData.value.length} 条最新消息`)
    } else {
      ElMessage.warning(res.message || '暂无数据')
      // 使用模拟数据
      loadMockData()
    }
  } catch (error) {
    console.error('加载消息失败:', error)
    loadMockData()
  } finally {
    loading.value = false
  }
}

// 模拟数据
const loadMockData = () => {
  tableData.value = [
    {
      msgId: '1-111-1960687134775255385',
      chatId: '100-1-111-222',
      fromUserId: '111',
      toUserId: '222',
      msgContent: '你好，在吗？',
      msgFormat: 1,
      msgStatus: 4,
      msgCreateTime: Date.now() - 3600000,
      createTime: Date.now() - 3600000,
      updateTime: Date.now() - 3600000
    },
    {
      msgId: '1-222-1960687134775255386',
      chatId: '100-1-111-222',
      fromUserId: '222',
      toUserId: '111',
      msgContent: '在的，有什么事吗？',
      msgFormat: 1,
      msgStatus: 4,
      msgCreateTime: Date.now() - 3500000,
      createTime: Date.now() - 3500000,
      updateTime: Date.now() - 3500000
    },
    {
      msgId: '1-111-1960687134775255387',
      chatId: '100-1-111-222',
      fromUserId: '111',
      toUserId: '222',
      msgContent: '想问一下明天的会议几点开始？',
      msgFormat: 1,
      msgStatus: 3,
      msgCreateTime: Date.now() - 3400000,
      createTime: Date.now() - 3400000,
      updateTime: Date.now() - 3400000
    }
  ]
  hasMore.value = false
}

// 搜索
const handleSearch = async () => {
  if (!queryParams.fromUserId && !queryParams.toUserId && !queryParams.chatId) {
    ElMessage.warning('请至少输入一个搜索条件')
    return
  }
  
  loading.value = true
  try {
    const res = await searchMessages({
      fromUserId: queryParams.fromUserId || undefined,
      toUserId: queryParams.toUserId || undefined,
      chatId: queryParams.chatId || undefined
    })
    if (res.success) {
      tableData.value = res.data || []
      ElMessage.success(`搜索到 ${tableData.value.length} 条消息`)
    } else {
      ElMessage.warning(res.message || '暂无数据')
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
  queryParams.fromUserId = ''
  queryParams.toUserId = ''
  queryParams.chatId = ''
  loadLatestMessages()
}

// 加载更多
const loadMore = async () => {
  if (!lastRowKey.value) return
  
  loading.value = true
  try {
    const res = await getLatestMessages(50)
    if (res.success && res.data) {
      tableData.value = [...tableData.value, ...res.data]
      hasMore.value = res.hasMore || false
      lastRowKey.value = res.nextRowKey || ''
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
    if (res.success) {
      tableData.value = res.data || []
      ElMessage.success(`会话 ${row.chatId} 共 ${tableData.value.length} 条消息`)
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
