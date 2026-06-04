/**
 * Agent 推理步骤解析工具函数
 */

export const ANALYSIS_HINTS = [
  '正在分析问题并规划步骤',
  '正在拆解子任务并选择工具',
  '正在检索信息并提取关键点',
  '正在汇总证据并生成结论'
]

export const TERMINAL_FINALIZE_GRACE_MS = 1500
export const STEP_TYPING_FRAME_MS = 16
export const STEP_TYPING_MAX_DURATION_MS = 520
export const STEP_DIRECT_RENDER_THRESHOLD = 280
export const ANALYSIS_HINT_INTERVAL_MS = 1200

export const formatStepContent = (text) => {
  const normalized = text.replace(/\\n/g, '\n').trim()
  return normalized
}

export const parseStepChunks = (raw) => {
  const cleaned = raw.trim()
  if (!cleaned) {
    return []
  }

  const chunks = cleaned
    .split(/(?=Step\s+\d+\s*:)/g)
    .map(item => item.trim())
    .filter(Boolean)

  if (chunks.length === 0) {
    return [cleaned]
  }

  return chunks
}

export const classifyStep = (content) => {
  if (content.includes('工具') && content.includes('返回的结果')) {
    return 'ai-step-tool'
  }
  if (content.includes('思考完成')) {
    return 'ai-step-think'
  }
  if (content.includes('执行错误') || content.includes('遇到了问题')) {
    return 'ai-step-error'
  }
  return 'ai-step-result'
}

export const decodeFinalSummary = (encoded) => {
  if (!encoded) return ''
  return encoded
    .replace(/\\n/g, '\n')
    .replace(/\\\\/g, '\\')
    .trim()
}

export const stripInjectedHtmlDump = (text) => {
  if (!text) return ''

  const hasHtmlStart = /(?:^|\n)\s*<(?:!doctype\s+html|html|head|meta|title)\b/i.test(text)
  const hasBaiduVerificationSignal = /百度安全验证|format-detection|x-ua-compatible|apple-mobile-web-app-capable/i.test(text)

  if (hasHtmlStart && hasBaiduVerificationSignal) {
    const cutIndex = text.search(/(?:^|\n)\s*<(?:!doctype\s+html|html|head|meta|title)\b/i)
    if (cutIndex >= 0) {
      return text.slice(0, cutIndex).trim()
    }
  }

  return text
}

export const filterFinalResultContent = (rawMarkdown) => {
  if (!rawMarkdown) return ''
  const normalized = stripInjectedHtmlDump(rawMarkdown.replace(/\r\n/g, '\n').trim())

  // 提取 "## 最终总结" 之后、"## 执行轨迹摘要" 或 "## 生成时间" 之前的内容
  const summaryMatch = normalized.match(/##\s*最终总结\s*\n([\s\S]*?)(?=\n##\s*(?:执行轨迹摘要|生成时间)|$)/)
  let result = summaryMatch ? summaryMatch[1].trim() : normalized

  // 清理残留的元数据
  result = result
    .replace(/^#\s*智能体整理结果\s*\n?/gm, '')
    .replace(/^##\s*(?:用户问题|最终总结|执行轨迹摘要|生成时间)[^\n]*\n?/gm, '')
    .replace(/^[\s\S]*?【输出要求】[\s\S]*?\n\n/, '')
    .replace(/【输出要求】[^\n]*\n?/g, '')
    .replace(/^-\s*Step\s+\d+:[^\n]*$/gm, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim()

  return result
}

export const isTerminalStepContent = (content) => {
  if (!content) return false
  return content.includes('任务结束')
    || content.includes('Terminated:')
    || content.includes('doTerminate 返回的结果')
}

/**
 * 从历史消息内容重建 Agent trace 结构
 * @param {string} content - AI 消息内容
 * @returns {Object|null} trace 对象，如果不是 agent 消息则返回 null
 */
export const reconstructTraceFromContent = (content) => {
  if (!content) return null

  // 检查是否包含步骤标记
  const hasStepMarkers = /Step\s+\d+\s*:/.test(content)
  const hasFinalResult = content.includes('[FINAL_RESULT]')

  if (!hasStepMarkers && !hasFinalResult) {
    return null
  }

  // 提取最终结果
  let finalResultMarkdown = ''
  let mainContent = content

  if (hasFinalResult) {
    const finalResultMatch = content.match(/\[FINAL_RESULT\](.*)$/s)
    if (finalResultMatch) {
      finalResultMarkdown = filterFinalResultContent(decodeFinalSummary(finalResultMatch[1]))
      mainContent = content.slice(0, content.indexOf('[FINAL_RESULT]'))
    }
  }

  // 解析步骤
  const chunks = parseStepChunks(mainContent)
  const steps = chunks.map((chunk, index) => {
    const stepMatch = chunk.match(/^Step\s+(\d+)\s*:\s*([\s\S]*)$/)
    let stepNo = index + 1
    let stepBody = chunk

    if (stepMatch) {
      stepNo = Number(stepMatch[1])
      stepBody = formatStepContent(stepMatch[2])
    }

    const stepType = classifyStep(stepBody)

    return {
      stepNo,
      type: stepType,
      fullContent: stepBody,
      content: stepBody,
      typing: false
    }
  })

  // 如果没有最终结果，从最后一个非终止步骤提取
  if (!finalResultMarkdown) {
    const fallbackStep = [...steps]
      .reverse()
      .find(step => step.type === 'ai-step-result' && !isTerminalStepContent(step.fullContent || step.content))

    if (fallbackStep) {
      finalResultMarkdown = filterFinalResultContent(fallbackStep.fullContent || fallbackStep.content)
    }
  }

  return {
    steps,
    collapsed: true,
    finalResultMarkdown: finalResultMarkdown || '历史记录',
    summary: `已完成 ${steps.length} 步推理`,
    status: 'done',
    progressText: ''
  }
}
