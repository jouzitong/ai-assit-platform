<script setup lang="ts">
import RunActivityTimeline from '../../ai-chat/components/RunActivityTimeline.vue'
import type { ChatRunActivity } from '../../ai-chat/types'

defineProps<{
  activities: ChatRunActivity[]
  messageStatus?: string
}>()
</script>

<template>
  <RunActivityTimeline :activities="activities" :run-status="messageStatus" />
</template>
