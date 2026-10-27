import { createApp } from 'vue'

import './styles/index.scss'
import App from './App.vue'
import { parallax } from './directives/parallax'
import { reveal } from './directives/reveal'
import { spotlight } from './directives/spotlight'
import { tilt } from './directives/tilt'
import router from './router'
import { initTheme } from './composables/useTheme'

// 浏览器默认会恢复刷新前的滚动位置，和 router 的「新页面回顶部」互相打架，
// 表现就是刷新后停在页面底部。交给 router 统一管理，刷新一律从顶部开始。
if ('scrollRestoration' in history) history.scrollRestoration = 'manual'

// 在挂载前写入 data-theme，避免首屏先亮后暗闪一下
initTheme()

createApp(App)
  .use(router)
  .directive('reveal', reveal)
  .directive('spotlight', spotlight)
  .directive('tilt', tilt)
  .directive('parallax', parallax)
  .mount('#app')
