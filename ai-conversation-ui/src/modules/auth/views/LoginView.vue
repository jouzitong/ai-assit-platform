<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { submitLogin } from '../service'

const formState = reactive({
  username: '',
  password: '',
  tenantId: '',
  remember: true,
})

const passwordVisible = ref(false)
const submitting = ref(false)
const networkTransform = ref('translate(0px, 0px)')
const route = useRoute()
const router = useRouter()

const submitLabel = computed(() => (submitting.value ? '身份验证中...' : '进入系统'))
const passwordInputType = computed(() => (passwordVisible.value ? 'text' : 'password'))

function handleTogglePassword() {
  passwordVisible.value = !passwordVisible.value
}

function handlePointerMove(event: MouseEvent) {
  const x = (event.clientX / window.innerWidth - 0.5) * 20
  const y = (event.clientY / window.innerHeight - 0.5) * 20
  networkTransform.value = `translate(${x}px, ${y}px)`
}

async function handleSubmit() {
  if (submitting.value) {
    return
  }

  if (!formState.username.trim()) {
    ElMessage.error('请输入账号')
    return
  }

  if (!formState.password) {
    ElMessage.error('请输入密码')
    return
  }

  submitting.value = true
  try {
    await submitLogin(formState, route, router)
    ElMessage.success('登录成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page" @pointermove="handlePointerMove">
    <div class="technology-bg">
      <div class="grid"></div>
      <div class="glow glow-1"></div>
      <div class="glow glow-2"></div>
      <div class="glow glow-3"></div>
      <div class="scan-line"></div>
    </div>

    <section class="brand-section">
      <div class="brand-logo">
        <div class="logo-icon"></div>
        <div class="brand-name">智能问数平台</div>
      </div>

      <div class="network" :style="{ transform: networkTransform }">
        <div class="network-ring ring-1">
          <span class="network-dot dot-1"></span>
          <span class="network-dot dot-2"></span>
        </div>
        <div class="network-ring ring-2">
          <span class="network-dot dot-1"></span>
          <span class="network-dot dot-3"></span>
        </div>
        <div class="network-ring ring-3">
          <span class="network-dot dot-2"></span>
        </div>
      </div>

      <div class="brand-content">
        <div class="brand-tag">
          <span class="brand-tag-dot"></span>
          AI DATA INTELLIGENCE
        </div>

        <h1 class="brand-title">
          让数据<br />
          <strong>理解你的问题</strong>
        </h1>

        <p class="brand-description">
          基于人工智能的数据分析平台，通过自然语言完成数据查询、智能分析与业务洞察，让复杂的数据世界变得简单。
        </p>

        <div class="data-flow">
          <div class="data-item">
            <div class="data-value">AI</div>
            <div class="data-label">智能理解</div>
          </div>

          <div class="data-item">
            <div class="data-value">SQL</div>
            <div class="data-label">自动查询</div>
          </div>

          <div class="data-item">
            <div class="data-value">BI</div>
            <div class="data-label">数据洞察</div>
          </div>
        </div>
      </div>

      <div class="brand-footer">
        <span class="brand-footer-line"></span>
        INTELLIGENT DATA PLATFORM · 2026
      </div>
    </section>

    <section class="login-section">
      <div class="login-card">
        <div class="card-light"></div>

        <div class="login-header">
          <h2>欢迎回来</h2>
          <p>登录您的账号，进入智能数据世界</p>
        </div>

        <form @submit.prevent="handleSubmit">
          <div class="form-group">
            <label class="form-label">账号</label>
            <div class="input-wrapper">
              <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                <circle cx="12" cy="8" r="4"></circle>
                <path d="M4 21c0-4 3.6-7 8-7s8 3 8 7"></path>
              </svg>
              <input
                v-model="formState.username"
                class="form-input"
                type="text"
                placeholder="请输入账号"
                autocomplete="username"
              />
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">密码</label>
            <div class="input-wrapper">
              <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                <rect x="4" y="10" width="16" height="11" rx="2"></rect>
                <path d="M8 10V7a4 4 0 0 1 8 0v3"></path>
              </svg>
              <input
                v-model="formState.password"
                class="form-input"
                :type="passwordInputType"
                placeholder="请输入密码"
                autocomplete="current-password"
              />
              <button class="password-toggle" type="button" aria-label="切换密码显示" @click="handleTogglePassword">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                  <path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z"></path>
                  <circle cx="12" cy="12" r="2.5"></circle>
                </svg>
              </button>
            </div>
          </div>

          <div class="form-options">
            <label class="remember">
              <input v-model="formState.remember" type="checkbox" />
              记住登录状态
            </label>

            <button class="forgot" type="button">忘记密码？</button>
          </div>

          <button class="login-button" type="submit" :disabled="submitting">
            {{ submitLabel }}
          </button>
        </form>

        <div class="system-status">
          <span class="status-dot"></span>
          系统服务运行正常
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  display: flex;
  min-height: 100vh;
  overflow: hidden;
  color: #f3f7ff;
  background:
    radial-gradient(circle at 20% 30%, rgba(47, 101, 255, 0.12), transparent 35%),
    radial-gradient(circle at 80% 70%, rgba(56, 220, 255, 0.08), transparent 35%),
    #050912;
}

.technology-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.grid {
  position: absolute;
  inset: -50%;
  background-image:
    linear-gradient(rgba(77, 138, 255, 0.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(77, 138, 255, 0.055) 1px, transparent 1px);
  background-size: 64px 64px;
  transform: perspective(700px) rotateX(58deg) translateY(30%);
  transform-origin: center center;
  animation: grid-move 20s linear infinite;
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(120px);
  opacity: 0.45;
}

.glow-1 {
  width: 500px;
  height: 500px;
  top: -200px;
  left: -100px;
  background: rgba(44, 109, 255, 0.42);
}

.glow-2 {
  width: 450px;
  height: 450px;
  right: -180px;
  bottom: -180px;
  background: rgba(47, 212, 255, 0.22);
}

.glow-3 {
  width: 300px;
  height: 300px;
  left: 42%;
  top: 35%;
  background: rgba(122, 75, 255, 0.12);
}

.scan-line {
  position: absolute;
  top: -10%;
  left: 0;
  width: 100%;
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(67, 214, 255, 0.5), transparent);
  box-shadow: 0 0 20px rgba(67, 214, 255, 0.3);
  animation: scan 9s linear infinite;
}

.brand-section {
  position: relative;
  z-index: 2;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 46px 64px 52px;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 14px;
}

.logo-icon {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border: 1px solid rgba(100, 170, 255, 0.35);
  border-radius: 12px;
  background: linear-gradient(145deg, rgba(49, 121, 255, 0.35), rgba(29, 209, 255, 0.12));
  box-shadow:
    inset 0 0 20px rgba(78, 153, 255, 0.15),
    0 0 25px rgba(59, 134, 255, 0.15);
}

.logo-icon::before,
.logo-icon::after {
  content: '';
  position: absolute;
  border-radius: 50%;
}

.logo-icon::before {
  width: 18px;
  height: 18px;
  border: 2px solid #7cb5ff;
}

.logo-icon::after {
  width: 5px;
  height: 5px;
  background: #51e4ff;
  box-shadow:
    0 0 8px #51e4ff,
    0 0 15px rgba(81, 228, 255, 0.8);
}

.brand-name {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 1px;
}

.brand-content {
  max-width: 700px;
  margin-top: -30px;
}

.brand-tag {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 26px;
  padding: 7px 14px;
  border: 1px solid rgba(89, 161, 255, 0.18);
  border-radius: 30px;
  color: #8ebcff;
  background: rgba(54, 111, 211, 0.08);
  font-size: 12px;
  letter-spacing: 1.5px;
}

.brand-tag-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #4be3ff;
  box-shadow:
    0 0 8px #4be3ff,
    0 0 15px rgba(75, 227, 255, 0.6);
  animation: pulse 2s infinite;
}

.brand-title {
  margin-bottom: 24px;
  font-size: clamp(44px, 5vw, 76px);
  font-weight: 300;
  line-height: 1.12;
  letter-spacing: -3px;
}

.brand-title strong {
  background: linear-gradient(90deg, #f4f8ff, #78aaff 50%, #47defb);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  font-weight: 600;
}

.brand-description {
  max-width: 580px;
  color: #8e9db5;
  font-size: 16px;
  line-height: 1.9;
  letter-spacing: 0.3px;
}

.data-flow {
  display: flex;
  gap: 14px;
  margin-top: 42px;
}

.data-item {
  min-width: 140px;
  padding: 16px 18px;
  border: 1px solid rgba(121, 163, 255, 0.12);
  border-radius: 12px;
  background: rgba(14, 28, 51, 0.3);
  backdrop-filter: blur(10px);
}

.data-value {
  margin-bottom: 5px;
  font-size: 22px;
  font-weight: 500;
  color: #dceaff;
}

.data-label {
  font-size: 11px;
  color: #62728c;
  letter-spacing: 1px;
}

.brand-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #4c5d78;
  font-size: 12px;
}

.brand-footer-line {
  width: 40px;
  height: 1px;
  background: linear-gradient(90deg, rgba(81, 147, 255, 0.7), transparent);
}

.network {
  position: absolute;
  top: 14%;
  left: 5%;
  width: 620px;
  height: 620px;
  opacity: 0.45;
  pointer-events: none;
  transition: transform 0.2s ease-out;
}

.network-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  border: 1px solid rgba(87, 155, 255, 0.12);
  border-radius: 50%;
  transform: translate(-50%, -50%);
}

