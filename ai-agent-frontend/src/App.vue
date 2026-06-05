<template>
  <router-view v-if="isLoginPage" />
  <div v-else class="app-layout">
    <aside class="sidebar" :class="{ 'collapsed': isSidebarCollapsed }">
      <div class="sidebar-header">
        <div class="logo-area" v-if="!isSidebarCollapsed">
          <img src="/favicon.svg" alt="logo" class="logo-icon-img" />
          <span class="logo-text">AI 金融智能体平台</span>
        </div>
        <button class="toggle-btn" @click="toggleSidebar" :title="isSidebarCollapsed ? '展开侧边栏' : '收起侧边栏'">
          <svg v-if="isSidebarCollapsed" viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><line x1="9" y1="3" x2="9" y2="21"></line></svg>
          <svg v-else viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><line x1="15" y1="3" x2="15" y2="21"></line></svg>
        </button>
      </div>

      <div class="sidebar-actions" v-if="!isSidebarCollapsed">
        <button class="new-chat-btn" @click="newChat">
          <span class="plus-icon">+</span> 新建对话
        </button>
      </div>
      <div class="sidebar-actions-collapsed" v-else>
        <button class="new-chat-btn-mini" @click="newChat" title="新建对话">+</button>
      </div>

      <nav class="sidebar-nav history-list" v-if="!isSidebarCollapsed">
        <div v-if="sessionStore.sessions.length === 0" class="empty-sessions">暂无历史记录</div>

        <template v-for="group in groupedSessions" :key="group.label">
          <div class="nav-group" v-if="group.sessions.length > 0">
            <div class="nav-title">{{ group.label }}</div>
            <div
              v-for="session in group.sessions"
              :key="session.id"
              class="nav-item"
              :class="{ 'active': isActiveSession(session.id), 'pinned': session.pinned }"
              @click="editingSessionId !== session.id && goToSession(session)"
            >
              <span class="nav-icon">
                <!-- 智能体：机器人头像 -->
                <svg v-if="session.mode === 'agent'" viewBox="0 0 24 24" fill="none" class="nav-svg">
                  <rect x="3" y="5" width="18" height="14" rx="4" fill="#7c3aed"/>
                  <circle cx="9" cy="11.5" r="2.5" fill="#FFFFFF"/>
                  <circle cx="15" cy="11.5" r="2.5" fill="#FFFFFF"/>
                  <rect x="8" y="16" width="8" height="2" rx="1" fill="#FFFFFF"/>
                </svg>
                <!-- 理财顾问：柱状图 -->
                <svg v-else viewBox="0 0 24 24" fill="#7c3aed" class="nav-svg">
                  <rect x="3" y="14" width="4" height="7" rx="1"/>
                  <rect x="9" y="10" width="4" height="11" rx="1"/>
                  <rect x="15" y="6" width="4" height="15" rx="1"/>
                </svg>
              </span>
              <input
                v-if="editingSessionId === session.id"
                class="rename-input"
                v-model="editingTitle"
                @blur="confirmRename"
                @keydown.enter="confirmRename"
                @keydown.escape="cancelRename"
                @click.stop
              />
              <span v-else class="nav-label">{{ session.title }}</span>
              <button class="more-btn" @click.stop="toggleMenu(session.id, $event)" title="更多操作">⋯</button>
            </div>
          </div>
        </template>
      </nav>

      <!-- 折叠态侧边栏 -->
      <nav class="sidebar-nav history-list" v-else>
        <div
          v-for="session in sessionStore.sortedSessions"
          :key="session.id"
          class="nav-item collapsed"
          :class="{ 'active': isActiveSession(session.id) }"
          @click="goToSession(session)"
        >
          <span class="nav-icon">{{ session.mode === 'agent' ? '🤖' : '💬' }}</span>
        </div>
      </nav>

      <!-- 右键菜单弹窗 -->
      <Teleport to="body">
        <div v-if="menuVisible" class="context-menu-mask" @click="closeMenu"></div>
        <div v-if="menuVisible" class="context-menu" :style="menuStyle">
          <button class="context-menu-item" @click="menuSession && startRename(menuSession)">
            <svg class="ctx-svg" viewBox="0 0 20 20" fill="currentColor" width="15" height="15"><path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z"/></svg>
            重命名
          </button>
          <button class="context-menu-item" @click="handlePin">
            <svg class="ctx-svg" viewBox="0 0 20 20" fill="currentColor" width="15" height="15"><path d="M9.828 3.414a2 2 0 012.828 0l.5.5a1 1 0 001.414-.707V2a1 1 0 00-1-1H6a1 1 0 00-1 1v1.207a1 1 0 001.414.707l.5-.5a2 2 0 012.914 0zM7 7l3-3 3 3m-4 4v5a1 1 0 001 1h2a1 1 0 001-1v-5m-4 0H5a1 1 0 00-1 1v2a1 1 0 001 1h10a1 1 0 001-1v-2a1 1 0 00-1-1h-4z"/></svg>
            {{ menuSession?.pinned ? '取消置顶' : '置顶' }}
          </button>
          <div class="context-menu-divider"></div>
          <button class="context-menu-item danger" @click="menuSession && showDeleteConfirm(menuSession)">
            <svg class="ctx-svg" viewBox="0 0 20 20" fill="currentColor" width="15" height="15"><path fill-rule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clip-rule="evenodd"/></svg>
            删除
          </button>
        </div>
      </Teleport>

      <!-- 删除确认弹窗 -->
      <Teleport to="body">
        <Transition name="modal-fade">
          <div v-if="deleteConfirmId" class="modal-mask" @click.self="cancelDelete">
            <div class="modal-box">
              <div class="modal-icon">
                <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="#ef4444" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
              </div>
              <div class="modal-title">删除对话</div>
              <div class="modal-desc">确定要删除「{{ deleteConfirmSession?.title }}」吗？此操作不可撤销。</div>
              <div class="modal-actions">
                <button class="modal-btn cancel" @click="cancelDelete">取消</button>
                <button class="modal-btn danger" @click="confirmDelete">删除</button>
              </div>
            </div>
          </div>
        </Transition>
      </Teleport>

      <!-- 个人信息 / 账号设置弹窗 -->
      <Teleport to="body">
        <Transition name="modal-fade">
          <div v-if="profileDialog" class="modal-mask" @click.self="closeProfileDialog">
            <div class="modal-box profile-modal">
              <template v-if="profileDialog === 'info'">
                <div class="profile-modal-avatar">
                  <UserAvatarFallback
                    :avatar-url="authStore.user?.avatarUrl || ''"
                    :name="authStore.displayName"
                    :seed="authStore.user?.username || authStore.displayName"
                  />
                </div>
                <div class="modal-title">个人信息</div>
                <div class="profile-detail-list">
                  <div><span>昵称</span><strong>{{ authStore.displayName }}</strong></div>
                  <div><span>账号</span><strong>{{ authStore.user?.username || '-' }}</strong></div>
                  <div><span>状态</span><strong>在线</strong></div>
                </div>
                <div class="modal-actions">
                  <button class="modal-btn cancel" @click="closeProfileDialog">关闭</button>
                  <button class="modal-btn primary" @click="openProfileDialog('edit')">编辑资料</button>
                </div>
              </template>

              <template v-else-if="profileDialog === 'edit'">
                <div class="modal-title">编辑资料</div>
                <div class="profile-form">
                  <div class="avatar-edit-block">
                    <UserAvatarFallback
                      :avatar-url="avatarPreviewUrl || authStore.user?.avatarUrl || ''"
                      :name="profileForm.nickname || authStore.displayName"
                      :seed="authStore.user?.username || authStore.displayName"
                    />
                    <label class="avatar-upload-btn">
                      上传头像
                      <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" @change="handleAvatarFileChange" />
                    </label>
                    <small>支持 JPG、PNG、WebP、GIF，最大 2MB</small>
                  </div>
                  <div class="avatar-preset-head">
                    <span>随机头像</span>
                    <button type="button" @click="loadAvatarPresets(true)" :disabled="avatarPresetsLoading">
                      {{ avatarPresetsLoading ? '加载中' : '换一批' }}
                    </button>
                  </div>
                  <div class="avatar-presets" aria-label="随机头像">
                    <div v-if="avatarPresetsLoading && !avatarPresets.length" class="avatar-preset-empty">正在获取随机头像...</div>
                    <div v-else-if="!avatarPresets.length" class="avatar-preset-empty">暂无随机头像，可先上传头像</div>
                    <button
                      v-for="preset in avatarPresets"
                      :key="preset.id"
                      class="avatar-preset-btn"
                      :class="{ active: profileForm.avatarUrl === preset.url && !avatarFile }"
                      type="button"
                      @click="selectPresetAvatar(preset.url)"
                    >
                      <UserAvatarFallback
                        :avatar-url="preset.url"
                        :name="profileForm.nickname || authStore.displayName"
                        :seed="preset.id"
                      />
                      <span>{{ preset.name }}</span>
                    </button>
                  </div>
                  <label>
                    <span>昵称</span>
                    <input v-model.trim="profileForm.nickname" maxlength="32" placeholder="请输入昵称" />
                  </label>
                </div>
                <p
                  class="profile-feedback"
                  :class="{ 'is-error': profileError, 'is-notice': !profileError && profileNotice, 'is-empty': !profileFeedbackText }"
                  aria-live="polite"
                >
                  {{ profileFeedbackText || '占位提示' }}
                </p>
                <div class="modal-actions">
                  <button class="modal-btn cancel" @click="closeProfileDialog">取消</button>
                  <button class="modal-btn primary" @click="saveProfile">保存</button>
                </div>
              </template>

              <template v-else-if="profileDialog === 'password'">
                <div class="modal-title">设置密码</div>
                <div class="modal-desc">验证码登录的账号设置密码后，也可以使用手机号 + 密码登录。</div>
                <div class="profile-form">
                  <label>
                    <span>新密码</span>
                    <input v-model="passwordForm.password" autocomplete="new-password" type="password" maxlength="64" placeholder="至少 6 位" />
                  </label>
                  <label>
                    <span>确认密码</span>
                    <input v-model="passwordForm.confirm" autocomplete="new-password" type="password" maxlength="64" placeholder="再次输入密码" />
                  </label>
                </div>
                <p
                  class="profile-feedback"
                  :class="{ 'is-error': profileError, 'is-notice': !profileError && profileNotice, 'is-empty': !profileFeedbackText }"
                  aria-live="polite"
                >
                  {{ profileFeedbackText || '占位提示' }}
                </p>
                <div class="modal-actions">
                  <button class="modal-btn cancel" @click="closeProfileDialog">取消</button>
                  <button class="modal-btn primary" @click="savePassword">保存</button>
                </div>
              </template>
            </div>
          </div>
        </Transition>
      </Teleport>

      <div class="sidebar-footer">
        <Transition name="user-menu-slide">
          <div
            v-if="userMenuVisible && authStore.isAuthenticated"
            class="user-menu-popover"
            :class="{ collapsed: isSidebarCollapsed }"
            @click.stop
          >
            <button class="user-menu-item" type="button" @click="openProfileDialog('info')">
              <span>个人信息</span>
              <small>查看账号资料</small>
            </button>
            <button class="user-menu-item" type="button" @click="openProfileDialog('edit')">
              <span>编辑资料</span>
              <small>修改头像和昵称</small>
            </button>
            <button class="user-menu-item" type="button" @click="openProfileDialog('password')">
              <span>设置密码</span>
              <small>手机号密码登录</small>
            </button>

            <div class="user-menu-divider"></div>

            <button class="user-menu-item danger" type="button" @click="handleLogout">
              <span>退出登录</span>
              <small>当前页面保留</small>
            </button>
          </div>
        </Transition>

        <button class="user-profile" :class="{ 'collapsed': isSidebarCollapsed }" type="button" @click.stop="toggleUserMenu">
          <UserAvatarFallback
            :avatar-url="authStore.user?.avatarUrl || ''"
            :name="authStore.displayName"
            :seed="authStore.user?.username || authStore.displayName"
          />
          <div class="user-info" v-if="!isSidebarCollapsed">
            <div class="user-name">{{ authStore.displayName }}</div>
            <div class="user-status">{{ authStore.isAuthenticated ? '在线' : `剩余 ${authStore.guestRemaining} 次体验` }}</div>
          </div>
          <span v-if="!isSidebarCollapsed && authStore.isAuthenticated" class="user-menu-more" :class="{ open: userMenuVisible }">
            <span></span>
            <span></span>
            <span></span>
          </span>
        </button>
      </div>
    </aside>

    <main class="main-content">
      <button
        v-if="!authStore.isAuthenticated"
        class="top-login-btn"
        type="button"
        @click="authStore.openLoginModal"
      >
        登录
      </button>
      <router-view />
    </main>
    <AuthModal />
    <Transition name="profile-toast">
      <div
        v-if="profileToast.visible"
        class="profile-toast"
        :class="`is-${profileToast.type}`"
        role="status"
        aria-live="polite"
      >
        <span class="profile-toast-icon" aria-hidden="true">
          <svg v-if="profileToast.type === 'success'" viewBox="0 0 20 20" fill="none">
            <path d="M5 10.4 8.2 13.5 15 6.5" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <svg v-else viewBox="0 0 20 20" fill="none">
            <path d="M10 6.5v4.2M10 13.8h.01" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" />
            <circle cx="10" cy="10" r="7" stroke="currentColor" stroke-width="1.8" />
          </svg>
        </span>
        <span>{{ profileToast.message }}</span>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { uploadAvatar as apiUploadAvatar, getRandomAvatars as apiGetRandomAvatars } from '@/api'
