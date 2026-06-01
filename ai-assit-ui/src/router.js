import { createRouter, createWebHistory } from 'vue-router'
import IndexPage from './views/index.vue'
import AttendancePage from './views/attendance.vue'
import PerformancePage from './views/performance.vue'
import CostPage from './views/cost.vue'
import ProfilePage from './views/profile.vue'
import SystemPage from './views/system.vue'

const routes = [
  { path: '/', redirect: '/home' },
  { path: '/home', component: IndexPage, meta: { title: '首页' } },
  { path: '/emp/attendance', component: AttendancePage, meta: { title: '考勤看板' } },
  { path: '/emp/performance', component: PerformancePage, meta: { title: '绩效洞察' } },
  { path: '/emp/cost', component: CostPage, meta: { title: '人力成本分析' } },
  { path: '/settings/profile', component: ProfilePage, meta: { title: '个人管理' } },
  { path: '/settings/system', component: SystemPage, meta: { title: '系统管理' } }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
