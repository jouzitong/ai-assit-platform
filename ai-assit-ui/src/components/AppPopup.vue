<script setup>
import { popupState } from '../utils/popup'
</script>

<template>
  <Teleport to="body">
    <Transition name="app-popup">
      <div v-if="popupState.visible && popupState.message" class="app-popup-host" :style="{ top: popupState.offsetTop }">
        <section class="app-popup-card" :class="`is-${popupState.type}`" role="status" aria-live="polite">
          <span class="app-popup-badge">{{ popupState.badge }}</span>
          <div class="app-popup-copy">
            <strong>{{ popupState.title }}</strong>
            <p>{{ popupState.message }}</p>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.app-popup-host {
  position: fixed;
  left: 50%;
  z-index: 1200;
  transform: translateX(-50%);
  pointer-events: none;
}

.app-popup-card {
  min-width: min(420px, calc(100vw - 32px));
  max-width: min(520px, calc(100vw - 32px));
  display: grid;
  grid-template-columns: 40px 1fr;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.16);
  backdrop-filter: blur(16px);
}

.app-popup-badge {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: #fff;
}

.app-popup-copy strong {
  display: block;
  margin-bottom: 2px;
  font-size: 14px;
}

.app-popup-copy p {
  margin: 0;
  font-size: 13px;
  color: #475569;
}

.app-popup-card.is-success .app-popup-badge {
  background: linear-gradient(135deg, #16a34a, #22c55e);
}

.app-popup-card.is-error .app-popup-badge {
  background: linear-gradient(135deg, #dc2626, #f97316);
}

.app-popup-card.is-warning .app-popup-badge {
  background: linear-gradient(135deg, #d97706, #f59e0b);
}

.app-popup-card.is-info .app-popup-badge {
  background: linear-gradient(135deg, #2563eb, #38bdf8);
}

.app-popup-card.is-success {
  border-color: rgba(134, 239, 172, 0.7);
}

.app-popup-card.is-error {
  border-color: rgba(252, 165, 165, 0.7);
}

.app-popup-card.is-warning {
  border-color: rgba(253, 230, 138, 0.9);
}

.app-popup-card.is-info {
  border-color: rgba(147, 197, 253, 0.85);
}

.app-popup-enter-active,
.app-popup-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.app-popup-enter-from,
.app-popup-leave-to {
  opacity: 0;
  transform: translate(-50%, -10px);
}

@media (max-width: 640px) {
  .app-popup-host {
    width: calc(100vw - 24px);
  }

  .app-popup-card {
    min-width: 100%;
  }
}
</style>