import UserAvatarFallback from '@/components/UserAvatarFallback.vue'
import AuthModal from '@/components/AuthModal.vue'

const router = useRouter()
const route = useRoute()
const sessionStore = useSessionStore()
const chatStore = useChatStore()
const authStore = useAuthStore()
const isSidebarCollapsed = ref(false)
const isLoginPage = computed(() => route.name === 'Login')

const userMenuVisible = ref(false)
const profileDialog = ref('')
const profileError = ref('')
const profileNotice = ref('')
const profileFeedbackText = computed(() => profileError.value || profileNotice.value)
const profileToast = reactive({
  visible: false,
  type: 'success',
  message: ''
})
let profileToastTimer = null
const avatarFile = ref(null)
const avatarPreviewUrl = ref('')
const avatarObjectUrl = ref('')
const avatarPresets = ref([])
const avatarPresetsLoading = ref(false)
const profileForm = reactive({
  nickname: '',
  avatarUrl: ''
})
const passwordForm = reactive({
  password: '',
  confirm: ''
})

// ── 菜单状态 ──
const menuVisible = ref(false)
const menuSessionId = ref('')
const menuStyle = ref({})
const menuSession = computed(() => sessionStore.sessions.find(s => s.id === menuSessionId.value))

const toggleMenu = (id, event) => {
  if (menuVisible.value && menuSessionId.value === id) {
    closeMenu()
    return
  }
  const rect = event.currentTarget.getBoundingClientRect()
  menuStyle.value = {
    top: `${rect.bottom + 4}px`,
    left: `${rect.left}px`
  }
  menuSessionId.value = id
  menuVisible.value = true
}

