import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getSessions, createSession as apiCreateSession, deleteSession as apiDeleteSession, renameSession as apiRenameSession, pinSession as apiPinSession } from '@/api'

export const useSessionStore = defineStore('session', () => {
  // State
  const sessions = ref([])
  const loading = ref(false)

  // Getters
  const sortedSessions = computed(() => {
    return [...sessions.value].sort((a, b) => {
      // 置顶优先
      if (a.pinned && !b.pinned) return -1
      if (!a.pinned && b.pinned) return 1
      return new Date(b.update_time || b.create_time) - new Date(a.update_time || a.create_time)
    })
  })

  const getSessionById = computed(() => {
    return (id) => sessions.value.find(s => s.id === id)
  })

  // Actions
  async function fetchSessions() {
    loading.value = true
    try {
      const list = await getSessions()
      sessions.value = list || []
    } catch (error) {
      console.error('加载会话列表失败:', error)
    } finally {
      loading.value = false
    }
  }

  function resetSessions() {
    sessions.value = []
    loading.value = false
  }

  async function createSession(title, mode) {
    try {
      const session = await apiCreateSession(title, mode)
      if (session) {
        const now = new Date().toISOString()
        session.create_time = session.create_time || now
        session.update_time = session.update_time || now
        const existingIndex = sessions.value.findIndex(s => s.id === session.id)
        if (existingIndex >= 0) {
          sessions.value.splice(existingIndex, 1)
        }
        sessions.value.unshift(session)
      }
      return session
    } catch (error) {
      console.error('创建会话失败:', error)
      throw error
    }
  }

  async function deleteSession(id) {
    try {
      await apiDeleteSession(id)
      sessions.value = sessions.value.filter(s => s.id !== id)
    } catch (error) {
      console.error('删除会话失败:', error)
      throw error
    }
  }

  async function renameSession(id, title) {
    try {
      await apiRenameSession(id, title)
      const session = sessions.value.find(s => s.id === id)
      if (session) {
        session.title = title
      }
    } catch (error) {
      console.error('重命名失败:', error)
      throw error
    }
  }

  async function pinSession(id, pinned) {
    try {
      await apiPinSession(id, pinned)
      const session = sessions.value.find(s => s.id === id)
      if (session) {
        session.pinned = pinned
        if (pinned) {
          session.update_time = new Date().toISOString()
        }
      }
    } catch (error) {
      console.error('置顶失败:', error)
      throw error
    }
  }

  return {
    sessions,
    loading,
    sortedSessions,
    getSessionById,
    fetchSessions,
    resetSessions,
    createSession,
    deleteSession,
    renameSession,
    pinSession
  }
})
