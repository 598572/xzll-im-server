<template>
  <div class="report-list-container">
    <el-card>
      <template #header>
        <span>举报处理管理</span>
      </template>

      <!-- 搜索和统计 -->
      <el-row :gutter="20" class="stats-row">
        <el-col :span="6">
          <el-statistic title="待处理" :value="stats.pending" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="处理中" :value="stats.processing" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="已处理" :value="stats.handled" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="已驳回" :value="stats.rejected" />
        </el-col>
      </el-row>

      <!-- 搜索表单 -->
      <el-form :inline="true" :model="queryForm" class="search-form">
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable>
            <el-option label="待处理" :value="0" />
            <el-option label="处理中" :value="1" />
            <el-option label="已处理" :value="2" />
            <el-option label="已驳回" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="tableData" border v-loading="loading">
        <el-table-column prop="reportId" label="举报ID" width="150" />
        <el-table-column prop="reporterId" label="举报人" width="120" />
        <el-table-column prop="reportedUserId" label="被举报人" width="120" />
        <el-table-column prop="reportType" label="举报类型" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.reportType === 1" type="danger">色情</el-tag>
            <el-tag v-else-if="row.reportType === 2" type="warning">欺诈</el-tag>
            <el-tag v-else-if="row.reportType === 3" type="info">骚扰</el-tag>
            <el-tag v-else-if="row.reportType === 4" type="success">广告</el-tag>
            <el-tag v-else>其他</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reportContent" label="举报内容" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="warning">待处理</el-tag>
            <el-tag v-else-if="row.status === 1" type="primary">处理中</el-tag>
            <el-tag v-else-if="row.status === 2" type="success">已处理</el-tag>
            <el-tag v-else type="info">已驳回</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="举报时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleView(row)">
              查看
            </el-button>
            <el-button
              v-if="row.status !== 2"
              type="success"
              size="small"
              @click="handleProcess(row)"
            >
              处理
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.current"
        :total="pagination.total"
        @current-change="fetchData"
        layout="total, prev, pager, next"
      />
    </el-card>

    <!-- 处理对话框 -->
    <el-dialog v-model="dialogVisible" title="处理举报" width="600px">
      <el-form :model="processForm" label-width="100px">
        <el-form-item label="举报内容">
          <div class="report-content">{{ currentReport.reportContent }}</div>
        </el-form-item>
        <el-form-item label="处理结果">
          <el-radio-group v-model="processForm.handleResult">
            <el-radio :label="1">警告用户</el-radio>
            <el-radio :label="2">封禁账号</el-radio>
            <el-radio :label="3">驳回举报</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理说明">
          <el-input v-model="processForm.result" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitProcess">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const currentReport = ref<any>({})

const stats = reactive({
  pending: 12,
  processing: 5,
  handled: 89,
  rejected: 3
})

const queryForm = reactive({
  status: null
})

const pagination = reactive({
  current: 1,
  total: 0
})

const processForm = reactive({
  handleResult: 1,
  result: ''
})

onMounted(() => {
  fetchData()
})

function fetchData() {
  loading.value = true
  // TODO: 调用真实接口
  setTimeout(() => {
    tableData.value = [
      {
        id: 1,
        reportId: 'RPT001',
        reporterId: 'USER001',
        reportedUserId: 'USER002',
        reportType: 2,
        reportContent: '该用户发送欺诈信息，诱导转账',
        status: 0,
        createTime: '2024-01-15 10:00:00'
      }
    ]
    pagination.total = 1
    loading.value = false
  }, 500)
}

function handleQuery() {
  pagination.current = 1
  fetchData()
}

function handleView(row: any) {
  ElMessageBox.alert(`
    举报ID：${row.reportId}
    举报人：${row.reporterId}
    被举报人：${row.reportedUserId}
    举报内容：${row.reportContent}
    举报时间：${row.createTime}
  `, '举报详情')
}

function handleProcess(row: any) {
  currentReport.value = row
  dialogVisible.value = true
}

function handleSubmitProcess() {
  // TODO: 调用处理接口
  ElMessage.success('处理成功')
  dialogVisible.value = false
  fetchData()
}
</script>

<style scoped>
.report-list-container {
  padding: 20px;
}

.stats-row {
  margin-bottom: 20px;
}

.search-form {
  margin-bottom: 20px;
}

.report-content {
  padding: 10px;
  background: #f5f7fa;
  border-radius: 4px;
  line-height: 1.6;
}

.el-pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