const closeMenu = () => {
  menuVisible.value = false
  menuSessionId.value = ''
}

// ── 内联重命名 ──
const editingSessionId = ref('')
const editingTitle = ref('')
const editInputRef = ref(null)

const startRename = (session) => {
  closeMenu()
  editingSessionId.value = session.id
  editingTitle.value = session.title
  nextTick(() => {
    const input = document.querySelector('.rename-input')
    if (input) { input.focus(); input.select() }
  })
}

const confirmRename = async () => {
  const id = editingSessionId.value
  const title = editingTitle.value.trim()
  if (!id || !title) {
    editingSessionId.value = ''
    return
  }
  const session = sessionStore.sessions.find(s => s.id === id)
  if (!session || session.title === title) {
    editingSessionId.value = ''
    return
  }
  try {
    await sessionStore.renameSession(id, title)
    editingSessionId.value = ''
  } catch (e) {
    console.error('重命名失败:', e)
  }
}

const cancelRename = () => {
  editingSessionId.value = ''
}

const handlePin = async () => {
  const session = menuSession.value
  closeMenu()
  if (!session) return
  await sessionStore.pinSession(session.id, !session.pinned)
}

// ── 删除确认 UI ──
const deleteConfirmId = ref('')
const deleteConfirmSession = computed(() => sessionStore.sessions.find(s => s.id === deleteConfirmId.value))

