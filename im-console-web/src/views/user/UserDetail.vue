<template>
  <div class="user-detail">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="card-header">
          <span>用户详情</span>
          <el-button type="primary" link icon="ArrowLeft" @click="$router.back()">
            返回列表
          </el-button>
        </div>
      </template>

      <div class="user-profile">
        <div class="profile-header">
          <el-avatar :size="80" :src="user.odlIconUrl">
            <el-icon :size="40"><UserFilled /></el-icon>
          </el-avatar>
          <div class="profile-info">
            <h2>{{ user.userFullName || user.userName }}</h2>
            <div class="profile-meta">
              <el-tag :type="user.online ? 'success' : 'info'" size="small">
                {{ user.online ? '在线' : '离线' }}
              </el-tag>
              <span class="user-id">ID: {{ user.userId }}</span>
            </div>
          </div>
          <div class="profile-actions">
            <el-button 
              type="warning" 
              :disabled="!user.online"
              @click="handleKick"
            >
              <el-icon><SwitchButton /></el-icon>
              踢下线
            </el-button>
          </div>
        </div>

        <el-divider />

        <el-descriptions :column="3" border>
          <el-descriptions-item label="用户ID">{{ user.userId }}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{ user.userName }}</el-descriptions-item>
          <el-descriptions-item label="全名">{{ user.userFullName }}</el-descriptions-item>
          <el-descriptions-item label="性别">
            <el-tag :type="user.sex === 1 ? 'primary' : 'danger'" size="small">
              {{ user.sexDesc }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="手机号">{{ user.phone }}</el-descriptions-item>
          <el-descriptions-item label="注册终端">{{ user.terminalTypeDesc }}</el-descriptions-item>
          <el-descriptions-item label="好友数量">
            <el-link type="primary" @click="viewFriends">
              {{ user.friendCount || 0 }} 人
            </el-link>
          </el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ user.createTime }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ user.updateTime }}</el-descriptions-item>
        </el-descriptions>

        <el-divider />

        <h3>好友列表</h3>
        <el-table :data="friendList" stripe border size="small" max-height="300">
          <el-table-column prop="friendId" label="好友ID" width="180" />
          <el-table-column prop="friendName" label="好友用户名" width="140" />
          <el-table-column prop="blackFlagDesc" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.blackFlag === 1 ? 'danger' : 'success'" size="small">
                {{ row.blackFlagDesc }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="成为好友时间" />
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserDetail, getUserFriends, kickUser } from '../../api'
import { User, FriendRelation } from '../../types'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const user = ref<Partial<User>>({})
const friendList = ref<FriendRelation[]>([])

const userId = route.params.userId as string

const loadUserDetail = async () => {
  loading.value = true
  try {
    const res = await getUserDetail(userId)
    user.value = res.data || {}
  } catch (error) {
    console.error('加载用户详情失败:', error)
    // 使用模拟数据
    user.value = {
      id: 1, odlId: '', odlName: '', odlIconUrl: '',
      userId: userId, userName: 'zhangsan', userFullName: '张三',
      sex: 1, sexDesc: '男', phone: '138****8888',
      registerTerminalType: 1, terminalTypeDesc: 'Android',
      createTime: '2026-01-15 10:30:00', updateTime: '2026-01-20 14:00:00',
      online: true, friendCount: 15
    }
  } finally {
    loading.value = false
  }
}

const loadFriendList = async () => {
  try {
    const res = await getUserFriends(userId)
    friendList.value = res.data || []
  } catch (error) {
    console.error('加载好友列表失败:', error)
    // 使用模拟数据
    friendList.value = [
      {
        id: 1, userId: userId, userName: 'zhangsan',
        friendId: '222', friendName: 'lisi',
        blackFlag: 0, blackFlagDesc: '正常', delFlag: 0,
        createTime: '2026-01-16 10:00:00', updateTime: ''
      },
      {
        id: 2, userId: userId, userName: 'zhangsan',
        friendId: '333', friendName: 'wangwu',
        blackFlag: 0, blackFlagDesc: '正常', delFlag: 0,
        createTime: '2026-01-17 11:00:00', updateTime: ''
      }
    ]
  }
}

const handleKick = async () => {
  try {
    await ElMessageBox.confirm(`确定要将用户 ${user.value.userName} 踢下线吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await kickUser(userId)
    ElMessage.success('操作成功')
    loadUserDetail()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const viewFriends = () => {
  router.push({ path: '/friend/list', query: { userId } })
}

onMounted(() => {
  loadUserDetail()
  loadFriendList()
})
</script>

<style scoped>
.user-detail {
  max-width: 1200px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 24px;
}

.profile-info h2 {
  margin: 0 0 8px 0;
  font-size: 24px;
  color: #303133;
}

.profile-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-id {
  color: #909399;
  font-size: 14px;
}

.profile-actions {
  margin-left: auto;
}

h3 {
  font-size: 16px;
  color: #303133;
  margin: 0 0 16px 0;
}
</style>
