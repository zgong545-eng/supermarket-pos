import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import Vant from 'vant'
import 'vant/lib/index.css'

import App from './App.vue'
import router from './router'
import './styles.css'

const app = createApp(App)

// Element Plus 与 Vant 同时注册：管理端/收银端用前者，H5 用后者。
// 两者样式会互相影响，所以 H5 布局外面套了一层 .h5-scope 做样式隔离。
app.use(ElementPlus, { locale: zhCn })
app.use(Vant)
for (const [key, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, comp)
}

app.use(router)
app.mount('#app')
