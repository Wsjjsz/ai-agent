/**
 * Markdown 渲染工具函数
 */

export const escapeHtml = (text) => text
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;')

const unescapeHtml = (text) => text
  .replace(/&amp;/g, '&')
  .replace(/&lt;/g, '<')
  .replace(/&gt;/g, '>')
  .replace(/&quot;/g, '"')
  .replace(/&#39;/g, "'")

const escapeAttr = (text) => escapeHtml(String(text || ''))

const safeUrl = (url, { image = false } = {}) => {
  const value = unescapeHtml(String(url || '')).trim()
  if (!value) return ''
  if (value.startsWith('/')) return value
  try {
    const parsed = new URL(value)
    if (parsed.protocol === 'http:' || parsed.protocol === 'https:') {
      return parsed.href
    }
    if (!image && parsed.protocol === 'mailto:') {
      return parsed.href
    }
  } catch {
    return ''
  }
  return ''
}

export const renderMarkdown = (markdownText) => {
  if (!markdownText) return ''
  // 预处理：在编号列表和无序列表前插入换行
  const preprocessed = markdownText
    // 匹配 "数字." 后跟中文或字母（排除小数、年份等）
    .replace(/(?<!\n)(\d+)\.(?=[^\d\s.])/g, '\n$1.')
    // 匹配列表项标记 "- "：前面是行首、冒号、句号等分隔符
    .replace(/(?<=^|[:：。；\n])[-–—－]\s+(?=\S)/gm, '\n- ')
    // 匹配 "数字）" 中文括号编号
    .replace(/(?<!\n)(\d+)[）)]\s*/g, '\n$1）')
    .replace(/^\n+/, '')
  const escaped = escapeHtml(preprocessed).replace(/\r\n/g, '\n')

  const codeBlocks = []
  const withCodeToken = escaped.replace(/```([\s\S]*?)```/g, (_, code) => {
    const token = `@@CODE_BLOCK_${codeBlocks.length}@@`
    codeBlocks.push(code.trim())
    return token
  })

  const inlineParsed = withCodeToken
    .replace(/^#{4}\s+(.*)$/gm, '<h4>$1</h4>')
    .replace(/^###\s+(.*)$/gm, '<h3>$1</h3>')
    .replace(/^##\s+(.*)$/gm, '<h2>$1</h2>')
    .replace(/^#\s+(.*)$/gm, '<h1>$1</h1>')
    .replace(/!\[([^\]]*)\]\(([^)\s]+)\)/g, (_, alt, url) => {
      const src = safeUrl(url, { image: true })
      if (!src) return alt
      return `<img src="${escapeAttr(src)}" alt="${escapeAttr(alt)}">`
    })
    .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (_, label, url) => {
      const href = safeUrl(url)
      if (!href) return label
      return `<a href="${escapeAttr(href)}" target="_blank" rel="noopener noreferrer">${label}</a>`
    })
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`([^`\n]+)`/g, '<code>$1</code>')

  const lines = inlineParsed.split('\n')
  const out = []
  let inParagraph = false
  let inUnorderedList = false
  let inOrderedList = false
  let inTable = false

  const closeParagraph = () => {
    if (inParagraph) {
      out.push('</p>')
      inParagraph = false
    }
  }
  const closeUnorderedList = () => {
    if (inUnorderedList) {
      out.push('</ul>')
      inUnorderedList = false
    }
  }
  const closeOrderedList = () => {
    if (inOrderedList) {
      out.push('</ol>')
      inOrderedList = false
    }
  }
  const closeTable = () => {
    if (inTable) {
      out.push('</tbody></table>')
      inTable = false
    }
  }

  lines.forEach((line) => {
    const trimmed = line.trim()

    // 空行：只关闭段落和表格，不关闭列表
    if (!trimmed) {
      closeParagraph()
      closeTable()
      return
    }

    // 表格分隔行 "|---|---|"
    if (/^\|[\s\-:|]+\|$/.test(trimmed)) {
      return // 跳过分隔行
    }

    // 表格行 "| xxx | xxx |"
    const tableRowMatch = trimmed.match(/^\|(.+)\|$/)
    if (tableRowMatch) {
      closeParagraph()
      closeUnorderedList()
      closeOrderedList()
      const cells = tableRowMatch[1].split('|').map(c => c.trim())
      if (!inTable) {
        out.push('<table><thead><tr>')
        cells.forEach(c => { out.push(`<th>${c}</th>`) })
        out.push('</tr></thead><tbody>')
        inTable = true
      } else {
        out.push('<tr>')
        cells.forEach(c => { out.push(`<td>${c}</td>`) })
        out.push('</tr>')
      }
      return
    }

    if (/^<h[1-4]>.*<\/h[1-4]>$/.test(trimmed)) {
      closeParagraph()
      closeUnorderedList()
      closeOrderedList()
      closeTable()
      out.push(trimmed)
      return
    }

    // 无序列表项 "- xxx"
    const unorderedMatch = trimmed.match(/^\-\s+(.*)$/)
    if (unorderedMatch) {
      closeParagraph()
      closeTable()
      if (inOrderedList) {
        out.push(`<ul class="sub-list"><li>${unorderedMatch[1]}</li></ul>`)
        return
      }
      closeOrderedList()
      if (!inUnorderedList) {
        out.push('<ul>')
        inUnorderedList = true
      }
      out.push(`<li>${unorderedMatch[1]}</li>`)
      return
    }

    // 有序列表项 "1. xxx"
    const orderedMatch = trimmed.match(/^\d+\.\s+(.*)$/)
    if (orderedMatch) {
      closeParagraph()
      closeUnorderedList()
      closeTable()
      if (!inOrderedList) {
        out.push('<ol>')
        inOrderedList = true
      }
      out.push(`<li>${orderedMatch[1]}</li>`)
      return
    }

    // 当前在列表中 → 当前文本作为上一个 <li> 的延续内容
    if (inOrderedList || inUnorderedList) {
      // 追加到上一个 li 的内容后面
      const lastLi = out.length - 1
      if (lastLi >= 0 && out[lastLi].endsWith('</li>')) {
        out[lastLi] = out[lastLi].replace(/<\/li>$/, `<br>${trimmed}</li>`)
      } else {
        out.push(`<br>${trimmed}`)
      }
      return
    }

    // 普通段落
    closeUnorderedList()
    closeOrderedList()
    closeTable()
    if (!inParagraph) {
      out.push('<p>')
      inParagraph = true
      out.push(trimmed)
    } else {
      out.push(`<br>${trimmed}`)
    }
  })

  closeParagraph()
  closeUnorderedList()
  closeOrderedList()
  closeTable()

  const combined = out.join('')
  return combined.replace(/@@CODE_BLOCK_(\d+)@@/g, (_, index) => {
    const code = codeBlocks[Number(index)] || ''
    return `<pre><code>${code}</code></pre>`
  })
}
