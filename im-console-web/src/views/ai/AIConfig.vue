<template>
  <div class="ai-config-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>AI配置中心</span>
          <el-button type="primary" @click="handleSave">保存配置</el-button>
        </div>
      </template>

      <el-form :model="config" label-width="150px">
        <el-divider content-position="left">模型配置</el-divider>

        <el-form-item label="AI模型提供商">
          <el-select v-model="config.provider" placeholder="请选择">
            <el-option label="OpenAI" value="openai" />
            <el-option label="Azure" value="azure" />
            <el-option label="Anthropic" value="anthropic" />
            <el-option label="百度文心" value="baidu" />
            <el-option label="阿里通义" value="ali" />
          </el-select>
        </el-form-item>

        <el-form-item label="模型名称">
          <el-input v-model="config.modelName" placeholder="如：gpt-4" />
        </el-form-item>

        <el-form-item label="API地址">
          <el-input v-model="config.apiUrl" placeholder="API完整URL" />
        </el-form-item>

        <el-form-item label="API密钥">
          <el-input v-model="config.apiKey" type="password" show-password placeholder="请输入API密钥" />
        </el-form-item>

        <el-form-item label="最大Token数">
          <el-input-number v-model="config.maxTokens" :min="100" :max="4000" />
        </el-form-item>

        <el-form-item label="温度参数">
          <el-slider v-model="config.temperature" :min="0" :max="2" :step="0.1" show-input />
          <div class="tip">数值越高，回复越随机；数值越低，回复越确定</div>
        </el-form-item>

        <el-divider content-position="left">系统提示词</el-divider>

        <el-form-item label="系统提示词">
          <el-input
            v-model="config.systemPrompt"
            type="textarea"
            :rows="4"
            placeholder="设置AI的角色和行为方式"
          />
        </el-form-item>

        <el-divider content-position="left">知识库配置</el-divider>

        <el-form-item label="启用知识库">
          <el-switch v-model="config.knowledgeEnabled" />
        </el-form-item>

        <el-form-item label="知识库匹配阈值">
          <el-slider v-model="config.knowledgeThreshold" :min="0" :max="1" :step="0.1" show-input />
        </el-form-item>

        <el-divider content-position="left">高级配置</el-divider>

        <el-form-item label="流式输出">
          <el-switch v-model="config.streamEnabled" />
        </el-form-item>

        <el-form-item label="超时时间（秒）">
          <el-input-number v-model="config.timeout" :min="5" :max="120" />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 测试对话框 -->
    <el-dialog v-model="testDialogVisible" title="AI对话测试" width="600px">
      <div class="test-area">
        <el-input
          v-model="testMessage"
          type="textarea"
          :rows="4"
          placeholder="输入测试消息..."
        />
        <el-button type="primary" @click="handleTest" :loading="testLoading" style="margin-top: 10px">
          发送测试
        </el-button>
        <div v-if="testResponse" class="test-response">
          <div class="response-label">AI回复：</div>
          <div class="response-content">{{ testResponse }}</div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'

const config = reactive({
  provider: 'openai',
  modelName: 'gpt-4',
  apiUrl: 'https://api.openai.com/v1/chat/completions',
  apiKey: '',
  maxTokens: 2000,
  temperature: 0.7,
  systemPrompt: '你是一个智能客服助手，请友好、专业地回答用户问题。',
  knowledgeEnabled: true,
  knowledgeThreshold: 0.7,
  streamEnabled: false,
  timeout: 30
})

const testDialogVisible = ref(false)
const testMessage = ref('')
const testResponse = ref('')
const testLoading = ref(false)

function handleSave() {
  // TODO: 调用保存配置接口
  ElMessage.success('配置已保存')
}

function handleTest() {
  if (!testMessage.value) {
    ElMessage.warning('请输入测试消息')
    return
  }

  testLoading.value = true
  // TODO: 调用测试接口
  setTimeout(() => {
    testResponse.value = '这是测试回复。配置正常工作。'
    testLoading.value = false
  }, 1000)
}
</script>

<style scoped>
.ai-config-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tip {
  font-size: 12px;
  color: #909399;
  margin-top: 5px;
}

.test-area {
  padding: 20px;
}

.test-response {
  margin-top: 20px;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 4px;
}

.response-label {
  font-weight: bold;
  margin-bottom: 10px;
}

.response-content {
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>
