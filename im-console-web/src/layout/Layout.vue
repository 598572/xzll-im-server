<template>
  <div class="layout">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ collapsed: isCollapsed }">
      <div class="logo">
        <img src="../assets/vue.svg" alt="Logo" class="logo-img" />
        <span v-if="!isCollapsed" class="logo-text">IM管理后台</span>
      </div>

      <div class="menu-wrapper" ref="menuWrapperRef">
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapsed"
        :collapse-transition="false"
        background-color="#ffffff"
        text-color="#4b5563"
        active-text-color="#409EFF"
        router
        class="sidebar-menu"
      >
        <!-- 数据看板 -->
        <el-menu-item index="/dashboard">
          <el-icon><DataLine /></el-icon>
          <template #title>数据看板</template>
        </el-menu-item>

        <!-- 用户管理 -->
        <el-sub-menu index="/user">
          <template #title>
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </template>
          <el-menu-item index="/user/list">用户列表</el-menu-item>
        </el-sub-menu>

        <!-- 消息管理 -->
        <el-sub-menu index="/message">
          <template #title>
            <el-icon><ChatLineSquare /></el-icon>
            <span>消息管理</span>
          </template>
          <el-menu-item index="/message/list">聊天记录</el-menu-item>
          <el-menu-item index="/message/session">会话列表</el-menu-item>
        </el-sub-menu>

        <!-- 好友管理 -->
        <el-sub-menu index="/friend">
          <template #title>
            <el-icon><Connection /></el-icon>
            <span>好友管理</span>
          </template>
          <el-menu-item index="/friend/list">好友关系</el-menu-item>
        </el-sub-menu>

        <!-- 敏感词管理 -->
        <el-sub-menu index="/sensitive">
          <template #title>
            <el-icon><Warning /></el-icon>
            <span>敏感词管理</span>
          </template>
          <el-menu-item index="/sensitive/list">敏感词列表</el-menu-item>
        </el-sub-menu>

        <!-- 封禁管理 -->
        <el-sub-menu index="/ban">
          <template #title>
            <el-icon><Lock /></el-icon>
            <span>封禁管理</span>
          </template>
          <el-menu-item index="/ban/list">封禁列表</el-menu-item>
        </el-sub-menu>

        <!-- 举报处理 -->
        <el-sub-menu index="/report">
          <template #title>
            <el-icon><Document /></el-icon>
            <span>举报处理</span>
          </template>
          <el-menu-item index="/report/list">举报列表</el-menu-item>
        </el-sub-menu>

        <!-- 系统公告 -->
        <el-sub-menu index="/notice">
          <template #title>
            <el-icon><Bell /></el-icon>
            <span>系统公告</span>
          </template>
          <el-menu-item index="/notice/list">公告列表</el-menu-item>
        </el-sub-menu>

        <!-- 操作日志 -->
        <el-sub-menu index="/log">
          <template #title>
            <el-icon><DocumentCopy /></el-icon>
            <span>操作日志</span>
          </template>
          <el-menu-item index="/log/list">日志列表</el-menu-item>
        </el-sub-menu>

        <!-- AI管理 -->
        <el-sub-menu index="/ai">
          <template #title>
            <el-icon><ChatDotRound /></el-icon>
            <span>AI管理</span>
          </template>
          <el-menu-item index="/ai/customer-service">AI智能客服</el-menu-item>
          <el-menu-item index="/ai/knowledge">知识库管理</el-menu-item>
          <el-menu-item index="/ai/config">AI配置中心</el-menu-item>
        </el-sub-menu>
      </el-menu>
      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="main-container">
      <!-- 顶部导航 -->
      <header class="header">
        <div class="header-left">
          <el-icon 
            class="collapse-btn" 
            @click="toggleCollapse"
          >
            <Expand v-if="isCollapsed" />
            <Fold v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="$route.meta.title">
              {{ $route.meta.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        
        <div class="header-right">
          <el-dropdown>
            <span class="user-info">
              <el-avatar :size="32" icon="UserFilled" />
              <span class="username">管理员</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="content">
        <router-view v-slot="{ Component }">
          <component :is="Component" :key="$route.fullPath" />
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const isCollapsed = ref(false)
const menuWrapperRef = ref<HTMLElement | null>(null)

const activeMenu = computed(() => route.path)

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    localStorage.removeItem('token')
    router.push('/login')
  })
}

// 自动滚动相关变量
let scrollAnimationId: number | null = null
let lastScrollTime = 0
const SCROLL_THRESHOLD = 50 // 距离底部多少像素时触发滚动
const SCROLL_SPEED = 2 // 滚动速度（像素/帧）
const SCROLL_INTERVAL = 16 // 滚动间隔（约60fps）

