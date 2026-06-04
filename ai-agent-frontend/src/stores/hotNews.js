import { ref } from 'vue'
import { getHotNews } from '@/api'

const hotNews = ref([])
const loading = ref(false)
const error = ref('')

const fetchHotNews = async (force = false) => {
  if (loading.value && !force) return
  loading.value = true
  error.value = ''
  try {
    const data = await getHotNews()
    if (Array.isArray(data)) {
      hotNews.value = data
    }
  } catch (e) {
    error.value = e?.response?.data?.error || e?.message || '财经热点加载失败'
    console.error('获取热点新闻失败:', e)
  } finally {
    loading.value = false
  }
}

// 计算到次日 6:00 的毫秒数
const msUntilNextSixAM = () => {
  const now = new Date()
  const sixAM = new Date(now)
  sixAM.setHours(6, 0, 0, 0)
  if (now >= sixAM) {
    sixAM.setDate(sixAM.getDate() + 1)
  }
  return sixAM.getTime() - now.getTime()
}

// 启动每日 6:00 自动刷新定时器（模块级，整个应用生命周期内有效）
let dailyTimer = null
const scheduleDailyRefresh = () => {
  if (dailyTimer) clearTimeout(dailyTimer)
  const delay = msUntilNextSixAM()
  dailyTimer = setTimeout(() => {
    fetchHotNews()
    // 之后每 24 小时刷新一次
    dailyTimer = setInterval(fetchHotNews, 24 * 60 * 60 * 1000)
  }, delay)
}

// 页面销毁时清理（由视图层 onBeforeUnmount 触发，这里暴露清理函数）
const clearDailyTimer = () => {
  if (dailyTimer) {
    clearTimeout(dailyTimer)
    clearInterval(dailyTimer)
    dailyTimer = null
  }
}

// 首次加载
fetchHotNews()
scheduleDailyRefresh()

export function useHotNews() {
  return { hotNews, loading, error, fetchHotNews, clearDailyTimer }
}