.ring-1 {
  width: 420px;
  height: 420px;
  animation: rotate 28s linear infinite;
}

.ring-2 {
  width: 300px;
  height: 300px;
  animation: rotate-reverse 20s linear infinite;
}

.ring-3 {
  width: 180px;
  height: 180px;
  animation: rotate 16s linear infinite;
}

.network-dot {
  position: absolute;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #5be1ff;
  box-shadow:
    0 0 10px #5be1ff,
    0 0 20px rgba(91, 225, 255, 0.4);
}

.dot-1 {
  top: -4px;
  left: 50%;
}

.dot-2 {
  top: 50%;
  right: -4px;
}

.dot-3 {
  bottom: -4px;
  left: 30%;
}

.login-section {
  position: relative;
  z-index: 5;
  width: 520px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 52px 40px 20px;
}

.login-card {
  position: relative;
  width: 100%;
  max-width: 420px;
  padding: 38px;
  overflow: hidden;
  border: 1px solid rgba(125, 164, 255, 0.16);
  border-radius: 20px;
  background: linear-gradient(145deg, rgba(16, 28, 50, 0.8), rgba(6, 14, 28, 0.7));
  box-shadow:
    0 30px 80px rgba(0, 0, 0, 0.38),
    inset 0 1px rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(30px);
}

