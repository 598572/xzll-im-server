<template>
  <div class="friend-list">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="用户ID">
          <el-input
            v-model="queryParams.userId"
            placeholder="输入用户ID"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="好友ID">
          <el-input
            v-model="queryParams.friendId"
            placeholder="输入好友ID"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.blackFlag" placeholder="全部" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="已拉黑" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleSearch">搜索</el-button>
          <el-button icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 好友检测工具 -->
    <el-card shadow="never" class="tool-card">
      <template #header>
        <span>好友关系检测</span>
      </template>
      <el-form inline>
        <el-form-item label="用户A">
          <el-input v-model="checkParams.userA" placeholder="用户ID" style="width: 150px" />
        </el-form-item>
        <el-form-item label="用户B">
          <el-input v-model="checkParams.userB" placeholder="用户ID" style="width: 150px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleCheckFriendship">检测关系</el-button>
        </el-form-item>
        <el-form-item v-if="checkResult !== null">
          <el-tag :type="checkResult.isMutualFriend ? 'success' : 'info'" size="large">
            {{ checkResult.isMutualFriend ? '互为好友' : (checkResult.isFriend ? '单向好友' : '非好友关系') }}
          </el-tag>
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
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="userId" label="用户ID" width="180" show-overflow-tooltip />
        <el-table-column prop="userName" label="用户名" width="140" />
        <el-table-column prop="friendId" label="好友ID" width="180" show-overflow-tooltip />
        <el-table-column prop="friendName" label="好友用户名" width="140" />
        <el-table-column prop="blackFlagDesc" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.blackFlag === 1 ? 'danger' : 'success'" size="small">
              {{ row.blackFlagDesc }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="成为好友时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="viewUser(row.userId)">
              查看用户
            </el-button>
            <el-button type="success" link size="small" @click="viewUser(row.friendId)">
              查看好友
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { pageFriendRelations, checkFriendship } from '../../api'
import { FriendRelation, FriendQueryParams } from '../../types'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const tableData = ref<FriendRelation[]>([])
const total = ref(0)

const queryParams = reactive<FriendQueryParams>({
  pageNum: 1,
  pageSize: 10,
  userId: (route.query.userId as string) || '',
  friendId: '',
  blackFlag: undefined
})

const checkParams = reactive({
  userA: '',
  userB: ''
})
const checkResult = ref<{ isFriend: boolean; isMutualFriend: boolean } | null>(null)

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
    const res = await pageFriendRelations(queryParams)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('加载好友关系失败:', error)
    ElMessage.error('加载好友关系失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.pageNum = 1
  loadData()
}

const handleReset = () => {
  queryParams.userId = ''
  queryParams.friendId = ''
  queryParams.blackFlag = undefined
  queryParams.pageNum = 1
  loadData()
}

const handleCheckFriendship = async () => {
  if (!checkParams.userA || !checkParams.userB) {
    ElMessage.warning('请输入两个用户ID')
    return
  }
  try {
    const res = await checkFriendship(checkParams.userA, checkParams.userB)
    checkResult.value = res.data
  } catch (error) {
    console.error('检测好友关系失败:', error)
    ElMessage.error('检测好友关系失败')
  }
}

const viewUser = (userId: string) => {
  router.push(`/user/detail/${userId}`)
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.friend-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-card,
.tool-card {
  padding: 12px;
}

.search-card :deep(.el-form-item),
.tool-card :deep(.el-form-item) {
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
