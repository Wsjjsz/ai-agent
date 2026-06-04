<template>
  <div class="user-avatar-badge" :style="avatarStyle" role="img" aria-label="用户头像">
    <img v-if="avatarUrl" :src="avatarUrl" alt="" class="user-avatar-img" @error="imageFailed = true" />
    <span v-else-if="presetId" class="user-avatar-symbol" aria-hidden="true">
      <svg v-if="presetId === 'amethyst'" viewBox="0 0 24 24">
        <circle cx="7" cy="7" r="2.2" />
        <circle cx="17" cy="7" r="2.2" />
        <circle cx="12" cy="17" r="2.2" />
        <path d="M8.8 8.7 11 14.6M15.2 8.7 13 14.6M9.3 7h5.4" />
      </svg>
      <svg v-else-if="presetId === 'nebula'" viewBox="0 0 24 24">
        <path d="M5 17h14" />
        <path d="M8 15V8M12 15V5M16 15v-9" />
        <path d="M7 10h2M11 8h2M15 11h2" />
      </svg>
      <svg v-else-if="presetId === 'emerald'" viewBox="0 0 24 24">
        <path d="M12 4 18 7v4.8c0 4-2.4 6.7-6 8.2-3.6-1.5-6-4.2-6-8.2V7l6-3Z" />
        <path d="m8.8 12.2 2.1 2.1 4.5-4.7" />
      </svg>
      <svg v-else-if="presetId === 'sunrise'" viewBox="0 0 24 24">
        <path d="M5 17h14" />
        <path d="m6.5 14.5 3.6-3.8 3.1 2.6 4.8-6" />
        <path d="M15.8 7.3H18v2.2" />
      </svg>
      <svg v-else-if="presetId === 'blossom'" viewBox="0 0 24 24">
        <ellipse cx="12" cy="7" rx="5" ry="2.4" />
        <path d="M7 7v7c0 1.3 2.2 2.4 5 2.4s5-1.1 5-2.4V7" />
        <path d="M7 10.5c0 1.3 2.2 2.4 5 2.4s5-1.1 5-2.4" />
      </svg>
      <svg v-else viewBox="0 0 24 24">
        <path d="M12 5a7 7 0 1 0 7 7" />
        <path d="M12 5v7l5 5" />
        <path d="M16.8 4.8h2.4v2.4" />
      </svg>
    </span>
    <span v-else class="user-avatar-initial">{{ initial }}</span>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  avatarUrl: {
    type: String,
    default: ''
  },
  name: {
    type: String,
    default: ''
  },
  seed: {
    type: String,
    default: ''
  },
  variant: {
    type: String,
    default: 'auto'
  }
})

const imageFailed = ref(false)

watch(() => props.avatarUrl, () => {
  imageFailed.value = false
})

const avatarUrl = computed(() => {
  if (imageFailed.value) return ''
  const value = props.avatarUrl?.trim() || ''
  return value.startsWith('preset:') ? '' : value
})

const initial = computed(() => {
  const value = (props.name || '用户').trim()
  return value.slice(0, 1).toUpperCase()
})

const palettes = [
  ['#dbeafe', '#bfdbfe', '#1d4ed8'],
  ['#ede9fe', '#ddd6fe', '#7c3aed'],
  ['#dcfce7', '#bbf7d0', '#15803d'],
  ['#fef3c7', '#fde68a', '#b45309'],
  ['#ffe4e6', '#fecdd3', '#be123c'],
  ['#ccfbf1', '#99f6e4', '#0f766e']
]

const presetPalettes = {
  amethyst: ['radial-gradient(circle at 30% 28%, #f5d0fe 0 18%, transparent 19%), linear-gradient(135deg, #7c3aed, #c084fc)', '#c4b5fd', '#ffffff'],
  nebula: ['radial-gradient(circle at 72% 24%, #93c5fd 0 16%, transparent 17%), linear-gradient(135deg, #312e81, #2563eb)', '#bfdbfe', '#ffffff'],
  emerald: ['radial-gradient(circle at 70% 72%, #bbf7d0 0 20%, transparent 21%), linear-gradient(135deg, #047857, #22c55e)', '#86efac', '#ffffff'],
  sunrise: ['radial-gradient(circle at 34% 32%, #fde68a 0 20%, transparent 21%), linear-gradient(135deg, #f97316, #facc15)', '#fed7aa', '#7c2d12'],
  blossom: ['radial-gradient(circle at 68% 30%, #fbcfe8 0 18%, transparent 19%), linear-gradient(135deg, #db2777, #fb7185)', '#f9a8d4', '#ffffff'],
  graphite: ['radial-gradient(circle at 28% 76%, #94a3b8 0 18%, transparent 19%), linear-gradient(135deg, #111827, #475569)', '#cbd5e1', '#ffffff']
}

const presetId = computed(() => {
  const value = props.avatarUrl?.trim() || ''
  return value.startsWith('preset:') ? value.slice('preset:'.length) : ''
})

const fallbackStyle = computed(() => {
  if (presetId.value && presetPalettes[presetId.value]) {
    const [background, border, text] = presetPalettes[presetId.value]
    return {
      background,
      borderColor: border,
      color: text
    }
  }
  if (props.variant === 'brand') {
    return {
      background: 'linear-gradient(135deg, var(--brand), var(--brand-strong))',
      borderColor: 'var(--brand-strong)',
      color: '#ffffff'
    }
  }
  const seed = `${props.seed || props.name || 'user'}`
  let hash = 0
  for (let i = 0; i < seed.length; i++) {
    hash = (hash * 31 + seed.charCodeAt(i)) >>> 0
  }
  const [from, to, text] = palettes[hash % palettes.length]
  return {
    background: `linear-gradient(135deg, ${from}, ${to})`,
    borderColor: to,
    color: text
  }
})

const avatarStyle = computed(() => {
  if (avatarUrl.value && props.variant === 'brand') {
    return {
      borderColor: 'var(--brand-strong)',
      color: 'var(--brand-strong)'
    }
  }
  if (avatarUrl.value) return undefined
  return fallbackStyle.value
})
</script>

<style scoped>
.user-avatar-badge {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #dbeafe, #ccfbf1);
  color: #1d4ed8;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 2px solid currentColor;
  overflow: hidden;
}

.user-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.user-avatar-initial {
  font-size: 0.95rem;
  font-weight: 800;
  color: currentColor;
}

.user-avatar-symbol {
  width: 62%;
  height: 62%;
  color: currentColor;
  display: grid;
  place-items: center;
}

.user-avatar-symbol svg {
  width: 100%;
  height: 100%;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.user-avatar-symbol circle,
.user-avatar-symbol ellipse {
  fill: currentColor;
  stroke: none;
}
</style>
