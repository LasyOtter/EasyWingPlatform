import type { App } from 'vue'
import { h } from 'vue'

// 创建 SVG 图标组件
function createSvgIcon(name: string, icon: string) {
  return defineComponent({
    name: `SvgIcon-${name}`,
    props: {
      size: {
        type: [Number, String],
        default: 16
      }
    },
    setup(props) {
      return () =>
        h('svg', { class: 'svg-icon', 'aria-hidden': true, width: props.size, height: props.size }, [
          h('use', { 'xlink:href': `#icon-${name}` })
        ])
    }
  })
}

// 全局组件列表
const globalComponents: Record<string, any> = {
  // 可以在此添加全局组件
}

// 注册全局组件
export function setupGlobComponents(app: App) {
  // 注册 SVG 图标组件
  // const svgIcon = createSvgIcon('icon', '')

  // 注册其他全局组件
  Object.keys(globalComponents).forEach((key) => {
    app.component(key, globalComponents[key])
  })
}

export default {
  install(app: App) {
    setupGlobComponents(app)
  }
}
