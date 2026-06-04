import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    redirect: '/',
    meta: {
      title: '登录 - AI金牌顾问',
      public: true
    }
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: '首页 - AI金牌顾问',
      description: 'AI金牌顾问提供智能理财分析和超级智能投资助理服务，满足您的财务咨询需求'
    }
  },
  {
    path: '/chat/:mode/:sessionId?',
    name: 'Chat',
    component: () => import('../views/ChatView.vue'),
    meta: {
      title: 'AI对话 - AI金牌顾问',
      description: 'AI智能对话'
    }
  },
  // Backward compatible redirects
  {
    path: '/finance-advisor',
    redirect: (to) => {
      const sessionId = to.query.sessionId
      const q = to.query.q
      if (sessionId) {
        return { path: `/chat/basic/${sessionId}` }
      }
      return { path: '/chat/basic', query: q ? { q } : {} }
    }
  },
  {
    path: '/super-agent',
    redirect: (to) => {
      const sessionId = to.query.sessionId
      const q = to.query.q
      if (sessionId) {
        return { path: `/chat/agent/${sessionId}` }
      }
      return { path: '/chat/agent', query: q ? { q } : {} }
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局导航守卫，设置文档标题
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title
  }

  // Validate mode parameter
  if (to.name === 'Chat') {
    const mode = to.params.mode
    if (mode && !['basic', 'agent'].includes(mode)) {
      next({ path: '/' })
      return
    }
  }

  next()
})

export default router
