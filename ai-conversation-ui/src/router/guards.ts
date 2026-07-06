import type { Router } from 'vue-router'
import { buildLoginPath, getToken, normalizeRedirectPath } from '../utils/session'

export function setupRouterGuards(router: Router) {
  router.beforeEach((to) => {
    document.title = typeof to.meta.title === 'string' ? to.meta.title : 'AI Conversation UI'

    const token = getToken()
    const isPublicRoute = to.matched.some((record) => record.meta?.public)

    if (isPublicRoute) {
      if (!token) {
        return true
      }

      const redirectPath = normalizeRedirectPath(typeof to.query.redirect === 'string' ? to.query.redirect : '/')
      return redirectPath || '/'
    }

    if (token) {
      return true
    }

    return buildLoginPath(to.fullPath)
  })
}
