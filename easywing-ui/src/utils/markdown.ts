import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.use({
  gfm: true,
  breaks: true
})

/**
 * 代码块渲染为带复制按钮的 HTML。
 *
 * 返回 HTML 字符串而非 VNode：AI 回复是流式拼接的长文本，按 token 逐段渲染
 * 比在模板里维护增量 DOM 更简单可靠。输出经 DOMPurify 清洗后再交给 v-html。
 */
export function renderMarkdown(source: string): string {
  if (!source) {
    return ''
  }

  const html = marked.parse(source, { async: false }) as string
  return DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true },
    FORBID_TAGS: ['style', 'form', 'input', 'button', 'iframe', 'object', 'embed'],
    FORBID_ATTR: ['style', 'onerror', 'onload', 'onclick']
  })
}

/** 从 Markdown 中提取首个标题，用于会话自动命名。 */
export function extractFirstLine(source: string, maxLength = 40): string {
  const plain = (source || '')
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/[#>*_`~-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  if (!plain) {
    return ''
  }
  return plain.length > maxLength ? `${plain.slice(0, maxLength)}…` : plain
}
