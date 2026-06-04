<template>
  <div class="ai-avatar" :class="variantClass" role="img" :aria-label="ariaLabel">
    <svg viewBox="0 0 256 256" fill="none" xmlns="http://www.w3.org/2000/svg" class="avatar-svg">
      <g transform="translate(128, 128) scale(1.2) translate(-128, -135)">
        <!-- 深紫色牛头主体 -->
        <path fill="#6A1FB5" d="M72 64 C40 48, 32 80, 48 112 Q128 40, 208 112 C216 80, 224 48, 184 64 L200 192 Q128 232, 56 192 Z"/>
        <!-- 白色眼睛 -->
        <ellipse cx="96" cy="130" rx="10" ry="12" fill="#FFFFFF" />
        <ellipse cx="160" cy="130" rx="10" ry="12" fill="#FFFFFF" />
        <!-- 白色鼻子 -->
        <ellipse cx="128" cy="175" rx="28" ry="18" fill="#FFFFFF" />
        <!-- 黑色鼻中点 -->
        <ellipse cx="128" cy="175" rx="12" ry="8" fill="#000000" />
      </g>
    </svg>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  type: { type: String, default: 'default' }
})

const variantClass = computed(() => props.type === 'love' ? 'love' : 'super')
const ariaLabel = computed(() => props.type === 'love' ? 'AI 理财顾问' : 'AI 超级智能体')
</script>

<style scoped>
.ai-avatar {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-svg {
  width: 100%;
  height: 100%;
  display: block;
}
</style>
