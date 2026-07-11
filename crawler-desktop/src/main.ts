import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { createRouter, createWebHashHistory } from 'vue-router'
import {
  NAlert,
  NButton,
  NCard,
  NConfigProvider,
  NDataTable,
  NDynamicTags,
  NEmpty,
  NForm,
  NFormItem,
  NFormItemGi,
  NGrid,
  NLayout,
  NLayoutContent,
  NLayoutSider,
  NInput,
  NInputNumber,
  NMessageProvider,
  NMenu,
  NSelect,
  NSpace,
  NSpin,
  NSwitch,
  NTag,
  darkTheme
} from 'naive-ui'
import App from './App.vue'
const routes = [
  { path: '/', redirect: '/tasks' },
  { path: '/tasks', component: () => import('./views/TaskManagementView.vue') },
  { path: '/candidates', component: () => import('./views/CandidatePoolView.vue') },
  { path: '/candidates/:id', component: () => import('./views/CandidateDetailView.vue') },
  { path: '/site-profiles', component: () => import('./views/SiteProfileView.vue') },
  { path: '/assets', component: () => import('./views/AssetManagementView.vue') },
  { path: '/push-targets', component: () => import('./views/PushTargetView.vue') },
  { path: '/backend-cleanup', component: () => import('./views/BackendCleanupView.vue') }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

const queryClient = new QueryClient()

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(VueQueryPlugin, { queryClient })
app.component('NConfigProvider', NConfigProvider)
app.component('NLayout', NLayout)
app.component('NLayoutSider', NLayoutSider)
app.component('NLayoutContent', NLayoutContent)
app.component('NMenu', NMenu)
app.component('NSelect', NSelect)
app.component('NMessageProvider', NMessageProvider)
app.component('NCard', NCard)
app.component('NSpace', NSpace)
app.component('NInput', NInput)
app.component('NButton', NButton)
app.component('NDataTable', NDataTable)
app.component('NForm', NForm)
app.component('NFormItem', NFormItem)
app.component('NDynamicTags', NDynamicTags)
app.component('NGrid', NGrid)
app.component('NFormItemGi', NFormItemGi)
app.component('NInputNumber', NInputNumber)
app.component('NSwitch', NSwitch)
app.component('NTag', NTag)
app.component('NAlert', NAlert)
app.component('NEmpty', NEmpty)
app.component('NSpin', NSpin)
app.provide('theme', darkTheme)
app.mount('#app')
