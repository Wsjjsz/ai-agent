<template>
  <Transition name="auth-fade">
    <div v-if="authStore.loginModalVisible" class="auth-overlay" @click.self="authStore.closeLoginModal">
      <section class="auth-dialog" role="dialog" aria-modal="true" aria-label="登录">
        <button class="auth-close" type="button" aria-label="关闭登录弹窗" @click="authStore.closeLoginModal">×</button>

        <div class="auth-main">
          <div class="auth-form-panel">
            <div class="auth-title">登录</div>
            <div class="login-mode-tabs" aria-label="登录方式">
              <button type="button" :class="{ active: loginMode === 'sms' }" @click="switchLoginMode('sms')">验证码登录</button>
              <span aria-hidden="true">|</span>
              <button type="button" :class="{ active: loginMode === 'password' }" @click="switchLoginMode('password')">密码登录</button>
            </div>

            <form v-if="loginMode === 'sms'" class="auth-form" @submit.prevent="submitSms">
              <div class="soft-input phone-input">
                <label class="country-code">
                  <select v-model="smsForm.countryCode" aria-label="国家区号" @change="resetFeedback">
                    <option v-for="option in countryOptions" :key="option.code" :value="option.code">
                      {{ option.label }}
                    </option>
                  </select>
                  <span class="chevron" aria-hidden="true"></span>
                </label>
                <input v-model.trim="smsForm.phone" autocomplete="tel" inputmode="tel" placeholder="请输入手机号" />
              </div>

              <div class="soft-input code-input">
                <input v-model.trim="smsForm.code" autocomplete="one-time-code" inputmode="numeric" maxlength="6" placeholder="请输入验证码" />
                <button class="code-btn" type="button" :disabled="smsSending || countdown > 0" @click="sendCode">
                  {{ countdown > 0 ? `${countdown}s` : smsSending ? '发送中' : '获取验证码' }}
                </button>
              </div>

              <p
                class="auth-feedback"
                :class="{ 'is-error': error, 'is-notice': !error && notice, 'is-empty': !authFeedbackText }"
                aria-live="polite"
              >
                {{ authFeedbackText || '占位提示' }}
              </p>

              <button class="auth-submit" type="submit" :disabled="authStore.loading || !canSubmitSms">
                {{ authStore.loading ? '登录中...' : '登录' }}
              </button>
            </form>

            <form v-else class="auth-form" @submit.prevent="submitPassword">
              <div class="soft-input phone-input">
                <label class="country-code">
                  <select v-model="accountForm.countryCode" aria-label="国家区号" @change="resetFeedback">
                    <option v-for="option in countryOptions" :key="option.code" :value="option.code">
                      {{ option.label }}
                    </option>
                  </select>
                  <span class="chevron" aria-hidden="true"></span>
                </label>
                <input v-model.trim="accountForm.username" autocomplete="username" inputmode="tel" placeholder="请输入手机号" />
              </div>

              <div class="soft-input password-input">
                <svg class="input-icon" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M7 14a4 4 0 1 1 3.46-2H22v4h-3v-2h-2v2h-2v-2h-4.54A4 4 0 0 1 7 14Zm0-3a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z" fill="currentColor"/>
                </svg>
                <input v-model="accountForm.password" autocomplete="current-password" type="password" placeholder="请输入密码" />
              </div>

              <p
                class="auth-feedback"
                :class="{ 'is-error': error, 'is-empty': !authFeedbackText }"
                aria-live="polite"
              >
                {{ authFeedbackText || '占位提示' }}
              </p>

              <button class="auth-submit" type="submit" :disabled="authStore.loading || !canSubmitPassword">
                {{ authStore.loading ? '登录中...' : '登录' }}
              </button>
            </form>

            <p class="terms-text">
              登录即代表同意 用户协议 和 隐私政策
            </p>
          </div>

          <aside class="qr-panel" aria-label="微信扫码登录">
            <h3>微信扫码登录</h3>
            <div class="qr-box">
              <div class="qr-mock" aria-hidden="true">
                <span class="qr-logo">微</span>
              </div>
              <div class="qr-unavailable">暂未接入</div>
            </div>
            <p>微信开放平台接入后可用。</p>
          </aside>
        </div>
      </section>
    </div>
  </Transition>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useSessionStore } from '@/stores/session'

const authStore = useAuthStore()
const sessionStore = useSessionStore()

const loginMode = ref('sms')
const error = ref('')
const notice = ref('')
const smsSending = ref(false)
const countdown = ref(0)
let countdownTimer = null

const countryOptions = [
  { code: '+86', label: '+86' }
]

const smsForm = reactive({
  countryCode: '+86',
  phone: '',
  code: ''
})

const accountForm = reactive({
  countryCode: '+86',
  username: '',
  password: ''
})

const canSubmitSms = computed(() => {
  return isValidPhone(smsForm.phone, smsForm.countryCode) && /^\d{6}$/.test(smsForm.code)
})

const canSubmitPassword = computed(() => {
  const username = accountForm.username.trim()
  return username.length > 0
    && (!isPhoneLikeInput(username) || isValidPhone(username, accountForm.countryCode))
    && accountForm.password.length > 0
})

