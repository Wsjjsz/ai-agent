<template>
  <div class="home-container">
    <div class="chat-init-box">
      <!-- 品牌标题区 -->
      <div class="init-header">
        <div class="init-logo">
          <img src="/favicon.svg" alt="" class="init-logo-img" />
        </div>
        <h1 class="init-title">发现机会，先问 AI</h1>
      </div>

      <!-- 模式切换胶囊 -->
      <div class="mode-switcher-wrap">
        <div class="mode-switcher">
          <button
            type="button"
            class="mode-btn"
            :class="{ active: currentMode === 'basic' }"
            @click="currentMode = 'basic'"
          >
            <svg class="mode-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path class="mode-icon-fill" d="M5.5 5.75h13a2.25 2.25 0 0 1 2.25 2.25v7.1a2.25 2.25 0 0 1-2.25 2.25h-6.25l-4.6 3.05v-3.05H5.5a2.25 2.25 0 0 1-2.25-2.25V8A2.25 2.25 0 0 1 5.5 5.75Z"/>
              <path d="M5.5 5.75h13a2.25 2.25 0 0 1 2.25 2.25v7.1a2.25 2.25 0 0 1-2.25 2.25h-6.25l-4.6 3.05v-3.05H5.5a2.25 2.25 0 0 1-2.25-2.25V8A2.25 2.25 0 0 1 5.5 5.75Z"/>
              <path d="M8 10.25h8M8 13.25h5.25"/>
            </svg>
            基础提问
          </button>
          <div class="mode-divider"></div>
          <button
            type="button"
            class="mode-btn"
            :class="{ active: currentMode === 'agent' }"
            @click="currentMode = 'agent'"
          >
            <svg class="mode-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path class="mode-icon-fill" d="M6.75 6.5h10.5A2.25 2.25 0 0 1 19.5 8.75v7.5a2.25 2.25 0 0 1-2.25 2.25H6.75a2.25 2.25 0 0 1-2.25-2.25v-7.5A2.25 2.25 0 0 1 6.75 6.5Z"/>
              <path d="M6.75 6.5h10.5A2.25 2.25 0 0 1 19.5 8.75v7.5a2.25 2.25 0 0 1-2.25 2.25H6.75a2.25 2.25 0 0 1-2.25-2.25v-7.5A2.25 2.25 0 0 1 6.75 6.5Z"/>
              <path d="M9 11.25h.01M15 11.25h.01M9.25 14.75h5.5M12 3.5v3"/>
              <path d="M7.75 18.5v2M16.25 18.5v2"/>
            </svg>
            专业Agent
          </button>
        </div>
      </div>

      <!-- 核心输入区 -->
      <div class="input-panel-wrapper">
        <div class="input-panel" :class="{ 'is-focused': isInputFocused }">
          <textarea
            ref="mainInputRef"
            class="main-input"
            v-model="inputQuery"
            :disabled="isStartingConversation"
            @focus="isInputFocused = true"
            @blur="isInputFocused = false"
            @keydown.enter.prevent="startConversation"
            placeholder="输入感兴趣的投资问题或标的代码..."
            rows="3"
            autofocus
          ></textarea>

          <div class="input-toolbar">
            <div class="toolbar-left">
              <button type="button" class="tool-btn">
                <span class="icon">⚲</span> 深度投研
              </button>
              <button type="button" class="tool-btn">
                <span class="icon">🌐</span> 联网搜索
              </button>
            </div>
            <div class="toolbar-right">
              <button type="button" class="attach-btn" title="上传报告文件">
                <svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none"><path d="M21.44 11.05l-9.19 9.19a6 6 0 01-8.49-8.49l9.19-9.19a4 4 0 015.66 5.66l-9.2 9.19a2 2 0 01-2.83-2.83l8.49-8.48"></path></svg>
              </button>
              <button type="button" class="send-btn" :class="{ active: inputQuery.trim() }" :disabled="isStartingConversation" @click="startConversation" title="发送">
                <svg viewBox="0 0 24 24" width="20" height="20" stroke="white" stroke-width="2" fill="none"><line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon></svg>
              </button>
            </div>
          </div>
        </div>
        <p v-if="startError" class="start-error">{{ startError }}</p>
      </div>

      <!-- 推荐搜索 -->
      <div class="hints-area">
        <div class="hint-label">试试这样问</div>
        <div class="hint-list">
          <button
            v-for="(q, i) in displayHints"
            :key="i"
            class="hint-chip"
            :disabled="isStartingConversation"
            @click="startWithHint(q)"
          >{{ q }}</button>
        </div>
      </div>

      <!-- 热点新闻 -->
      <div class="hot-news-section">
        <div class="hot-news-header">
          <div class="hot-news-header-left">
            <svg viewBox="0 0 20 20" width="18" height="18" fill="var(--brand)"><path fill-rule="evenodd" d="M12.395 2.553a1 1 0 00-1.45-.385c-.345.23-.614.558-.822.88-.214.33-.403.713-.57 1.116-.334.804-.614 1.768-.84 2.734a31.365 31.365 0 00-.613 3.58 2.64 2.64 0 01-.945-1.067c-.328-.68-.398-1.534-.398-2.654A1 1 0 005.05 6.05 6.981 6.981 0 003 11a7 7 0 1011.95-4.95c-.592-.591-.98-.985-1.348-1.467-.363-.476-.724-1.063-1.207-2.03zM12.12 15.12A3 3 0 017 13s.879.5 2.5.5c0-1 .5-4 1.25-4.5.5 1 .786 1.293 1.371 1.879A2.99 2.99 0 0113 13a2.99 2.99 0 01-.879 2.121z" clip-rule="evenodd"/></svg>
            <span class="hot-news-title">财经热点</span>
            <span class="hot-news-badge">AI 精选</span>
          </div>
          <button
            v-if="authStore.isAuthenticated"
            class="refresh-btn"
            @click="fetchHotNews(true)"
            :disabled="loadingNews"
            :class="{ spinning: loadingNews }"
            title="刷新热点"
          >
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/></svg>
          </button>
        </div>
        <div class="hot-news-grid" v-if="hotNews.length > 0">
          <div
            v-for="(item, i) in hotNews.slice(0, 6)"
            :key="i"
            class="news-card"
            title="点击生成投资提问"
            @click="fillQuestionFromNews(item)"
          >
            <div class="news-card-img" :class="!item.imageUrl ? `gradient-${i % 6}` : ''">
              <img v-if="item.imageUrl" :src="getImageProxyUrl(item.imageUrl)" class="news-card-pic" loading="lazy" />
              <svg v-else viewBox="0 0 24 24" width="28" height="28" fill="rgba(255,255,255,0.5)"><path d="M3.5 18.49l6-6.01 4 4L22 6.92l-1.41-1.41-7.09 7.97-4-4L2 16.99z"/><path d="M3.5 18.49l6-6.01 4 4L22 6.92l-1.41-1.41-7.09 7.97-4-4L2 16.99z" opacity="0.4" transform="translate(0,2)"/></svg>
            </div>
            <div class="news-card-body">
              <div class="news-card-title">{{ item.title }}</div>
              <div class="news-card-summary">{{ item.summary }}</div>
              <div class="news-card-footer">
                <div class="news-card-time" v-if="item.pubTime">{{ formatPubTime(item.pubTime) }}</div>
                <button class="news-detail-btn" type="button" title="查看详情" @click.stop="openNewsDetail(item)">
                  详情
                </button>
              </div>
            </div>
          </div>
        </div>
        <div class="hot-news-loading" v-else-if="loadingNews">
          <div class="loading-dots"><i></i><i></i><i></i></div>
          <span>正在获取财经热点...</span>
        </div>
        <div class="hot-news-error" v-else>
          <span>{{ hotNewsEmptyText }}</span>
          <button v-if="authStore.isAuthenticated" class="retry-btn" @click="fetchHotNews(true)">
            重试
          </button>
        </div>
      </div>

    </div>
  </div>

  <!-- 新闻详情弹窗 -->
  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="detailNews" class="news-modal-mask" @click.self="detailNews = null">
        <div class="news-modal-box">
          <button class="news-modal-close" @click="detailNews = null">
            <svg viewBox="0 0 24 24" width="20" height="20" stroke="currentColor" stroke-width="2" fill="none"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
          <div class="news-modal-banner" :class="!detailNews.imageUrl ? 'gradient-0' : ''">
            <img v-if="detailNews.imageUrl" :src="getImageProxyUrl(detailNews.imageUrl)" class="news-modal-pic" />
            <svg v-else viewBox="0 0 24 24" width="40" height="40" fill="rgba(255,255,255,0.4)"><path d="M3.5 18.49l6-6.01 4 4L22 6.92l-1.41-1.41-7.09 7.97-4-4L2 16.99z"/></svg>
          </div>
          <div class="news-modal-content">
            <h2 class="news-modal-title">{{ detailNews.title }}</h2>
            <div class="news-modal-meta" v-if="detailNews.pubTime">
              <span class="news-modal-time">{{ formatPubTime(detailNews.pubTime) }}</span>
            </div>
            <p class="news-modal-summary">{{ detailNews.summary }}</p>
            <a :href="detailNews.sourceUrl" target="_blank" rel="noopener" class="news-modal-link">
              查看原文 →
            </a>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed, ref, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { useHotNews } from '@/stores/hotNews'
