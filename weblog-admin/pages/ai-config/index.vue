<template>
  <div class="ai-config-page">
    <div class="page-header">
      <h2>AI 配置</h2>
    </div>

    <el-tabs v-model="activeTab" class="config-tabs">
      <!-- Tab 1: 基础配置 -->
      <el-tab-pane label="基础配置" name="config">
        <div v-loading="configLoading" class="config-grid">
          <!-- 模型配置 -->
          <el-card shadow="never" class="config-card">
            <template #header>
              <div class="card-header">
                <svg class="card-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>
                <span class="card-title">模型配置</span>
              </div>
            </template>
            <el-form label-width="140px" label-position="right">
              <el-form-item label="启用 AI">
                <el-switch v-model="configForm.enabled" />
              </el-form-item>
              <el-form-item label="模型提供商">
                <el-select v-model="configForm.provider" style="width: 100%">
                  <el-option label="OpenAI" value="openai" />
                  <el-option label="DeepSeek" value="deepseek" />
                  <el-option label="通义千问" value="qwen" />
                  <el-option label="其他（兼容 OpenAI）" value="custom" />
                </el-select>
              </el-form-item>
              <el-form-item label="API Key">
                <el-input v-model="configForm.apiKey" type="password" show-password placeholder="sk-..." />
              </el-form-item>
              <el-form-item label="Base URL">
                <el-input v-model="configForm.baseUrl" placeholder="https://api.openai.com" />
                <span class="form-tip">不要包含 /v1 后缀，系统会自动拼接</span>
              </el-form-item>
              <el-form-item label="模型名称">
                <el-input v-model="configForm.model" placeholder="gpt-4o-mini" />
              </el-form-item>
              <el-form-item label="Embedding 模型">
                <el-input v-model="configForm.embeddingModel" placeholder="text-embedding-3-small" />
              </el-form-item>
              <el-form-item label="最大 Token">
                <el-input-number v-model="configForm.maxTokens" :min="100" :max="128000" :step="100" />
              </el-form-item>
              <el-form-item label="超时（秒）">
                <el-input-number v-model="configForm.timeout" :min="5" :max="120" />
              </el-form-item>
            </el-form>
          </el-card>

          <!-- 功能开关与限额 -->
          <el-card shadow="never" class="config-card">
            <template #header>
              <div class="card-header">
                <svg class="card-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                <span class="card-title">功能开关与限额</span>
              </div>
            </template>
            <el-form label-width="140px" label-position="right">
              <el-form-item label="AI 写作助手">
                <el-switch v-model="configForm.features.writing" />
              </el-form-item>
              <el-form-item label="元信息生成">
                <el-switch v-model="configForm.features.meta" />
              </el-form-item>
              <el-form-item label="评论 AI 审核">
                <el-switch v-model="configForm.features.commentReview" />
              </el-form-item>
              <el-form-item label="语义推荐">
                <el-switch v-model="configForm.features.recommend" />
              </el-form-item>
              <el-form-item label="AI 问答">
                <el-switch v-model="configForm.features.chat" />
              </el-form-item>
              <el-divider />
              <el-form-item label="月度 Token 上限">
                <el-input-number v-model="configForm.monthlyTokenLimit" :min="0" :max="100000000" :step="10000" />
                <span class="form-tip">0 表示不限制</span>
              </el-form-item>
            </el-form>
          </el-card>
        </div>

        <!-- 悬浮保存按钮 -->
        <div class="fab-actions">
          <el-button class="fab-test" :loading="testing" @click="handleTestConnection">测试连接</el-button>
          <el-button class="fab-save" type="primary" :loading="saving" @click="handleSaveConfig">保存配置</el-button>
        </div>
      </el-tab-pane>

      <!-- Tab 2: 提示词模板 -->
      <el-tab-pane label="提示词模板" name="prompts">
        <el-table v-loading="promptsLoading" :data="prompts" stripe>
          <el-table-column prop="name" label="名称" width="180" />
          <el-table-column prop="description" label="用途" min-width="250" />
          <el-table-column label="自定义" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.isCustomized" type="warning" size="small">已自定义</el-tag>
              <el-tag v-else type="info" size="small">默认</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
              <el-button text type="warning" size="small" @click="handleResetPrompt(row)">恢复默认</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 提示词编辑弹窗 -->
    <el-dialog v-model="editDialogVisible" :title="`编辑模板 - ${editingPrompt?.name || ''}`" width="800px" destroy-on-close>
      <div class="prompt-edit-layout">
        <div class="prompt-edit-main">
          <el-form label-position="top">
            <el-form-item label="系统提示词">
              <el-input v-model="editForm.systemPrompt" type="textarea" :rows="8" placeholder="系统提示词..." />
            </el-form-item>
            <el-form-item label="用户提示词模板">
              <el-input v-model="editForm.userPromptTemplate" type="textarea" :rows="8" placeholder="用户提示词模板，使用 {{变量名}} 作为占位符..." />
            </el-form-item>
          </el-form>
        </div>
        <div class="prompt-edit-sidebar">
          <h4>可用变量</h4>
          <div v-if="editingPrompt?.variables" class="variable-list">
            <el-tag v-for="v in parseVariables(editingPrompt.variables)" :key="v" size="small" class="variable-tag">
              {{ formatVariable(v) }}
            </el-tag>
          </div>
          <p v-else class="no-variables">无可用变量</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="promptSaving" @click="handleSavePrompt">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiApi, type AiConfig, type PromptTemplate } from '~/api/ai'
