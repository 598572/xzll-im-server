<template>
  <div class="ban-list-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户封禁管理</span>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :inline="true" :model="queryForm" class="search-form">
        <el-form-item label="用户ID">
          <el-input v-model="queryForm.userId" placeholder="请输入用户ID" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="tableData" border v-loading="loading">
        <el-table-column prop="userId" label="用户ID" width="150" />
        <el-table-column prop="banType" label="封禁类型" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.banType === 1" type="danger">账号封禁</el-tag>
            <el-tag v-else-if="row.banType === 2" type="warning">IP封禁</el-tag>
            <el-tag v-else type="info">设备封禁</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="banReason" label="封禁原因" show-overflow-tooltip />
        <el-table-column prop="banStartTime" label="封禁开始时间" width="180" />
        <el-table-column prop="banEndTime" label="封禁结束时间" width="180">
          <template #default="{ row }">
            {{ row.banEndTime || '永久' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="danger">封禁中</el-tag>
            <el-tag v-else type="success">已解封</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 1"
              type="primary"
              size="small"
              @click="handleUnban(row)"
            >
              解封
            </el-button>
            <el-button type="info" size="small" @click="handleViewDetail(row)">
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
        @current-change="handlePageChange"
        layout="total, prev, pager, next, jumper"
      />
    </el-card>

    <!-- 封禁对话框 -->
    <el-dialog v-model="banDialogVisible" title="封禁用户" width="500px">
      <el-form :model="banForm" label-width="100px">
        <el-form-item label="用户ID">
          <el-input v-model="banForm.userId" disabled />
        </el-form-item>
        <el-form-item label="封禁原因">
          <el-input v-model="banForm.banReason" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="封禁天数">
          <el-input-number v-model="banForm.banDays" :min="1" :max="365" />
          <span style="margin-left: 10px; color: #909399;">留空表示永久封禁</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="banDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmBan">确认封禁</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const banDialogVisible = ref(false)

const queryForm = reactive({
  userId: ''
})

const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

const banForm = reactive({
  userId: '',
  banReason: '',
  banDays: null
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
        userId: 'USER001',
        banType: 1,
        banReason: '违规发送广告信息',
        banStartTime: '2024-01-15 10:00:00',
        banEndTime: null,
        status: 1
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
  queryForm.userId = ''
  handleQuery()
}

function handlePageChange(page: number) {
  pagination.current = page
  fetchData()
}

function handleUnban(row: any) {
  ElMessageBox.confirm('确认解封该用户吗？', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    // TODO: 调用解封接口
    ElMessage.success('解封成功')
    fetchData()
  })
}

function handleViewDetail(row: any) {
  // 查看详情
  ElMessageBox.alert(`
    封禁类型：${row.banType === 1 ? '账号封禁' : row.banType === 2 ? 'IP封禁' : '设备封禁'}
    封禁原因：${row.banReason}
    开始时间：${row.banStartTime}
    结束时间：${row.banEndTime || '永久'}
  `, '封禁详情')
}

function handleConfirmBan() {
  // TODO: 调用封禁接口
  ElMessage.success('封禁成功')
  banDialogVisible.value = false
  fetchData()
}
</script>

<style scoped>
.ban-list-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-form {
  margin-bottom: 20px;
}

.el-pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