import { getImageProxyUrl } from '@/api'

const router = useRouter()
const sessionStore = useSessionStore()
const chatStore = useChatStore()
const authStore = useAuthStore()
const currentMode = ref('basic')
const inputQuery = ref('')
const isInputFocused = ref(false)
const isStartingConversation = ref(false)
const startError = ref('')
const mainInputRef = ref(null)

// 热点新闻（使用共享缓存，应用启动时已预加载）
const { hotNews, loading: loadingNews, error: hotNewsError, fetchHotNews, clearDailyTimer } = useHotNews()
const detailNews = ref(null)
const hotNewsEmptyText = computed(() => {
  return hotNewsError.value || '暂无财经热点数据，请稍后重试'
})

const openNewsDetail = (item) => {
  detailNews.value = item
}

const cleanNewsText = (text) => {
  return String(text || '')
    .replace(/\s+/g, ' ')
    .replace(/[｜|].*$/, '')
    .trim()
}

const buildNewsQuestion = (item) => {
  const title = cleanNewsText(item?.title)
  const summary = cleanNewsText(item?.summary)
  const text = `${title} ${summary}`
  if (!title) return ''

  if (/(新规|政策|监管|条例|办法|证监会|央行|财政部|发改委|国务院|税|降准|降息|加息|关税|配额|制裁)/.test(text)) {
    return `请解读“${title}”对市场情绪、相关行业和普通投资者有什么影响。`
  }
  if (/(收盘|开盘|指数|涨|跌|新高|新低|成交|资金流入|资金流出|美股|A股|港股|股市|债市|汇率|美元|黄金|原油)/.test(text)) {
    return `请分析“${title}”释放了什么市场信号，后续应该关注哪些机会和风险。`
  }
  if (/(合作|签约|订单|融资|并购|收购|上市|财报|业绩|营收|利润|回购|分红|阿里巴巴|腾讯|百度|京东|华为|特斯拉|英伟达|苹果|微软|谷歌)/.test(text)) {
    return `请分析“${title}”对相关公司的业务增长、估值和投资风险有什么影响。`
  }
  if (/(行业|产业|市场|需求|供给|价格|运价|产能|装机|销量|库存|电池|芯片|光伏|航运|地产|医药|消费|有色|钢铁|煤炭|电力|AI|云计算)/i.test(text)) {
    return `请分析“${title}”背后的行业趋势、受益板块和潜在风险。`
  }
  if (/(GDP|CPI|PPI|通胀|就业|利率|国债|PMI|外贸|出口|进口|财政|货币|经济|美联储|欧洲央行|日本央行)/i.test(text)) {
    return `请分析“${title}”对股债汇商品等资产配置有什么影响。`
  }
  return `请结合这条新闻“${title}”，分析其中的投资机会、受益方向和主要风险。`
}

const fillQuestionFromNews = async (item) => {
  const question = buildNewsQuestion(item)
  if (!question) return
  inputQuery.value = question
  await nextTick()
  mainInputRef.value?.focus()
}

// 格式化发布时间
const formatPubTime = (ts) => {
  if (!ts) return ''
  const d = new Date(Number(ts))
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterday = new Date(today.getTime() - 86400000)
  const newsDay = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  const hm = d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  if (newsDay.getTime() === today.getTime()) return hm
  if (newsDay.getTime() === yesterday.getTime()) return '昨天 ' + hm
  if (d.getFullYear() === now.getFullYear()) {
    return (d.getMonth() + 1) + '-' + String(d.getDate()).padStart(2, '0')
  }
  return d.getFullYear() + '-' + (d.getMonth() + 1) + '-' + String(d.getDate()).padStart(2, '0')
}

