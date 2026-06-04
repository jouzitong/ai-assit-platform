import { loginAuth } from '../../../../../api/auth'
import { setSession } from '../../../../../utils/session'

export async function submitLogin(form, route, router) {
  const response = await loginAuth({
    username: form.username.trim(),
    password: form.password,
    tenantId: form.tenant.trim(),
    credentialType: 'PASSWORD'
  })

  const loginToken = response?.token ?? response?.data?.token
  const loginUser = response?.user ?? response?.data?.user
  if (!loginToken) {
    throw new Error('登录接口未返回 token')
  }

  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
  setSession({
    token: loginToken,
    user: loginUser
  })

  await router.push(redirect)
}
