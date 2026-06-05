import { defineStore } from 'pinia'
import { ref } from 'vue'
import { chatWithFinanceApp, chatWithManus, getSessionMessages } from '@/api'
import { useAuthStore } from '@/stores/auth'
import {
  parseStepChunks,
  classifyStep,
  formatStepContent,
  filterFinalResultContent,
  decodeFinalSummary,
  isTerminalStepContent,
  reconstructTraceFromContent,
  ANALYSIS_HINTS,
  ANALYSIS_HINT_INTERVAL_MS,
  TERMINAL_FINALIZE_GRACE_MS,
  STEP_TYPING_FRAME_MS,
  STEP_TYPING_MAX_DURATION_MS,
  STEP_DIRECT_RENDER_THRESHOLD
} from '@/utils/agentParser'

export const useChatStore = defineStore('chat', () => {
  const BASIC_TYPING_FRAME_MS = 4
  const BASIC_TYPING_MAX_FRAMES_PER_CHUNK = 18
  const BASIC_PENDING_TEXT = '正在生成...'

  // State
  const messages = ref([])
  const connectionStatus = ref('disconnected')
  const chatId = ref('')
  const mode = ref('basic')

  // Internal state (not reactive)
  let messageSeed = 0
  let typingChain = Promise.resolve()

  // Per-session active SSE connections
  // Map<sessionId, { eventSource, messagesRef, analysisHintTimer, terminalFinalizeTimer }>
  const activeConnections = new Map()

  // 消息缓存：保存每个会话的 messages 数组引用，切换模式/会话时不丢失
  const sessionMessagesCache = new Map()

  // Helper: create message object
  const createMessage = (content, isUser, type = '', extra = {}) => ({
    id: `${Date.now()}-${messageSeed++}`,
    content,
    isUser,
    type,
    time: new Date().getTime(),
    ...extra
  })

  // Helper: wait
  const wait = (ms) => new Promise(resolve => setTimeout(resolve, ms))

  const enqueueBasicTypewriter = (sessionEntry, sessionMessages, aiMessageIndex, chunk) => {
    const text = String(chunk || '')
    if (!text) return
    sessionEntry.basicTypingChain = (sessionEntry.basicTypingChain || Promise.resolve()).then(async () => {
      if (sessionEntry.pendingPlaceholder && aiMessageIndex < sessionMessages.length) {
        sessionMessages[aiMessageIndex].content = ''
        sessionEntry.pendingPlaceholder = false
      }
      const charsPerFrame = Math.max(1, Math.ceil(text.length / BASIC_TYPING_MAX_FRAMES_PER_CHUNK))
      for (let i = 0; i < text.length; i += charsPerFrame) {
        if (sessionEntry.cancelled) return
        if (aiMessageIndex < sessionMessages.length) {
          sessionMessages[aiMessageIndex].content += text.slice(i, i + charsPerFrame)
        }
        await wait(BASIC_TYPING_FRAME_MS)
      }
    })
  }

  const finalizeBasicAfterTyping = (sessionId, sessionEntry) => {
    const chain = sessionEntry.basicTypingChain || Promise.resolve()
    chain.then(() => {
      if (sessionEntry.cancelled) return
      sessionEntry.eventSource = null
      if (chatId.value === sessionId) {
        connectionStatus.value = 'disconnected'
        activeConnections.delete(sessionId)
      }
    })
  }

  // Start analysis hint progress
  const startAnalysisHintProgress = (sessionEntry, trace) => {
    if (sessionEntry.analysisHintTimer) {
      clearInterval(sessionEntry.analysisHintTimer)
    }
    let hintIndex = 0
    trace.progressText = ANALYSIS_HINTS[hintIndex]
    sessionEntry.analysisHintTimer = setInterval(() => {
      if (trace.status !== 'running') return
      hintIndex = (hintIndex + 1) % ANALYSIS_HINTS.length
      trace.progressText = ANALYSIS_HINTS[hintIndex]
    }, ANALYSIS_HINT_INTERVAL_MS)
  }

  // Clear session timers
  const clearSessionTimers = (sessionEntry) => {
    if (sessionEntry?.analysisHintTimer) {
      clearInterval(sessionEntry.analysisHintTimer)
      sessionEntry.analysisHintTimer = null
    }
    if (sessionEntry?.terminalFinalizeTimer) {
      clearTimeout(sessionEntry.terminalFinalizeTimer)
      sessionEntry.terminalFinalizeTimer = null
    }
  }

  // Push step with typewriter effect
  const pushStepWithTypewriter = (trace, stepNo, type, fullContent, extra = {}) => {
    const step = {
      stepNo,
      type,
      fullContent,
      content: '',
      typing: true,
      ...extra
    }
    trace.steps.push(step)

    const shouldDirectRender = type === 'ai-step-tool' || fullContent.length > STEP_DIRECT_RENDER_THRESHOLD
    if (shouldDirectRender) {
      step.content = fullContent
      step.typing = false
      return step
    }

    typingChain = typingChain.then(async () => {
      const maxFrames = Math.max(1, Math.floor(STEP_TYPING_MAX_DURATION_MS / STEP_TYPING_FRAME_MS))
      const charsPerFrame = Math.max(1, Math.ceil(fullContent.length / maxFrames))
      for (let i = 0; i < fullContent.length; i += charsPerFrame) {
        step.content = fullContent.slice(0, i + charsPerFrame)
        await wait(STEP_TYPING_FRAME_MS)
      }
      step.content = fullContent
      step.typing = false
    })

    return step
  }

  // Push structured agent message
  const pushStructuredAgentMessage = (trace, rawChunk) => {
    const result = { terminal: false }
    const stepMatch = rawChunk.match(/^Step\s+(\d+)\s*:\s*([\s\S]*)$/)
    if (!stepMatch) {
      const body = formatStepContent(rawChunk)
      pushStepWithTypewriter(trace, trace.steps.length + 1, 'ai-step-result', body)
      result.terminal = isTerminalStepContent(body)
      return result
    }

    const stepNo = Number(stepMatch[1])
    const stepBody = formatStepContent(stepMatch[2])
    const stepType = classifyStep(stepBody)
    pushStepWithTypewriter(trace, stepNo, stepType, stepBody)

    result.terminal = isTerminalStepContent(stepBody)
    return result
  }

  const parseAgentStreamEvent = (data) => {
    if (!data || data[0] !== '{') return null
    try {
      const event = JSON.parse(data)
      if (!event || typeof event.type !== 'string') return null
      return event
    } catch (e) {
      return null
    }
  }

  const pushAgentEventStep = (trace, event) => {
    const body = formatStepContent(event.content || '')
    const eventTypeMap = {
      tool_plan: 'ai-step-think',
      tool_start: 'ai-step-tool',
      tool_result: 'ai-step-tool',
      artifact: 'ai-step-result',
      chart: 'ai-step-result',
      source: 'ai-step-tool',
      status: 'ai-step-result',
      error: 'ai-step-error'
    }
    const stepType = eventTypeMap[event.type] || classifyStep(body)
    const stepNo = event.stepNo || trace.steps.length + 1
    pushStepWithTypewriter(trace, stepNo, stepType, body || event.title || '', {
      title: event.title || '',
      eventType: event.type,
      eventStatus: event.status || '',
      data: event.data || null
    })
    return { terminal: isTerminalStepContent(body) }
  }

  // Toggle reasoning
  const toggleReasoning = (traceMessageId) => {
    const traceMsg = messages.value.find(msg => msg.id === traceMessageId)
    if (!traceMsg?.trace?.steps?.length) return
    traceMsg.trace.collapsed = !traceMsg.trace.collapsed
  }

  // Load session
  async function loadSession(sessionId, sessionMode) {
    chatId.value = sessionId
    mode.value = sessionMode

    // 1. 如果该会话在 activeConnections 中（SSE 正在运行或已完成），直接使用其消息
    const active = activeConnections.get(sessionId)
    if (active) {
      messages.value = active.messagesRef
      connectionStatus.value = active.eventSource ? 'connecting' : 'disconnected'
      // SSE 已完成，清理 activeConnections 条目（消息已在缓存中）
      if (!active.eventSource) {
        activeConnections.delete(sessionId)
      }
      return true
    }

    // 2. 检查消息缓存（跨模式切换时保留的消息）
    const cached = sessionMessagesCache.get(sessionId)
    if (cached && cached.length > 0) {
      messages.value = cached
      connectionStatus.value = 'disconnected'
      return true
    }

    // 3. 从数据库加载
    messages.value = []
    connectionStatus.value = 'disconnected'

    try {
      const history = await getSessionMessages(sessionId)
      if (history && history.length > 0) {
        const loaded = history.map(h => {
          if (sessionMode === 'agent' && h.role !== 'user') {
            const trace = reconstructTraceFromContent(h.content)
            if (trace) {
              return {
                id: `${Date.now()}-${messageSeed++}`,
                content: '',
                isUser: false,
                type: 'ai-trace',
                trace,
                time: new Date(h.create_time).getTime()
              }
            }
          }
          return {
            id: `${Date.now()}-${messageSeed++}`,
            content: h.content,
            isUser: h.role === 'user',
            time: new Date(h.create_time).getTime()
          }
        })
        messages.value = loaded
        // 加载后也放入缓存
        sessionMessagesCache.set(sessionId, loaded)
        return true
      }
    } catch (e) {
      console.error('加载会话历史失败:', e)
    }
    return false
  }

  // Send message (basic mode)
  const sendBasicMessage = (message) => {
    const sessionId = chatId.value
    if (!sessionId) {
      messages.value.push(createMessage('发送失败：当前没有可用会话，请重新新建对话。', false))
      connectionStatus.value = 'error'
      return
    }

    // Close existing connection for this session if any
    const existing = activeConnections.get(sessionId)
    if (existing?.eventSource) {
      existing.cancelled = true
      existing.eventSource.close()
    }
    clearSessionTimers(existing)

    // 复用当前 messages 数组（保留已有历史消息）
    const sessionMessages = messages.value

    // 缓存消息引用，切换模式/会话时不丢失
    sessionMessagesCache.set(sessionId, sessionMessages)

    // Add messages
    sessionMessages.push(createMessage(message, true))
    const aiMessageIndex = sessionMessages.length
    sessionMessages.push(createMessage(BASIC_PENDING_TEXT, false))

    // Store in activeConnections
    const sessionEntry = {
      eventSource: null,
      messagesRef: sessionMessages,
      analysisHintTimer: null,
      terminalFinalizeTimer: null,
      basicTypingChain: Promise.resolve(),
      pendingPlaceholder: true,
      cancelled: false
    }
    activeConnections.set(sessionId, sessionEntry)

    connectionStatus.value = 'connecting'

    const es = chatWithFinanceApp(message, sessionId, (data) => {
      if (data && data !== '[DONE]') {
        enqueueBasicTypewriter(sessionEntry, sessionMessages, aiMessageIndex, data)
      }
      if (data === '[DONE]') {
        finalizeBasicAfterTyping(sessionId, sessionEntry)
        return
      }
    }, (error) => {
      console.error('SSE Error:', error)
      sessionEntry.cancelled = true
      if (error?.status === 401 || error?.status === 429) {
        useAuthStore().openLoginModal()
      }
      if (aiMessageIndex < sessionMessages.length) {
        sessionMessages[aiMessageIndex].content = buildClientErrorMessage(error)
      }
      sessionEntry.eventSource = null
      if (chatId.value === sessionId) {
        connectionStatus.value = 'error'
      }
    })

    sessionEntry.eventSource = es
  }

  // Send message (agent mode)
  const sendAgentMessage = (message) => {
    const sessionId = chatId.value
    if (!sessionId) {
      messages.value.push(createMessage('发送失败：当前没有可用会话，请重新新建对话。', false, 'ai-trace', {
        trace: {
          steps: [],
          collapsed: false,
          finalResultMarkdown: '发送失败：当前没有可用会话，请重新新建对话。',
          summary: '发送失败',
          status: 'error',
          progressText: ''
        }
      }))
      connectionStatus.value = 'error'
      return
    }

    // Close existing connection for this session if any
    const existing = activeConnections.get(sessionId)
    if (existing?.eventSource) {
      existing.eventSource.close()
    }
    clearSessionTimers(existing)

    // 复用当前 messages 数组（保留已有历史消息）
    const sessionMessages = messages.value

    // 缓存消息引用，切换模式/会话时不丢失
    sessionMessagesCache.set(sessionId, sessionMessages)

    // Store in activeConnections
    const sessionEntry = {
      eventSource: null,
      messagesRef: sessionMessages,
      analysisHintTimer: null,
      terminalFinalizeTimer: null
    }
    activeConnections.set(sessionId, sessionEntry)

    // Add messages
    sessionMessages.push(createMessage(message, true, 'user-question'))

    const traceMsg = createMessage('', false, 'ai-trace', {
      trace: {
        steps: [],
        collapsed: false,
        finalResultMarkdown: '',
        summary: '',
        status: 'running',
        progressText: ANALYSIS_HINTS[0]
      }
    })
    sessionMessages.push(traceMsg)
    startAnalysisHintProgress(sessionEntry, traceMsg.trace)

    typingChain = Promise.resolve()

    connectionStatus.value = 'connecting'

    let finalSummaryFromBackend = ''
    let hasFinalized = false

    const scheduleFinalizeAfterTerminalStep = () => {
      if (sessionEntry.terminalFinalizeTimer) {
        clearTimeout(sessionEntry.terminalFinalizeTimer)
      }
      sessionEntry.terminalFinalizeTimer = setTimeout(() => {
        finalizeConversation()
      }, TERMINAL_FINALIZE_GRACE_MS)
    }

    const finalizeConversation = async () => {
      if (hasFinalized) return
      hasFinalized = true
      clearSessionTimers(sessionEntry)

      traceMsg.trace.status = 'done'
      traceMsg.trace.collapsed = true

      const fallbackAnswer = () => traceMsg.trace.steps
        .slice()
        .reverse()
        .find(step => step.type === 'ai-step-result' && !isTerminalStepContent(step.fullContent || step.content))

      const fallbackText = fallbackAnswer()?.fullContent ?? fallbackAnswer()?.content ?? ''
      traceMsg.trace.finalResultMarkdown = finalSummaryFromBackend || filterFinalResultContent(fallbackText) || '未收到后端返回的最终整理结果。'

      traceMsg.trace.summary = `已完成 ${traceMsg.trace.steps.length} 步推理`

      // 关闭 SSE 连接，但保留 activeConnections 条目（切换回来时仍可查看）
      const conn = activeConnections.get(sessionId)
      if (conn?.eventSource) {
        conn.eventSource.close()
        conn.eventSource = null
      }

      // 后端会在 Agent 正常完成后统一保存消息，前端只负责展示当前流式结果。
      if (chatId.value === sessionId) {
        connectionStatus.value = 'disconnected'
        activeConnections.delete(sessionId)
      }
      // 如果用户正在查看其他会话，保留 activeConnections 条目
      // loadSession 切换回来时会读取并清理
    }

    const es = chatWithManus(message, sessionId, (data) => {
      const agentEvent = parseAgentStreamEvent(data)
      if (agentEvent) {
        if (agentEvent.type === 'final') {
          finalSummaryFromBackend = filterFinalResultContent(agentEvent.content || '')
          finalizeConversation()
          return
        }
        if (agentEvent.type === 'done') {
          finalizeConversation()
          return
        }
        if (['step', 'status', 'error', 'tool_plan', 'tool_start', 'tool_result', 'artifact', 'chart', 'source'].includes(agentEvent.type)) {
          const parseResult = pushAgentEventStep(traceMsg.trace, agentEvent)
          if (parseResult.terminal) {
            scheduleFinalizeAfterTerminalStep()
          }
          if (agentEvent.type === 'error') {
            traceMsg.trace.status = 'error'
          }
          return
        }
      }

      if (data && data.startsWith('[FINAL_RESULT]')) {
        const encodedSummary = data.slice('[FINAL_RESULT]'.length)
        finalSummaryFromBackend = filterFinalResultContent(decodeFinalSummary(encodedSummary))
        finalizeConversation()
        return
      }

      if (data && data !== '[DONE]') {
        const chunks = parseStepChunks(data)
        let terminalDetected = false
        chunks.forEach(chunk => {
          const parseResult = pushStructuredAgentMessage(traceMsg.trace, chunk)
          terminalDetected = terminalDetected || parseResult.terminal
        })

        if (terminalDetected) {
          scheduleFinalizeAfterTerminalStep()
        }
      }

      if (data === '[DONE]') {
        finalizeConversation()
        return
      }
    }, (error) => {
      const hasTerminalStep = traceMsg.trace.steps.some(step => isTerminalStepContent(step.fullContent || step.content))
      if (hasTerminalStep) {
        finalizeConversation()
        return
      }

      console.error('SSE Error:', error)
      if (error?.status === 401 || error?.status === 429) {
        useAuthStore().openLoginModal()
      }
      if (chatId.value === sessionId) {
        connectionStatus.value = 'error'
      }
      clearSessionTimers(sessionEntry)
      // 关闭连接但保留条目，切换回来时仍可查看已接收的内容
      const conn = activeConnections.get(sessionId)
      if (conn) {
        conn.eventSource = null
      }
      traceMsg.trace.steps.push({
        stepNo: traceMsg.trace.steps.length + 1,
        type: 'ai-step-error',
        fullContent: buildClientErrorMessage(error),
        content: buildClientErrorMessage(error),
        typing: false
      })
      traceMsg.trace.summary = '推理中断'
      traceMsg.trace.status = 'error'
    })

    sessionEntry.eventSource = es
  }

  // Send message (public)
  function sendMessage(message) {
    if (mode.value === 'agent') {
      sendAgentMessage(message)
    } else {
      sendBasicMessage(message)
    }
  }

  function startDraft(nextMode = 'basic', welcomeContent = '') {
    chatId.value = ''
    mode.value = nextMode
    messages.value = []
    connectionStatus.value = 'disconnected'
    typingChain = Promise.resolve()
    if (welcomeContent) {
      addMessage(welcomeContent, false, '')
    }
  }

  function buildClientErrorMessage(error) {
    if (error?.status === 401) return '发送失败：请先登录或重新登录后再试。'
    if (error?.status === 429) return '发送失败：请求过于频繁或免费次数已用完，请稍后再试。'
    if (error?.status) return `发送失败：后端返回 HTTP ${error.status}。`
    return '发送失败：无法连接后端服务，请检查后端是否已启动、前端代理是否指向正确端口。'
  }

  // Reset - close all connections and clear state
  function reset() {
    cleanupAll()
    messages.value = []
    chatId.value = ''
    mode.value = 'basic'
    connectionStatus.value = 'disconnected'
    messageSeed = 0
  }

  // Cleanup - close current session's connection only
  function cleanup() {
    const conn = activeConnections.get(chatId.value)
    if (conn) {
      clearSessionTimers(conn)
      if (conn.eventSource) {
        conn.eventSource.close()
      }
      activeConnections.delete(chatId.value)
    }
    typingChain = Promise.resolve()
  }

  // Close all active connections
  function cleanupAll() {
    activeConnections.forEach((conn) => {
      clearSessionTimers(conn)
      if (conn.eventSource) {
        conn.eventSource.close()
      }
    })
    activeConnections.clear()
    sessionMessagesCache.clear()
    typingChain = Promise.resolve()
  }

  // Add message to current view (for welcome message etc.)
  const addMessage = (content, isUser, type = '', extra = {}) => {
    const msg = createMessage(content, isUser, type, extra)
    messages.value.push(msg)
    return msg
  }

  return {
    messages,
    connectionStatus,
    chatId,
    mode,
    loadSession,
    sendMessage,
    startDraft,
    toggleReasoning,
    reset,
    cleanup,
    cleanupAll,
    addMessage
  }
})
