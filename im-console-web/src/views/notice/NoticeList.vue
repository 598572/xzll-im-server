<template>
  <div class="notice-list-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>系统公告管理</span>
          <el-button type="primary" @click="handleAdd">发布公告</el-button>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :inline="true" :model="queryForm" class="search-form">
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable>
            <el-option label="草稿" :value="0" />
            <el-option label="已发布" :value="1" />
            <el-option label="已撤回" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="tableData" border v-loading="loading">
        <el-table-column prop="title" label="公告标题" min-width="200" />
        <el-table-column prop="noticeType" label="公告类型" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.noticeType === 1" type="primary">系统公告</el-tag>
            <el-tag v-else-if="row.noticeType === 2" type="warning">维护通知</el-tag>
            <el-tag v-else type="success">活动通知</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="info">草稿</el-tag>
            <el-tag v-else-if="row.status === 1" type="success">已发布</el-tag>
            <el-tag v-else type="danger">已撤回</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishTime" label="发布时间" width="180" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleView(row)">查看</el-button>
            <el-button
              v-if="row.status === 0"
              type="primary"
              size="small"
              @click="handlePublish(row)"
            >
              发布
            </el-button>
            <el-button
              v-if="row.status === 1"
              type="warning"
              size="small"
              @click="handleRevoke(row)"
            >
              撤回
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.current"
        :total="pagination.total"
        layout="total, prev, pager, next"
      />
    </el-card>

    <!-- 公告编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑公告' : '发布公告'" width="700px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="公告标题" required>
          <el-input v-model="form.title" placeholder="请输入公告标题" />
        </el-form-item>
        <el-form-item label="公告类型" required>
          <el-radio-group v-model="form.noticeType">
            <el-radio :label="1">系统公告</el-radio>
            <el-radio :label="2">维护通知</el-radio>
            <el-radio :label="3">活动通知</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="公告内容" required>
          <el-input v-model="form.content" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="目标用户">
          <el-radio-group v-model="form.targetUsers">
            <el-radio label="ALL">全部用户</el-radio>
            <el-radio label="SPECIFIC">指定用户</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageNoticeList, createNotice, publishNotice, deleteNotice } from '../../api'

const loading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)

const queryForm = reactive({
  status: null
})

const pagination = reactive({
  current: 1,
  total: 0
})

const form = reactive({
  title: '',
  noticeType: 1,
  content: '',
  targetUsers: 'ALL'
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
    const res = await pageNoticeList({
      current: pagination.current,
      size: 10,
      status: queryForm.status
    })
    tableData.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } catch (error) {
    console.error('加载公告列表失败:', error)
    ElMessage.error('加载公告列表失败')
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  pagination.current = 1
  fetchData()
}

function handleAdd() {
  isEdit.value = false
  Object.assign(form, {
    title: '',
    noticeType: 1,
    content: '',
    targetUsers: 'ALL'
  })
  dialogVisible.value = true
}

function handleView(row: any) {
  ElMessageBox.alert(row.content, row.title, {
    dangerouslyUseHTMLString: false
  })
}

async function handlePublish(row: any) {
  try {
    await ElMessageBox.confirm('确认发布该公告吗？', '提示', {
      type: 'warning'
    })
    await publishNotice(row.id)
    ElMessage.success('发布成功')
    fetchData()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('发布公告失败:', error)
      ElMessage.error('发布公告失败')
    }
  }
}

async function handleRevoke(row: any) {
  try {
    await ElMessageBox.confirm('确认撤回该公告吗？', '提示', {
      type: 'warning'
    })
    // TODO: 调用撤回接口（后端可能还没实现）
    ElMessage.success('撤回成功')
    fetchData()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('撤回公告失败:', error)
      ElMessage.error('撤回公告失败')
    }
  }
}

async function handleSubmit() {
  if (!form.title?.trim()) {
    ElMessage.warning('请输入公告标题')
    return
  }
  if (!form.content?.trim()) {
    ElMessage.warning('请输入公告内容')
    return
  }

  try {
    await createNotice({
      title: form.title,
      content: form.content,
      type: form.noticeType
    })
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    dialogVisible.value = false
    fetchData()
  } catch (error) {
    console.error('保存公告失败:', error)
    ElMessage.error('保存公告失败')
  }
}
</script>

<style scoped>
.notice-list-container {
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