import { handleAiError } from '~/utils/aiError'

const activeTab = ref('config')

// ========== 基础配置 ==========
const configLoading = ref(false)
const saving = ref(false)
const testing = ref(false)
const configForm = reactive<AiConfig>({
  enabled: false,
  provider: 'openai',
  apiKey: '',
  baseUrl: '',
  model: '',
  embeddingModel: '',
  maxTokens: 4096,
  timeout: 30,
  monthlyTokenLimit: 0,
  features: {
    writing: true,
    meta: true,
    commentReview: true,
    recommend: true,
    chat: true,
  },
})

async function loadConfig() {
  configLoading.value = true
  try {
    const configRes = await aiApi.getConfig()
    Object.assign(configForm, configRes.data)
  } catch (e: unknown) {
    handleAiError(e)
  } finally {
    configLoading.value = false
  }
}

async function handleSaveConfig() {
  // 基础表单验证
  if (configForm.enabled) {
    if (!configForm.apiKey?.trim()) {
      ElMessage.warning('启用 AI 时必须填写 API Key')
      return
    }
    if (!configForm.baseUrl?.trim()) {
      ElMessage.warning('启用 AI 时必须填写 Base URL')
      return
    }
    if (!configForm.model?.trim()) {
      ElMessage.warning('启用 AI 时必须填写模型名称')
      return
    }
    try {
      new URL(configForm.baseUrl)
    } catch {
      ElMessage.warning('Base URL 格式不正确，请输入完整的 URL')
      return
    }
    // 自动去除末尾的 /v1，Spring AI 会自动拼接
    configForm.baseUrl = configForm.baseUrl.replace(/\/v1\/?$/, '')
  }
  saving.value = true
  try {
    const res = await aiApi.updateConfig(configForm)
    const result = res.data
    ElMessage.success(result.message)
    // 保存成功后重新加载配置，确认实际生效状态
    await loadConfig()
  } catch (e: unknown) {
    handleAiError(e)
  } finally {
    saving.value = false
  }
}

async function handleTestConnection() {
  // 验证并规范化 URL（与保存时保持一致）
  if (!configForm.baseUrl?.trim()) {
    ElMessage.warning('请先填写 Base URL')
    return
  }
  // 去除末尾的 /v1，与保存时保持一致
  const testUrl = configForm.baseUrl.replace(/\/v1\/?$/, '')
  try {
    new URL(testUrl)
  } catch {
    ElMessage.warning('Base URL 格式不正确，请输入完整的 URL')
    return
  }
  testing.value = true
  try {
    const res = await aiApi.testConnection()
    ElMessage.success(`连接成功: ${res.data}`)
  } catch (e: unknown) {
    handleAiError(e)
  } finally {
    testing.value = false
  }
}

// ========== 提示词模板 ==========
const promptsLoading = ref(false)
const prompts = ref<PromptTemplate[]>([])
const editDialogVisible = ref(false)
const editingPrompt = ref<PromptTemplate | null>(null)
const promptSaving = ref(false)
const editForm = reactive({
  systemPrompt: '',
  userPromptTemplate: '',
})

function parseVariables(variables: string): string[] {
  if (!variables) return []
  return variables.split(',').map(v => v.trim()).filter(Boolean)
}

