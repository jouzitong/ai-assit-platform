import type { Router } from 'vue-router'

export function setupRouterGuards(router: Router) {
  router.beforeEach((to) => {
    document.title = typeof to.meta.title === 'string' ? to.meta.title : 'AI Conversation UI'
  })
}
