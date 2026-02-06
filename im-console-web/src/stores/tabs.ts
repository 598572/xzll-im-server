import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface Tab {
  path: string
  title: string
  name: string
  closable: boolean
}

export const useTabsStore = defineStore('tabs', () => {
  // 打开的标签页列表
  const tabs = ref<Tab[]>([])

  // 当前激活的标签
  const activeTab = ref('')

  // 添加标签
  const addTab = (tab: Tab) => {
    // 检查标签是否已存在
    const existTab = tabs.value.find(t => t.path === tab.path)
    if (!existTab) {
      tabs.value.push(tab)
    }
    activeTab.value = tab.path
  }

  // 删除标签
  const removeTab = (targetPath: string) => {
    const index = tabs.value.findIndex(t => t.path === targetPath)
    if (index === -1) return

    tabs.value.splice(index, 1)

    // 如果删除的是当前激活的标签，需要激活相邻的标签
    if (activeTab.value === targetPath) {
      if (tabs.value.length > 0) {
        // 激活右侧标签，如果没有则激活左侧
        const nextTab = tabs.value[index] || tabs.value[index - 1]
        activeTab.value = nextTab.path
      } else {
        activeTab.value = ''
      }
    }
  }

  // 关闭其他标签
  const closeOtherTabs = (targetPath: string) => {
    tabs.value = tabs.value.filter(t => t.path === targetPath)
    activeTab.value = targetPath
  }

  // 关闭所有标签
  const closeAllTabs = () => {
    tabs.value = []
    activeTab.value = ''
  }

  // 关闭左侧标签
  const closeLeftTabs = (targetPath: string) => {
    const index = tabs.value.findIndex(t => t.path === targetPath)
    if (index > 0) {
      tabs.value = tabs.value.slice(index)
    }
  }

  // 关闭右侧标签
  const closeRightTabs = (targetPath: string) => {
    const index = tabs.value.findIndex(t => t.path === targetPath)
    if (index < tabs.value.length - 1) {
      tabs.value = tabs.value.slice(0, index + 1)
    }
  }

  // 设置当前激活的标签
  const setActiveTab = (path: string) => {
    activeTab.value = path
  }

  return {
    tabs,
    activeTab,
    addTab,
    removeTab,
    closeOtherTabs,
    closeAllTabs,
    closeLeftTabs,
    closeRightTabs,
    setActiveTab
  }
})
