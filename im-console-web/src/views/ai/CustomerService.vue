<template>
  <div class="customer-service-container">
    <el-card class="chat-card">
      <template #header>
        <div class="card-header">
          <span>AI智能客服</span>
          <el-tag type="success">在线</el-tag>
        </div>
      </template>

      <!-- 消息列表 -->
      <div class="message-list" ref="messageListRef">
        <div
          v-for="msg in messages"
          :key="msg.id"
          :class="['message-item', msg.type === 'user' ? 'user-message' : 'ai-message']"
        >
          <div class="message-avatar">
            <el-avatar :size="40" :src="msg.type === 'user' ? userAvatar : aiAvatar" />
          </div>
          <div class="message-content">
            <div class="message-sender">{{ msg.sender }}</div>
            <div class="message-text">{{ msg.content }}</div>
            <div class="message-time">{{ msg.time }}</div>
          </div>
        </div>

        <!-- 加载动画 -->
        <div v-if="loading" class="message-item ai-message">
          <div class="message-avatar">
            <el-avatar :size="40" :src="aiAvatar" />
          </div>
          <div class="message-content">
            <div class="typing-indicator">
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="input-area">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="3"
          placeholder="请输入您的问题..."
          @keydown.enter.ctrl="handleSend"
        />
        <div class="input-actions">
          <span class="tip">提示：按 Ctrl+Enter 发送</span>
          <el-button type="primary" :loading="loading" @click="handleSend">
            发送
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 快捷问题 -->
    <el-card class="quick-questions">
      <template #header>
        <span>常见问题</span>
      </template>
      <div class="question-list">
        <el-tag
          v-for="(q, index) in quickQuestions"
          :key="index"
          class="question-tag"
          @click="handleQuickQuestion(q)"
        >
          {{ q }}
        </el-tag>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick } from 'vue'
import { ElMessage } from 'element-plus'

const userAvatar = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const aiAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

const messageListRef = ref<HTMLElement>()
const inputMessage = ref('')
const loading = ref(false)

interface Message {
  id: number
  type: 'user' | 'ai'
  sender: string
  content: string
  time: string
}

const messages = reactive<Message[]>([
  {
    id: 1,
    type: 'ai',
    sender: 'AI客服',
    content: '您好！我是智能客服助手，有什么可以帮您的吗？',
    time: getCurrentTime()
  }
])

const quickQuestions = [
  '如何注册账号？',
  '如何修改密码？',
  '如何添加好友？',
  '消息发送失败怎么办？',
  '账号被禁用了怎么办？'
]

function getCurrentTime() {
  const now = new Date()
  return `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`
}

async function handleSend() {
  const content = inputMessage.value.trim()
  if (!content) {
    ElMessage.warning('请输入消息内容')
    return
  }

  // 添加用户消息
  messages.push({
    id: Date.now(),
    type: 'user',
    sender: '我',
    content,
    time: getCurrentTime()
  })

  inputMessage.value = ''
  await scrollToBottom()

  // 调用AI接口
  loading.value = true
  try {
    // TODO: 调用真实AI接口
    // const response = await fetch('http://localhost:8081/api/admin/ai/chat', {
    //   method: 'POST',
    //   headers: {
    //     'Content-Type': 'application/json',
    //     'Authorization': `Bearer ${localStorage.getItem('token')}`
    //   },
    //   body: JSON.stringify({ userId: 'test', message: content })
    // })

    // 模拟AI响应（实际应调用后端接口）
    await new Promise(resolve => setTimeout(resolve, 1000))

    const aiResponse = getMockResponse(content)

    messages.push({
      id: Date.now() + 1,
      type: 'ai',
      sender: 'AI客服',
      content: aiResponse,
      time: getCurrentTime()
    })
  } catch (error) {
    ElMessage.error('消息发送失败')
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

function getMockResponse(question: string): string {
  // 模拟AI响应（实际应该调用后端接口）
  if (question.includes('注册')) {
    return '注册步骤：\n1. 点击APP首页的"注册"按钮\n2. 输入手机号\n3. 获取并输入验证码\n4. 设置登录密码\n5. 完成注册'
  } else if (question.includes('密码')) {
    return '修改密码步骤：\n1. 进入"设置"页面\n2. 点击"账号与安全"\n3. 选择"修改密码"\n4. 输入原密码和新密码\n5. 确认修改'
  } else if (question.includes('好友')) {
    return '添加好友的方法：\n1. 知道对方账号：点击"+"号，输入对方账号搜索\n2. 扫二维码：点击扫一扫，扫描对方二维码\n3. 通讯录推荐：在"推荐好友"中查看'
  } else {
    return '感谢您的提问！我们会尽快为您解答。如需更多帮助，请联系人工客服。'
  }
}

function handleQuickQuestion(question: string) {
  inputMessage.value = question
  handleSend()
}

async function scrollToBottom() {
  await nextTick()
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}
</script>

<style scoped>
.customer-service-container {
  display: flex;
  gap: 20px;
  height: calc(100vh - 120px);
}

.chat-card {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 20px;
}

.message-item {
  display: flex;
  margin-bottom: 20px;
  align-items: flex-start;
}

.user-message {
  flex-direction: row-reverse;
}

.message-avatar {
  margin: 0 10px;
}

.message-content {
  max-width: 60%;
}

.message-sender {
  font-size: 12px;
  color: #909399;
  margin-bottom: 5px;
}

.user-message .message-sender {
  text-align: right;
}

.message-text {
  padding: 10px 15px;
  background: #fff;
  border-radius: 4px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-message .message-text {
  background: #e8f4ff;
}

.user-message .message-text {
  background: #409eff;
  color: #fff;
}

.message-time {
  font-size: 12px;
  color: #909399;
  margin-top: 5px;
}

.typing-indicator {
  display: flex;
  gap: 5px;
  padding: 10px 15px;
  background: #e8f4ff;
  border-radius: 4px;
  width: fit-content;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background: #409eff;
  border-radius: 50%;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-10px);
  }
}

.input-area {
  border-top: 1px solid #eee;
  padding-top: 15px;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
}

.tip {
  font-size: 12px;
  color: #909399;
}

.quick-questions {
  width: 300px;
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.question-tag {
  cursor: pointer;
  padding: 10px 15px;
  margin: 0;
}

.question-tag:hover {
  transform: translateX(5px);
  transition: transform 0.3s;
}
</style>
