<template>
  <div class="tabs-view">
    <el-tabs
      v-model="activeTab"
      type="card"
      closable
      @tab-click="handleTabClick"
      @tab-remove="handleTabRemove"
      class="tabs-container"
    >
      <el-tab-pane
        v-for="tab in tabs"
        :key="tab.path"
        :label="tab.title"
        :name="tab.path"
        :closable="tab.closable"
      />
    </el-tabs>

    <div class="tabs-operation">
      <el-dropdown @command="handleCommand">
        <el-button size="small" type="primary" text>
          标签选项
          <el-icon class="el-icon--right">
            <ArrowDown />
          </el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="closeCurrent">关闭当前</el-dropdown-item>
            <el-dropdown-item command="closeOthers">关闭其他</el-dropdown-item>
            <el-dropdown-item command="closeLeft">关闭左侧</el-dropdown-item>
            <el-dropdown-item command="closeRight">关闭右侧</el-dropdown-item>
            <el-dropdown-item command="closeAll" divided>关闭全部</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useTabsStore } from '../stores/tabs'
import { ArrowDown } from '@element-plus/icons-vue'

const router = useRouter()
const tabsStore = useTabsStore()

const tabs = computed(() => tabsStore.tabs)
const activeTab = computed({
  get: () => tabsStore.activeTab,
  set: (val) => tabsStore.setActiveTab(val)
})

// 点击标签切换路由
const handleTabClick = (tab: any) => {
  const path = tab.paneName as string
  if (path !== router.currentRoute.value.path) {
    router.push(path)
  }
}

// 移除标签
const handleTabRemove = (targetPath: string) => {
  const targetTab = tabs.value.find(t => t.path === targetPath)
  if (!targetTab) return

  tabsStore.removeTab(targetPath)

  // 如果还有标签，跳转到激活的标签
  if (tabsStore.activeTab) {
    router.push(tabsStore.activeTab)
  }
}

// 处理下拉菜单命令
const handleCommand = (command: string) => {
  const currentPath = router.currentRoute.value.path

  switch (command) {
    case 'closeCurrent':
      if (currentPath) {
        handleTabRemove(currentPath)
      }
      break
    case 'closeOthers':
      if (currentPath) {
        tabsStore.closeOtherTabs(currentPath)
      }
      break
    case 'closeLeft':
      if (currentPath) {
        tabsStore.closeLeftTabs(currentPath)
      }
      break
    case 'closeRight':
      if (currentPath) {
        tabsStore.closeRightTabs(currentPath)
      }
      break
    case 'closeAll':
      tabsStore.closeAllTabs()
      router.push('/dashboard')
      break
  }
}
</script>

<style>
/* 全局样式，覆盖 Element Plus 默认行为 */
.tabs-container .el-tabs__item {
  transition: none !important;
  padding-right: 30px !important;
}

.tabs-container .el-tabs__item:hover {
  transform: none !important;
}

/* 强制关闭图标始终显示 */
.tabs-container .el-tabs__item .el-icon-close {
  width: 14px !important;
  display: inline-block !important;
  opacity: 1 !important;
  transform: scale(1) !important;
}

.tabs-container .el-tabs__item .el-icon-close:hover {
  background-color: #c0c4cc !important;
  color: #fff !important;
  border-radius: 50% !important;
}
</style>

<style scoped>
.tabs-view {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 20px 0;
  background-color: #fff;
  border-bottom: 1px solid #e5e7eb;
}

.tabs-container {
  flex: 1;
  overflow: hidden;
}

.tabs-container :deep(.el-tabs__header) {
  margin: 0;
  border-bottom: none;
}

.tabs-container :deep(.el-tabs__nav) {
  border: none;
  border-radius: 4px;
}

.tabs-container :deep(.el-tabs__item) {
  border: 1px solid #e5e7eb;
  border-bottom: none;
  margin-right: 4px;
  border-radius: 4px 4px 0 0;
  background-color: #f5f7fa;
  color: #606266;
  height: 32px;
  line-height: 32px;
  padding: 0 16px;
}

.tabs-container :deep(.el-tabs__item.is-active) {
  background-color: #fff !important;
  color: #8b5cf6 !important;
  border-color: #8b5cf6 !important;
  border-bottom: 1px solid #fff;
  margin-bottom: -1px;
}

.tabs-operation {
  margin-left: 12px;
  flex-shrink: 0;
}
</style>
