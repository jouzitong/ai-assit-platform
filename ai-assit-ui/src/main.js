import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './style.scss'
import App from './App.vue'
import router from './router'
import { initTheme } from './assets/style/themes/theme-manager'
import { preloadServiceEnums } from './store/enums'

initTheme()

preloadServiceEnums().finally(() => {
  createApp(App).use(router).use(ElementPlus).mount('#app')
})
