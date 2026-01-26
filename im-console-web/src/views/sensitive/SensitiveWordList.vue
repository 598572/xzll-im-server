<template>
  <div class="sensitive-word-list">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="queryParams.keyword"
            placeholder="敏感词内容"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="queryParams.wordType" placeholder="全部" clearable style="width: 140px">
            <el-option label="政治敏感" :value="1" />
            <el-option label="色情低俗" :value="2" />
            <el-option label="暴力血腥" :value="3" />
            <el-option label="广告营销" :value="4" />
            <el-option label="其他" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 100px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleSearch">搜索</el-button>
          <el-button icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作栏 -->
    <el-card shadow="never" class="action-card">
      <el-button type="primary" icon="Plus" @click="handleAdd">添加敏感词</el-button>
      <el-button type="success" icon="Upload" @click="showImportDialog = true">批量导入</el-button>
      <el-button 
        type="danger" 
        icon="Delete" 
        :disabled="selectedIds.length === 0"
        @click="handleBatchDelete"
      >
        批量删除
      </el-button>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        border
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="word" label="敏感词" min-width="180" show-overflow-tooltip />
        <el-table-column prop="wordType" label="分类" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ WORD_TYPE_MAP[row.wordType] || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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

    <!-- 添加/编辑对话框 -->
    <el-dialog
      v-model="formDialogVisible"
      :title="editingId ? '编辑敏感词' : '添加敏感词'"
      width="500px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="敏感词" prop="word">
          <el-input v-model="formData.word" placeholder="请输入敏感词" />
        </el-form-item>
        <el-form-item label="分类" prop="wordType">
          <el-select v-model="formData.wordType" placeholder="请选择分类" style="width: 100%">
            <el-option label="政治敏感" :value="1" />
            <el-option label="色情低俗" :value="2" />
            <el-option label="暴力血腥" :value="3" />
            <el-option label="广告营销" :value="4" />
            <el-option label="其他" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="formData.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入对话框 -->
    <el-dialog v-model="showImportDialog" title="批量导入敏感词" width="600px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      >
        <template #title>
          每行一个敏感词，导入后默认分类为"其他"，状态为"启用"
        </template>
      </el-alert>
      <el-input
        v-model="importText"
        type="textarea"
        :rows="10"
        placeholder="请输入敏感词，每行一个"
      />
      <template #footer>
        <el-button @click="showImportDialog = false">取消</el-button>
        <el-button type="primary" @click="handleImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox, FormInstance, FormRules } from 'element-plus'
import {
  pageSensitiveWords,
  addSensitiveWord,
  updateSensitiveWord,
  deleteSensitiveWord,
  toggleSensitiveWord,
  batchAddSensitiveWords
} from '../../api'
import { SensitiveWord, SensitiveWordQueryParams, WORD_TYPE_MAP } from '../../types'

const loading = ref(false)
const tableData = ref<SensitiveWord[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])

const queryParams = reactive<SensitiveWordQueryParams>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  wordType: undefined,
  status: undefined
})

// 表单相关
const formDialogVisible = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const formData = reactive({
  word: '',
  wordType: 5, // 默认为"其他"
  status: 1
})

const formRules: FormRules = {
  word: [{ required: true, message: '请输入敏感词', trigger: 'blur' }],
  wordType: [{ required: true, message: '请选择分类', trigger: 'change' }]
}

// 批量导入相关
const showImportDialog = ref(false)
const importText = ref('')

const loadData = async () => {
  loading.value = true
  try {
    const res = await pageSensitiveWords(queryParams)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('加载敏感词失败:', error)
    ElMessage.error('加载敏感词失败')
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
  queryParams.wordType = undefined
  queryParams.status = undefined
  queryParams.pageNum = 1
  loadData()
}

const handleSelectionChange = (selection: SensitiveWord[]) => {
  selectedIds.value = selection.map(item => item.id)
}

const handleAdd = () => {
  editingId.value = null
  formData.word = ''
  formData.wordType = 5
  formData.status = 1
  formDialogVisible.value = true
}

const handleEdit = (row: SensitiveWord) => {
  editingId.value = row.id
  formData.word = row.word
  formData.wordType = row.wordType
  formData.status = row.status
  formDialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (editingId.value) {
          await updateSensitiveWord({ id: editingId.value, ...formData })
          ElMessage.success('更新成功')
        } else {
          await addSensitiveWord(formData)
          ElMessage.success('添加成功')
        }
        formDialogVisible.value = false
        loadData()
      } catch (error) {
        ElMessage.error('操作失败')
      }
    }
  })
}

const handleStatusChange = async (row: SensitiveWord) => {
  try {
    await toggleSensitiveWord(row.id, row.status)
    ElMessage.success(row.status === 1 ? '已启用' : '已禁用')
  } catch (error) {
    ElMessage.error('状态更新失败')
    row.status = row.status === 1 ? 0 : 1
  }
}

const handleDelete = async (row: SensitiveWord) => {
  try {
    await ElMessageBox.confirm(`确定要删除敏感词"${row.word}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteSensitiveWord(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleBatchDelete = async () => {
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedIds.value.length} 个敏感词吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    loading.value = true
    const promises = selectedIds.value.map(id => deleteSensitiveWord(id))
    await Promise.all(promises)
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('批量删除失败')
    }
  } finally {
    loading.value = false
  }
}

const handleImport = async () => {
  const words = importText.value
    .split('\n')
    .map(w => w.trim())
    .filter(w => w.length > 0)

  if (words.length === 0) {
    ElMessage.warning('请输入要导入的敏感词')
    return
  }

  try {
    // wordType参数: 5-其他
    await batchAddSensitiveWords(words, 5)
    ElMessage.success(`成功导入 ${words.length} 个敏感词`)
    showImportDialog.value = false
    importText.value = ''
    loadData()
  } catch (error) {
    ElMessage.error('导入失败')
  }
}

// 首次加载
onMounted(() => {
  loadData()
})

// 页面激活时重新加载（解决 keep-alive 缓存问题）
onActivated(() => {
  loadData()
})
</script>

<style scoped>
.sensitive-word-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-card,
.action-card {
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
