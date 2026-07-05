import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { registerLoadingDirective } from './directives/loading'
import { registerPermissionDirective } from './directives/permission'
import './styles/index.scss'

const app = createApp(App)

registerPermissionDirective(app)
registerLoadingDirective(app)

app.use(router).use(ElementPlus).mount('#app')