const showDeleteConfirm = (session) => {
  closeMenu()
  deleteConfirmId.value = session.id
}

const cancelDelete = () => {
  deleteConfirmId.value = ''
}

const confirmDelete = async () => {
  const id = deleteConfirmId.value
  deleteConfirmId.value = ''
  if (!id) return
  try {
    await sessionStore.deleteSession(id)
    if (route.params.sessionId === id) {
      router.push('/')
    }
  } catch (e) {
    console.error('删除失败:', e)
  }
}

// ── 按时间分组（每个 session 只出现在一个分组） ──
const DAY_MS = 24 * 60 * 60 * 1000
const timeGroups = [
  { label: '今天', test: (diff) => diff < 1 * DAY_MS },
  { label: '7天内', test: (diff) => diff < 7 * DAY_MS },
  { label: '1月内', test: (diff) => diff < 30 * DAY_MS },
  { label: '3月内', test: (diff) => diff < 90 * DAY_MS },
  { label: '6月内', test: (diff) => diff < 180 * DAY_MS },
  { label: '1年内', test: (diff) => diff < 365 * DAY_MS },
  { label: '更早', test: () => true }
]

const groupedSessions = computed(() => {
  const now = Date.now()
  const pinned = []
  const unpinned = []

  for (const s of sessionStore.sortedSessions) {
    if (s.pinned) {
      pinned.push(s)
    } else {
      unpinned.push(s)
    }
  }

  const groups = []

  if (pinned.length > 0) {
    groups.push({ label: '置顶', sessions: pinned })
  }

  // 每个 session 只归入第一个匹配的分组
  const buckets = timeGroups.map(g => ({ label: g.label, sessions: [] }))
  for (const s of unpinned) {
    const diff = now - new Date(s.create_time).getTime()
    for (const bucket of buckets) {
      const g = timeGroups.find(t => t.label === bucket.label)
      if (g.test(diff)) {
        bucket.sessions.push(s)
        break
      }
    }
  }
  for (const bucket of buckets) {
    if (bucket.sessions.length > 0) {
      groups.push(bucket)
    }
  }

  return groups
})

onMounted(() => {
  if (!isLoginPage.value) {
    authStore.fetchMe().catch(() => {})
    sessionStore.fetchSessions()
  }
})

watch(() => route.name, async (name) => {
  if (name !== 'Login') {
    if (authStore.isAuthenticated) {
      await authStore.fetchMe().catch(() => {})
    }
    await sessionStore.fetchSessions()
  }
})

const toggleSidebar = () => {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
}

const newChat = () => {
  const draftMode = ['basic', 'agent'].includes(route.params.mode) ? route.params.mode : 'basic'
  chatStore.startDraft(draftMode)
  router.push('/')
}

const handleLogout = () => {
  closeUserMenu()
  closeProfileDialog()
  chatStore.reset()
  sessionStore.resetSessions()
  authStore.logout()
  sessionStore.fetchSessions()
}

const goToSession = (session) => {
  router.push(`/chat/${session.mode}/${session.id}`)
}

const isActiveSession = (id) => {
  return route.params.sessionId === id
}

