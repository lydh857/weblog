<template>
  <div v-loading="pendingLoading" class="info-card todo-card">
    <div class="info-card-title todo-title-row">
      <span>代办事项</span>
      <span class="todo-title-total">总计 {{ formatNumber(todoTotal) }}</span>
    </div>

    <div class="todo-summary">
      <div class="todo-summary-item">
        <span class="todo-summary-label">待处理总数</span>
        <span class="todo-summary-value">{{ formatNumber(todoTotal) }}</span>
      </div>
      <div class="todo-summary-item">
        <span class="todo-summary-label">涉及类型</span>
        <span class="todo-summary-value">{{ todoActiveTypes }} 项</span>
      </div>
    </div>

    <div class="todo-grid">
      <button
        v-for="item in todoItems"
        :key="item.key"
        type="button"
        class="todo-task"
        :class="[`todo-task--${item.tone}`, { 'is-empty': item.count === 0, 'is-active': item.count > 0 }]"
        @click="$emit('navigate', item.path, item.query)"
      >
        <div class="todo-task-head">
          <div class="todo-task-main">
            <span class="todo-task-dot" />
            <span class="todo-task-label">{{ item.label }}</span>
          </div>
          <span class="todo-task-count">{{ formatNumber(item.count) }}</span>
        </div>
        <span class="todo-task-action">{{ item.count > 0 ? '进入处理' : '查看列表' }}</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
interface TodoTask {
  key: string
  label: string
  count: number
  path: string
  tone: 'primary' | 'warning' | 'success' | 'info'
  query?: Record<string, string>
}

defineProps<{
  pendingLoading: boolean
  todoTotal: number
  todoActiveTypes: number
  todoItems: TodoTask[]
}>()

defineEmits<{
  (e: 'navigate', path: string, query?: Record<string, string>): void
}>()

function formatNumber(num: number): string {
  return num.toLocaleString()
}
</script>

<style scoped>
/* 代办事项 */
.todo-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.todo-title-total {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
  background: var(--admin-panel-bg-soft);
}

.todo-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.todo-summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--admin-panel-bg-soft);
}

.todo-summary-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.todo-summary-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.todo-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.todo-task {
  border: none;
  border-radius: 8px;
  background: var(--admin-panel-bg-soft);
  min-height: 72px;
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 6px;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.todo-task:hover {
  background: var(--admin-panel-hover);
}

.todo-task:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: 1px;
}

.todo-task.is-empty {
  opacity: 0.72;
}

.todo-task-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.todo-task-main {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.todo-task-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-text-color-placeholder);
}

.todo-task--primary .todo-task-dot {
  background: var(--el-color-primary);
}

.todo-task--warning .todo-task-dot {
  background: var(--el-color-warning);
}

.todo-task--success .todo-task-dot {
  background: var(--el-color-success);
}

.todo-task--info .todo-task-dot {
  background: var(--el-color-info);
}

.todo-task-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
}

.todo-task-count {
  font-size: 18px;
  line-height: 1;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.todo-task.is-active .todo-task-count {
  color: var(--el-text-color-primary);
}

.todo-task--primary.is-active .todo-task-count {
  color: var(--el-color-primary);
}

.todo-task--warning.is-active .todo-task-count {
  color: var(--el-color-warning);
}

.todo-task--success.is-active .todo-task-count {
  color: var(--el-color-success);
}

.todo-task--info.is-active .todo-task-count {
  color: var(--el-color-info);
}

.todo-task-action {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.todo-task.is-active .todo-task-action {
  color: var(--el-text-color-regular);
}

.info-card {
  background: var(--admin-panel-bg);
  border: 1px solid var(--admin-panel-border);
  border-radius: 12px;
  padding: 16px;
  transition: background-color 0.3s ease, border-color 0.3s ease;
  height: 100%;
  box-sizing: border-box;
}

.info-card-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 14px;
}
</style>