const authFeedbackText = computed(() => {
  return error.value || notice.value
})

const switchLoginMode = (nextMode) => {
  loginMode.value = nextMode
  resetFeedback()
}

const resetFeedback = () => {
  error.value = ''
  notice.value = ''
}

const validatePhone = () => {
  if (!isValidPhone(smsForm.phone, smsForm.countryCode)) {
    error.value = '请输入有效的中国大陆手机号'
    return false
  }
  return true
}

const validateAccountForPassword = () => {
  const username = accountForm.username.trim()
  if (!username) {
    error.value = '请输入手机号或账号'
    return false
  }
  if (isPhoneLikeInput(username) && !isValidPhone(username, accountForm.countryCode)) {
    error.value = '请输入有效的中国大陆手机号'
    return false
  }
  return true
}

const isPhoneLikeInput = (value) => {
  const normalized = String(value || '').trim()
  return /^[+\d][\d\s-]*$/.test(normalized)
}

const isValidPhone = (value, countryCode = '+86') => {
  if (countryCode !== '+86') {
    return false
  }
  const normalized = String(value || '').replace(/[\s-]/g, '')
  const phone = normalized.startsWith('+86')
    ? normalized.slice(3)
    : normalized.startsWith('86') && normalized.length === 13
      ? normalized.slice(2)
      : normalized
  return /^1[3-9]\d{9}$/.test(phone)
}

const sendCode = async () => {
  resetFeedback()
  if (!validatePhone() || countdown.value > 0) return

  smsSending.value = true
  try {
    await authStore.sendSmsCode(smsForm.phone, smsForm.countryCode)
    notice.value = '验证码已发送'
    startCountdown()
  } catch (e) {
    error.value = getErrorMessage(e, '验证码发送失败，请稍后重试')
  } finally {
    smsSending.value = false
  }
}

const submitSms = async () => {
  resetFeedback()
  if (!validatePhone()) return
  if (!/^\d{6}$/.test(smsForm.code)) {
    error.value = '请输入 6 位验证码'
    return
  }

  try {
    await authStore.loginBySms(smsForm.phone, smsForm.countryCode, smsForm.code)
    await authStore.fetchMe().catch(() => {})
    await sessionStore.fetchSessions()
  } catch (e) {
    error.value = getErrorMessage(e, '登录失败，请稍后重试')
  }
}

const submitPassword = async () => {
  resetFeedback()
  if (!validateAccountForPassword()) return
  if (!accountForm.password) {
    error.value = '请输入密码'
    return
  }

  try {
    await authStore.login(accountForm.username, accountForm.password, accountForm.countryCode)
    await authStore.fetchMe().catch(() => {})
    await sessionStore.fetchSessions()
  } catch (e) {
    error.value = getErrorMessage(e, '登录失败，请稍后重试')
  }
}

const startCountdown = () => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
  countdown.value = 60
  countdownTimer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

const getErrorMessage = (e, fallback) => {
  return e?.response?.data?.error || e?.response?.data?.message || e?.message || fallback
}

onBeforeUnmount(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
})
</script>

<style scoped>
.auth-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  background: rgba(17, 24, 39, 0.34);
  padding: 24px;
}

.auth-dialog {
  position: relative;
  width: min(560px, 100%);
  height: 390px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.24);
  padding: 28px 26px 26px;
  overflow: hidden;
}

.auth-close {
  position: absolute;
  right: 16px;
  top: 14px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #6b7280;
  font-size: 28px;
  line-height: 1;
  z-index: 1;
}

.auth-close:hover {
  background: #f3f4f6;
  color: #111827;
}

.auth-main {
  display: grid;
  grid-template-columns: 254px 180px;
  gap: 20px;
  justify-content: center;
  height: 100%;
}

.auth-form-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding-top: 2px;
}

.auth-title {
  margin: 0 0 12px;
  color: #7c3aed;
  font-size: 1.15rem;
  font-weight: 800;
  line-height: 1.2;
  text-align: center;
}

.auth-form {
  display: grid;
  grid-template-rows: 50px 50px 22px 48px;
  gap: 13px;
  flex: 0 0 209px;
}

.login-mode-tabs {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 18px;
  color: #d1d5db;
}

.login-mode-tabs button {
  color: #b8bdc7;
  font-size: 0.98rem;
  font-weight: 700;
}

.login-mode-tabs button.active {
  color: #7c3aed;
}

.soft-input {
  display: flex;
  align-items: center;
  width: 100%;
  height: 50px;
  border-radius: 10px;
  background: #f5f5f6;
  overflow: hidden;
}

.soft-input:focus-within {
  box-shadow: 0 0 0 2px rgba(124, 58, 237, 0.16);
}

.soft-input input {
  min-width: 0;
  flex: 1;
  height: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: #374151;
  font-size: 0.95rem;
}

.soft-input input::placeholder {
  color: #b8bdc7;
}

.country-code {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  align-self: stretch;
  padding: 0 12px 0 13px;
  color: #374151;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
}

