<script setup>
import CardList from '../../components/CardList.vue'

defineEmits(['create', 'edit', 'delete'])

defineProps({
  sectionMeta: {
    type: Object,
    required: true
  }
})
</script>

<template>
  <CardList
    :eyebrow="sectionMeta.eyebrow"
    :title="sectionMeta.title"
    description="这里按流程分类管理，再进入具体流程定义。"
    :items="sectionMeta.rows"
    empty-text="当前没有流程类型数据。"
  >
    <template #head-action>
      <button type="button" class="mini-action primary" @click="$emit('create', 'workflow')">新增</button>
    </template>

    <template #list="{ items }">
      <div class="flow-list">
        <article v-for="item in items" :key="item.key" class="flow-row">
          <div class="flow-main">
            <div class="flow-title-row">
              <h3>{{ item.name }}</h3>
              <span class="flow-status" :class="item.status === '已接入' ? 'is-live' : 'is-draft'">
                {{ item.status }}
              </span>
            </div>
            <p class="flow-code">{{ item.code }}</p>
            <p class="flow-scene">{{ item.scene }}</p>
            <p class="flow-nodes">
              <span>默认节点：</span>
              {{ item.nodes }}
            </p>
          </div>

          <div class="flow-side">
            <div class="flow-tags">
              <span v-for="tag in item.tags" :key="tag" class="flow-tag">{{ tag }}</span>
            </div>
            <div class="flow-action-group">
              <RouterLink :to="`/settings/system/ai-flow/${item.key}`" class="flow-action">
                进入配置
              </RouterLink>
              <button type="button" class="flow-action ghost" @click="$emit('edit', 'workflow', item)">编辑</button>
              <button type="button" class="flow-action ghost danger" @click="$emit('delete', 'workflow', item)">删除</button>
            </div>
          </div>
        </article>
      </div>
    </template>
  </CardList>
</template>

<style scoped src="../../styles/ai-flow-section.scss"></style>
