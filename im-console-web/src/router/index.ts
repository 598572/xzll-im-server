import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import Layout from '../layout/Layout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/dashboard/Index.vue'),
        meta: { title: '数据看板', icon: 'DataLine' }
      }
    ]
  },
  {
    path: '/user',
    component: Layout,
    redirect: '/user/list',
    meta: { title: '用户管理', icon: 'User' },
    children: [
      {
        path: 'list',
        name: 'UserList',
        component: () => import('../views/user/UserList.vue'),
        meta: { title: '用户列表' }
      },
      {
        path: 'detail/:userId',
        name: 'UserDetail',
        component: () => import('../views/user/UserDetail.vue'),
        meta: { title: '用户详情', hidden: true }
      }
    ]
  },
  {
    path: '/message',
    component: Layout,
    redirect: '/message/list',
    meta: { title: '消息管理', icon: 'ChatLineSquare' },
    children: [
      {
        path: 'list',
        name: 'MessageList',
        component: () => import('../views/message/MessageList.vue'),
        meta: { title: '聊天记录' }
      },
      {
        path: 'session',
        name: 'SessionList',
        component: () => import('../views/message/SessionList.vue'),
        meta: { title: '会话列表' }
      }
    ]
  },
  {
    path: '/friend',
    component: Layout,
    redirect: '/friend/list',
    meta: { title: '好友管理', icon: 'Connection' },
    children: [
      {
        path: 'list',
        name: 'FriendList',
        component: () => import('../views/friend/FriendList.vue'),
        meta: { title: '好友关系' }
      }
    ]
  },
  {
    path: '/sensitive',
    component: Layout,
    redirect: '/sensitive/list',
    meta: { title: '敏感词管理', icon: 'Warning' },
    children: [
      {
        path: 'list',
        name: 'SensitiveWordList',
        component: () => import('../views/sensitive/SensitiveWordList.vue'),
        meta: { title: '敏感词列表' }
      }
    ]
  },
  {
    path: '/ban',
    component: Layout,
    redirect: '/ban/list',
    meta: { title: '封禁管理', icon: 'Lock' },
    children: [
      {
        path: 'list',
        name: 'BanList',
        component: () => import('../views/ban/BanList.vue'),
        meta: { title: '封禁列表' }
      }
    ]
  },
  {
    path: '/report',
    component: Layout,
    redirect: '/report/list',
    meta: { title: '举报处理', icon: 'Document' },
    children: [
      {
        path: 'list',
        name: 'ReportList',
        component: () => import('../views/report/ReportList.vue'),
        meta: { title: '举报列表' }
      }
    ]
  },
  {
    path: '/notice',
    component: Layout,
    redirect: '/notice/list',
    meta: { title: '系统公告', icon: 'Bell' },
    children: [
      {
        path: 'list',
        name: 'NoticeList',
        component: () => import('../views/notice/NoticeList.vue'),
        meta: { title: '公告列表' }
      }
    ]
  },
  {
    path: '/log',
    component: Layout,
    redirect: '/log/list',
    meta: { title: '操作日志', icon: 'DocumentCopy' },
    children: [
      {
        path: 'list',
        name: 'OperationLog',
        component: () => import('../views/log/OperationLog.vue'),
        meta: { title: '日志列表' }
      }
    ]
  },
  {
    path: '/ai',
    component: Layout,
    redirect: '/ai/customer-service',
    meta: { title: 'AI管理', icon: 'ChatDotRound' },
    children: [
      {
        path: 'customer-service',
        name: 'CustomerService',
        component: () => import('../views/ai/CustomerService.vue'),
        meta: { title: 'AI智能客服' }
      },
      {
        path: 'knowledge',
        name: 'AiKnowledge',
        component: () => import('../views/ai/AiKnowledge.vue'),
        meta: { title: '知识库管理' }
      },
      {
        path: 'config',
        name: 'AIConfig',
        component: () => import('../views/ai/AIConfig.vue'),
        meta: { title: 'AI配置中心' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  // 设置页面标题
  document.title = `${to.meta.title || 'IM管理后台'} - IM Console`
  
  const token = localStorage.getItem('token')
  
  if (to.path === '/login') {
    if (token) {
      next('/dashboard')
    } else {
      next()
    }
  } else {
    if (token) {
      next()
    } else {
      next('/login')
    }
  }
})

export default router