// 推荐搜索问题池
const hintPool = [
  // ── A股市场 ──
  '当前A股红利策略解析','A股大盘走势预测','A股什么时候适合抄底','A股和港股哪个更值得投资',
  'A股退市新规解读','A股注册制对散户的影响','A股打新收益分析','A股北向资金持续流入说明什么',
  'A股市场情绪指标怎么看','A股破净股值得投资吗','A股高股息蓝筹股推荐','A股与美股相关性分析',
  'A股中特估概念股有哪些','A股量化交易对散户的影响','A股融资融券风险分析','A股限售股解禁影响大吗',
  'A股ST股票还能买吗','A股年报行情怎么把握','A股商誉减值风险如何规避','A股可转债打新收益如何',
  // ── 宁德时代 ──
  '宁德时代最新一季报分析','宁德时代固态电池进展','宁德时代海外建厂布局','宁德时代储能业务前景',
  '宁德时代股价估值合理吗','宁德时代与比亚迪电池对比','宁德时代产业链上下游分析','宁德时代麒麟电池技术优势',
  '宁德时代钠离子电池量产了吗','宁德时代研发投入分析','宁德时代市占率变化趋势','宁德时代换电业务前景',
  // ── 基金定投 ──
  '2026年基金定投推荐','基金定投选周投还是月投','基金定投止盈策略有哪些','基金定投亏损了怎么办',
  '基金定投适合什么市场环境','基金定投和一次性买入哪个好','基金定投选主动基金还是指数基金','基金定投最佳扣款日期',
  '基金定投收益率一般多少','基金定投要投多久才赚钱','基金定投如何选择标的','基金定投中途可以暂停吗',
  '基金定投和银行存款哪个好','基金定投复利效应有多大','基金定投摊薄成本原理','基金定投需要择时吗',
  '基金定投如何设置止盈点','基金定投每月投多少合适','基金定投十年收益测算','基金定投常见误区有哪些',
  // ── 黄金投资 ──
  '黄金ETF值得投资吗','2026年黄金价格走势预测','实物黄金和纸黄金区别','黄金首饰算投资吗',
  '黄金定投怎么操作','黄金和白银投资哪个好','美联储降息对黄金的影响','黄金期货风险有多大',
  '中国黄金股票值得买吗','黄金避险功能还靠谱吗','黄金T+D交易规则详解','黄金积存和黄金ETF哪个好',
  '央行增持黄金说明什么','黄金回收价格怎么算','黄金投资有哪些渠道','黄金与美元的关系是什么',
  // ── 新能源 ──
  '新能源板块后市怎么看','新能源汽车销量排行2026','光伏产业2026年趋势','风电行业投资前景分析',
  '新能源电池技术路线对比','充电桩行业投资机会','新能源补贴退坡影响','锂矿价格走势预测',
  '储能行业市场规模预测','氢能源产业链投资分析','新能源ETF哪个好','新能源车渗透率趋势',
  '光伏组件价格还会降吗','海上风电装机量预测','新能源与传统能源投资对比','固态电池什么时候量产',
  // ── 银行理财 ──
  '银行理财和货币基金哪个好','银行理财产品还保本吗','银行大额存单利率对比','银行理财净值化转型影响',
  '银行股投资价值分析','银行理财和国债逆回购哪个好','银行存款利率还会降吗','银行理财子公司产品靠谱吗',
  '民营银行高息存款安全吗','银行结构性存款值得买吗','银行理财产品费率对比','银行信用风险如何评估',
  '城商行和国有银行理财区别','银行理财提前赎回损失大吗','银行理财和信托产品对比','银行代销基金费率高吗',
  // ── 指数基金 ──
  '沪深300指数基金对比','中证500指数基金哪个好','创业板指数基金值得买吗','科创50指数基金投资价值',
  '上证50和沪深300选哪个','红利指数基金长期收益如何','纳斯达克100指数基金对比','标普500指数基金怎么买',
  '指数基金增强版值得买吗','ETF和指数基金怎么选','指数基金定投组合推荐','行业指数基金怎么挑',
  '宽基指数和窄基指数区别','指数基金跟踪误差怎么看','Smart Beta指数基金是什么','指数基金规模多大合适',
  // ── 可转债 ──
  '可转债投资入门指南','可转债打新收益分析','可转债如何转股操作','可转债到期收益率怎么算',
  '可转债强赎是什么意思','可转债下修转股价利好吗','可转债双低策略详解','可转债和股票哪个风险大',
  '可转债信用评级怎么看','可转债回售条款怎么用','可转债摊薄效应分析','可转债T+0交易技巧',
  '可转债发行条件是什么','可转债违约风险大吗','可转债溢价率怎么看','可转债投资组合构建',
  // ── 资产配置 ──
  '如何构建稳健型投资组合','家庭资产配置比例建议','100万如何做资产配置','保守型投资者如何配置',
  '股债平衡策略详解','美林时钟现在处于什么阶段','全天候投资组合怎么构建','资产配置再平衡多久一次',
  '年轻人资产配置建议','退休后资产配置比例','高净值人群资产配置方案','全球资产配置策略分析',
  '核心卫星投资策略详解','杠铃策略适合什么市场','风险平价模型怎么应用','资产配置中另类投资比例',
  // ── 美股 ──
  '美股科技股还能买吗','美股七巨头估值分析','美股开户流程详解','美股和A股哪个更适合散户',
  '美股分红税收政策','美股期权入门指南','美股ETF有哪些推荐','美联储加息对美股的影响',
  '美股中概股投资价值','美股泡沫风险大吗','纳斯达克和标普500选哪个','美股盘前盘后交易规则',
  '美股ADR是什么意思','美股做空机制详解','美股IPO打新怎么参与','美股财报季怎么布局',
  // ── REITs ──
  'REITs基金收益分析','公募REITs和私募REITs区别','REITs分红收益率一般多少','中国REITs市场发展前景',
  'REITs和直接买房产哪个好','REITs底层资产怎么看','REITs扩募对持有人影响','高速公路REITs收益如何',
  '保障房REITs值得投资吗','REITs估值方法有哪些','REITs和债券基金哪个好','产业园REITs投资分析',
  'REITs流动性风险大吗','仓储物流REITs前景如何','REITs与房地产股票区别','海外REITs投资渠道',
  // ── 医药 ──
  '医药板块估值处于什么水平','创新药投资机会在哪里','医药集采对药企的影响','CXO行业投资前景分析',
  '医疗器械板块值得买吗','中药股投资价值分析','医药ETF哪个最值得买','生物医药技术突破方向',
  '医药反腐对行业的影响','疫苗行业竞争格局分析','基因治疗概念股有哪些','医药基金长期定投策略',
  '医药商业流通模式分析','互联网医疗投资机会','医药研发外包行业趋势','公立医院改革对药企影响',
  // ── 消费 ──
  '消费类基金定投策略','白酒股还有投资价值吗','消费复苏利好哪些板块','国货品牌概念股分析',
  '免税行业投资前景','旅游板块何时复苏','餐饮行业上市公司分析','家电行业竞争格局变化',
  '消费升级趋势下的投资机会','快消品行业投资逻辑','电商直播对消费股的影响','乳制品行业投资分析',
  '调味品行业龙头对比','休闲零食赛道投资价值','预制菜行业前景如何','消费金融投资风险分析',
  // ── 半导体 ──
  '半导体行业投资前景','国产芯片替代进展如何','光刻机概念股有哪些','半导体设备国产化率',
  '芯片设计公司哪家强','半导体材料投资机会','存储芯片价格走势预测','AI芯片市场竞争格局',
  '半导体封测行业分析','芯片制造产能过剩了吗','第三代半导体投资价值','半导体行业周期分析',
  'GPU和CPU投资逻辑对比','FPGA芯片国产替代进展','半导体行业估值方法','芯片产业链上下游分析',
  // ── 固收+ ──
  '固收+基金适合什么人','固收+产品和纯债基金区别','固收+策略在熊市表现如何','固收+基金最大回撤分析',
  '固收+产品中权益仓位多少合适','固收+基金如何挑选','固收+产品收益一般多少','固收+和二级债基区别',
  '固收+基金适合定投吗','固收+产品风险等级划分','固收+基金经理怎么选','固收+策略的夏普比率',
  // ── 基金分析 ──
  '如何看懂基金季报','基金规模多大合适','基金经理换人了怎么办','基金评级靠谱吗',
  '基金净值和估值怎么看','基金赎回费率怎么算','基金A类和C类怎么选','FOF基金值得买吗',
  '基金分红方式选哪种','基金限购说明什么问题','基金清盘了钱还能拿回来吗','基金持仓集中度高好吗',
  '基金最大回撤怎么看','基金夏普比率是什么','基金风格漂移怎么判断','基金公司自购说明什么',
  // ── 债券 ──
  '国债逆回购操作指南','国债收益率走势分析','企业债和公司债区别','债券基金为什么会亏损',
  '城投债还有投资价值吗','债券久期风险怎么理解','可转债和纯债基金对比','债券ETF有哪些推荐',
  '信用债违约风险如何评估','债券牛市还能持续吗','利率债和信用债区别','地方政府债投资分析',
  '债券收益率曲线怎么看','中短债基金收益分析','债券基金杠杆率怎么看','同业存单指数基金收益如何',
  // ── 宏观经济 ──
  '2026年GDP增速预测','CPI和PPI数据怎么解读','央行降准降息对股市影响','人民币汇率走势预测',
  '中国经济结构转型方向','人口老龄化对投资的影响','共同富裕政策下的投资机会','房地产税对市场的影响',
  '碳中和政策投资机遇','一带一路概念股分析','数字经济政策利好哪些板块','供给侧改革对行业的影响',
  '地方政府债务风险分析','外贸形势对A股的影响','通胀预期下如何投资','LPR利率调整对理财的影响',
  // ── 保险 ──
  '增额终身寿险值得买吗','年金险和银行存款哪个好','重疾险新规后怎么选','百万医疗险对比评测',
  '万能险结算利率走势','分红险收益靠谱吗','保险公司的投资能力怎么看','税优健康险值得买吗',
  '养老年金险怎么选','定期寿险和终身寿险区别','保险资管产品收益如何','互联网保险靠谱吗',
  '带病投保有哪些产品','意外险和医疗险区别','教育金保险值得买吗','保险公司偿付能力怎么看',
  // ── 量化投资 ──
  '量化基金收益怎么样','量化策略有哪些类型','指数增强量化基金靠谱吗','量化交易对散户的影响',
  'CTA策略是什么意思','量化对冲基金风险大吗','高频交易对市场的影响','量化选股模型有哪些',
  '机器学习在投资中的应用','量化投资和主观投资对比','多因子模型怎么构建','量化私募排名怎么查',
  // ── 养老投资 ──
  '养老目标基金怎么选','个人养老金账户值得开吗','养老目标日期基金和目标风险基金区别','养老FOF基金收益分析',
  '个人养老金能投哪些产品','养老储蓄产品收益如何','退休规划投资建议','养老投资组合怎么构建',
  '养老目标基金下滑曲线是什么','养老理财产品试点分析','企业年金投资收益如何','养老投资需要注意什么',
  // ── ESG投资 ──
  'ESG基金收益表现如何','ESG评级怎么看','绿色债券投资价值','碳交易市场投资机会',
  'ESG投资策略详解','新能源ESG基金推荐','ESG和可持续投资关系','ESG信息披露标准解读',
  // ── 房地产 ──
  '房地产股票还能买吗','REITs和直接买房哪个好','一线城市房价走势预测','商业地产投资前景',
  '保障性住房投资机会','房地产调控政策走向','房企债务危机影响分析','长租公寓投资价值',
  '房地产行业估值方法','物业管理股投资价值','城市更新概念股分析','房产投资回报率计算',
  // ── 科技股 ──
  '人工智能概念股有哪些','ChatGPT对投资的影响','AI大模型产业链分析','云计算行业投资前景',
  'SaaS公司估值方法','自动驾驶概念股分析','机器人行业投资机会','元宇宙概念股还值得买吗',
  '网络安全行业投资价值','大数据产业链分析','物联网行业发展趋势','5G应用投资机会',
  'AR/VR产业链分析','AI芯片投资逻辑','量子计算概念股有哪些','数字货币概念股分析',
  // ── 海外市场 ──
  '港股投资价值分析','恒生科技指数基金对比','日经225指数基金怎么买','越南股市值得投资吗',
  '印度基金值得买吗','QDII基金哪个好','全球股市估值对比','新兴市场基金投资分析',
  '欧洲股市投资机会','东南亚市场投资前景','巴西股市投资风险','海外REITs投资渠道',
  '港股通税收政策详解','港股打新收益分析','港股和A股估值差异','中概股回归港股影响',
  // ── 交易策略 ──
  '价值投资策略详解','成长股投资逻辑','趋势交易怎么操作','均线交易系统构建',
  '网格交易策略详解','止损止盈怎么设置','仓位管理技巧有哪些','左侧交易和右侧交易区别',
  '逆向投资策略分析','动量投资策略详解','事件驱动投资方法','季节性投资规律',
  '成交量分析技巧','K线图入门指南','技术指标哪个最靠谱','基本面分析框架',
  // ── 期货期权 ──
  '股指期货入门指南','商品期货投资风险','期权交易策略有哪些','期货和现货的区别',
  '期权定价模型详解','期权希腊字母含义','期货套利策略分析','期权卖方风险有多大',
  '商品期货保证金比例','期货交割方式有哪些','期权组合策略详解','期货持仓量怎么分析',
  // ── 外汇 ──
  '美元指数走势预测','人民币贬值对投资的影响','外汇交易入门指南','日元走势分析',
  '欧元兑美元汇率预测','外汇储备变化影响','跨境投资汇率风险','港币联系汇率制度',
  // ── 数字货币 ──
  '比特币投资风险分析','以太坊值得投资吗','数字货币和区块链关系','央行数字货币对投资的影响',
  'DeFi投资风险有哪些','NFT投资价值分析','数字货币交易所怎么选','区块链技术投资机会',
  // ── 投资心理 ──
  '投资中如何克服贪婪','FOMO心理怎么控制','投资亏损心态调整','长期投资如何坚持',
  '投资决策中的认知偏差','羊群效应怎么避免','过度交易的危害','投资复盘怎么做',
  // ── 投资入门 ──
  '新手如何开始投资','股票开户流程详解','投资理财书籍推荐','如何看懂财务报表',
  '市盈率市净率怎么看','ROE指标怎么分析','现金流分析入门','如何评估一家公司价值',
  '投资中常见的坑有哪些','分散投资的原则','风险和收益的关系','复利效应怎么理解',
  // ── 行业分析 ──
  '白酒行业竞争格局','乳业行业投资分析','调味品行业龙头对比','家电行业估值水平',
  '汽车行业电动化转型','航空业投资前景','航运周期分析','钢铁行业供给侧改革',
  '煤炭行业还能投资吗','有色金属价格走势','化工行业周期分析','建材行业投资机会',
  '纺织服装行业趋势','造纸行业景气度分析','农业板块投资逻辑','军工行业投资前景',
  // ── 个股分析 ──
  '贵州茅台最新财报分析','比亚迪投资价值评估','腾讯控股估值分析','阿里巴巴最新季报解读',
  '中国平安保险业务分析','招商银行投资价值','隆基绿能产能分析','药明康德研发管线',
  '海天味业估值合理吗','中国中免免税业务前景','迈瑞医疗国际化进展','东方财富商业模式分析',
  '紫金矿业资源储量分析','长江电力分红收益率','恒瑞医药创新药进展','三一重工海外业务增长',
  // ── 政策解读 ──
  '新国九条对A股的影响','印花税调整对市场影响','退市新规详解','减持新规对市场的影响',
  'IPO节奏变化分析','再融资新规解读','分红新规对投资者利好','转融通业务暂停影响',
  '基金费率改革影响','个人养老金税收优惠','跨境理财通怎么参与','深港通和沪港通区别',
  // ── 另类投资 ──
  '艺术品投资收益分析','红酒投资入门指南','收藏品投资风险','私募股权投资机会',
  '天使投资和VC区别','对冲基金策略分析','大宗商品投资逻辑','农产品期货投资分析',
  // ── 理财工具 ──
  '支付宝理财和微信理财对比','天天基金和蛋卷基金哪个好','雪球产品风险分析','券商理财和银行理财区别',
  '基金投顾服务靠谱吗','智能投顾收益如何','理财通和余额宝收益对比','互联网理财平台安全性',
  // ── 财务自由 ──
  '财务自由需要多少钱','被动收入有哪些渠道','FIRE运动在中国可行吗','4%法则适用于中国吗',
  '如何规划退休生活','副业收入如何投资','家庭财务规划怎么做','教育金如何提前准备',
  // ── 充电桩与新能源车 ──
  '充电桩行业投资机会','充电桩运营盈利模式','新能源车补贴政策2026','新能源车渗透率趋势',
  '换电模式发展前景','新能源车电池回收行业','智能驾驶产业链分析','新能源车出口数据解读',
  '混合动力车还有市场吗','新能源车险费率分析','氢燃料电池车前景','新能源车残值率分析',
  // ── AI与大模型 ──
  'AI大模型产业链分析','人工智能概念股有哪些','AI对各行业的影响分析','AI算力需求投资机会',
  'AI应用落地哪些领域','AI芯片市场竞争格局','AI医疗投资前景','AI教育概念股分析',
  'AI机器人产业链投资','AI安全与监管投资机会','AI自动驾驶技术进展','AI办公软件投资逻辑',
  '大模型训练成本分析','AI推理芯片投资价值','AI数据标注行业分析','AI agent技术投资前景',
  'Sora概念股有哪些','AI搜索对广告行业影响','AI编程工具投资机会','AI客服行业投资分析',
  // ── 区块链与Web3 ──
  '区块链技术投资机会','Web3.0概念股分析','NFT投资价值分析','DeFi投资风险有哪些',
  '央行数字货币进展','比特币ETF投资分析','以太坊2.0升级影响','元宇宙投资机会分析',
  '数字藏品投资风险','区块链游戏投资前景','DAO组织投资分析','Layer2技术投资价值',
  // ── 机器人 ──
  '人形机器人产业链分析','工业机器人投资前景','服务机器人市场规模','机器人核心零部件投资',
  '减速器行业投资分析','伺服电机国产替代进展','机器人视觉技术投资','协作机器人市场前景',
  '机器人行业估值水平','扫地机器人龙头分析','手术机器人投资机会','机器人关节电机技术',
  // ── 军工 ──
  '军工行业投资前景','军工ETF哪个好','航空发动机产业链分析','军用无人机投资机会',
  '军工电子投资分析','导弹产业链投资逻辑','军民融合概念股有哪些','军工行业估值方法',
  '军品定价机制改革影响','军工科研院所改制','军贸出口增长分析','卫星互联网投资机会',
  // ── 交通运输 ──
  '航空业投资前景','航运周期分析','快递行业竞争格局','高铁产业链投资机会',
  '港口行业投资价值','物流行业发展趋势','网约车行业投资分析','共享出行投资前景',
  '航空货运价格走势','集装箱运价走势预测','船运股投资价值分析','公路收费权投资分析',
  // ── 食品饮料 ──
  '白酒行业竞争格局','乳业行业投资分析','调味品行业龙头对比','啤酒行业高端化趋势',
  '预制菜行业前景如何','休闲零食赛道投资价值','饮料行业创新趋势','烘焙行业投资分析',
  '酱油行业竞争格局','速冻食品市场规模','乳制品行业集中度','功能饮料市场分析',
  // ── 家电 ──
  '家电行业估值水平','白色家电龙头对比','小家电市场增长趋势','扫地机器人龙头分析',
  '智能家电投资前景','家电出口数据解读','厨电行业竞争格局','家电行业分红率分析',
  '投影仪行业投资机会','智能家居产业链分析','空调行业格局变化','家电以旧换新政策影响',
  // ── 通信 ──
  '5G应用投资机会','光通信产业链分析','卫星通信投资前景','物联网行业发展趋势',
  '光纤光缆行业分析','通信设备国产替代','6G技术投资前瞻','通信运营商投资价值',
  '数据中心投资机会','边缘计算投资分析','网络切片技术投资','毫米波技术投资前景',
  // ── 环保 ──
  '碳中和政策投资机遇','环保行业投资前景','垃圾分类产业链分析','水处理行业投资机会',
  '大气治理市场规模','固废处理行业分析','环保设备国产替代','碳交易市场投资机会',
  '绿色金融产品分析','新能源环保双碳投资','土壤修复行业前景','环境监测行业分析',
  // ── 传媒 ──
  '传媒行业投资机会','游戏行业版号影响','短视频平台投资价值','直播电商投资逻辑',
  '影视行业复苏分析','广告行业景气度分析','出版行业投资价值','IP经济投资机会',
  '电竞行业投资前景','在线音乐平台估值','知识付费行业分析','虚拟偶像投资机会',
  // ── 纺织服装 ──
  '纺织服装行业趋势','国货运动品牌分析','快时尚行业投资逻辑','服装行业库存分析',
  '纺织出口数据解读','功能性服装市场增长','户外运动品牌投资价值','设计师品牌投资前景',
  // ── 有色金属 ──
  '有色金属价格走势','锂矿价格走势预测','钴矿投资前景分析','稀土行业投资机会',
  '铜价走势预测2026','铝行业供给侧分析','镍矿投资风险分析','锡矿供需格局分析',
  '黄金矿业股投资价值','白银投资前景分析','铂族金属投资分析','钛矿投资机会',
  // ── 钢铁 ──
  '钢铁行业供给侧改革','特钢行业投资机会','钢铁股估值分析','钢材价格走势预测',
  '钢铁行业碳达峰影响','不锈钢行业竞争格局','钢铁电商投资分析','钢铁出口退税政策影响',
  // ── 煤炭 ──
  '煤炭行业还能投资吗','煤炭价格走势预测','动力煤和焦煤区别','煤炭行业分红率分析',
  '煤炭清洁利用投资机会','煤炭行业碳中和影响','煤炭股估值水平分析','煤炭进口政策变化',
  // ── 农业 ──
  '农业板块投资逻辑','种业投资机会分析','养殖行业周期分析','饲料行业竞争格局',
  '农业现代化投资前景','农产品价格走势预测','农药行业投资分析','化肥行业供给侧改革',
  '转基因商业化投资机会','农业机械化投资价值','水产养殖行业分析','林业碳汇投资前景',
  // ── 旅游酒店 ──
  '旅游板块何时复苏','酒店行业投资分析','免税行业投资前景','景区运营公司估值',
  '在线旅游平台竞争格局','出境游复苏数据解读','民宿行业投资前景','旅游消费升级趋势',
  // ── 教育 ──
  '教育行业投资机会','职业教育政策利好','在线教育投资前景','教育信息化市场分析',
  '留学行业复苏趋势','教育硬件投资机会','素质教育赛道分析','教育科技投资逻辑',
  // ── 医疗器械 ──
  '医疗器械板块值得买吗','体外诊断行业分析','心血管器械投资机会','骨科器械市场规模',
  '医疗影像设备国产替代','家用医疗器械市场增长','口腔医疗投资前景','眼科医疗投资分析',
  '微创手术器械投资价值','康复医疗器械市场','医疗AI器械审批进展','高值耗材集采影响',
  // ── 生物科技 ──
  '基因治疗概念股有哪些','细胞治疗投资前景','mRNA技术投资分析','CAR-T疗法商业化进展',
  '生物类似药投资价值','合成生物学投资机会','基因编辑技术投资','抗体药物研发进展',
  '生物医药CDMO分析','生物安全法影响分析','疫苗行业竞争格局','生物试剂行业投资',
  // ── 新材料 ──
  '碳纤维行业投资前景','特种钢材投资分析','电子化学品市场','半导体材料投资机会',
  '高性能塑料投资价值','稀土永磁材料分析','光学材料投资前景','锂电池材料供需分析',
  '光伏材料价格走势','钛合金材料投资机会','高温合金投资分析','新型建材投资逻辑',
  // ── 物流 ──
  '物流行业发展趋势','快递行业竞争格局','冷链物流投资机会','智慧物流技术投资',
  '跨境物流市场分析','仓储自动化投资价值','即时配送行业分析','供应链金融投资机会',
  // ── 电力 ──
  '电力行业投资前景','核电投资机会分析','水电股投资价值','火电转型新能源分析',
  '电网投资产业链','电价改革影响分析','虚拟电厂投资前景','电力市场化交易分析',
  '抽水蓄能投资机会','分布式能源投资价值','电力设备国产替代','特高压产业链分析',
  // ── 服装与奢侈品 ──
  '奢侈品行业投资逻辑','中国奢侈品消费趋势','高端白酒和奢侈品对比','轻奢品牌投资前景',
  '运动服饰市场增长分析','童装行业投资机会','内衣行业竞争格局','男装市场投资价值',
  // ── 造纸与包装 ──
  '造纸行业景气度分析','纸浆价格走势预测','包装行业投资机会','瓦楞纸市场分析',
  '特种纸行业投资前景','造纸行业环保政策影响','纸品出口数据解读','造纸龙头股对比',
  // ── 汽车 ──
  '汽车行业电动化转型','汽车零部件投资机会','轮胎行业投资分析','汽车电子市场规模',
  '二手车市场投资前景','汽车金融投资分析','自动驾驶产业链','汽车出口数据解读',
  '商用车新能源转型','汽车后市场投资机会','智能座舱产业链分析','汽车芯片国产替代',
  // ── 互联网 ──
  '互联网平台监管影响','社交平台投资价值','电商平台竞争格局','本地生活服务投资分析',
  '互联网广告市场分析','SaaS公司估值方法','云计算行业投资前景','网络安全行业投资价值',
  '互联网医疗投资机会','互联网金融监管影响','跨境出口电商分析','社区团购投资逻辑',
  // ── 消费电子 ──
  '消费电子行业复苏分析','智能手机市场趋势','可穿戴设备投资机会','TWS耳机市场分析',
  'VR头显设备投资前景','折叠屏手机市场增长','消费电子芯片投资','智能手表市场规模',
  // ── 博彩与酒店 ──
  '博彩行业复苏分析','澳门博彩股投资价值','酒店行业RevPAR分析','度假村投资前景',
  // ── 体育 ──
  '体育产业投资机会','电竞行业投资前景','体育用品市场分析','健身行业投资逻辑',
  '足球产业投资价值','体育赛事版权分析','运动营养品市场','户外运动装备投资',
  // ── 法律与合规 ──
  '证券投资者保护机制','内幕交易如何识别','虚假陈述民事赔偿','证券集体诉讼制度',
  '投资者适当性管理','信息披露违规处罚','操纵市场案例分析','老鼠仓如何防范',
  // ── 税务 ──
  '股票投资税收政策','基金分红税收规定','港股通税收详解','个人养老金税收优惠',
  '房产投资税收计算','债券利息税收政策','期货交易税收规定','遗产税对投资的影响',
  // ── 退休规划 ──
  '退休需要多少养老金','退休投资组合怎么构建','延迟退休对投资的影响','企业年金投资收益如何',
  '退休后如何领取个人养老金','退休生活费怎么规划','退休医疗费用预估','退休住房规划建议',
  // ── 教育投资 ──
  '教育金如何提前准备','子女留学费用规划','教育储蓄险值得买吗','教育基金定投策略',
  '学区房投资价值分析','国际学校费用对比','教育贷款利率分析','海外留学投资回报',
  // ── 婚姻家庭理财 ──
  '婚前财产如何理财','夫妻共同财产投资','子女教育金规划','家庭应急资金多少合适',
  '家庭保险配置方案','双薪家庭理财策略','全职妈妈理财建议','二胎家庭财务规划',
  // ── 创业投资 ──
  '天使投资和VC区别','创业公司估值方法','独角兽企业投资机会','IPO打新收益分析',
  '科创板上市条件详解','北交所投资机会分析','创业板注册制影响','新三板投资门槛',
  // ── 全球宏观 ──
  '美联储加息周期分析','欧洲央行货币政策','日本央行负利率影响','全球通胀走势预测',
  '地缘政治对投资的影响','中美关系对A股影响','全球供应链重构影响','能源危机对市场影响',
  '全球粮食安全投资逻辑','新兴市场债务风险','发达经济体衰退风险','全球央行政策分化',
  // ── 投资大师 ──
  '巴菲特投资理念解析','芒格投资哲学总结','彼得林奇选股方法','索罗斯反身性理论',
  '格雷厄姆安全边际','费雪成长股投资法','霍华德马克斯周期论','达利欧全天候策略',
  '段永平投资案例分析','张磊价值投资理念','林园投资策略分析','但斌投资方法论',
  // ── 技术分析 ──
  'K线图入门指南','均线系统怎么用','MACD指标实战技巧','RSI超买超卖判断',
  '布林带交易策略','成交量分析技巧','波浪理论实战应用','江恩理论入门',
  '道氏理论核心要点','斐波那契回调分析','支撑位和压力位判断','趋势线画法技巧',
  // ── 风险管理 ──
  '如何设置止损点位','仓位管理技巧有哪些','风险和收益的关系','分散投资的原则',
  '黑天鹅事件如何应对','投资组合风险评估','风险敞口怎么计算','最大回撤控制方法',
  'VaR风险价值模型','压力测试怎么做','流动性风险管理','信用风险评估方法',
  // ── 行为金融 ──
  '投资中如何克服贪婪','FOMO心理怎么控制','锚定效应在投资中的表现','确认偏误如何避免',
  '损失厌恶对投资的影响','过度自信投资陷阱','从众心理怎么克服','近因效应投资误区',
  '框架效应投资决策','沉没成本投资谬误','可得性偏差投资影响','代表性偏差投资判断',
  // ── 量化指标 ──
  '市盈率市净率怎么看','ROE指标怎么分析','PEG指标怎么用','自由现金流分析方法',
  'EV/EBITDA估值法','DCF模型怎么算','市销率适用场景','股息率怎么计算',
  '资产负债率多少合适','流动比率和速动比率','毛利率和净利率区别','应收账款周转率分析',
  // ── 财报分析 ──
  '如何看懂财务报表','资产负债表关键指标','利润表怎么分析','现金流量表怎么看',
  '财报造假怎么识别','商誉减值风险分析','关联交易怎么看','审计报告意见类型',
  '财务造假常见手法','存货周转率分析','固定资产折旧方法','无形资产估值分析',
  // ── 股票基本面 ──
  '如何评估一家公司价值','商业模式分析方法','护城河理论怎么用','竞争格局分析框架',
  '行业生命周期分析','管理层评估方法','公司治理结构分析','股权激励计划解读',
  '大股东增减持信号','机构持仓变化分析','股东人数变化意义','限售股解禁影响',
  // ── 市场情绪 ──
  '市场情绪指标怎么看','融资余额变化分析','换手率高低怎么看','涨停板数据分析',
  '龙虎榜怎么解读','大宗交易信号分析','折溢价率分析','恐慌指数VIX含义',
  '投资者信心指数分析','新开户数变化趋势','基金发行规模分析','市场成交量分析',
  // ── ETF专题 ──
  'ETF和场外基金区别','ETF套利策略详解','ETF流动性怎么看','跨境ETF投资分析',
  '行业ETF怎么选','商品ETF投资价值','债券ETF收益分析','货币ETF和余额宝对比',
  'ETF联接基金和ETF区别','Smart Beta ETF分析','杠杆ETF风险分析','反向ETF适合什么人',
  // ── 分红与股息 ──
  '高股息股票推荐','股息率排名查询','分红再投资收益测算','股票分红税收政策',
  '红利指数基金长期收益','股利贴现模型应用','分红频率对收益影响','强制分红政策分析',
  // ── 并购重组 ──
  '并购重组投资机会','借壳上市案例分析','重大资产重组审核','并购基金投资逻辑',
  '产业并购投资价值','并购后整合风险','商誉减值对股价影响','并购套利策略分析',
  // ── 新股与打新 ──
  'A股打新收益分析','新股破发风险分析','科创板打新门槛','注册制下打新策略',
  '港股打新收益分析','北交所打新规则','新股中签率怎么提高','新股上市首日策略',
  // ── 定增与回购 ──
  '定增投资机会分析','股票回购信号解读','大股东增持含义','员工持股计划分析',
  '定向增发锁定期','回购注销对股价影响','定增破发投资机会','股权激励行权条件',
]