.country-code::after {
  content: '';
  position: absolute;
  right: 0;
  top: 50%;
  width: 1px;
  height: 16px;
  background: #d9dce3;
  transform: translateY(-50%);
}

.country-code select {
  appearance: none;
  border: 0;
  outline: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
}

.chevron {
  width: 7px;
  height: 7px;
  border-right: 1.5px solid #c3c7d0;
  border-bottom: 1.5px solid #c3c7d0;
  transform: translateY(-2px) rotate(45deg);
}

.phone-input input {
  padding: 0 14px;
}

.password-input {
  gap: 13px;
  padding-left: 21px;
}

.password-input input {
  padding-right: 16px;
}

.input-icon {
  width: 18px;
  height: 18px;
  color: #b8bdc7;
}

.code-input {
  gap: 8px;
}

.code-input input {
  padding-left: 15px;
}

.code-btn {
  flex: 0 0 96px;
  align-self: stretch;
  color: #7c3aed;
  font-weight: 800;
  font-size: 0.88rem;
}

.code-btn:hover:not(:disabled) {
  color: #6d28d9;
}

.code-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.terms-text {
  margin: 0;
  font-size: 0.9rem;
}

.auth-feedback {
  display: flex;
  align-items: center;
  height: 22px;
  margin: 0;
  color: transparent;
  font-size: 0.84rem;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.auth-feedback.is-empty {
  visibility: hidden;
}

.auth-feedback.is-notice {
  color: #047857;
}

.auth-feedback.is-error {
  color: #dc2626;
}

.auth-submit {
  height: 48px;
  margin-top: 0;
  border-radius: 10px;
  background: #7c3aed;
  color: #fff;
  font-weight: 800;
  font-size: 0.95rem;
}

.auth-submit:hover:not(:disabled) {
  background: #6d28d9;
}

.auth-submit:disabled {
  background: #c4b5fd;
  opacity: 1;
  cursor: not-allowed;
}

.terms-text {
  flex: 0 0 22px;
  margin-top: 8px;
  color: #9ca3af;
  line-height: 1.6;
  text-align: center;
  font-size: 0.82rem;
}

.qr-panel {
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 10px;
  padding: 30px 0 12px 20px;
  border-left: 1px solid #e5e7eb;
}

.qr-panel h3 {
  margin: 0;
  color: #111827;
  font-size: 0.96rem;
  font-weight: 800;
}

.qr-panel p {
  margin: 0;
  color: #6b7280;
  font-size: 0.8rem;
  line-height: 1.45;
  text-align: center;
}

.qr-box {
  position: relative;
  display: grid;
  place-items: center;
  width: 138px;
  height: 138px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
}

.qr-mock {
  position: relative;
  width: 110px;
  height: 110px;
  border-radius: 8px;
  background:
    linear-gradient(90deg, #111827 10px, transparent 10px 18px, #111827 18px 26px, transparent 26px 36px, #111827 36px 44px, transparent 44px),
    linear-gradient(#111827 10px, transparent 10px 18px, #111827 18px 26px, transparent 26px 36px, #111827 36px 44px, transparent 44px),
    repeating-linear-gradient(90deg, #111827 0 6px, transparent 6px 14px),
    repeating-linear-gradient(0deg, transparent 0 10px, rgba(17, 24, 39, 0.22) 10px 16px);
  background-size: 40px 40px, 40px 40px, 110px 110px, 110px 110px;
  background-position: 0 0, 70px 70px, 0 0, 0 0;
  overflow: hidden;
}

.qr-mock::before,
.qr-mock::after {
  content: '';
  position: absolute;
  width: 26px;
  height: 26px;
  border: 8px solid #111827;
  background: #fff;
}

.qr-mock::before {
  left: 7px;
  bottom: 7px;
}

.qr-mock::after {
  right: 7px;
  top: 7px;
}

.qr-logo {
  position: absolute;
  left: 50%;
  top: 50%;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  color: #fff;
  background: #22c55e;
  font-size: 0.84rem;
  font-weight: 800;
  transform: translate(-50%, -50%);
}

.qr-unavailable {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.82);
  color: #7c3aed;
  font-size: 0.88rem;
  font-weight: 800;
}

.auth-fade-enter-active,
.auth-fade-leave-active {
  transition: opacity 0.18s ease;
}

.auth-fade-enter-from,
.auth-fade-leave-to {
  opacity: 0;
}

@media (max-width: 720px) {
  .auth-dialog {
    width: min(440px, 100%);
    height: auto;
    min-height: 390px;
    padding: 26px 20px;
    border-radius: 14px;
  }

  .auth-main {
    grid-template-columns: 1fr;
    gap: 22px;
  }

  .qr-panel {
    border-left: 0;
    border-top: 1px solid #e5e7eb;
    padding: 16px 0 0;
  }

  .qr-box {
    width: 130px;
    height: 130px;
  }

  .qr-mock {
    width: 104px;
    height: 104px;
    background-size: 38px 38px, 38px 38px, 104px 104px, 104px 104px;
    background-position: 0 0, 66px 66px, 0 0, 0 0;
  }
}
</style>