const toggleUserMenu = () => {
  if (!authStore.isAuthenticated) {
    authStore.openLoginModal()
    return
  }
  userMenuVisible.value = !userMenuVisible.value
}

const closeUserMenu = () => {
  userMenuVisible.value = false
}

const openProfileDialog = (type) => {
  closeUserMenu()
  profileError.value = ''
  profileNotice.value = ''
  if (type === 'edit') {
    cleanupAvatarPreview()
    avatarFile.value = null
    profileForm.nickname = authStore.user?.nickname || authStore.displayName || ''
    profileForm.avatarUrl = authStore.user?.avatarUrl || ''
    avatarPreviewUrl.value = profileForm.avatarUrl
    loadAvatarPresets(false)
  }
  if (type === 'password') {
    passwordForm.password = ''
    passwordForm.confirm = ''
  }
  profileDialog.value = type
}

const closeProfileDialog = () => {
  profileDialog.value = ''
  profileError.value = ''
  profileNotice.value = ''
  cleanupAvatarPreview()
  avatarFile.value = null
  avatarPreviewUrl.value = ''
}

const showProfileToast = (message, type = 'success') => {
  if (profileToastTimer) {
    clearTimeout(profileToastTimer)
    profileToastTimer = null
  }
  profileToast.message = message
  profileToast.type = type
  profileToast.visible = true
  profileToastTimer = setTimeout(() => {
    profileToast.visible = false
    profileToastTimer = null
  }, 2600)
}

const saveProfile = async () => {
  profileError.value = ''
  profileNotice.value = ''
  if (!profileForm.nickname.trim()) {
    profileError.value = '请输入昵称'
    showProfileToast('请输入昵称后再保存', 'error')
    return
  }
  try {
    let avatarUrl = profileForm.avatarUrl || authStore.user?.avatarUrl || ''
    if (avatarFile.value) {
      const uploadResult = await apiUploadAvatar(avatarFile.value)
      avatarUrl = uploadResult?.avatarUrl || avatarUrl
    }
    await authStore.updateProfile(profileForm.nickname.trim(), avatarUrl)
    closeProfileDialog()
    showProfileToast('个人信息已保存', 'success')
  } catch (error) {
    const message = error?.response?.data?.error || error?.message || '保存失败，请稍后重试'
    profileError.value = message
    showProfileToast(message, 'error')
  }
}

const loadAvatarPresets = async (force = false) => {
  if (avatarPresetsLoading.value) return
  if (!force && avatarPresets.value.length) return
  avatarPresetsLoading.value = true
  try {
    const result = await apiGetRandomAvatars(6, force)
    avatarPresets.value = Array.isArray(result?.avatars) ? result.avatars.filter(item => item?.url) : []
  } catch (error) {
    if (force) {
      showProfileToast('随机头像获取失败，请稍后重试', 'error')
    }
  } finally {
    avatarPresetsLoading.value = false
  }
}

const selectPresetAvatar = (avatarUrl) => {
  if (!avatarUrl) return
  cleanupAvatarPreview()
  avatarFile.value = null
  profileForm.avatarUrl = avatarUrl
  avatarPreviewUrl.value = profileForm.avatarUrl
}

const handleAvatarFileChange = (event) => {
  profileError.value = ''
  const file = event.target.files?.[0]
  if (!file) return
  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
  if (!allowedTypes.includes(file.type)) {
    profileError.value = '仅支持 JPG、PNG、WebP 或 GIF 图片'
    event.target.value = ''
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    profileError.value = '头像图片不能超过 2MB'
    event.target.value = ''
    return
  }
  cleanupAvatarPreview()
  avatarFile.value = file
  avatarObjectUrl.value = URL.createObjectURL(file)
  avatarPreviewUrl.value = avatarObjectUrl.value
}

const cleanupAvatarPreview = () => {
  if (avatarObjectUrl.value) {
    URL.revokeObjectURL(avatarObjectUrl.value)
    avatarObjectUrl.value = ''
  }
}

const savePassword = async () => {
  profileError.value = ''
  profileNotice.value = ''
  if (passwordForm.password.length < 6) {
    profileError.value = '密码至少 6 位'
    showProfileToast('密码至少 6 位', 'error')
    return
  }
  if (passwordForm.password !== passwordForm.confirm) {
    profileError.value = '两次输入的密码不一致'
    showProfileToast('两次输入的密码不一致', 'error')
    return
  }
  try {
    await authStore.setPassword(passwordForm.password)
    closeProfileDialog()
    showProfileToast('密码已设置', 'success')
  } catch (error) {
    const message = error?.response?.data?.error || error?.message || '设置失败，请稍后重试'
    profileError.value = message
    showProfileToast(message, 'error')
  }
}

