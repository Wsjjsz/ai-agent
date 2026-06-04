import axios from 'axios'

// API 基础 URL（开发环境通过 Vite proxy 转发到后端）
const API_BASE_URL = '/api'
const AUTH_TOKEN_KEY = 'ai-agent-token'
const AUTH_USER_KEY = 'ai-agent-user'
const GUEST_ID_KEY = 'ai-agent-guest-id'

// 创建axios实例
const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  } else {
    config.headers = config.headers || {}
    const guestId = getOrCreateGuestId()
    config.headers['X-Guest-Id'] = guestId
    config.headers['X-Device-Id'] = guestId
  }
  return config
})

request.interceptors.response.use(
  response => response,
  error => {
    if (error?.response?.status === 401 && localStorage.getItem(AUTH_TOKEN_KEY)) {
      localStorage.removeItem(AUTH_TOKEN_KEY)
      localStorage.removeItem(AUTH_USER_KEY)
    }
    return Promise.reject(error)
  }
)

// 封装 SSE 连接（支持 POST 请求）
export const connectSSE = (url, params, onMessage, onError) => {
  const controller = new AbortController()
  const signal = controller.signal
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  const guestId = getOrCreateGuestId()

  fetch(`${API_BASE_URL}${url}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : { 'X-Guest-Id': guestId })
    },
    body: JSON.stringify(params),
    signal
  })
    .then(response => {
      if (!response.ok) {
        const error = new Error(`HTTP error! status: ${response.status}`)
        error.status = response.status
        throw error
      }
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      function read() {
        reader.read().then(({ done, value }) => {
          if (done) {
            if (onMessage) onMessage('[DONE]')
            return
          }

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''

          for (const line of lines) {
            if (line.startsWith('data:')) {
              const data = line.slice(5).trim()
              if (data === '[DONE]') {
                if (onMessage) onMessage('[DONE]')
              } else if (data) {
                if (onMessage) onMessage(data)
              }
            } else if (line.trim()) {
              // 处理非 SSE 格式的响应
              if (onMessage) onMessage(line.trim())
            }
          }

          read()
        }).catch(error => {
          if (error.name !== 'AbortError') {
            if (onError) onError(error)
          }
        })
      }

      read()
    })
    .catch(error => {
      if (error.name !== 'AbortError') {
        if (onError) onError(error)
      }
    })

  // 返回一个对象，包含 close 方法以便取消请求
  return {
    close: () => controller.abort()
  }
}

// AI金牌理财顾问聊天
export const chatWithFinanceApp = (message, chatId, onMessage, onError) => {
  return connectSSE('/ai/finance_app/chat/sse', { message, chatId }, onMessage, onError)
}

// AI超级智能体聊天
export const chatWithManus = (message, chatId, onMessage, onError) => {
  return connectSSE('/ai/manus/chat', { message, chatId }, onMessage, onError)
}

// 获取 Manus 最终结果文件内容
export const getManusResultFile = async (fileName) => {
  const resp = await request.get('/ai/manus/result', {
    params: { fileName },
    responseType: 'text'
  })
  return resp.data
}

// 获取 Manus 最新结果文件内容（兜底）
export const getLatestManusResultFile = async () => {
  const resp = await request.get('/ai/manus/result', {
    responseType: 'text'
  })
  return resp.data
}

export const getManusFileAccessUrl = async (filePath, disposition = 'preview') => {
  const resp = await request.get('/ai/manus/file/sign', {
    params: { path: filePath, disposition }
  })
  return resp.data?.url || ''
}

// 下载 Manus 生成的文件（支持 PDF 等二进制格式）
export const downloadManusFile = async (filePath) => {
  const url = await getManusFileAccessUrl(filePath, 'download')
  const a = document.createElement('a')
  a.href = url
  a.download = ''
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

// 预览 Manus 生成的文件（PDF/HTML 等）
export const previewManusFile = async (filePath) => {
  const url = await getManusFileAccessUrl(filePath, 'preview')
  window.open(url, '_blank', 'noopener,noreferrer')
}

// 获取 Manus 生成的文件列表
export const getManusFiles = async () => {
  const resp = await request.get('/ai/manus/files')
  return resp.data
}


// 获取会话列表
export const getSessions = async () => {
  const resp = await request.get('/session/list')
  return resp.data
}

// 创建新会话
export const createSession = async (title, mode) => {
  const resp = await request.post('/session/create', { title, mode })
  return resp.data
}

// 删除会话
export const deleteSession = async (id) => {
  const resp = await request.delete(`/session/${id}`)
  return resp.data
}

// 重命名会话
export const renameSession = async (id, title) => {
  const resp = await request.put(`/session/${id}/rename`, { title })
  return resp.data
}

// 置顶/取消置顶会话
export const pinSession = async (id, pinned) => {
  const resp = await request.put(`/session/${id}/pin`, { pinned })
  return resp.data
}

// 获取会话消息记录
export const getSessionMessages = async (id) => {
  const resp = await request.get(`/session/${id}/messages`)
  return resp.data
}

// 保存消息到会话
export const saveMessage = async (sessionId, role, content) => {
  const resp = await request.post(`/session/${sessionId}/messages`, { role, content })
  return resp.data
}

// 获取热点新闻
export const getHotNews = async () => {
  const resp = await request.get('/hotnews/list', { timeout: 30000 })
  return resp.data
}

// 获取图片代理 URL
export const getImageProxyUrl = (imageUrl) => {
  if (!imageUrl) return ''
  return `${API_BASE_URL}/hotnews/image?url=${encodeURIComponent(imageUrl)}`
}

// 用户认证
export const login = async (username, password, countryCode = '+86') => {
  const resp = await request.post('/auth/login', { username, countryCode, password, guestId: getOrCreateGuestId() })
  return resp.data
}

export const sendSmsCode = async (phone, countryCode = '+86') => {
  const resp = await request.post('/auth/sms/send', { phone, countryCode })
  return resp.data
}

export const loginBySms = async (phone, countryCode = '+86', code) => {
  const resp = await request.post('/auth/sms/login', { phone, countryCode, code, guestId: getOrCreateGuestId() })
  return resp.data
}

export const getCurrentUser = async () => {
  const resp = await request.get('/auth/me')
  return resp.data
}

export const updateProfile = async (nickname, avatarUrl) => {
  const resp = await request.put('/auth/me', { nickname, avatarUrl })
  return resp.data
}

export const uploadAvatar = async (file) => {
  const formData = new FormData()
  formData.append('file', file)
  const resp = await request.post('/auth/avatar', formData)
  return resp.data
}

export const setPassword = async (password) => {
  const resp = await request.post('/auth/password', { password })
  return resp.data
}

export const setAuthStorage = (token, user) => {
  if (token) {
    localStorage.setItem(AUTH_TOKEN_KEY, token)
  } else {
    localStorage.removeItem(AUTH_TOKEN_KEY)
  }
  if (user) {
    localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user))
  } else {
    localStorage.removeItem(AUTH_USER_KEY)
  }
}

export const clearAuthStorage = () => setAuthStorage('', null)

export const getStoredAuthUser = () => {
  const raw = localStorage.getItem(AUTH_USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export const getStoredAuthToken = () => localStorage.getItem(AUTH_TOKEN_KEY) || ''

export const getOrCreateGuestId = () => {
  let guestId = localStorage.getItem(GUEST_ID_KEY)
  if (!guestId) {
    guestId = typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`
    localStorage.setItem(GUEST_ID_KEY, guestId)
  }
  return guestId
}

export default {
  getSessions,
  createSession,
  deleteSession,
  renameSession,
  pinSession,
  getSessionMessages,
  chatWithFinanceApp,
  chatWithManus,
  getManusResultFile,
  getLatestManusResultFile,
  downloadManusFile,
  previewManusFile,
  getManusFileAccessUrl,
  getManusFiles,
  login,
  sendSmsCode,
  loginBySms,
  getCurrentUser,
  updateProfile,
  uploadAvatar,
  setPassword,
  setAuthStorage,
  clearAuthStorage,
  getStoredAuthUser,
  getStoredAuthToken,
  getOrCreateGuestId
}