const displayHints = ref([])
let hintTimer = null

const featuredHintQuestions = {
  '有色金属价格走势': '未来3个月有色金属价格可能怎么走，哪些品种更值得关注？',
  '航运周期分析': '航运周期现在处于什么阶段，船运股还有投资机会吗？',
  '转融通业务暂停影响': '转融通业务暂停会如何影响A股市场和普通投资者？',
  'DeFi投资风险有哪些': 'DeFi投资有哪些主要风险，普通投资者应该如何规避？',
  '如何看懂基金季报': '如何在5分钟内看懂基金季报，并判断基金经理是否靠谱？',
  '沪深300指数基金对比': '请对比主流沪深300指数基金，哪只更适合长期定投？',
  '黄金ETF值得投资吗': '现在黄金ETF还值得配置吗，适合占投资组合多少比例？',
  '碳交易市场投资机会': '碳交易市场有哪些投资机会，相关风险点是什么？'
}

const normalizeHintQuestion = (topic) => {
  const text = String(topic || '').trim()
  if (!text) return ''
  if (featuredHintQuestions[text]) return featuredHintQuestions[text]
  if (/[？?]$/.test(text)) return text
  if (/(如何|怎么|哪些|什么|为何|为什么|是否|吗|能不能|值得|该不该|怎么办|怎么选|怎么看|哪个好|哪家|多少合适)/.test(text)) {
    return `${text}？`
  }
  if (/推荐$/.test(text)) {
    return `请帮我筛选${text.replace(/推荐$/, '')}，并说明选择标准和主要风险。`
  }
  if (/详解$/.test(text)) {
    return `请用通俗方式解释${text.replace(/详解$/, '')}，并说明适合哪些投资场景？`
  }
  if (/入门指南$/.test(text)) {
    return `请用新手能看懂的方式讲解${text.replace(/入门指南$/, '')}，有哪些注意事项？`
  }
  if (/竞争格局$/.test(text)) {
    return `请分析${text}，哪些公司更有竞争优势？`
  }
  if (/投资逻辑$/.test(text)) {
    return `请解释${text}，普通投资者应该关注哪些指标？`
  }
  if (/对比$/.test(text)) {
    return `请对比${text.replace(/对比$/, '')}，哪种更适合长期投资？`
  }
  if (/分析$/.test(text)) {
    return `请帮我分析${text.replace(/分析$/, '')}的投资逻辑、机会和风险。`
  }
  if (/预测$/.test(text)) {
    return `请预测${text.replace(/预测$/, '')}的可能走势，并说明关键影响因素。`
  }
  if (/走势$/.test(text)) {
    return `请分析${text}，未来有哪些机会和风险？`
  }
  if (/影响$/.test(text)) {
    return `${text}会带来哪些市场影响和投资机会？`
  }
  if (/机会$/.test(text)) {
    return `${text}主要体现在哪些方向，风险点是什么？`
  }
  if (/风险$/.test(text)) {
    return `${text}有哪些，普通投资者应该如何规避？`
  }
  if (/解读$/.test(text)) {
    return `请解读${text.replace(/解读$/, '')}，对普通投资者有什么影响？`
  }
  return `请帮我分析${text}的投资价值、机会和风险。`
}

