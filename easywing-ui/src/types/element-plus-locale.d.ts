/**
 * Element Plus 的 locale 子路径未提供 .d.ts，这里补齐类型声明。
 */
declare module 'element-plus/dist/locale/*.mjs' {
  import type { Language } from 'element-plus/es/locale'
  const locale: Language
  export default locale
}
