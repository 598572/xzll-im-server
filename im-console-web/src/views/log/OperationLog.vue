<template>
  <div class="operation-log-container">
    <el-card>
      <template #header>
        <span>操作日志</span>
      </template>

      <!-- 搜索表单 -->
      <el-form :inline="true" :model="queryForm" class="search-form">
        <el-form-item label="管理员ID">
          <el-input v-model="queryForm.adminId" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="queryForm.operationType" placeholder="全部" clearable>
            <el-option label="用户管理" value="USER" />
            <el-option label="敏感词管理" value="WORD" />
            <el-option label="封禁管理" value="BAN" />
            <el-option label="举报处理" value="REPORT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="tableData" border v-loading="loading">
        <el-table-column prop="adminId" label="管理员ID" width="120" />
        <el-table-column prop="adminName" label="管理员名称" width="120" />
        <el-table-column prop="operationType" label="操作类型" width="150" />
        <el-table-column prop="operationDesc" label="操作描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="requestIp" label="请求IP" width="140" />
        <el-table-column prop="responseResult" label="结果" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.responseResult === 'SUCCESS'" type="success">成功</el-tag>
            <el-tag v-else type="danger">失败</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="操作时间" width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleViewDetail(row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="fetchData"
      />
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="dialogVisible" title="操作详情" width="600px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="管理员ID">{{ currentLog.adminId }}</el-descriptions-item>
        <el-descriptions-item label="管理员名称">{{ currentLog.adminName }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">{{ currentLog.operationType }}</el-descriptions-item>
        <el-descriptions-item label="操作描述">{{ currentLog.operationDesc }}</el-descriptions-item>
        <el-descriptions-item label="目标类型">{{ currentLog.targetType }}</el-descriptions-item>
        <el-descriptions-item label="目标ID">{{ currentLog.targetId }}</el-descriptions-item>
        <el-descriptions-item label="请求IP">{{ currentLog.requestIp }}</el-descriptions-item>
        <el-descriptions-item label="请求参数">
          <pre>{{ formatJson(currentLog.requestParams) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ currentLog.createTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const currentLog = ref<any>({})

const queryForm = reactive({
  adminId: '',
  operationType: ''
})

const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
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
        adminId: 'ADMIN001',
        adminName: 'admin',
        operationType: 'USER_DISABLE',
        operationDesc: '禁用用户 USER001',
        targetType: 'USER',
        targetId: 'USER001',
        requestIp: '192.168.1.100',
        requestParams: '{"userId":"USER001","reason":"违规"}',
        responseResult: 'SUCCESS',
        createTime: '2024-01-15 10:30:00'
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

function handleReset() {
  queryForm.adminId = ''
  queryForm.operationType = ''
  handleQuery()
}

function handleExport() {
  ElMessage.info('导出功能开发中...')
}

function handleViewDetail(row: any) {
  currentLog.value = row
  dialogVisible.value = true
}

function formatJson(json: string) {
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}
</script>

<style scoped>
.operation-log-container {
  padding: 20px;
}

.search-form {
  margin-bottom: 20px;
}

.el-pagination {
  margin-top: 20px;
  justify-content: flex-end;
}

pre {
  background: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  font-size: 12px;
  max-height: 300px;
  overflow-y: auto;
}
</style>
