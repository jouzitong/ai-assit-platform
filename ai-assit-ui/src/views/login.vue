<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { setSession } from '../utils/session'

const route = useRoute()
const router = useRouter()
const submitting = ref(false)

const form = reactive({
  username: 'admin',
  password: '',
  tenant: 'OKX-HR'
})

const canSubmit = computed(() => form.username.trim() && form.password.trim() && form.tenant.trim())

async function handleSubmit() {
  if (!canSubmit.value || submitting.value) {
    return
  }

  submitting.value = true

  const token = `mock-token-${Date.now()}`
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'

  setSession({
    token,
    user: {
      name: form.username.trim(),
      tenant: form.tenant.trim()
    }
  })

  await router.push(redirect)
  submitting.value = false
}
</script>

<template>
  <main class="login-page">
    <section class="login-hero">
      <p class="login-kicker">EMP AI Assistant</p>
      <h1>统一登录入口</h1>
      <p class="login-desc">
        连接组织、人效分析和智能问数能力，先完成身份校验，再进入 EMP Console。
      </p>

      <div class="login-highlights">
        <article class="login-highlight-card">
          <strong>智能问数</strong>
          <span>对接人效、考勤与绩效数据，直接完成自然语言分析。</span>
        </article>
        <article class="login-highlight-card">
          <strong>统一控制台</strong>
          <span>个人设置、系统管理、主题切换都集中在同一入口。</span>
        </article>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-panel-head">
        <h2>账号登录</h2>
        <p>当前为前端演示态，提交后会写入本地会话。</p>
      </div>

      <form class="login-form" @submit.prevent="handleSubmit">
        <label class="login-field">
          <span>租户编码</span>
          <input v-model="form.tenant" type="text" placeholder="请输入租户编码" />
        </label>

        <label class="login-field">
          <span>用户名</span>
          <input v-model="form.username" type="text" placeholder="请输入用户名" />
        </label>

        <label class="login-field">
          <span>密码</span>
          <input v-model="form.password" type="password" placeholder="请输入密码" />
        </label>

        <button class="login-submit" type="submit" :disabled="!canSubmit || submitting">
          {{ submitting ? '登录中...' : '登录并进入控制台' }}
        </button>
      </form>
    </section>
  </main>
</template>
