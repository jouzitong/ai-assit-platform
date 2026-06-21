import { createApp } from 'vue'
import './style.scss'
import App from './App.vue'
import router from './router'
import { initTheme } from './assets/style/themes/theme-manager'
import { preloadServiceEnums } from './store/enums'

initTheme()

preloadServiceEnums().finally(() => {
  createApp(App).use(router).mount('#app')
})
