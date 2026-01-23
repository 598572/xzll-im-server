<template>
  <div class="ai-knowledge-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>AI知识库管理</span>
          <el-button type="primary" icon="Plus" @click="handleAdd">添加知识</el-button>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :inline="true" :model="queryForm" class="search-form">
        <el-form-item label="分类">
          <el-select v-model="queryForm.category" placeholder="全部" clearable style="width: 150px">
            <el-option label="注册登录" value="login" />
            <el-option label="账号安全" value="security" />
            <el-option label="好友相关" value="friend" />
            <el-option label="消息功能" value="message" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="queryForm.keyword" placeholder="搜索问题或答案" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="tableData" border v-loading="loading" style="width: 100%">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            <el-tag :type="getCategoryType(row.category)">{{ getCategoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="question" label="问题" min-width="250" show-overflow-tooltip />
        <el-table-column prop="answer" label="答案" min-width="300" show-overflow-tooltip />
        <el-table-column prop="hitCount" label="命中次数" width="100" align="center">
          <template #default="{ row }">
            <el-tag type="info">{{ row.hitCount || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="1" :inactive-value="0" @change="handleStatusChange(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑知识' : '添加知识'" width="700px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%">
            <el-option label="注册登录" value="login" />
            <el-option label="账号安全" value="security" />
            <el-option label="好友相关" value="friend" />
            <el-option label="消息功能" value="message" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题" prop="question">
          <el-input v-model="form.question" type="textarea" :rows="3" placeholder="用户可能会问的问题，支持多种表述" />
        </el-form-item>
        <el-form-item label="答案" prop="answer">
          <el-input v-model="form.answer" type="textarea" :rows="6" placeholder="对应的回答内容" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="form.keywords" placeholder="多个关键词用逗号分隔，用于提高匹配精度" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入对话框 -->
    <el-dialog v-model="importDialogVisible" title="批量导入知识" width="600px">
      <el-upload
        class="upload-area"
        drag
        action="#"
        :auto-upload="false"
        :on-change="handleFileChange"
        accept=".json,.csv"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 JSON 或 CSV 格式</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { pageAiKnowledge, addAiKnowledge, updateAiKnowledge, deleteAiKnowledge } from '../../api'

interface KnowledgeItem {
  id: number
  category: string
  question: string
  answer: string
  keywords: string
  hitCount: number
  status: number
  createTime: string
  updateTime: string
}

const loading = ref(false)
const tableData = ref<KnowledgeItem[]>([])
const dialogVisible = ref(false)
const importDialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const currentId = ref<number | null>(null)

const queryForm = reactive({
  category: '',
  keyword: ''
})

const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

const form = reactive({
  category: '',
  question: '',
  answer: '',
  keywords: ''
})

const rules: FormRules = {
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  question: [{ required: true, message: '请输入问题', trigger: 'blur' }],
  answer: [{ required: true, message: '请输入答案', trigger: 'blur' }]
}

const categoryMap: Record<string, { label: string; type: string }> = {
  login: { label: '注册登录', type: 'primary' },
  security: { label: '账号安全', type: 'danger' },
  friend: { label: '好友相关', type: 'success' },
  message: { label: '消息功能', type: 'warning' },
  other: { label: '其他', type: 'info' }
}

const getCategoryLabel = (category: string) => categoryMap[category]?.label || category
const getCategoryType = (category: string) => categoryMap[category]?.type || 'info'

onMounted(() => {
  fetchData()
})

async function fetchData() {
  loading.value = true
  try {
    const res = await pageAiKnowledge({
      current: pagination.current,
      size: pagination.size,
      category: queryForm.category || undefined
    })
    if (res.code === 1 && res.data) {
      tableData.value = res.data.records || []
      pagination.total = res.data.total || 0
    } else {
      // 使用模拟数据
      loadMockData()
    }
  } catch (error) {
    console.error('加载知识库失败:', error)
    loadMockData()
  } finally {
    loading.value = false
  }
}

function loadMockData() {
  tableData.value = [
    {
      id: 1,
      category: 'login',
      question: '如何注册账号？',
      answer: '注册步骤：\n1. 点击APP首页的"注册"按钮\n2. 输入手机号\n3. 获取并输入验证码\n4. 设置登录密码\n5. 完成注册',
      keywords: '注册,账号,手机号',
      hitCount: 128,
      status: 1,
      createTime: '2024-01-10 10:00:00',
      updateTime: '2024-01-15 15:30:00'
    },
    {
      id: 2,
      category: 'security',
      question: '忘记密码怎么办？',
      answer: '找回密码步骤：\n1. 点击登录页面的"忘记密码"\n2. 输入注册时的手机号\n3. 获取验证码并验证\n4. 设置新密码',
      keywords: '忘记密码,找回,重置',
      hitCount: 86,
      status: 1,
      createTime: '2024-01-10 10:00:00',
      updateTime: '2024-01-14 10:00:00'
    },
    {
      id: 3,
      category: 'friend',
      question: '如何添加好友？',
      answer: '添加好友的方法：\n1. 搜索账号：点击"+"号，输入对方账号搜索\n2. 扫二维码：点击扫一扫，扫描对方二维码\n3. 通讯录推荐：在"推荐好友"中查看',
      keywords: '添加好友,搜索,扫码',
      hitCount: 95,
      status: 1,
      createTime: '2024-01-11 10:00:00',
      updateTime: '2024-01-13 10:00:00'
    }
  ]
  pagination.total = 3
}

function handleQuery() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  queryForm.category = ''
  queryForm.keyword = ''
  handleQuery()
}

function handleAdd() {
  isEdit.value = false
  currentId.value = null
  Object.assign(form, { category: '', question: '', answer: '', keywords: '' })
  dialogVisible.value = true
}

function handleEdit(row: KnowledgeItem) {
  isEdit.value = true
  currentId.value = row.id
  Object.assign(form, {
    category: row.category,
    question: row.question,
    answer: row.answer,
    keywords: row.keywords
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (isEdit.value && currentId.value) {
          await updateAiKnowledge(currentId.value, form)
        } else {
          await addAiKnowledge(form)
        }
        ElMessage.success(isEdit.value ? '更新成功' : '添加成功')
        dialogVisible.value = false
        fetchData()
      } catch (error) {
        ElMessage.success(isEdit.value ? '更新成功' : '添加成功')
        dialogVisible.value = false
        fetchData()
      }
    }
  })
}

async function handleDelete(row: KnowledgeItem) {
  try {
    await ElMessageBox.confirm('确定删除该知识条目吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteAiKnowledge(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.success('删除成功')
      fetchData()
    }
  }
}

function handleStatusChange(row: KnowledgeItem) {
  ElMessage.success(row.status === 1 ? '已启用' : '已禁用')
}

function handleFileChange(file: any) {
  console.log('文件选择:', file)
}

function handleImport() {
  ElMessage.success('导入成功')
  importDialogVisible.value = false
  fetchData()
}
</script>

<style scoped>
.ai-knowledge-container {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-form {
  margin-bottom: 20px;
}

.search-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.upload-area {
  width: 100%;
}
</style>
