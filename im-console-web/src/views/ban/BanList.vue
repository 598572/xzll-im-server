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
          <el-button type="danger" @click="handleOpenBanDialog">封禁用户</el-button>
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
        <el-form-item label="封禁类型" required>
          <el-select v-model="banForm.banType" placeholder="请选择封禁类型" style="width: 100%">
            <el-option label="账号封禁" :value="1" />
            <el-option label="IP封禁" :value="2" />
            <el-option label="设备封禁" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户ID/IP/设备ID" required>
          <el-input
            v-model="banForm.userId"
            :placeholder="getBanPlaceholder(banForm.banType)"
            clearable
          />
        </el-form-item>
        <el-form-item label="封禁原因" required>
          <el-input v-model="banForm.banReason" type="textarea" :rows="3" placeholder="请输入封禁原因" />
        </el-form-item>
        <el-form-item label="封禁天数">
          <el-input-number v-model="banForm.banDays" :min="1" :max="365" placeholder="留空表示永久封禁" />
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
import { ref, reactive, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageBanList, banUser, unbanUser } from '../../api'

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
  banType: 1,  // 默认账号封禁
  userId: '',
  banReason: '',
  banDays: null
})

// 首次加载
onMounted(() => {
  fetchData()
})

// 页面激活时重新加载（解决 keep-alive 缓存问题）
onActivated(() => {
  fetchData()
})

async function fetchData() {
  loading.value = true
  try {
    const res = await pageBanList({
      current: pagination.current,
      size: pagination.size,
      userId: queryForm.userId || undefined
    })
    tableData.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } catch (error) {
    console.error('加载封禁列表失败:', error)
    ElMessage.error('加载封禁列表失败')
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  queryForm.userId = ''
  handleQuery()
}

function handleOpenBanDialog() {
  // 清空表单并打开对话框
  banForm.banType = 1
  banForm.userId = queryForm.userId || ''  // 如果搜索栏有用户ID，自动填充
  banForm.banReason = ''
  banForm.banDays = null
  banDialogVisible.value = true
}

function getBanPlaceholder(banType: number) {
  switch (banType) {
    case 1:
      return '请输入用户ID'
    case 2:
      return '请输入IP地址'
    case 3:
      return '请输入设备ID'
    default:
      return '请输入用户ID/IP/设备ID'
  }
}

function handlePageChange(page: number) {
  pagination.current = page
  fetchData()
}

async function handleUnban(row: any) {
  try {
    await ElMessageBox.confirm('确认解封该用户吗？', '提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await unbanUser(row.id, '管理员解封')
    ElMessage.success('解封成功')
    fetchData()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('解封失败:', error)
      ElMessage.error('解封失败')
    }
  }
}

function handleViewDetail(row: any) {
  // 查看详情
  const banTypeText = row.banType === 1 ? '账号封禁' : row.banType === 2 ? 'IP封禁' : '设备封禁'
  const statusText = row.status === 1 ? '封禁中' : '已解封'

  ElMessageBox.alert(
    `<div style="line-height: 2;">
      <div><strong>用户ID：</strong>${row.userId}</div>
      <div><strong>封禁类型：</strong>${banTypeText}</div>
      <div><strong>封禁原因：</strong>${row.banReason || '无'}</div>
      <div><strong>开始时间：</strong>${row.banStartTime}</div>
      <div><strong>结束时间：</strong>${row.banEndTime || '永久'}</div>
      <div><strong>状态：</strong>${statusText}</div>
    </div>`,
    '封禁详情',
    {
      dangerouslyUseHTMLString: true,
      confirmButtonText: '关闭'
    }
  )
}

async function handleConfirmBan() {
  // 表单验证
  if (!banForm.userId?.trim()) {
    ElMessage.warning('请输入用户ID/IP/设备ID')
    return
  }
  if (!banForm.banReason?.trim()) {
    ElMessage.warning('请输入封禁原因')
    return
  }

  try {
    await banUser(banForm.userId, banForm.banReason, banForm.banDays)
    ElMessage.success('封禁成功')
    banDialogVisible.value = false
    fetchData()
  } catch (error) {
    console.error('封禁失败:', error)
    ElMessage.error('封禁失败')
  }
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