const pickRandomHints = () => {
  const shuffled = [...hintPool].sort(() => Math.random() - 0.5)
  displayHints.value = shuffled.slice(0, 4).map(normalizeHintQuestion)
}

const openConversationWithMessage = async (message, options = {}) => {
  const text = String(message || '').trim()
  if (!text || isStartingConversation.value) return
  if (!authStore.canStartGuestConversation()) return

  const mode = currentMode.value
  isStartingConversation.value = true
  startError.value = ''

  try {
    chatStore.startDraft(mode)
    const title = text.substring(0, 10) + (text.length > 10 ? '...' : '')
    const session = await sessionStore.createSession(title, mode)
    if (!session?.id) {
      throw new Error('创建会话失败')
    }
    if (options.clearInput) {
      inputQuery.value = ''
    }
    await router.push({
      path: `/chat/${mode}/${session.id}`,
      query: { q: text }
    })
  } catch (error) {
    console.error('创建会话失败:', error)
    startError.value = error?.response?.data?.error || error?.message || '无法创建新对话，请检查后端服务后重试。'
    chatStore.startDraft(mode)
  } finally {
    isStartingConversation.value = false
  }
}

const startWithHint = (query) => {
  openConversationWithMessage(query)
}

onMounted(() => {
  document.title = 'AI理财终端 - 首页'
  pickRandomHints()
  hintTimer = setInterval(pickRandomHints, 30000)
  if (authStore.isAuthenticated && hotNews.value.length === 0) {
    fetchHotNews(true)
  }
})

