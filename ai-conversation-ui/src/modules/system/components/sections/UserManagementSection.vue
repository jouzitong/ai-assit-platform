<script setup lang="ts">
import { Delete, EditPen, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { AppPagination } from '../../../../components'
import {
  createSecurityRole, createSecurityUser, deleteSecurityRole, deleteSecurityUser,
  getSecurityUserProfile, searchSecurityRoles, searchSecurityUsers,
  updateSecurityRole, updateSecurityUserProfile,
  type SecurityRoleItem, type SecurityUserItem,
} from '../../api/securityUsers'

type Tab = 'users' | 'roles'
const activeTab = ref<Tab>('users')
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const users = ref<SecurityUserItem[]>([])
const roles = ref<SecurityRoleItem[]>([])
const roleOptions = ref<SecurityRoleItem[]>([])
const editorVisible = ref(false)
const editorMode = ref<'create' | 'edit'>('create')
const editingId = ref<string | number | null>(null)
const saving = ref(false)
const editorTab = ref('basic')
const userForm = reactive({ username: '', displayName: '', status: 'ENABLED', tenantId: '', password: '', roleCodes: [] as string[], passwordConfigured: false, passwordAlgo: '' })
const roleForm = reactive({ roleCode: '', roleName: '', status: 'ENABLED' })
const title = computed(() => `${editorMode.value === 'create' ? '新增' : '编辑'}${activeTab.value === 'users' ? '用户' : '角色'}`)

function clearForms() { Object.assign(userForm, { username: '', displayName: '', status: 'ENABLED', tenantId: '', password: '', roleCodes: [], passwordConfigured: false, passwordAlgo: '' }); Object.assign(roleForm, { roleCode: '', roleName: '', status: 'ENABLED' }) }
function resultTotal(value?: number) { const n = Number(value); return Number.isFinite(n) ? n : 0 }
async function loadRoleOptions() { const result = await searchSecurityRoles({ page: 1, size: 500 }); roleOptions.value = result?.list ?? [] }
async function loadRows() {
  loading.value = true; errorMessage.value = ''
  try {
    const query = { page: currentPage.value, size: pageSize.value, keyword: keyword.value.trim() || undefined }
    const result = activeTab.value === 'users' ? await searchSecurityUsers(query) : await searchSecurityRoles(query)
    if (activeTab.value === 'users') users.value = result?.list ?? []; else roles.value = result?.list ?? []
    total.value = resultTotal(result?.pageInfo?.total)
  } catch (error) { users.value = []; roles.value = []; total.value = 0; errorMessage.value = error instanceof Error ? error.message : '数据加载失败' } finally { loading.value = false }
}
async function search() { currentPage.value = 1; await loadRows() }
async function changeTab() { keyword.value = ''; currentPage.value = 1; await loadRows() }
async function openCreate() { editorMode.value = 'create'; editingId.value = null; editorTab.value = 'basic'; clearForms(); if (activeTab.value === 'users') await loadRoleOptions(); editorVisible.value = true }
async function editUser(row: SecurityUserItem) {
  editorMode.value = 'edit'; editingId.value = row.id; editorTab.value = 'basic'; clearForms()
  try { const [profile] = await Promise.all([getSecurityUserProfile(row.id), loadRoleOptions()]); Object.assign(userForm, { ...profile.user, displayName: profile.user.displayName || '', tenantId: profile.user.tenantId || '', password: '', roleCodes: profile.roleCodes ?? [], passwordConfigured: profile.passwordConfigured, passwordAlgo: profile.passwordAlgo || '' }); editorVisible.value = true } catch (error) { ElMessage.error(error instanceof Error ? error.message : '用户档案加载失败') }
}
function editRole(row: SecurityRoleItem) { editorMode.value = 'edit'; editingId.value = row.id; Object.assign(roleForm, row); editorVisible.value = true }
function userPayload() { return { username: String(userForm.username || '').trim(), displayName: String(userForm.displayName || '').trim() || undefined, status: userForm.status, tenantId: String(userForm.tenantId || '').trim() || undefined } }
async function save() {
  if (activeTab.value === 'users' && (!userForm.username.trim() || !userForm.status)) return ElMessage.error('请填写用户名和状态')
  if (activeTab.value === 'roles' && (!roleForm.roleCode.trim() || !roleForm.roleName.trim() || !roleForm.status)) return ElMessage.error('请填写角色编码、名称和状态')
  saving.value = true
  try {
    if (activeTab.value === 'users') {
      let id = editingId.value
      if (editorMode.value === 'create') { const user = await createSecurityUser(userPayload()); id = user.id }
      if (id !== null) await updateSecurityUserProfile(id, { user: userPayload(), roleCodes: userForm.roleCodes, password: userForm.password || undefined })
    } else {
      const payload = { roleCode: roleForm.roleCode.trim(), roleName: roleForm.roleName.trim(), status: roleForm.status }
      if (editorMode.value === 'create') await createSecurityRole(payload); else if (editingId.value !== null) await updateSecurityRole(editingId.value, payload)
    }
    ElMessage.success('保存成功'); editorVisible.value = false; await loadRows()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存失败') } finally { saving.value = false }
}
async function remove(row: SecurityUserItem | SecurityRoleItem) {
  const isUser = activeTab.value === 'users'; const label = isUser ? (row as SecurityUserItem).username : (row as SecurityRoleItem).roleName
  try { await ElMessageBox.confirm(`确认删除「${label}」吗？${isUser ? '关联角色也会被移除。' : ''}`, '删除确认', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }); if (isUser) await deleteSecurityUser(row.id); else await deleteSecurityRole(row.id); ElMessage.success('删除成功'); await loadRows() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}
onMounted(() => { void loadRows() })
</script>

<template>
  <section class="user-management-page"><el-container class="user-management-layout">
    <el-header class="user-management-layout__header"><div><h3>用户管理</h3><p>管理用户、认证凭据与角色授权。</p></div><div class="tools"><el-input v-model="keyword" :placeholder="activeTab === 'users' ? '搜索用户或租户' : '搜索角色编码或名称'" clearable @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input><el-button plain @click="loadRows"><el-icon><RefreshRight /></el-icon>刷新</el-button><el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新增{{ activeTab === 'users' ? '用户' : '角色' }}</el-button></div></el-header>
    <el-main class="user-management-layout__main"><el-tabs v-model="activeTab" @tab-change="changeTab"><el-tab-pane label="用户" name="users"><div v-if="loading" class="state">用户加载中...</div><div v-else-if="errorMessage" class="state error">{{ errorMessage }}</div><el-table v-else :data="users" empty-text="暂无用户"><el-table-column prop="username" label="用户名" min-width="140"/><el-table-column prop="displayName" label="显示名称" min-width="140"><template #default="{ row }">{{ row.displayName || '-' }}</template></el-table-column><el-table-column prop="status" label="状态" width="120"/><el-table-column prop="tenantId" label="租户 ID" min-width="140"><template #default="{ row }">{{ row.tenantId || '-' }}</template></el-table-column><el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="editUser(row)"><el-icon><EditPen /></el-icon>编辑</el-button><el-button link type="danger" @click="remove(row)"><el-icon><Delete /></el-icon>删除</el-button></template></el-table-column></el-table></el-tab-pane>
    <el-tab-pane label="角色" name="roles"><div v-if="loading" class="state">角色加载中...</div><div v-else-if="errorMessage" class="state error">{{ errorMessage }}</div><el-table v-else :data="roles" empty-text="暂无角色"><el-table-column prop="roleCode" label="角色编码" min-width="160"/><el-table-column prop="roleName" label="角色名称" min-width="180"/><el-table-column prop="status" label="状态" width="120"/><el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="editRole(row)"><el-icon><EditPen /></el-icon>编辑</el-button><el-button link type="danger" @click="remove(row)"><el-icon><Delete /></el-icon>删除</el-button></template></el-table-column></el-table></el-tab-pane></el-tabs></el-main>
    <el-footer><AppPagination v-model:current-page="currentPage" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]" :total="total" @current-change="loadRows" @size-change="search"/></el-footer>
  </el-container>
  <el-dialog v-model="editorVisible" :title="title" width="640" draggable destroy-on-close @closed="clearForms"><el-tabs v-if="activeTab === 'users'" v-model="editorTab"><el-tab-pane label="基础信息" name="basic"><el-form label-position="top"><el-form-item label="用户名" required><el-input v-model="userForm.username"/></el-form-item><el-form-item label="显示名称"><el-input v-model="userForm.displayName"/></el-form-item><div class="form-grid"><el-form-item label="状态" required><el-select v-model="userForm.status"><el-option label="启用" value="ENABLED"/><el-option label="禁用" value="DISABLED"/></el-select></el-form-item><el-form-item label="租户 ID"><el-input v-model="userForm.tenantId"/></el-form-item></div></el-form></el-tab-pane><el-tab-pane label="认证信息" name="credential"><p class="hint">{{ userForm.passwordConfigured ? `已配置 ${userForm.passwordAlgo || ''} 密码；填写下方密码即可重置。` : '尚未配置密码；填写下方密码即可启用密码认证。' }}</p><el-form label-position="top"><el-form-item label="新密码"><el-input v-model="userForm.password" type="password" show-password autocomplete="new-password" placeholder="留空则不变"/></el-form-item></el-form></el-tab-pane><el-tab-pane label="角色分配" name="roles"><p class="hint">角色需先在“角色”管理中创建。</p><el-checkbox-group v-model="userForm.roleCodes" class="role-checks"><el-checkbox v-for="role in roleOptions" :key="role.id" :label="role.roleCode" :disabled="role.status !== 'ENABLED'">{{ role.roleName }}（{{ role.roleCode }}）</el-checkbox></el-checkbox-group></el-tab-pane></el-tabs><el-form v-else label-position="top"><el-form-item label="角色编码" required><el-input v-model="roleForm.roleCode"/></el-form-item><el-form-item label="角色名称" required><el-input v-model="roleForm.roleName"/></el-form-item><el-form-item label="状态" required><el-select v-model="roleForm.status"><el-option label="启用" value="ENABLED"/><el-option label="禁用" value="DISABLED"/></el-select></el-form-item></el-form><template #footer><el-button @click="editorVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template></el-dialog></section>
</template>

<style scoped>
.user-management-page{display:flex;flex:1;min-height:0}.user-management-layout{display:flex;flex:1;flex-direction:column;min-height:0;height:100%;overflow:hidden;border:1px solid var(--system-border);border-radius:18px;background:var(--system-surface-strong);box-shadow:var(--system-shadow)}.user-management-layout__header{display:flex;align-items:center;justify-content:space-between;gap:16px;min-height:66px;padding:12px 16px;border-bottom:1px solid var(--system-border-subtle)}h3{margin:0;color:var(--system-title);font-size:16px}.user-management-layout__header p,.hint{margin:3px 0;color:var(--system-text-muted);font-size:12px}.tools{display:flex;gap:10px;align-items:center}.tools :deep(.el-input){width:280px}.user-management-layout__main{min-height:0;padding:0 16px;background:var(--system-surface-muted);overflow:auto}.state{display:flex;justify-content:center;align-items:center;min-height:240px;color:var(--system-text-muted)}.error{color:var(--system-danger)}.user-management-layout :deep(.el-table){width:100%;border:1px solid var(--system-border);border-radius:12px;overflow:hidden}.user-management-layout :deep(.el-tabs__header){margin:0 0 14px}.user-management-layout :deep(.el-footer){display:flex;justify-content:flex-end;height:auto;padding:12px 16px;border-top:1px solid var(--system-border-subtle)}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.form-grid :deep(.el-select){width:100%}.role-checks{display:grid;gap:10px}@media(max-width:860px){.user-management-layout__header,.tools{align-items:stretch;flex-direction:column}.tools :deep(.el-input){width:100%}}@media(max-width:560px){.form-grid{grid-template-columns:1fr;gap:0}}
</style>