.login-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 12%;
  width: 76%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(96, 190, 255, 0.7), transparent);
}

.card-light {
  position: absolute;
  top: -120px;
  right: -100px;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  background: rgba(45, 109, 255, 0.12);
  filter: blur(60px);
  pointer-events: none;
}

.login-header {
  position: relative;
  margin-bottom: 34px;
}

.login-header h2 {
  margin: 0 0 10px;
  font-size: 28px;
  font-weight: 500;
  letter-spacing: 1px;
}

.login-header p {
  margin: 0;
  color: #72839d;
  font-size: 13px;
}

.form-group {
  margin-bottom: 20px;
}

.form-label {
  display: block;
  margin-bottom: 9px;
  color: #a7b5c9;
  font-size: 12px;
}

.input-wrapper {
  position: relative;
}

.input-icon {
  position: absolute;
  top: 50%;
  left: 15px;
  width: 18px;
  height: 18px;
  transform: translateY(-50%);
  color: #53647e;
  transition: 0.3s;
}

.form-input {
  width: 100%;
  height: 50px;
  padding: 0 44px 0 46px;
  border: 1px solid rgba(117, 153, 205, 0.13);
  border-radius: 10px;
  outline: none;
  background: rgba(4, 11, 24, 0.5);
  color: #eef5ff;
  font-size: 14px;
  transition: 0.3s;
}

