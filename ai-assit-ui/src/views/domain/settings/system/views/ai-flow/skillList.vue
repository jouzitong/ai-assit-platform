<script setup>
import CardList from '../../components/CardList.vue'

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
    description="按 Skill 分类维护挂载能力，后面可扩成独立挂载明细页。"
    :items="sectionMeta.rows"
    empty-text="当前没有 Skill 分类数据。"
  >
    <template #head-action>
      <button type="button" class="mini-action primary">新增 Skill 类型</button>
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
              <span>默认阶段：</span>
              {{ item.nodes }}
            </p>
          </div>

          <div class="flow-side">
            <div class="flow-tags">
              <span v-for="tag in item.tags" :key="tag" class="flow-tag">{{ tag }}</span>
            </div>
            <button type="button" class="flow-action ghost">
              查看列表
            </button>
          </div>
        </article>
      </div>
    </template>
  </CardList>
</template>

<style scoped src="../../styles/ai-flow-section.css"></style>