// 点击页面其他地方关闭菜单
const handleGlobalClick = (e) => {
  if (menuVisible.value && !e.target.closest('.context-menu') && !e.target.closest('.more-btn')) {
    closeMenu()
  }
  if (userMenuVisible.value && !e.target.closest('.user-menu-popover') && !e.target.closest('.user-profile')) {
    closeUserMenu()
  }
}
onMounted(() => document.addEventListener('click', handleGlobalClick))
onBeforeUnmount(() => document.removeEventListener('click', handleGlobalClick))
onBeforeUnmount(() => cleanupAvatarPreview())
onBeforeUnmount(() => {
  if (profileToastTimer) {
    clearTimeout(profileToastTimer)
  }
})

watch(() => authStore.isAuthenticated, async (isAuthenticated) => {
  if (isAuthenticated) {
    await authStore.fetchMe().catch(() => {})
    await sessionStore.fetchSessions().catch(() => {})
  } else {
    closeUserMenu()
    closeProfileDialog()
  }
})
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  width: 100vw;
  background-color: var(--bg-surface);
  overflow: hidden;
}

.sidebar {
  width: 260px;
  background-color: var(--bg-surface-soft);
  border-right: 1px solid var(--line-soft);
  display: flex;
  flex-direction: column;
  transition: width 0.3s var(--ease-smooth);
  flex-shrink: 0;
  z-index: 10;
}

.sidebar.collapsed {
  width: 68px;
}

.sidebar-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
}

.sidebar.collapsed .sidebar-header {
  justify-content: center;
  padding: 0;
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
  white-space: nowrap;
}

.logo-icon-img {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  flex-shrink: 0;
}

.logo-text {
  font-weight: 600;
  font-size: 1.1rem;
  color: var(--text-strong);
}

.toggle-btn {
  color: var(--text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  transition: background-color 0.2s, color 0.2s;
}

.toggle-btn:hover {
  background-color: var(--line-soft);
  color: var(--text-strong);
}

.sidebar-actions {
  padding: 10px 16px;
}

.sidebar-actions-collapsed {
  padding: 10px 0;
  display: flex;
  justify-content: center;
}

.new-chat-btn {
  width: 100%;
  padding: 10px 14px;
  background-color: var(--brand);
  color: white;
  border-radius: var(--radius-md);
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: opacity 0.2s;
  box-shadow: var(--shadow-soft);
}

.new-chat-btn:hover {
  opacity: 0.9;
}

.new-chat-btn-mini {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background-color: var(--brand);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
  transition: opacity 0.2s;
}

.nav-title {
  font-size: 0.72rem;
  color: var(--text-muted);
  padding: 12px 12px 4px;
  font-weight: 600;
  letter-spacing: 0.03em;
  text-transform: uppercase;
}

.sidebar-nav {
  flex: 1;
  padding: 0 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
}

.nav-group {
  margin-bottom: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  color: var(--text-main);
  text-decoration: none;
  font-size: 0.9rem;
  transition: background-color 0.2s, color 0.2s;
  white-space: nowrap;
  overflow: hidden;
  cursor: pointer;
}

.nav-item:hover {
  background-color: var(--line-soft);
  color: var(--text-strong);
}

.nav-item.active {
  background-color: #f5f3ff;
  color: #5b21b6;
  font-weight: 600;
  border-left: 3px solid var(--brand);
  padding-left: 9px;
}

.nav-item.pinned .nav-label {
  font-weight: 600;
}

.empty-sessions {
  font-size: 0.8rem;
  color: var(--text-muted);
  text-align: center;
  padding: 20px 0;
}

.nav-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-svg {
  width: 20px;
  height: 20px;
  display: block;
}

.nav-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

.more-btn {
  opacity: 0;
  background: transparent;
  border: none;
  font-size: 1.1rem;
  color: var(--text-muted);
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  letter-spacing: 2px;
  transition: opacity 0.15s, background-color 0.15s;
  flex-shrink: 0;
}

.nav-item:hover .more-btn {
  opacity: 1;
}

.more-btn:hover {
  background-color: rgba(0,0,0,0.06);
  color: var(--text-strong);
}

.nav-item.collapsed {
  justify-content: center;
  padding: 10px 0;
}

/* ── Context Menu ── */
.context-menu-mask {
  position: fixed;
  inset: 0;
  z-index: 999;
}

.context-menu {
  position: fixed;
  z-index: 1000;
  background: var(--bg-surface);
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  box-shadow: 0 8px 30px rgba(0,0,0,0.12);
  min-width: 140px;
  padding: 4px;
  animation: menuFadeIn 0.12s ease-out;
}

@keyframes menuFadeIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

.context-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 0.85rem;
  color: var(--text-main);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: background-color 0.12s;
  text-align: left;
}

.context-menu-item:hover {
  background-color: var(--bg-surface-soft);
  color: var(--text-strong);
}

.context-menu-item.danger {
  color: #dc2626;
}

.context-menu-item.danger:hover {
  background-color: #fef2f2;
}

.ctx-icon {
  font-size: 0.9rem;
  flex-shrink: 0;
}

.ctx-svg {
  flex-shrink: 0;
  color: var(--text-muted);
}

.context-menu-item:hover .ctx-svg {
  color: inherit;
}

.context-menu-item.danger .ctx-svg {
  color: #fca5a5;
}

