<template>
  <footer class="app-footer">
    <div class="footer-content">
      <div class="brand-line">
        <span class="brand-dot"></span>
        <h3>AI金牌理财顾问</h3>
      </div>

      <p class="brand-desc">更轻量的交互，更聚焦的回答体验。</p>

      <div class="policy-links">
        <a href="#">用户协议</a>
        <span aria-hidden="true">·</span>
        <a href="#">隐私政策</a>
      </div>
    </div>
    
    <div class="footer-bottom">
      <p>© {{ currentYear }} AI金牌理财顾问</p>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'

// 计算当前年份
const currentYear = computed(() => new Date().getFullYear())
</script>

<style scoped>
.app-footer {
  background: rgba(255, 255, 255, 0.55);
  padding: 18px 0 14px;
  color: var(--text-muted);
  border-top: 1px solid rgba(201, 216, 239, 0.65);
  backdrop-filter: blur(10px);
  width: 100%;
  margin-top: auto;
  position: relative;
  z-index: 2;
}

.footer-content {
  width: min(980px, 100%);
  margin: 0 auto;
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 0 20px;
}

.brand-line {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.brand-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--brand), #a78bfa);
  box-shadow: 0 0 0 4px rgba(124, 58, 237, 0.1);
}

.brand-line h3 {
  font-family: var(--font-display);
  font-size: 1rem;
  color: var(--text-strong);
}

.brand-desc {
  font-size: 0.88rem;
  color: var(--text-muted);
}

.policy-links {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.policy-links a,
.policy-links span {
  font-size: 0.84rem;
  color: var(--text-muted);
}

.policy-links a {
  text-decoration: none;
  transition: color 0.2s;
}

.policy-links a:hover {
  color: var(--brand);
}

.footer-bottom {
  text-align: center;
  padding-top: 10px;
  margin-top: 8px;
  border-top: 1px dashed rgba(201, 216, 239, 0.85);
  color: var(--text-muted);
  font-size: 0.8rem;
}

@media (max-width: 768px) {
  .app-footer {
    padding: 16px 0 12px;
  }
}

@media (max-width: 480px) {
  .brand-line h3 {
    font-size: 0.92rem;
  }

  .brand-desc,
  .policy-links a,
  .policy-links span {
    font-size: 0.8rem;
  }
}
</style> 