// 处理鼠标移动，实现自动滚动
const handleMouseMove = (event: MouseEvent) => {
  if (!menuWrapperRef.value) return

  const wrapper = menuWrapperRef.value
  const rect = wrapper.getBoundingClientRect()
  const scrollTop = wrapper.scrollTop
  const scrollHeight = wrapper.scrollHeight
  const clientHeight = wrapper.clientHeight
  const maxScroll = scrollHeight - clientHeight

  // 鼠标在侧边栏内的相对位置
  const mouseY = event.clientY - rect.top

  // 如果没有可滚动内容，不执行
  if (maxScroll <= 0) return

  // 检查鼠标是否接近底部区域
  if (mouseY > clientHeight - SCROLL_THRESHOLD && scrollTop < maxScroll) {
    // 启动向下滚动
    startScrollDown(wrapper)
  } else if (mouseY < SCROLL_THRESHOLD && scrollTop > 0) {
    // 启动向上滚动
    startScrollUp(wrapper)
  } else {
    // 停止滚动
    stopScroll()
  }
}

// 向下滚动
const startScrollDown = (element: HTMLElement) => {
  stopScroll() // 先停止之前的滚动

  const scroll = () => {
    const maxScroll = element.scrollHeight - element.clientHeight
    if (element.scrollTop < maxScroll) {
      element.scrollTop += SCROLL_SPEED
      scrollAnimationId = requestAnimationFrame(scroll)
    }
  }

  scrollAnimationId = requestAnimationFrame(scroll)
}

// 向上滚动
const startScrollUp = (element: HTMLElement) => {
  stopScroll() // 先停止之前的滚动

  const scroll = () => {
    if (element.scrollTop > 0) {
      element.scrollTop -= SCROLL_SPEED
      scrollAnimationId = requestAnimationFrame(scroll)
    }
  }

  scrollAnimationId = requestAnimationFrame(scroll)
}

// 停止滚动
const stopScroll = () => {
  if (scrollAnimationId !== null) {
    cancelAnimationFrame(scrollAnimationId)
    scrollAnimationId = null
  }
}

// 鼠标离开时停止滚动
const handleMouseLeave = () => {
  stopScroll()
}

onMounted(() => {
  const wrapper = menuWrapperRef.value
  if (wrapper) {
    wrapper.addEventListener('mousemove', handleMouseMove)
    wrapper.addEventListener('mouseleave', handleMouseLeave)
  }
})

onUnmounted(() => {
  const wrapper = menuWrapperRef.value
  if (wrapper) {
    wrapper.removeEventListener('mousemove', handleMouseMove)
    wrapper.removeEventListener('mouseleave', handleMouseLeave)
  }
  stopScroll()
})
</script>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  width: 100vw;
}

.sidebar {
  width: 220px;
  background-color: #ffffff;
  border-right: 1px solid #e5e7eb;
  transition: width 0.3s;
  display: flex;
  flex-direction: column;
  height: 100vh;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.03);
}

.sidebar.collapsed {
  width: 64px;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 20px;
  background-color: #ffffff;
  border-bottom: 1px solid #e5e7eb;
}

.logo-img {
  width: 32px;
  height: 32px;
}

.logo-text {
  margin-left: 12px;
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  white-space: nowrap;
}

.menu-wrapper {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  background-color: #ffffff;
  position: relative;
}

/* 底部渐变提示 - 表示可以向下滚动 */
.menu-wrapper::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 40px;
  background: linear-gradient(to bottom, transparent, rgba(0, 0, 0, 0.05));
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.3s;
}

.menu-wrapper:hover::after {
  opacity: 1;
}

.menu-wrapper::-webkit-scrollbar {
  width: 6px;
}

.menu-wrapper::-webkit-scrollbar-thumb {
  background-color: rgba(0, 0, 0, 0.15);
  border-radius: 3px;
}

.menu-wrapper::-webkit-scrollbar-thumb:hover {
  background-color: rgba(0, 0, 0, 0.25);
}

.menu-wrapper::-webkit-scrollbar-track {
  background-color: transparent;
}

.sidebar-menu {
  border-right: none !important;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  height: 60px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  margin-right: 16px;
  color: #5a5e66;
}

.collapse-btn:hover {
  color: #409EFF;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.username {
  margin-left: 8px;
  font-size: 14px;
  color: #5a5e66;
}

.content {
  flex: 1;
  padding: 20px;
  background-color: #f0f2f5;
  overflow-y: auto;
}
</style>