watch(
  () => authStore.isAuthenticated,
  (isAuthenticated, wasAuthenticated) => {
    if (isAuthenticated && !wasAuthenticated) {
      fetchHotNews(true)
    }
  }
)

onBeforeUnmount(() => {
  if (hintTimer) clearInterval(hintTimer)
  clearDailyTimer()
})

const startConversation = async () => {
  await openConversationWithMessage(inputQuery.value, { clearInput: true })
}
</script>

<style scoped>
.home-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  width: 100%;
  background-color: var(--bg-surface);
}

.chat-init-box {
  width: 100%;
  max-width: 1100px;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 24px;
  transform: translateY(-3vh);
}

.init-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.init-logo {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 36px;
}

.init-logo-img {
  display: block;
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.init-title {
  font-size: 1.8rem;
  line-height: 36px;
  font-weight: 600;
  color: var(--text-strong);
  letter-spacing: 0;
}

.mode-switcher-wrap {
  display: flex;
  justify-content: center;
  width: 100%;
  margin-bottom: 32px;
}

.mode-switcher {
  display: flex;
  align-items: center;
  background-color: var(--bg-surface-soft);
  border-radius: 999px;
  padding: 4px;
  border: 1px solid var(--line-soft);
}

.mode-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 24px;
  border-radius: 999px;
  font-size: 0.95rem;
  font-weight: 500;
  color: var(--text-muted);
  transition: all 0.2s var(--ease-smooth);
}

