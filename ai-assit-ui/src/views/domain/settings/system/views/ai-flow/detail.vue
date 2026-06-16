<script setup>
import { useAiFlowDetailPage } from '../../service/ai-flow/detail'

const {
  workflow,
  loading,
  errorMessage,
  nodeDefinitions,
  selectedNode,
  selectedNodeKey,
  selectedNodeInputDefinitions,
  selectedNodeOutputDefinitions,
  selectedNodeConfigItems,
  selectedNodeSkillItems,
  editorState,
  detailState,
  confirmState,
  editorTitle,
  availableNodeTemplates,
  selectedNodeTemplate,
  availableSkillTemplates,
  selectedSkillTemplate,
  goBack,
  openNodeEditor,
  openItemEditor,
  syncNodeFormWithTemplate,
  syncSkillFormWithTemplate,
  closeEditor,
  openItemDetail,
  closeDetail,
  moveNode,
  toggleNodeStatus,
  removeItem,
  closeConfirm,
  confirmRemoveItem,
  submitEditor
} = useAiFlowDetailPage()
</script>

<template>
  <div class="ai-flow-detail-page">
    <section v-if="loading" class="detail-empty">
      <h2>正在加载流程详情</h2>
      <p>请稍候...</p>
    </section>

    <section v-else-if="errorMessage" class="detail-empty">
      <h2>流程详情加载失败</h2>
      <p>{{ errorMessage }}</p>
      <button type="button" class="back-link" @click="goBack">返回流程列表</button>
    </section>

    <template v-else-if="workflow">
      <header class="content-head">
        <div class="content-head-main">
          <div>
            <p class="crumb">系统设置 / AI流程配置 / {{ workflow.name }}</p>
            <h1>{{ workflow.name }}</h1>
            <p class="section-desc">{{ workflow.scene }}</p>
          </div>
          <button type="button" class="back-link" @click="goBack">返回流程列表</button>
        </div>
      </header>

      <section class="detail-grid">
        <article class="detail-card detail-card-wide">
          <div class="section-head">
            <div>
              <p class="eyebrow">配置分区</p>
              <h3>节点定义</h3>
            </div>
            <button type="button" class="mini-action primary" @click="openNodeEditor('create')">新增节点</button>
          </div>
          <p class="section-desc-inline">这里按子列表维护节点顺序、跳转关系、启停状态和节点类型。</p>

          <div class="node-list">
            <article
              v-for="(node, index) in nodeDefinitions"
              :key="node.key"
              class="node-row"
              :class="{ active: selectedNode?.key === node.key }"
              @click="selectedNodeKey = node.key"
            >
              <div class="node-index">{{ index + 1 }}</div>

              <div class="node-main">
                <div class="node-title-row">
                  <strong>{{ node.name }}</strong>
                  <span class="node-type">{{ node.type }}</span>
                </div>
                <div class="node-meta">
                  <span>节点 Key：{{ node.key }}</span>
                  <span>执行方式：{{ node.mode }}</span>
                  <span>状态：{{ node.status }}</span>
                </div>
              </div>

              <div class="node-actions">
                <button type="button" class="mini-action" @click.stop="openNodeEditor('edit', node)">编辑</button>
                <button type="button" class="mini-action" @click.stop="toggleNodeStatus(node)">{{ node.status === '启用' ? '停用' : '启用' }}</button>
                <button type="button" class="mini-action danger" @click.stop="removeItem('node', node)">删除</button>
                <div class="node-move-actions">
                  <button type="button" class="mini-action icon" title="上移" :disabled="index === 0" @click.stop="moveNode(index, 'up')">↑</button>
                  <button type="button" class="mini-action icon" title="下移" :disabled="index === nodeDefinitions.length - 1" @click.stop="moveNode(index, 'down')">↓</button>
                </div>
              </div>
            </article>
          </div>
        </article>

        <div class="detail-side-column">
          <article class="detail-card">
            <p class="eyebrow">节点功能描述</p>
            <h3>{{ selectedNode?.name }}</h3>
            <p>{{ selectedNode?.summary }}</p>
          </article>

          <article class="detail-card detail-card-scroll-section">
            <div class="section-head">
              <div>
                <p class="eyebrow">节点输入</p>
                <h3>输入定义</h3>
              </div>
              <button type="button" class="mini-action" @click="openItemEditor('input', 'create')">新增输入</button>
            </div>
            <p class="section-desc-inline">维护节点消费的上下文字段、字段路径、类型和必填约束。</p>

            <div class="config-list">
              <article v-for="item in selectedNodeInputDefinitions" :key="item.fieldCode" class="config-row">
                <div class="config-main">
                  <div class="config-title-row">
                    <strong>{{ item.fieldName }}</strong>
                    <span class="config-type">{{ item.dataType || '-' }}</span>
                    <span class="config-status" :class="{ draft: !item.required }">{{ item.required ? '必填' : '可选' }}</span>
                  </div>
                  <p>{{ item.fieldCode }} / {{ item.fieldPath || '-' }}</p>
                </div>

                <div class="config-actions">
                  <button type="button" class="mini-action" @click="openItemDetail('input', item)">查看详情</button>
                  <button type="button" class="mini-action" @click="openItemEditor('input', 'edit', item)">编辑</button>
                  <button type="button" class="mini-action danger" @click="removeItem('input', item)">删除</button>
                </div>
              </article>
              <div v-if="!selectedNodeInputDefinitions.length" class="list-empty">当前节点还没有输入定义。</div>
            </div>
          </article>

          <article class="detail-card detail-card-scroll-section">
            <div class="section-head">
              <div>
                <p class="eyebrow">节点输出</p>
                <h3>输出定义</h3>
              </div>
              <button type="button" class="mini-action" @click="openItemEditor('output', 'create')">新增输出</button>
            </div>
            <p class="section-desc-inline">维护节点产出的核心字段定义，支持页面直接修改。</p>

            <div class="config-list">
              <article v-for="item in selectedNodeOutputDefinitions" :key="item.fieldCode" class="config-row">
                <div class="config-main">
                  <div class="config-title-row">
                    <strong>{{ item.fieldName }}</strong>
                    <span class="config-type">{{ item.dataType || '-' }}</span>
                    <span class="config-status" :class="{ draft: !item.required }">{{ item.required ? '必填' : '可选' }}</span>
                  </div>
                  <p>{{ item.fieldCode }} / {{ item.fieldPath || '-' }}</p>
                </div>

                <div class="config-actions">
                  <button type="button" class="mini-action" @click="openItemDetail('output', item)">查看详情</button>
                  <button type="button" class="mini-action" @click="openItemEditor('output', 'edit', item)">编辑</button>
                  <button type="button" class="mini-action danger" @click="removeItem('output', item)">删除</button>
                </div>
              </article>
              <div v-if="!selectedNodeOutputDefinitions.length" class="list-empty">当前节点还没有输出定义。</div>
            </div>
          </article>

          <article class="detail-card detail-card-scroll-section">
            <div class="section-head">
              <div>
                <p class="eyebrow">节点配置</p>
                <h3>配置项列表</h3>
              </div>
              <button type="button" class="mini-action" @click="openItemEditor('config', 'create')">新增配置</button>
            </div>
            <p class="section-desc-inline">核心维护提示 AI 的消息模板、返回数据结构以及规则类配置。</p>

            <div class="config-list">
              <article v-for="item in selectedNodeConfigItems" :key="item.key" class="config-row">
                <div class="config-main">
                  <div class="config-title-row">
                    <strong>{{ item.name }}</strong>
                    <span class="config-type">{{ item.type }}</span>
                    <span class="config-status">{{ item.status }}</span>
                  </div>
                  <p>{{ item.summary }}</p>
                </div>

                <div class="config-actions">
                  <button type="button" class="mini-action" @click="openItemDetail('config', item)">查看详情</button>
                  <button type="button" class="mini-action" @click="openItemEditor('config', 'edit', item)">编辑</button>
                  <button type="button" class="mini-action danger" @click="removeItem('config', item)">删除</button>
                </div>
              </article>
              <div v-if="!selectedNodeConfigItems.length" class="list-empty">当前节点还没有配置项，先新增一条配置。</div>
            </div>
          </article>

          <article class="detail-card detail-card-scroll-section">
            <div class="section-head">
              <div>
                <p class="eyebrow">Skill 配置</p>
                <h3>挂载技能列表</h3>
              </div>
              <button type="button" class="mini-action" @click="openItemEditor('skill', 'create')">新增 Skill</button>
            </div>
            <p class="section-desc-inline">按执行阶段维护 skill 挂载，支持查看详情、编辑和删除。</p>

            <div class="config-list">
              <article v-for="item in selectedNodeSkillItems" :key="item.key" class="config-row">
                <div class="config-main">
                  <div class="config-title-row">
                    <strong>{{ item.name }}</strong>
                    <span class="config-type">{{ item.phase }}</span>
                    <span class="config-status" :class="{ draft: item.status !== '已挂接' }">{{ item.status }}</span>
                  </div>
                  <p>{{ item.summary }}</p>
                </div>

                <div class="config-actions">
                  <button type="button" class="mini-action" @click="openItemDetail('skill', item)">查看详情</button>
                  <button type="button" class="mini-action" @click="openItemEditor('skill', 'edit', item)">编辑</button>
                  <button type="button" class="mini-action danger" @click="removeItem('skill', item)">删除</button>
                </div>
              </article>
              <div v-if="!selectedNodeSkillItems.length" class="list-empty">当前节点还没有 Skill 挂载，先新增一个 Skill。</div>
            </div>
          </article>
        </div>
      </section>

    </template>

    <section v-else class="detail-empty">
      <h2>流程不存在</h2>
      <p>未找到对应的流程类型，请返回流程列表重新选择。</p>
      <button type="button" class="back-link" @click="goBack">返回流程列表</button>
    </section>

    <div v-if="editorState.visible" class="floating-mask" @click.self="closeEditor">
      <section class="floating-panel">
        <div class="floating-head">
          <div>
            <p class="eyebrow">本地交互</p>
            <h3>{{ editorTitle }}</h3>
          </div>
          <button type="button" class="ghost-action" @click="closeEditor">关闭</button>
        </div>

        <div class="form-grid">
          <label v-if="editorState.entityType === 'node'" class="field field-full">
            <span>节点模板</span>
            <select
              v-model="editorState.form.templateKey"
              :disabled="editorState.mode === 'edit'"
              @change="syncNodeFormWithTemplate(editorState.form.templateKey)"
            >
              <option v-for="item in availableNodeTemplates" :key="item.key" :value="item.key">
                {{ item.name }} / {{ item.type }}
              </option>
            </select>
          </label>

          <label v-if="editorState.entityType === 'skill'" class="field field-full">
            <span>Skill 模板</span>
            <select
              v-model="editorState.form.templateKey"
              :disabled="editorState.mode === 'edit'"
              @change="syncSkillFormWithTemplate(editorState.form.templateKey)"
            >
              <option v-for="item in availableSkillTemplates" :key="item.key" :value="item.key">
                {{ item.name }} / {{ item.phase }}
              </option>
            </select>
          </label>

          <label v-if="editorState.entityType === 'config'" class="field">
            <span>{{ editorState.entityType === 'skill' ? 'Skill Key' : 'Key' }}</span>
            <input v-model.trim="editorState.form.key" type="text" placeholder="请输入唯一 Key" />
          </label>

          <label v-if="editorState.entityType === 'config'" class="field">
            <span>{{ editorState.entityType === 'skill' ? 'Skill 名称' : '名称' }}</span>
            <input v-model.trim="editorState.form.name" type="text" placeholder="请输入名称" />
          </label>

          <label v-if="editorState.entityType === 'input' || editorState.entityType === 'output'" class="field">
            <span>字段编码</span>
            <input v-model.trim="editorState.form.fieldCode" type="text" placeholder="请输入字段编码" />
          </label>

          <label v-if="editorState.entityType === 'input' || editorState.entityType === 'output'" class="field">
            <span>字段名称</span>
            <input v-model.trim="editorState.form.fieldName" type="text" placeholder="请输入字段名称" />
          </label>

          <label v-if="editorState.entityType === 'input' || editorState.entityType === 'output'" class="field">
            <span>字段路径</span>
            <input v-model.trim="editorState.form.fieldPath" type="text" placeholder="例如 context.planResult.sql" />
          </label>

          <label v-if="editorState.entityType === 'input' || editorState.entityType === 'output'" class="field">
            <span>数据类型</span>
            <input v-model.trim="editorState.form.dataType" type="text" placeholder="例如 STRING / OBJECT / ARRAY" />
          </label>

          <label v-if="editorState.entityType === 'input' || editorState.entityType === 'output'" class="field">
            <span>来源引用</span>
            <input v-model.trim="editorState.form.sourceRef" type="text" placeholder="例如 context / request" />
          </label>

          <label v-if="editorState.entityType === 'input' || editorState.entityType === 'output'" class="field">
            <span>是否必填</span>
            <select v-model="editorState.form.required">
              <option :value="true">是</option>
              <option :value="false">否</option>
            </select>
          </label>

          <label v-if="editorState.entityType === 'config'" class="field">
            <span>配置类型</span>
            <input v-model.trim="editorState.form.type" type="text" placeholder="例如：提示消息 / 输出结构" />
          </label>

          <label v-if="editorState.entityType === 'node'" class="field">
            <span>执行方式</span>
            <select v-model="editorState.form.mode">
              <option value="串行">串行</option>
              <option value="并行/串行">并行/串行</option>
              <option value="回跳控制">回跳控制</option>
            </select>
          </label>

          <label v-if="editorState.entityType === 'skill'" class="field">
            <span>执行阶段</span>
            <select v-model="editorState.form.phase">
              <option value="默认">默认</option>
              <option value="BEFORE_EXECUTE">BEFORE_EXECUTE</option>
              <option value="AFTER_EXECUTE">AFTER_EXECUTE</option>
              <option value="REVIEW_OUTPUT">REVIEW_OUTPUT</option>
            </select>
          </label>

          <label v-if="editorState.entityType === 'node' || editorState.entityType === 'config' || editorState.entityType === 'skill'" class="field">
            <span>{{ editorState.entityType === 'skill' ? '状态 / 挂载态' : '状态' }}</span>
            <select v-model="editorState.form.status">
              <option value="启用">启用</option>
              <option value="停用">停用</option>
              <option value="已挂接">已挂接</option>
              <option value="未挂接">未挂接</option>
              <option value="可扩展">可扩展</option>
              <option value="规划中">规划中</option>
              <option value="待补充">待补充</option>
            </select>
          </label>

          <label v-if="editorState.entityType === 'config'" class="field field-full">
            <span>描述</span>
            <textarea v-model.trim="editorState.form.summary" rows="5" placeholder="请输入描述信息" />
          </label>

          <div v-if="editorState.entityType === 'node' && selectedNodeTemplate" class="field field-full template-preview">
            <span>节点内容</span>
            <div class="template-preview-card">
              <div class="template-preview-grid">
                <div class="template-preview-item">
                  <em>Key</em>
                  <strong>{{ selectedNodeTemplate.key }}</strong>
                </div>
                <div class="template-preview-item">
                  <em>名称</em>
                  <strong>{{ selectedNodeTemplate.name }}</strong>
                </div>
                <div class="template-preview-item">
                  <em>节点类型</em>
                  <strong>{{ selectedNodeTemplate.type }}</strong>
                </div>
                <div class="template-preview-item">
                  <em>默认执行方式</em>
                  <strong>{{ selectedNodeTemplate.mode }}</strong>
                </div>
              </div>

              <div class="template-preview-block">
                <em>描述</em>
                <p>{{ selectedNodeTemplate.summary }}</p>
              </div>

              <div class="template-preview-block">
                <em>配置项</em>
                <ul>
                  <li v-for="item in selectedNodeTemplate.configItems" :key="item.key">
                    {{ item.name }} / {{ item.type }}
                  </li>
                </ul>
              </div>

              <div class="template-preview-block">
                <em>Skill 挂载</em>
                <ul>
                  <li v-for="item in selectedNodeTemplate.skillItems" :key="item.key">
                    {{ item.name }} / {{ item.phase }}
                  </li>
                </ul>
              </div>
            </div>
          </div>

          <div v-if="editorState.entityType === 'skill' && selectedSkillTemplate" class="field field-full template-preview">
            <span>Skill 内容</span>
            <div class="template-preview-card">
              <div class="template-preview-grid">
                <div class="template-preview-item">
                  <em>Skill Key</em>
                  <strong>{{ selectedSkillTemplate.key }}</strong>
                </div>
                <div class="template-preview-item">
                  <em>名称</em>
                  <strong>{{ selectedSkillTemplate.name }}</strong>
                </div>
                <div class="template-preview-item">
                  <em>默认执行阶段</em>
                  <strong>{{ selectedSkillTemplate.phase }}</strong>
                </div>
                <div class="template-preview-item">
                  <em>默认状态</em>
                  <strong>{{ selectedSkillTemplate.status }}</strong>
                </div>
              </div>

              <div class="template-preview-block">
                <em>描述</em>
                <p>{{ selectedSkillTemplate.summary }}</p>
              </div>
            </div>
          </div>
        </div>

        <div class="panel-actions">
          <button type="button" class="ghost-action" @click="closeEditor">取消</button>
          <button type="button" class="mini-action primary" @click="submitEditor">保存</button>
        </div>
      </section>
    </div>

    <div v-if="detailState.visible" class="floating-mask" @click.self="closeDetail">
      <section class="floating-panel floating-panel-detail">
        <div class="floating-head">
          <div>
            <p class="eyebrow">{{ detailState.entityType === 'config' ? '配置详情' : 'Skill 详情' }}</p>
            <h3>{{ detailState.title }}</h3>
          </div>
          <button type="button" class="ghost-action" @click="closeDetail">关闭</button>
        </div>

        <div class="detail-fields">
          <div v-for="field in detailState.fields" :key="field.label" class="detail-field-row">
            <span>{{ field.label }}</span>
            <strong>{{ field.value }}</strong>
          </div>
        </div>

        <div class="detail-summary-block">
          <span>描述</span>
          <p>{{ detailState.summary }}</p>
        </div>
      </section>
    </div>

    <div v-if="confirmState.visible" class="floating-mask" @click.self="closeConfirm">
      <section class="floating-panel floating-panel-confirm">
        <div class="floating-head">
          <div>
            <p class="eyebrow">删除确认</p>
            <h3>确认删除“{{ confirmState.title }}”</h3>
          </div>
        </div>
        <p class="confirm-copy">删除将直接写入后端配置，请确认后再执行。</p>
        <div class="panel-actions">
          <button type="button" class="ghost-action" @click="closeConfirm">取消</button>
          <button type="button" class="mini-action danger-fill" @click="confirmRemoveItem">确认删除</button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped src="../../styles/ai-flow/detail.css"></style>
