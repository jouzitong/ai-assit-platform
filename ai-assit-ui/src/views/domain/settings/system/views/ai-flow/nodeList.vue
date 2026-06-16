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
    description="节点列表负责承接类型分类，后面可以再接独立节点管理能力。"
    :items="sectionMeta.rows"
    empty-text="当前没有节点类型数据。"
  >
    <template #head-action>
      <button type="button" class="mini-action primary" @click="$emit('create', 'node')">新增节点类型</button>
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
              <span>适用流程：</span>
              {{ item.nodes }}
            </p>
          </div>

          <div class="flow-side">
            <div class="flow-tags">
              <span v-for="tag in item.tags" :key="tag" class="flow-tag">{{ tag }}</span>
            </div>
            <div class="flow-action-group">
              <button type="button" class="flow-action ghost" @click="$emit('edit', 'node', item)">编辑</button>
              <button type="button" class="flow-action ghost danger" @click="$emit('delete', 'node', item)">删除</button>
            </div>
          </div>
        </article>
      </div>
    </template>
  </CardList>
</template>

<style scoped src="../../styles/ai-flow-section.css"></style>
