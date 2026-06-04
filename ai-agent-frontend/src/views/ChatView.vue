<template>
  <div class="chat-page" :class="mode === 'agent' ? 'theme-super' : 'theme-love'">
    <header class="page-header">
      <h1 class="title">{{ pageTitle }}</h1>
      <span class="status-dot" :class="chatStore.connectionStatus" :title="statusText"></span>
    </header>

    <main class="content-wrapper">
      <ChatRoom
        :messages="chatStore.messages"
        :connection-status="chatStore.connectionStatus"
        :ai-type="mode === 'agent' ? 'super' : 'love'"
        @send-message="handleSendMessage"
        @toggle-reasoning="handleToggleReasoning"
      />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import ChatRoom from '@/components/ChatRoom.vue'

const router = useRouter()
const route = useRoute()
const sessionStore = useSessionStore()
const chatStore = useChatStore()
const authStore = useAuthStore()

// 防止 handleSendMessage 创建新会话后，路由 watcher 重复加载
let skipNextSessionLoad = false

const mode = computed(() => route.params.mode || 'basic')
const sessionId = computed(() => route.params.sessionId)

const pageTitle = computed(() => {
  return mode.value === 'agent' ? 'AI 超级智能体' : 'AI 理财顾问'
})

const statusText = computed(() => {
  if (chatStore.connectionStatus === 'connecting') {
    return mode.value === 'agent' ? '思考中' : '回复中'
  }
  if (chatStore.connectionStatus === 'error') return '连接异常'
  return '在线'
})

const welcomeMessage = (nextMode = mode.value) => nextMode === 'agent'
  ? '你好，我是 AI 超级智能体，可以帮你处理各类复杂任务。'
  : '你好，我是 AI 理财顾问，有任何财务问题都可以问我。'

watch(pageTitle, (title) => {
  document.title = `${title} - AI金牌顾问`
}, { immediate: true })


const handleSendMessage = async (message) => {
  if (!authStore.canUseGuestQuota()) {
    return
  }
  try {
    // 只有 URL 明确带 sessionId 时才复用会话；无 sessionId 表示新建草稿，首条消息必须创建新会话。
    if (sessionId.value) {
      chatStore.chatId = sessionId.value
      chatStore.mode = mode.value
    } else if (chatStore.chatId) {
      chatStore.startDraft(mode.value)
    }
    if (!chatStore.chatId) {
      const title = message.substring(0, 10) + (message.length > 10 ? '...' : '')
      const session = await sessionStore.createSession(title, mode.value)
      if (!session?.id) {
        throw new Error('创建会话失败')
      }
      chatStore.chatId = session.id
      chatStore.mode = mode.value
      // 标记跳过路由 watcher 的 loadSession，防止与 sendMessage 冲突
      skipNextSessionLoad = true
      router.replace({ path: `/chat/${mode.value}/${session.id}` })
    }
    if (!chatStore.mode) {
      chatStore.mode = mode.value
    }
    chatStore.sendMessage(message)
  } catch (error) {
    chatStore.connectionStatus = 'error'
    chatStore.addMessage(`发送失败：${error?.message || '无法创建会话，请检查后端服务是否已启动。'}`, false, '')
  }
}

const handleToggleReasoning = (traceMessageId) => {
  chatStore.toggleReasoning(traceMessageId)
}

onMounted(async () => {
  if (sessionId.value) {
    await chatStore.loadSession(sessionId.value, mode.value)
    const initQ = route.query.q
    if (initQ && chatStore.messages.length === 0) {
      chatStore.chatId = sessionId.value
      chatStore.mode = mode.value
      nextTick(() => { handleSendMessage(initQ) })
    }
    return
  }
  const initQ = route.query.q
  if (initQ) {
    chatStore.startDraft(mode.value)
    nextTick(() => { handleSendMessage(initQ) })
  } else {
    chatStore.startDraft(mode.value, welcomeMessage())
  }
})

watch(() => route.params.sessionId, async (nextId, prevId) => {
  if (nextId === prevId) return
  if (!nextId) {
    const initQ = route.query.q
    if (initQ) {
      chatStore.startDraft(mode.value)
      nextTick(() => { handleSendMessage(initQ) })
    } else {
      chatStore.startDraft(mode.value, welcomeMessage())
    }
    return
  }
  if (nextId === chatStore.chatId) return
  // 如果是 handleSendMessage 创建新会话触发的路由变更，跳过加载
  if (skipNextSessionLoad) {
    skipNextSessionLoad = false
    return
  }
  await chatStore.loadSession(nextId, mode.value)
})

watch(() => route.params.mode, async (nextMode, prevMode) => {
  if (!nextMode || nextMode === prevMode) return
  // 不再 reset，保留所有会话的缓存消息
  // 如果有 sessionId，加载对应会话（切模式带会话 ID）
  if (sessionId.value) {
    await chatStore.loadSession(sessionId.value, nextMode)
    return
  }
  // 无 sessionId，重置当前视图并显示欢迎消息
  chatStore.startDraft(nextMode, welcomeMessage(nextMode))
})

</script>

<style scoped>
.chat-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px 20px 12px;
  flex-shrink: 0;
}

.title {
  font-family: var(--font-body);
  font-size: 1.05rem;
  font-weight: 600;
  color: var(--text-strong);
  letter-spacing: -0.01em;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #22c55e;
  flex-shrink: 0;
}

.status-dot.connecting {
  background: #f59e0b;
  animation: pulse 1.2s ease-in-out infinite;
}

.status-dot.error {
  background: #ef4444;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.content-wrapper {
  flex: 1;
  min-height: 0;
  max-width: 860px;
  width: 100%;
  margin: 0 auto;
  padding: 0 20px 16px;
}
</style>
