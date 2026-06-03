import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'
import { THEME_STORAGE_KEY } from './utils/session'

const savedTheme = window.localStorage.getItem(THEME_STORAGE_KEY)
document.documentElement.dataset.theme = savedTheme === 'dark' ? 'dark' : 'light'

createApp(App).use(router).mount('#app')
