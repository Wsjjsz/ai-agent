import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  clearAuthStorage,
  getCurrentUser,
  getOrCreateGuestId,
  getStoredAuthToken,
  getStoredAuthUser,
  login as apiLogin,
  loginBySms as apiLoginBySms,
  sendSmsCode as apiSendSmsCode,
  setPassword as apiSetPassword,
  setAuthStorage,
  updateProfile as apiUpdateProfile,
  uploadAvatar as apiUploadAvatar
} from '@/api'

const GUEST_USAGE_KEY = 'ai-agent-guest-usage-count'
const FREE_GUEST_LIMIT = 3

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getStoredAuthToken())
  const user = ref(getStoredAuthUser())
  const loading = ref(false)
  const loginModalVisible = ref(false)
  const guestUsageCount = ref(Number(localStorage.getItem(GUEST_USAGE_KEY) || '0'))

  const isAuthenticated = computed(() => Boolean(token.value))
  const displayName = computed(() => user.value?.nickname || user.value?.username || '访客')
  const guestRemaining = computed(() => Math.max(0, FREE_GUEST_LIMIT - guestUsageCount.value))

  const setSession = (payload) => {
    token.value = payload?.token || ''
    user.value = payload?.user || null
    setAuthStorage(token.value, user.value)
    loginModalVisible.value = false
  }

  async function login(username, password, countryCode = '+86') {
    loading.value = true
    try {
      const payload = await apiLogin(username, password, countryCode)
      setSession(payload)
      return payload
    } finally {
      loading.value = false
    }
  }

  async function sendSmsCode(phone, countryCode = '+86') {
    return apiSendSmsCode(phone, countryCode)
  }

  async function loginBySms(phone, countryCode, code) {
    loading.value = true
    try {
      const payload = await apiLoginBySms(phone, countryCode, code)
      setSession(payload)
      return payload
    } finally {
      loading.value = false
    }
  }

  async function fetchMe() {
    if (!token.value) return null
    loading.value = true
    try {
      user.value = await getCurrentUser()
      setAuthStorage(token.value, user.value)
      return user.value
    } catch (error) {
      logout()
      throw error
    } finally {
      loading.value = false
    }
  }

  async function updateProfile(nickname, avatarUrl) {
    user.value = await apiUpdateProfile(nickname, avatarUrl)
    setAuthStorage(token.value, user.value)
    return user.value
  }

  async function uploadAvatar(file) {
    return apiUploadAvatar(file)
  }

  async function setPassword(password) {
    return apiSetPassword(password)
  }

  function logout() {
    token.value = ''
    user.value = null
    clearAuthStorage()
  }

  function canUseGuestQuota() {
    if (isAuthenticated.value) {
      return true
    }
    getOrCreateGuestId()
    if (guestUsageCount.value >= FREE_GUEST_LIMIT) {
      openLoginModal()
      return false
    }
    guestUsageCount.value += 1
    localStorage.setItem(GUEST_USAGE_KEY, String(guestUsageCount.value))
    return true
  }

  function canStartGuestConversation() {
    if (isAuthenticated.value) {
      return true
    }
    getOrCreateGuestId()
    if (guestUsageCount.value >= FREE_GUEST_LIMIT) {
      openLoginModal()
      return false
    }
    return true
  }

  function openLoginModal() {
    loginModalVisible.value = true
  }

  function closeLoginModal() {
    loginModalVisible.value = false
  }

  return {
    token,
    user,
    loading,
    isAuthenticated,
    displayName,
    loginModalVisible,
    guestUsageCount,
    guestRemaining,
    login,
    sendSmsCode,
    loginBySms,
    fetchMe,
    updateProfile,
    uploadAvatar,
    setPassword,
    logout,
    canUseGuestQuota,
    canStartGuestConversation,
    openLoginModal,
    closeLoginModal
  }
})
