<template>
  <div class="user-list">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="queryParams.keyword"
            placeholder="用户ID/用户名/手机号"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="queryParams.sex" placeholder="全部" clearable style="width: 120px">
            <el-option label="男" :value="1" />
            <el-option label="女" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="终端类型">
          <el-select v-model="queryParams.terminalType" placeholder="全部" clearable style="width: 120px">
            <el-option label="Android" :value="1" />
            <el-option label="iOS" :value="2" />
            <el-option label="小程序" :value="3" />
            <el-option label="Web" :value="4" />
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
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="userId" label="用户ID" width="180" show-overflow-tooltip />
        <el-table-column prop="userName" label="用户名" width="140" />
        <el-table-column prop="userFullName" label="全名" width="140" />
        <el-table-column label="头像" width="80" align="center">
          <template #default="{ row }">
            <el-avatar :size="36" :src="row.odlIconUrl">
              <el-icon><UserFilled /></el-icon>
            </el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="sexDesc" label="性别" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.sex === 1 ? 'primary' : 'danger'" size="small">
              {{ row.sexDesc }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="terminalTypeDesc" label="注册终端" width="100" align="center" />
        <el-table-column prop="status" label="账号状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'danger' : 'success'" size="small">
              {{ row.statusDesc || '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="在线状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.online ? 'success' : 'info'" size="small">
              {{ row.online ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" width="180" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDetail(row)">
              详情
            </el-button>
            <el-button
              type="warning"
              link
              size="small"
              :disabled="!row.online"
              @click="handleKick(row)"
            >
              踢下线
            </el-button>
            <el-button
              v-if="row.status === 0"
              type="danger"
              link
              size="small"
              @click="handleDisable(row)"
            >
              禁用
            </el-button>
            <el-button
              v-else
              type="success"
              link
              size="small"
              @click="handleEnable(row)"
            >
              启用
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

    <!-- 禁用对话框 -->
    <el-dialog v-model="disableDialogVisible" title="禁用用户" width="400px">
      <el-form>
        <el-form-item label="禁用原因">
          <el-input
            v-model="disableReason"
            type="textarea"
            :rows="3"
            placeholder="请输入禁用原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="disableDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmDisable">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageUsers, kickUser, disableUser, enableUser } from '../../api'
import type { User, UserQueryParams } from '../../types'

const router = useRouter()
const loading = ref(false)
const tableData = ref<User[]>([])
const total = ref(0)

const queryParams = reactive<UserQueryParams>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  sex: undefined,
  terminalType: undefined
})

const disableDialogVisible = ref(false)
const disableReason = ref('')
const currentUser = ref<User | null>(null)

// 首次加载
onMounted(() => {
  loadData()
})

// 页面激活时重新加载（解决 keep-alive 缓存问题）
onActivated(() => {
  loadData()
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await pageUsers(queryParams)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('加载用户列表失败:', error)
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.pageNum = 1
  loadData()
}

const handleReset = () => {
  queryParams.keyword = ''
  queryParams.sex = undefined
  queryParams.terminalType = undefined
  queryParams.pageNum = 1
  loadData()
}

const handleDetail = (row: User) => {
  router.push(`/user/detail/${row.userId}`)
}

const handleKick = async (row: User) => {
  try {
    await ElMessageBox.confirm(`确定要将用户 ${row.userName} 踢下线吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await kickUser(row.userId)
    ElMessage.success('操作成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleDisable = (row: User) => {
  currentUser.value = row
  disableReason.value = ''
  disableDialogVisible.value = true
}

const confirmDisable = async () => {
  if (!currentUser.value) return
  if (!disableReason.value.trim()) {
    ElMessage.warning('请输入禁用原因')
    return
  }
  try {
    await disableUser(currentUser.value.userId, disableReason.value)
    ElMessage.success('禁用成功')
    disableDialogVisible.value = false
    loadData()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const handleEnable = async (row: User) => {
  try {
    await ElMessageBox.confirm(`确定要启用用户 ${row.userName} 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await enableUser(row.userId)
    ElMessage.success('启用成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.user-list {
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

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
