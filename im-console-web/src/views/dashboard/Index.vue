<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon user-icon">
              <el-icon :size="32"><User /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalUsers?.toLocaleString() || 0 }}</div>
              <div class="stat-label">总用户数</div>
            </div>
          </div>
          <div class="stat-footer">
            <span>今日新增 <strong>{{ stats.todayNewUsers || 0 }}</strong></span>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon online-icon">
              <el-icon :size="32"><Monitor /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.onlineUsers?.toLocaleString() || 0 }}</div>
              <div class="stat-label">在线用户</div>
            </div>
          </div>
          <div class="stat-footer">
            <span>实时在线</span>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon message-icon">
              <el-icon :size="32"><ChatDotRound /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.todayMessages?.toLocaleString() || 0 }}</div>
              <div class="stat-label">今日消息</div>
            </div>
          </div>
          <div class="stat-footer">
            <span>TPS <strong>{{ stats.messageTps || 0 }}</strong>/s</span>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon friend-icon">
              <el-icon :size="32"><Connection /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalFriendRelations?.toLocaleString() || 0 }}</div>
              <div class="stat-label">好友关系</div>
            </div>
          </div>
          <div class="stat-footer">
            <span>总关系数</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" class="chart-row">
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <span>近7日消息趋势</span>
          </template>
          <div ref="messageChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <span>终端类型分布</span>
          </template>
          <div ref="terminalChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 敏感词检测工具 -->
    <el-row :gutter="20" class="tool-row">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span>敏感词检测工具</span>
          </template>
          <div class="sensitive-tool">
            <el-input
              v-model="sensitiveText"
              type="textarea"
              :rows="4"
              placeholder="输入要检测的文本内容..."
            />
            <div class="tool-actions">
              <el-button type="primary" @click="handleCheckSensitive">检测</el-button>
              <el-button type="success" @click="handleFilterSensitive">过滤</el-button>
            </div>
            <div v-if="sensitiveResult" class="result-box">
              <div class="result-label">检测结果：</div>
              <div class="result-content">{{ sensitiveResult }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span>快捷操作</span>
          </template>
          <div class="quick-actions">
            <el-button type="primary" icon="User" @click="$router.push('/user/list')">
              用户管理
            </el-button>
            <el-button type="success" icon="Connection" @click="$router.push('/friend/list')">
              好友管理
            </el-button>
            <el-button type="warning" icon="Warning" @click="$router.push('/sensitive/list')">
              敏感词管理
            </el-button>
            <el-button type="info" icon="Refresh" @click="loadDashboardData">
              刷新数据
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { getDashboardStats, checkSensitiveContent, filterSensitiveContent } from '../../api'
import { DashboardStats } from '../../types'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

const stats = ref<Partial<DashboardStats>>({})
const sensitiveText = ref('')
const sensitiveResult = ref('')

const messageChartRef = ref<HTMLElement>()
const terminalChartRef = ref<HTMLElement>()
let messageChart: echarts.ECharts | null = null
let terminalChart: echarts.ECharts | null = null

const loadDashboardData = async () => {
  try {
    const res = await getDashboardStats()
    stats.value = res.data || {}
    updateCharts()
  } catch (error) {
    console.error('加载看板数据失败:', error)
    ElMessage.error('加载看板数据失败')
  }
}

const updateCharts = () => {
  // 消息趋势图
  if (messageChartRef.value && stats.value.messagesTrend) {
    if (!messageChart) {
      messageChart = echarts.init(messageChartRef.value)
    }
    const dates = Object.keys(stats.value.messagesTrend)
    const values = Object.values(stats.value.messagesTrend)
    
    messageChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', data: dates, boundaryGap: false },
      yAxis: { type: 'value' },
      series: [{
        name: '消息数',
        type: 'line',
        smooth: true,
        data: values,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.5)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.1)' }
          ])
        },
        lineStyle: { color: '#409EFF', width: 2 },
        itemStyle: { color: '#409EFF' }
      }]
    })
  }

  // 终端分布图
  if (terminalChartRef.value && stats.value.usersByTerminal) {
    if (!terminalChart) {
      terminalChart = echarts.init(terminalChartRef.value)
    }
    const data = Object.entries(stats.value.usersByTerminal).map(([name, value]) => ({
      name,
      value
    }))
    
    terminalChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { orient: 'vertical', left: 'left' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: { show: false, position: 'center' },
        emphasis: {
          label: { show: true, fontSize: 20, fontWeight: 'bold' }
        },
        labelLine: { show: false },
        data: data
      }]
    })
  }
}

const handleCheckSensitive = async () => {
  if (!sensitiveText.value.trim()) {
    ElMessage.warning('请输入要检测的文本')
    return
  }
  try {
    const res = await checkSensitiveContent(sensitiveText.value)
    const data = res.data
    if (data.hasSensitive) {
      sensitiveResult.value = `检测到敏感词: ${data.sensitiveWords.join(', ')}`
    } else {
      sensitiveResult.value = '未检测到敏感词'
    }
  } catch (error) {
    sensitiveResult.value = '检测服务暂不可用，请稍后重试'
  }
}

const handleFilterSensitive = async () => {
  if (!sensitiveText.value.trim()) {
    ElMessage.warning('请输入要过滤的文本')
    return
  }
  try {
    const res = await filterSensitiveContent(sensitiveText.value)
    sensitiveResult.value = `过滤后: ${res.data}`
  } catch (error) {
    sensitiveResult.value = '过滤服务暂不可用，请稍后重试'
  }
}

const handleResize = () => {
  messageChart?.resize()
  terminalChart?.resize()
}

onMounted(() => {
  loadDashboardData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  messageChart?.dispose()
  terminalChart?.dispose()
})
</script>

<style scoped>
.dashboard {
  padding: 0;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 8px;
}

.stat-content {
  display: flex;
  align-items: center;
  padding: 10px 0;
}

.stat-icon {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
  color: #fff;
}

.user-icon { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.online-icon { background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); }
.message-icon { background: linear-gradient(135deg, #ee0979 0%, #ff6a00 100%); }
.friend-icon { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); }

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.stat-footer {
  border-top: 1px solid #ebeef5;
  padding-top: 12px;
  margin-top: 12px;
  font-size: 13px;
  color: #909399;
}

.stat-footer strong {
  color: #409EFF;
}

.chart-row {
  margin-bottom: 20px;
}

.chart-container {
  height: 300px;
}

.tool-row {
  margin-bottom: 20px;
}

.sensitive-tool {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tool-actions {
  display: flex;
  gap: 12px;
}

.result-box {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 12px;
}

.result-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.result-content {
  font-size: 14px;
  color: #303133;
  word-break: break-all;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.quick-actions .el-button {
  flex: 1;
  min-width: 120px;
}
</style>