.form-input::placeholder {
  color: #45546a;
}

.form-input:focus {
  border-color: rgba(78, 153, 255, 0.58);
  background: rgba(8, 18, 36, 0.72);
  box-shadow:
    0 0 0 3px rgba(71, 133, 255, 0.08),
    inset 0 0 20px rgba(59, 132, 255, 0.04);
}

.input-wrapper:focus-within .input-icon {
  color: #67a6ff;
}

.password-toggle {
  position: absolute;
  top: 50%;
  right: 14px;
  border: none;
  color: #53647e;
  background: transparent;
  cursor: pointer;
  transform: translateY(-50%);
}

.password-toggle:hover {
  color: #8ebcff;
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 26px;
}

.remember {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #718099;
  font-size: 12px;
  cursor: pointer;
}

.remember input {
  accent-color: #4d8dff;
}

.forgot {
  border: none;
  color: #72a8ff;
  background: transparent;
  font-size: 12px;
  cursor: pointer;
}

.login-button {
  position: relative;
  width: 100%;
  height: 52px;
  overflow: hidden;
  border: 1px solid rgba(109, 184, 255, 0.5);
  border-radius: 10px;
  color: #ffffff;
  background: linear-gradient(100deg, #2767dc, #437ff0 52%, #258fc7);
  box-shadow:
    0 10px 30px rgba(42, 105, 220, 0.25),
    inset 0 1px rgba(255, 255, 255, 0.2);
  font-size: 14px;
  letter-spacing: 3px;
  cursor: pointer;
  transition:
    transform 0.25s,
    box-shadow 0.25s;
}

.login-button::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 80%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.25), transparent);
  transform: skewX(-20deg);
  transition: 0.7s;
}

.login-button:hover {
  transform: translateY(-2px);
  box-shadow:
    0 15px 40px rgba(42, 105, 220, 0.4),
    0 0 30px rgba(69, 152, 255, 0.14);
}

.login-button:hover::before {
  left: 140%;
}

.login-button:active {
  transform: translateY(0);
}

.login-button:disabled {
  cursor: wait;
  opacity: 0.88;
}

.system-status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 25px;
  color: #526179;
  font-size: 11px;
}

.status-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #35e09e;
  box-shadow: 0 0 8px rgba(53, 224, 158, 0.8);
}

@keyframes grid-move {
  from {
    background-position: 0 0;
  }

  to {
    background-position: 0 64px;
  }
}

@keyframes scan {
  0% {
    top: -5%;
    opacity: 0;
  }

  10%,
  90% {
    opacity: 1;
  }

  100% {
    top: 105%;
    opacity: 0;
  }
}

@keyframes pulse {
  50% {
    opacity: 0.45;
    transform: scale(0.8);
  }
}

@keyframes rotate {
  to {
    transform: translate(-50%, -50%) rotate(360deg);
  }
}

@keyframes rotate-reverse {
  to {
    transform: translate(-50%, -50%) rotate(-360deg);
  }
}

@media (max-width: 1000px) {
  .brand-section {
    padding: 40px;
  }

  .brand-title {
    font-size: 48px;
  }

  .data-flow {
    flex-wrap: wrap;
  }

  .login-section {
    width: 480px;
    padding-right: 30px;
  }
}

@media (max-width: 800px) {
  .brand-section {
    display: none;
  }

  .login-section {
    width: 100%;
    min-height: 100vh;
    padding: 24px;
  }

  .login-card {
    max-width: 430px;
  }
}

@media (max-width: 480px) {
  .login-card {
    padding: 30px 24px;
    border-radius: 16px;
  }

  .login-header h2 {
    font-size: 25px;
  }
}
</style>
