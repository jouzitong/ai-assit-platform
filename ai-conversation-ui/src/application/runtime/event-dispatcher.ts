import type {
  RendererAction,
  RendererQueryState,
} from '../schema'

export type RuntimeRendererEventType =
  | 'queryChange'
  | 'reload'
  | 'reset'
  | 'submit'
  | 'action'
  | 'itemAction'
  | 'valueChange'
  | string

export type RuntimeHookName =
  | 'beforeEvent'
  | 'afterEvent'
  | 'onQueryChange'
  | 'beforeLoad'
  | 'afterLoad'
  | 'beforeAction'
  | 'afterAction'

export interface RuntimeEventSource {
  renderer?: string
  componentId?: string
  event?: string
}

export interface RuntimeRendererEvent<TPayload = unknown> {
  type: RuntimeRendererEventType
  source?: RuntimeEventSource
  payload?: TPayload
  meta?: Record<string, unknown>
}

export interface RuntimeActionPayload {
  action: RendererAction
  row?: Record<string, unknown>
  [key: string]: unknown
}

export interface RuntimeEventContext {
  document?: Record<string, unknown> | null
  renderer?: string
  schema?: unknown
  query?: Partial<RendererQueryState>
  state?: Record<string, unknown>
  [key: string]: unknown
}

export interface RuntimeHookPayload {
  event: RuntimeRendererEvent
  context: RuntimeEventContext
}

export interface RuntimeEventDispatcherOptions {
  getContext: () => RuntimeEventContext
  updateQuery?: (query: Partial<RendererQueryState>, event: RuntimeRendererEvent) => void
  reload?: (query: Partial<RendererQueryState>, event: RuntimeRendererEvent) => void | Promise<void>
  executeAction?: (payload: RuntimeActionPayload, event: RuntimeRendererEvent) => void | Promise<void>
  runHook?: (name: RuntimeHookName, payload: RuntimeHookPayload) => void | Promise<void>
  onUnhandledEvent?: (event: RuntimeRendererEvent, context: RuntimeEventContext) => void | Promise<void>
}

export function createRuntimeEventDispatcher(options: RuntimeEventDispatcherOptions) {
  async function runHook(name: RuntimeHookName, event: RuntimeRendererEvent) {
    await options.runHook?.(name, {
      event,
      context: options.getContext(),
    })
  }

  async function reloadWithHooks(query: Partial<RendererQueryState>, event: RuntimeRendererEvent) {
    await runHook('beforeLoad', event)
    await options.reload?.(query, event)
    await runHook('afterLoad', event)
  }

  async function executeActionWithHooks(event: RuntimeRendererEvent) {
    const payload = event.payload as RuntimeActionPayload | undefined
    if (!payload?.action) {
      await options.onUnhandledEvent?.(event, options.getContext())
      return
    }

    await runHook('beforeAction', event)
    if (payload.action.action === 'RELOAD') {
      await reloadWithHooks(options.getContext().query || {}, event)
    } else {
      await options.executeAction?.(payload, event)
    }
    await runHook('afterAction', event)
  }

  return {
    async dispatch(event: RuntimeRendererEvent) {
      await runHook('beforeEvent', event)

      if (event.type === 'queryChange') {
        const query = event.payload as Partial<RendererQueryState>
        options.updateQuery?.(query, event)
        await runHook('onQueryChange', event)
      } else if (event.type === 'reload' || event.type === 'reset' || event.type === 'submit') {
        const query = event.payload as Partial<RendererQueryState> | undefined
        if (query) {
          options.updateQuery?.(query, event)
        }
        await reloadWithHooks(query || options.getContext().query || {}, event)
      } else if (event.type === 'action' || event.type === 'itemAction') {
        await executeActionWithHooks(event)
      } else if (event.type === 'valueChange') {
        const query = event.payload as Partial<RendererQueryState> | undefined
        if (query) {
          options.updateQuery?.(query, event)
        }
      } else {
        await options.onUnhandledEvent?.(event, options.getContext())
      }

      await runHook('afterEvent', event)
    },
  }
}
