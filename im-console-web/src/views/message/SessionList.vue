<template>
  <div class="session-list">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="用户ID">
          <el-input
            v-model="queryParams.userId"
            placeholder="请输入用户ID查询其会话"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="会话类型">
          <el-select v-model="queryParams.chatType" placeholder="全部" clearable style="width: 120px">
            <el-option label="单聊" :value="1" />
            <el-option label="群聊" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleSearch">搜索</el-button>
          <el-button icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <span>会话列表</span>
          <el-tag type="info">共 {{ tableData.length }} 个会话</el-tag>
        </div>
      </template>
      
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="chatId" label="会话ID" width="200" show-overflow-tooltip />
        <el-table-column label="会话类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.chatType === 1 ? 'primary' : 'success'" size="small">
              {{ row.chatType === 1 ? '单聊' : '群聊' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户ID" width="140" />
        <el-table-column prop="otherUserId" label="对方用户ID" width="140" />
        <el-table-column prop="otherUserName" label="对方昵称" width="140" />
        <el-table-column prop="lastMessageContent" label="最后消息" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="last-msg">
              <el-tag v-if="row.lastMsgFormat === 1" type="info" size="small">文本</el-tag>
              <el-tag v-else-if="row.lastMsgFormat === 2" type="warning" size="small">图片</el-tag>
              <el-tag v-else-if="row.lastMsgFormat === 3" type="success" size="small">语音</el-tag>
              <span>{{ row.lastMessageContent || '暂无消息' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="未读数" width="100" align="center">
          <template #default="{ row }">
            <el-badge v-if="row.unReadCount > 0" :value="row.unReadCount" class="unread-badge" />
            <span v-else>0</span>
          </template>
        </el-table-column>
        <el-table-column label="最后消息时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.lastMsgTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleViewMessages(row)">
              查看消息
            </el-button>
            <el-button type="info" link size="small" @click="handleViewDetail(row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 会话详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="会话详情" width="600px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="会话ID" :span="2">{{ currentSession?.chatId }}</el-descriptions-item>
        <el-descriptions-item label="会话类型">
          <el-tag :type="currentSession?.chatType === 1 ? 'primary' : 'success'">
            {{ currentSession?.chatType === 1 ? '单聊' : '群聊' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ currentSession?.userId }}</el-descriptions-item>
        <el-descriptions-item label="对方用户ID">{{ currentSession?.otherUserId }}</el-descriptions-item>
        <el-descriptions-item label="对方昵称">{{ currentSession?.otherUserName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="未读消息数">
          <el-tag :type="currentSession?.unReadCount > 0 ? 'danger' : 'info'">
            {{ currentSession?.unReadCount || 0 }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="最后消息ID">{{ currentSession?.lastMsgId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最后消息时间" :span="2">
          {{ formatTime(currentSession?.lastMsgTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="最后消息内容" :span="2">
          <div class="detail-content">{{ currentSession?.lastMessageContent || '暂无消息' }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 消息列表对话框 -->
    <el-dialog v-model="messagesDialogVisible" title="会话消息" width="900px" top="5vh">
      <div class="messages-container" v-loading="messagesLoading">
        <div v-if="sessionMessages.length === 0" class="no-messages">
          暂无消息记录
        </div>
        <div v-else class="message-item" v-for="msg in sessionMessages" :key="msg.msgId">
          <div class="msg-header">
            <span class="sender">{{ msg.fromUserId }}</span>
            <span class="time">{{ formatTime(msg.msgCreateTime) }}</span>
          </div>
          <div class="msg-body">
            <el-tag size="small" :type="msg.msgFormat === 1 ? 'info' : 'warning'">
              {{ getMsgFormatText(msg.msgFormat) }}
            </el-tag>
            <span class="msg-text">{{ msg.msgContent }}</span>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMessagesByChatId } from '../../api'

interface Session {
  chatId: string
  chatType: number
  userId: string
  otherUserId: string
  otherUserName: string
  otherUserAvatar: string
  lastMessageContent: string
  lastMsgFormat: number
  lastMsgId: string
  lastMsgTime: number
  unReadCount: number
}

interface Message {
  msgId: string
  chatId: string
  fromUserId: string
  toUserId: string
  msgContent: string
  msgFormat: number
  msgStatus: number
  msgCreateTime: number
}

const router = useRouter()
const loading = ref(false)
const tableData = ref<Session[]>([])
const total = ref(0)

const queryParams = reactive({
  userId: '',
  chatType: undefined as number | undefined,
  pageNum: 1,
  pageSize: 20
})

const detailDialogVisible = ref(false)
const currentSession = ref<Session | null>(null)

const messagesDialogVisible = ref(false)
const messagesLoading = ref(false)
const sessionMessages = ref<Message[]>([])

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
const getMsgFormatText = (format: number) => {
  const formatMap: Record<number, string> = {
    1: '文本',
    2: '图片',
    3: '语音',
    4: '视频',
    5: '文件'
  }
  return formatMap[format] || '未知'
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    // 这里使用模拟数据，实际应调用后端API
    // 后端的会话查询在 im-business 服务中，路径是 /api/chat/lastChatList
    // 但那是给客户端用的，管理后台可能需要单独的接口
    
    // 使用模拟数据展示效果
    await new Promise(resolve => setTimeout(resolve, 500))
    tableData.value = [
      {
        chatId: '100-1-111-222',
        chatType: 1,
        userId: '111',
        otherUserId: '222',
        otherUserName: '李四',
        otherUserAvatar: '',
        lastMessageContent: '你好，在吗？',
        lastMsgFormat: 1,
        lastMsgId: '1-111-1960687134775255385',
        lastMsgTime: Date.now() - 3600000,
        unReadCount: 2
      },
      {
        chatId: '100-1-111-333',
        chatType: 1,
        userId: '111',
        otherUserId: '333',
        otherUserName: '王五',
        otherUserAvatar: '',
        lastMessageContent: '明天见',
        lastMsgFormat: 1,
        lastMsgId: '1-111-1960687134775255390',
        lastMsgTime: Date.now() - 7200000,
        unReadCount: 0
      },
      {
        chatId: '100-1-222-444',
        chatType: 1,
        userId: '222',
        otherUserId: '444',
        otherUserName: '赵六',
        otherUserAvatar: '',
        lastMessageContent: '[图片]',
        lastMsgFormat: 2,
        lastMsgId: '1-222-1960687134775255395',
        lastMsgTime: Date.now() - 10800000,
        unReadCount: 5
      }
    ]
    total.value = 3
  } catch (error) {
    console.error('加载会话列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  queryParams.pageNum = 1
  loadData()
}

// 重置
const handleReset = () => {
  queryParams.userId = ''
  queryParams.chatType = undefined
  queryParams.pageNum = 1
  loadData()
}

// 查看消息
const handleViewMessages = async (row: Session) => {
  messagesDialogVisible.value = true
  messagesLoading.value = true
  
  try {
    const res = await getMessagesByChatId(row.chatId, 100)
    if (res.success) {
      sessionMessages.value = res.data || []
    } else {
      // 使用模拟数据
      sessionMessages.value = [
        {
          msgId: '1-111-001',
          chatId: row.chatId,
          fromUserId: row.userId,
          toUserId: row.otherUserId,
          msgContent: '你好',
          msgFormat: 1,
          msgStatus: 4,
          msgCreateTime: Date.now() - 7200000
        },
        {
          msgId: '1-222-002',
          chatId: row.chatId,
          fromUserId: row.otherUserId,
          toUserId: row.userId,
          msgContent: '你好，有什么事？',
          msgFormat: 1,
          msgStatus: 4,
          msgCreateTime: Date.now() - 7100000
        },
        {
          msgId: '1-111-003',
          chatId: row.chatId,
          fromUserId: row.userId,
          toUserId: row.otherUserId,
          msgContent: row.lastMessageContent,
          msgFormat: row.lastMsgFormat,
          msgStatus: 3,
          msgCreateTime: row.lastMsgTime
        }
      ]
    }
  } catch (error) {
    console.error('加载消息失败:', error)
    sessionMessages.value = []
  } finally {
    messagesLoading.value = false
  }
}

// 查看详情
const handleViewDetail = (row: Session) => {
  currentSession.value = row
  detailDialogVisible.value = true
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.session-list {
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

.last-msg {
  display: flex;
  align-items: center;
  gap: 8px;
}

.unread-badge {
  margin-top: 0;
}

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.detail-content {
  max-height: 100px;
  overflow-y: auto;
  word-break: break-all;
  white-space: pre-wrap;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
}

.messages-container {
  max-height: 60vh;
  overflow-y: auto;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.no-messages {
  text-align: center;
  padding: 40px;
  color: #909399;
}

.message-item {
  background: #fff;
  padding: 12px 16px;
  margin-bottom: 12px;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.msg-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 12px;
}

.sender {
  color: #409EFF;
  font-weight: 500;
}

.time {
  color: #909399;
}

.msg-body {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.msg-text {
  flex: 1;
  word-break: break-all;
  line-height: 1.6;
}
</style>