.mode-btn svg {
  flex: 0 0 18px;
}

.mode-btn.active {
  background-color: var(--bg-surface);
  color: var(--brand-strong);
  box-shadow: var(--shadow-strong);
}

.mode-btn:hover:not(.active) {
  color: var(--text-strong);
  background-color: rgba(0,0,0,0.03);
}

.mode-icon {
  width: 18px;
  height: 18px;
  color: currentColor;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
  opacity: 0.78;
  transition: color 0.2s var(--ease-smooth), opacity 0.2s var(--ease-smooth);
}

.mode-icon-fill {
  fill: currentColor;
  stroke: none;
  opacity: 0;
  transition: opacity 0.2s var(--ease-smooth);
}

.mode-btn.active .mode-icon {
  opacity: 1;
}

.mode-btn.active .mode-icon-fill {
  opacity: 0.16;
}

.mode-divider {
  width: 1px;
  height: 20px;
  background-color: var(--line-strong);
  margin: 0 4px;
  opacity: 0.5;
}

.input-panel-wrapper {
  width: 100%;
  position: relative;
}

.input-panel {
  width: 100%;
  background-color: var(--bg-surface);
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-lg);
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-shadow: var(--shadow-soft);
}

.input-panel.is-focused {
  border-color: var(--brand);
  box-shadow: 0 4px 20px rgba(124, 58, 237, 0.12);
}

