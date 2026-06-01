<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const open = ref(false)
const input = ref('')
const dock = ref('right')
const drag = ref({ x: 24, y: 88 })
const dragging = ref(false)

const messages = ref([
  { role: 'assistant', text: '你好，我是 EMP 智能助手。支持智能问数和目标页面生成跳转。' }
])

function send(raw) {
  const text = raw.trim()
  if (!text) return

  messages.value.push({ role: 'user', text })

  if (text.includes('出勤') || text.includes('问数') || text.includes('统计')) {
    messages.value.push({ role: 'assistant', text: '问数结果：本周出勤率 97.2%，异常打卡 9 次。要不要按部门展开？' })
  } else if (text.includes('生成') || text.includes('页面') || text.includes('跳转')) {
    router.push('/emp/performance')
    messages.value.push({ role: 'assistant', text: '已生成绩效洞察页面，并为你完成跳转。' })
  } else {
    messages.value.push({ role: 'assistant', text: '你可以试试："查询本月人力成本同比" 或 "生成考勤分析页面并跳转"。' })
  }

  input.value = ''
}

function toggleDock() {
  dock.value = dock.value === 'right' ? 'left' : 'right'
}

function onDragStart(e) {
  dragging.value = true
  const startX = e.clientX
  const startY = e.clientY
  const baseX = drag.value.x
  const baseY = drag.value.y

  function onMove(evt) {
    const dx = evt.clientX - startX
    const dy = evt.clientY - startY
    drag.value = {
      x: Math.max(8, baseX + dx),
      y: Math.max(8, baseY + dy)
    }
  }

  function onUp() {
    dragging.value = false
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
  }

  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
}
</script>

<template>
  <div class="chat-fab-wrap" :class="dock" :style="{ bottom: `${drag.y}px`, [dock === 'right' ? 'right' : 'left']: `${drag.x}px` }">
    <button v-if="!open" class="chat-fab" @click="open = true">🤖</button>

    <section v-else class="chat-panel" :class="{ dragging }">
      <header class="chat-head" @pointerdown="onDragStart">
        <strong>EMP 智能助手</strong>
        <div class="chat-actions">
          <button class="mini" @click.stop="toggleDock">切侧边</button>
          <button class="mini" @click.stop="open = false">收起</button>
        </div>
      </header>

      <div class="chat-body">
        <div v-for="(item, idx) in messages" :key="idx" class="bubble" :class="item.role">{{ item.text }}</div>
      </div>

      <form class="chat-input" @submit.prevent="send(input)">
        <input v-model="input" placeholder="输入问题或“生成页面并跳转”" />
        <button type="submit">发送</button>
      </form>
    </section>
  </div>
</template>
