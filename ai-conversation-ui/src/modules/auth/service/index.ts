import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'
import { loginAuth } from '../api'
import { setSession } from '../../../utils/session'

export async function submitLogin(
  form: { username: string; password: string; tenantId?: string },
  route: RouteLocationNormalizedLoaded,
  router: Router,
) {
  const response = await loginAuth({
    username: form.username.trim(),
    password: form.password,
    tenantId: (form.tenantId || '').trim(),
    credentialType: 'PASSWORD',
  })

  const loginToken = response?.token
  const loginUser = response?.user
  if (!loginToken) {
    throw new Error('登录接口未返回 token')
  }

  setSession({
    token: loginToken,
    user: loginUser,
  })

  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/ai/chat'
  await router.push(redirect)
}
