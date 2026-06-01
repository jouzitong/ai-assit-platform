<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const collapsed = ref(false)

const menus = [
  { path: '/home', label: 'AI 首页', short: '首页' },
  { path: '/emp/attendance', label: '考勤看板', short: '考勤' },
  { path: '/emp/performance', label: '绩效洞察', short: '绩效' },
  { path: '/emp/cost', label: '人力成本分析', short: '成本' }
]

const settingsMenus = [
  { path: '/settings/profile', label: '个人管理', short: '个人' },
  { path: '/settings/system', label: '系统管理', short: '系统' }
]
</script>

<template>
  <aside class="sidebar" :class="{ collapsed }">
    <div class="brand-row">
      <h2>{{ collapsed ? 'EMP' : 'EMP Console' }}</h2>
      <button class="collapse-btn" @click="collapsed = !collapsed">{{ collapsed ? '>' : '<' }}</button>
    </div>

    <nav class="menu-group">
      <RouterLink
        v-for="item in menus"
        :key="item.path"
        :to="item.path"
        class="menu-link"
        :class="{ active: route.path === item.path }"
        :title="item.label"
      >
        {{ collapsed ? item.short : item.label }}
      </RouterLink>
    </nav>

    <div class="settings-group">
      <p v-if="!collapsed">个人设置</p>
      <RouterLink
        v-for="item in settingsMenus"
        :key="item.path"
        :to="item.path"
        class="menu-link"
        :class="{ active: route.path === item.path }"
        :title="item.label"
      >
        {{ collapsed ? item.short : item.label }}
      </RouterLink>
    </div>
  </aside>
</template>