/** 格式化变量名为模板占位符显示（避免 Vue 模板编译器解析 {{ }} ） */
function formatVariable(v: string): string {
  return `\u007B\u007B${v}\u007D\u007D`
}

async function loadPrompts() {
  promptsLoading.value = true
  try {
    const res = await aiApi.getPrompts()
    prompts.value = res.data
  } catch (e: unknown) {
    handleAiError(e)
  } finally {
    promptsLoading.value = false
  }
}

function openEditDialog(row: PromptTemplate) {
  editingPrompt.value = row
  editForm.systemPrompt = row.systemPrompt
  editForm.userPromptTemplate = row.userPromptTemplate
  editDialogVisible.value = true
}

async function handleSavePrompt() {
  if (!editingPrompt.value) return
  promptSaving.value = true
  try {
    await aiApi.updatePrompt(editingPrompt.value.templateKey, {
      systemPrompt: editForm.systemPrompt,
      userPromptTemplate: editForm.userPromptTemplate,
    })
    ElMessage.success('模板保存成功')
    editDialogVisible.value = false
    loadPrompts()
  } catch (e: unknown) {
    handleAiError(e)
  } finally {
    promptSaving.value = false
  }
}

async function handleResetPrompt(row: PromptTemplate) {
  await ElMessageBox.confirm(`确定将「${row.name}」恢复为默认模板？自定义内容将丢失。`, '恢复默认', { type: 'warning' })
  try {
    await aiApi.resetPrompt(row.templateKey)
    ElMessage.success('已恢复默认')
    loadPrompts()
  } catch (e: unknown) {
    handleAiError(e)
  }
}

// ========== 初始化 ==========
onMounted(() => {
  loadConfig()
  loadPrompts()
})
</script>

<style scoped lang="scss">
.ai-config-page {
  .page-header {
    display: flex;
    align-items: center;
    margin-bottom: 16px;
    padding: 0 20px;
    height: 62px;
    background: var(--el-bg-color);
    border-radius: 10px;
    border: 1px solid var(--el-border-color-lighter);
    h2 {
      font-size: 1.05rem;
      font-weight: 600;
      color: var(--el-text-color-primary);
      margin: 0;
      letter-spacing: 0.3px;
    }
  }
}

// ========== 配置网格 ==========
.config-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}
.config-card--wide {
  grid-column: 1 / -1;
}
@media (max-width: 1200px) {
  .config-grid { grid-template-columns: 1fr; }
}

// ========== 卡片 ==========
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-icon {
  width: 18px;
  height: 18px;
  color: var(--el-color-primary);
  flex-shrink: 0;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.form-tip {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.2;
  margin-top: 2px;
}

// ========== 提示词编辑 ==========
.prompt-edit-layout {
  display: flex;
  gap: 20px;
}
.prompt-edit-main {
  flex: 1;
}
.prompt-edit-sidebar {
  width: 180px;
  flex-shrink: 0;
  h4 {
    font-size: 14px;
    font-weight: 600;
    margin: 0 0 10px;
    color: var(--el-text-color-primary);
  }
}
.variable-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.variable-tag {
  font-family: monospace;
  font-size: 12px;
}
.no-variables {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

// ========== 悬浮操作按钮 ==========
.fab-actions {
  position: fixed;
  right: 32px;
  bottom: 32px;
  z-index: 100;
  display: flex;
  gap: 10px;
}
.fab-test {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  &:hover {
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.15);
    transform: translateY(-1px);
  }
  transition: box-shadow 0.2s, transform 0.2s;
}
.fab-save {
  box-shadow: 0 4px 12px rgba(91, 141, 239, 0.4);
  border-radius: 8px;
  &:hover {
    box-shadow: 0 6px 16px rgba(91, 141, 239, 0.5);
    transform: translateY(-1px);
  }
  transition: box-shadow 0.2s, transform 0.2s;
}

// ========== 通用样式 ==========
:deep(.el-card) {
  border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter);
  .el-card__header {
    padding: 14px 20px;
    border-bottom: 1px solid var(--el-border-color-extra-light);
  }
  .el-card__body { padding: 20px; }
}
:deep(.el-switch) {
  --el-switch-on-color: var(--el-color-primary);
  --el-switch-off-color: var(--el-fill-color-darker);
}
:deep(.el-form-item) {
  margin-bottom: 12px;
}
:deep(.el-tabs__header) {
  margin-bottom: 16px;
}
</style>
