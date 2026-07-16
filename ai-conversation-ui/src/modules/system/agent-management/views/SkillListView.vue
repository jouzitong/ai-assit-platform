<script setup lang="ts">
import CatalogListView from '../components/CatalogListView.vue'
import { deleteSkill, listSkills, publishSkill, validateSkill } from '../api/skills'
</script>

<template>
  <CatalogListView
    title="Skill 管理"
    description="通过表单或 ZIP 包维护包含 SKILL.md、模板、数据和参考资料的不可变 Skill 版本。"
    resource-label="Skill"
    route-base="/settings/system/skills"
    :list-request="listSkills"
    :delete-request="deleteSkill"
    :validate-request="validateSkill"
    :publish-request="publishSkill"
  />
</template>