/* ── Inline Rename ── */
.rename-input {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--brand);
  border-radius: 4px;
  padding: 2px 6px;
  font-size: 0.85rem;
  color: var(--text-strong);
  background: var(--bg-surface);
  outline: none;
  box-shadow: 0 0 0 2px rgba(124, 58, 237, 0.15);
}

/* ── Delete Confirmation Modal ── */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1001;
}

.modal-box {
  background: var(--bg-surface);
  border-radius: 16px;
  padding: 28px 24px 20px;
  width: 320px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  text-align: center;
}

.modal-icon {
  margin-bottom: 12px;
}

.modal-title {
  font-size: 1rem;
  font-weight: 700;
  color: var(--text-strong);
  margin-bottom: 8px;
}

.modal-desc {
  font-size: 0.85rem;
  color: var(--text-muted);
  line-height: 1.5;
  margin-bottom: 20px;
}

.modal-actions {
  display: flex;
  gap: 10px;
}

.modal-btn {
  flex: 1;
  padding: 9px 0;
  border-radius: 10px;
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}

.modal-btn.cancel {
  background: var(--bg-surface-soft);
  border: 1px solid var(--line-soft);
  color: var(--text-main);
}

.modal-btn.cancel:hover {
  background: var(--line-soft);
}

.modal-btn.danger {
  background: #dc2626;
  border: 1px solid #dc2626;
  color: white;
}