.main-input {
  width: 100%;
  border: none;
  background: transparent;
  resize: none;
  font-size: 1.1rem;
  line-height: 1.6;
  color: var(--text-strong);
  outline: none;
}

.main-input::placeholder {
  color: #9ca3af;
}

.input-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}

.toolbar-left, .toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.tool-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid var(--line-soft);
  background-color: var(--bg-surface-soft);
  color: var(--text-main);
  font-size: 0.85rem;
  font-weight: 500;
  transition: background-color 0.2s;
}

.tool-btn:hover {
  background-color: var(--line-strong);
  color: var(--text-strong);
}

.attach-btn {
  color: var(--text-muted);
  padding: 8px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.2s, color 0.2s;
}

.attach-btn:hover {
  background-color: var(--bg-surface-soft);
  color: var(--text-strong);
}

.send-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background-color: var(--line-strong);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.2s, transform 0.1s;
  cursor: not-allowed;
}

.send-btn.active {
  background-color: var(--brand);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(124, 58, 237, 0.4);
}

.send-btn.active:hover {
  background-color: var(--brand-strong);
  transform: scale(1.05);
}

.send-btn:disabled,
.hint-chip:disabled {
  opacity: 0.65;
  cursor: wait;
}

.start-error {
  margin-top: 8px;
  color: #7c3aed;
  font-size: 0.86rem;
  text-align: center;
}

.hints-area {
  margin-top: 28px;
  width: 100%;
  min-height: 96px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.hint-label {
  font-size: 0.82rem;
  color: var(--text-muted);
  letter-spacing: 0.02em;
}

.hint-list {
  width: min(860px, 100%);
  min-height: 74px;
  display: grid;
  grid-template-columns: repeat(2, minmax(260px, 1fr));
  grid-auto-rows: 34px;
  justify-content: center;
  align-content: start;
  gap: 10px 12px;
}

.hint-chip {
  height: 34px;
  padding: 0 18px;
  border-radius: 999px;
  border: 1px solid var(--line-soft);
  background: var(--bg-surface);
  color: var(--text-main);
  font-size: 0.88rem;
  cursor: pointer;
  transition: all 0.2s var(--ease-smooth);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hint-chip:hover {
  border-color: var(--brand);
  color: var(--brand-strong);
  background: #f5f3ff;
  box-shadow: 0 2px 12px rgba(124, 58, 237, 0.12);
}

/* ── Hot News Section ── */
.hot-news-section {
  width: 100%;
  margin-top: 28px;
  max-width: 1100px;
}

.hot-news-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.hot-news-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hot-news-title {
  font-size: 1rem;
  font-weight: 700;
  color: var(--text-strong);
}

.hot-news-badge {
  font-size: 0.65rem;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  background: linear-gradient(135deg, #f5f3ff, #ede9fe);
  color: var(--brand-strong);
  border: 1px solid #ddd6fe;
}

.refresh-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid var(--line-soft);
  background: var(--bg-surface);
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.2s;
}

.refresh-btn:hover:not(:disabled) {
  border-color: var(--brand);
  color: var(--brand-strong);
  background: #f5f3ff;
}

.refresh-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.refresh-btn.spinning svg {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.hot-news-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}

.news-card {
  background: var(--bg-surface);
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s var(--ease-smooth);
}

.news-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  border-color: var(--brand);
}

.news-card-img {
  width: 100%;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.news-card-pic {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.gradient-0 { background: linear-gradient(135deg, #6d28d9, #7c3aed); }
.gradient-1 { background: linear-gradient(135deg, #0891b2, #06b6d4); }
.gradient-2 { background: linear-gradient(135deg, #7c3aed, #a78bfa); }
.gradient-3 { background: linear-gradient(135deg, #d97706, #f59e0b); }
.gradient-4 { background: linear-gradient(135deg, #dc2626, #f87171); }
.gradient-5 { background: linear-gradient(135deg, #2563eb, #60a5fa); }

.news-card-body {
  padding: 8px 10px 10px;
}

.news-card-title {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-strong);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 4px;
}

.news-card-summary {
  font-size: 0.68rem;
  color: var(--text-muted);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.news-card-time {
  font-size: 0.62rem;
  color: var(--text-muted);
  opacity: 0.7;
}

.news-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
}

.news-detail-btn {
  flex-shrink: 0;
  padding: 2px 7px;
  border: 1px solid var(--line-soft);
  border-radius: 999px;
  background: var(--bg-surface-soft);
  color: var(--text-muted);
  font-size: 0.62rem;
  line-height: 1.4;
  cursor: pointer;
  transition: all 0.15s;
}

.news-detail-btn:hover {
  border-color: var(--brand);
  background: #f5f3ff;
  color: var(--brand-strong);
}

/* ── Loading ── */
.hot-news-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 32px 0;
  color: var(--text-muted);
  font-size: 0.85rem;
}

.hot-news-error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 24px 0;
  color: var(--text-muted);
  font-size: 0.85rem;
}

.retry-btn {
  padding: 4px 14px;
  border-radius: 6px;
  border: 1px solid var(--line-soft);
  background: var(--bg-surface);
  color: var(--brand-strong);
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}

.retry-btn:hover {
  background: #f5f3ff;
  border-color: var(--brand);
}

.loading-dots {
  display: inline-flex;
  gap: 4px;
}

.loading-dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--brand);
  animation: dotBounce 1s ease-in-out infinite;
}

.loading-dots i:nth-child(2) { animation-delay: 0.15s; }
.loading-dots i:nth-child(3) { animation-delay: 0.3s; }

@keyframes dotBounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.4; }
  40% { transform: translateY(-4px); opacity: 1; }
}

/* ── Detail Modal ── */
.news-modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.news-modal-box {
  background: var(--bg-surface);
  border-radius: 16px;
  width: 520px;
  max-width: 90vw;
  max-height: 80vh;
  overflow: hidden;
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.2);
  position: relative;
}

.news-modal-close {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 1;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-main);
  transition: background 0.15s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.news-modal-close:hover {
  background: white;
  color: var(--text-strong);
}

.news-modal-banner {
  width: 100%;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.news-modal-pic {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.news-modal-content {
  padding: 20px 24px 24px;
}

.news-modal-title {
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--text-strong);
  line-height: 1.5;
  margin-bottom: 8px;
}

.news-modal-meta {
  margin-bottom: 12px;
}

.news-modal-time {
  font-size: 0.78rem;
  color: var(--text-muted);
}

.news-modal-summary {
  font-size: 0.92rem;
  color: var(--text-main);
  line-height: 1.7;
  margin-bottom: 16px;
}

.news-modal-link {
  display: inline-block;
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--brand-strong);
  text-decoration: none;
  padding: 6px 16px;
  border-radius: 8px;
  background: #f5f3ff;
  border: 1px solid #ddd6fe;
  transition: all 0.15s;
}

.news-modal-link:hover {
  background: #ede9fe;
  border-color: var(--brand);
}

/* ── Modal Transition ── */
.modal-fade-enter-active, .modal-fade-leave-active {
  transition: opacity 0.2s ease;
}
.modal-fade-enter-from, .modal-fade-leave-to {
  opacity: 0;
}
.modal-fade-enter-active .news-modal-box {
  animation: modalSlideIn 0.25s ease;
}
@keyframes modalSlideIn {
  from { transform: translateY(-12px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

/* ── Responsive ── */
@media (max-width: 900px) {
  .hot-news-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 500px) {
  .hints-area {
    min-height: auto;
  }

  .hint-list {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .hot-news-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