.modal-btn.danger:hover {
  background: #b91c1c;
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.2s ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

.modal-fade-enter-active .modal-box {
  animation: modalSlideIn 0.25s ease;
}

@keyframes modalSlideIn {
  from { transform: translateY(-12px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.context-menu-divider {
  height: 1px;
  background: var(--line-soft);
  margin: 4px 0;
}

/* ── Footer ── */
.sidebar-footer {
  position: relative;
  padding: 14px 16px;
  border-top: 0;
}

.sidebar.collapsed .sidebar-footer {
  padding: 16px 0;
  display: flex;
  justify-content: center;
}

.user-profile {
  width: 100%;
  border: 0;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 12px;
  text-align: left;
  cursor: pointer;
  border-radius: 10px;
  padding: 2px 0;
  transition: background 0.15s ease;
}

.user-profile:hover {
  background: transparent;
}

.user-profile.collapsed {
  justify-content: center;
  width: auto;
}

.user-info {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
}

.user-name {
  font-weight: 500;
  color: var(--text-strong);
  font-size: 0.95rem;
}

.user-status {
  font-size: 0.8rem;
  color: var(--text-muted);
}

.user-menu-more {
  margin-left: auto;
  width: 24px;
  height: 24px;
  border: 0;
  background: transparent;
  color: var(--text-muted);
  border-radius: 7px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease;
}

.user-menu-more span {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: currentColor;
}

.user-menu-more.open {
  color: var(--brand);
  background: #f5f3ff;
}

.user-menu-popover {
  position: absolute;
  left: 22px;
  right: 22px;
  bottom: calc(100% + 8px);
  z-index: 60;
  padding: 6px;
  border: 1px solid var(--line-soft);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.16);
  transform-origin: bottom center;
}

.user-menu-popover.collapsed {
  left: 10px;
  right: auto;
  width: 260px;
}

.user-menu-slide-enter-active,
.user-menu-slide-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.user-menu-slide-enter-from,
.user-menu-slide-leave-to {
  opacity: 0;
  transform: translateY(12px);
}

.user-menu-slide-enter-to,
.user-menu-slide-leave-from {
  opacity: 1;
  transform: translateY(0);
}

.profile-detail-list span,
.profile-form span {
  display: block;
  color: var(--text-muted);
  font-size: 0.74rem;
}

.user-menu-divider {
  height: 1px;
  margin: 4px 4px;
  background: var(--line-soft);
}

.user-menu-item {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr;
  gap: 2px;
  padding: 7px 9px;
  border-radius: 9px;
  text-align: left;
  transition: background 0.15s ease;
}

.user-menu-item:hover {
  background: #f5f3ff;
}

.user-menu-item span {
  color: var(--text-strong);
  font-size: 0.84rem;
  font-weight: 700;
}

.user-menu-item small {
  color: var(--text-muted);
  font-size: 0.72rem;
}

.user-menu-item.danger span {
  color: #dc2626;
}

.user-menu-item.danger:hover {
  background: #fff5f5;
}

.logout-btn {
  margin-left: auto;
  width: 32px;
  height: 32px;
  border: 1px solid var(--line-soft);
  background: #fff;
  color: var(--text-muted);
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.logout-btn:hover {
  color: #dc2626;
  border-color: #fecaca;
  background: #fff5f5;
}

.profile-modal {
  width: 360px;
  text-align: left;
}

.profile-modal .modal-title,
.profile-modal .modal-desc {
  text-align: center;
}

.profile-modal-avatar {
  display: flex;
  justify-content: center;
  margin-bottom: 12px;
}

.profile-detail-list {
  display: grid;
  gap: 8px;
  margin: 12px 0 18px;
}

.profile-detail-list div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8fafc;
}

.profile-detail-list strong {
  min-width: 0;
  color: var(--text-strong);
  font-size: 0.84rem;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-form {
  display: grid;
  gap: 12px;
  margin: 14px 0;
}

.avatar-edit-block {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
}

.avatar-edit-block :deep(.user-avatar-badge) {
  width: 52px;
  height: 52px;
}

.avatar-upload-btn {
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
  height: 32px;
  padding: 0 14px;
  border-radius: 999px;
  background: #f5f3ff;
  color: var(--brand);
  font-size: 0.82rem;
  font-weight: 800;
  cursor: pointer;
}

.avatar-upload-btn:hover {
  background: #ede9fe;
}

.avatar-upload-btn input {
  display: none;
}

.avatar-edit-block small {
  color: var(--text-muted);
  font-size: 0.72rem;
}

.avatar-preset-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--text-muted);
  font-size: 0.74rem;
}

.avatar-preset-head button {
  border: none;
  background: transparent;
  color: var(--brand);
  font-size: 0.74rem;
  font-weight: 700;
  cursor: pointer;
  padding: 2px 0;
}

.avatar-preset-head button:disabled {
  color: var(--text-muted);
  cursor: wait;
}

.avatar-presets {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.avatar-preset-empty {
  grid-column: 1 / -1;
  min-height: 42px;
  display: grid;
  place-items: center;
  border: 1px dashed var(--line-soft);
  border-radius: 12px;
  color: var(--text-muted);
  font-size: 0.74rem;
  background: #fafafa;
}

.avatar-preset-btn {
  display: grid;
  justify-items: center;
  gap: 5px;
  padding: 8px 4px;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: #fff;
  color: var(--text-muted);
  font-size: 0.74rem;
  transition: border-color 0.15s ease, background 0.15s ease, color 0.15s ease;
}

.avatar-preset-btn :deep(.user-avatar-badge) {
  width: 34px;
  height: 34px;
}

.avatar-preset-btn:hover,
.avatar-preset-btn.active {
  border-color: var(--brand);
  background: #f5f3ff;
  color: var(--brand);
}

.avatar-preset-btn span {
  display: block;
  color: inherit;
  font-size: 0.74rem;
}

.profile-form label {
  display: grid;
  gap: 6px;
}

.profile-form input {
  height: 40px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  padding: 0 12px;
  outline: none;
  color: var(--text-strong);
}

.profile-form input:focus {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px rgba(124, 58, 237, 0.14);
}

.profile-feedback {
  height: 18px;
  margin: -2px 0 12px;
  font-size: 0.82rem;
  line-height: 18px;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: transparent;
}

.profile-feedback.is-empty {
  visibility: hidden;
}

.profile-feedback.is-error {
  color: #dc2626;
}

.profile-feedback.is-notice {
  color: #047857;
}

.profile-toast {
  position: fixed;
  top: 50%;
  left: 50%;
  z-index: 1200;
  min-width: 210px;
  max-width: min(360px, calc(100vw - 32px));
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  border: 1px solid rgba(124, 58, 237, 0.2);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.96);
  color: var(--text-strong);
  box-shadow: 0 18px 48px rgba(76, 29, 149, 0.18);
  backdrop-filter: blur(14px);
  font-size: 0.88rem;
  font-weight: 700;
  transform: translate(-50%, -50%);
}

.profile-toast.is-success {
  border-color: rgba(124, 58, 237, 0.24);
}

.profile-toast.is-error {
  border-color: rgba(220, 38, 38, 0.22);
  box-shadow: 0 18px 48px rgba(127, 29, 29, 0.14);
}

.profile-toast-icon {
  width: 24px;
  height: 24px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  background: #f5f3ff;
  color: var(--brand);
}

.profile-toast.is-error .profile-toast-icon {
  background: #fff1f2;
  color: #dc2626;
}

.profile-toast-icon svg {
  width: 16px;
  height: 16px;
}

.profile-toast-enter-active,
.profile-toast-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.profile-toast-enter-from,
.profile-toast-leave-to {
  opacity: 0;
  transform: translate(-50%, calc(-50% - 8px)) scale(0.98);
}

.modal-btn.primary {
  background: var(--brand);
  border: 1px solid var(--brand);
  color: #fff;
}

.modal-btn.primary:hover {
  background: var(--brand-strong);
}

.main-content {
  flex: 1;
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  position: relative;
  background-color: var(--bg-surface);
}

.top-login-btn {
  position: absolute;
  top: 16px;
  right: 22px;
  z-index: 20;
  height: 34px;
  padding: 0 16px;
  border-radius: 999px;
  background: var(--brand);
  color: #fff;
  font-weight: 700;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.14);
}

.top-login-btn:hover {
  background: var(--brand-strong);
}

@media (max-width: 768px) {
  .sidebar {
    position: absolute;
    transform: translateX(-100%);
  }
  .sidebar.collapsed {
    transform: translateX(0);
    width: 68px;
  }
}
</style>
